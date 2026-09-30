package com.carpeol.similar.product.finder.infrastructure.product.repository.rest;

import com.carpeol.similar.product.finder.domain.exception.InvalidProductField;
import com.carpeol.similar.product.finder.domain.exception.ProductRepositoryError;
import com.carpeol.similar.product.finder.domain.model.Product;
import com.carpeol.similar.product.finder.domain.repository.ProductRepository;
import com.carpeol.similar.product.finder.domain.valueobject.ProductAvailability;
import com.carpeol.similar.product.finder.domain.valueobject.ProductId;
import com.carpeol.similar.product.finder.domain.valueobject.ProductName;
import com.carpeol.similar.product.finder.domain.valueobject.ProductPrice;
import com.carpeol.similar.product.finder.infrastructure.product.repository.rest.generated.model.ProductDetail;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.*;

public class ProductRepositoryRestAdapter implements ProductRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProductRepositoryRestAdapter.class);

    private final ProductApiGateway productApiGateway;

    public ProductRepositoryRestAdapter(ProductApiGateway productApiGateway) {
        this.productApiGateway = Objects.requireNonNull(productApiGateway, "productApiGateway must not be null");
    }

    @Override
    public Optional<Product> findById(ProductId productId) {
        try {
            ResponseEntity<ProductDetail> response = requireResponse(
                    productApiGateway.getProduct(productId.value().toString()));
            if (HttpStatus.NOT_FOUND.equals(response.getStatusCode())) {
                return Optional.empty();
            }

            return Optional.of(toDomainProduct(requireSuccessfulResponse(response, "retrieving product details", productId)));
        } catch (InvalidProductField | NumberFormatException exception) {
            LOGGER.error("Product API returned invalid details for productId={}", productId.value(), exception);
            throw new ProductRepositoryError("Invalid product details returned for ID: " + productId.value(), exception);
        } catch (CallNotPermittedException | BulkheadFullException exception) {
            LOGGER.error("Resilience {} error for productId{}: {}", exception.getClass().getSimpleName(), productId.value(), exception.getMessage());
            throw new ProductRepositoryError("Resilience error retrieving product details for ID: " + productId.value(), exception);
        } catch (RestClientException exception) {
            logRestClientFailure("Product details request failed", productId, exception);
            throw new ProductRepositoryError("Error retrieving product details for ID: " + productId.value(), exception);
        }
    }

    @Override
    public List<Product> findByIds(List<ProductId> productIds) {
        Objects.requireNonNull(productIds, "productIds must not be null");

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Optional<Product>>> productFutures = productIds.stream()
                    .map(productId -> executor.submit(() -> findSimilarProductById(productId)))
                    .toList();

            return productFutures.stream()
                    .map(this::getProductResult)
                    .flatMap(Optional::stream)
                    .toList();
        }
    }

    @Override
    @Cacheable(cacheNames = "similar_product_ids", key = "#p0")
    public List<ProductId> findSimilarProductIds(ProductId productId) {
        Objects.requireNonNull(productId, "productId must not be null");
        return retrieveSimilarProductIds(productId);
    }

    private List<ProductId> retrieveSimilarProductIds(ProductId productId) {
        try {
            ResponseEntity<java.util.Set<String>> response = requireResponse(
                    productApiGateway.getSimilarProductIds(productId.value().toString()));

            return requireSuccessfulResponse(response, "retrieving similar product IDs", productId).stream()
                    .map(value -> new ProductId(Long.valueOf(value)))
                    .toList();
        } catch (InvalidProductField | NumberFormatException exception) {
            LOGGER.error("Product API returned invalid similar product IDs for productId={}", productId.value(), exception);
            throw new ProductRepositoryError(
                    "Invalid similar product IDs returned for ID: " + productId.value(), exception);
        } catch (CallNotPermittedException | BulkheadFullException exception) {
            LOGGER.error("Resilience {} error for retrieving similar products for productId={}: {}", exception.getClass().getSimpleName(), productId.value(), exception.getMessage());
            throw new ProductRepositoryError("Resilience error retrieving product details for ID: " + productId.value(), exception);
        } catch (RestClientException exception) {
            logRestClientFailure("Similar product IDs request failed", productId, exception);
            throw new ProductRepositoryError("Error retrieving similar product IDs for ID: " + productId.value(), exception);
        }
    }

    @Override
    @Cacheable(cacheNames = "not_found_products", key = "#p0", unless = "#result")
    public boolean existsById(ProductId productId) {
        try {
            ResponseEntity<ProductDetail> response = requireResponse(
                    productApiGateway.getProduct(productId.value().toString()));
            if (HttpStatus.NOT_FOUND.equals(response.getStatusCode())) {
                return false;
            }

            requireSuccessfulStatus(response, "checking existence", productId);
            return true;
        } catch (CallNotPermittedException | BulkheadFullException exception) {
            LOGGER.error("Resilience {} error checking existence of productId={}: {}", exception.getClass().getSimpleName(), productId.value(), exception.getMessage());
            throw new ProductRepositoryError("Resilience error retrieving product details for ID: " + productId.value(), exception);
        } catch (RestClientException exception) {
            logRestClientFailure("Product existence check failed", productId, exception);
            throw new ProductRepositoryError("Error checking existence for product ID: " + productId.value(), exception);
        }
    }

    private void logRestClientFailure(String message, ProductId productId, RestClientException exception) {
        if (!isTimeout(exception)) {
            LOGGER.error("{} for productId={}", message, productId.value(), exception);
        }
    }

    private boolean isTimeout(Throwable exception) {
        for (Throwable cause = exception; cause != null && cause != cause.getCause(); cause = cause.getCause()) {
            if (cause instanceof HttpTimeoutException
                    || cause instanceof SocketTimeoutException
                    || cause instanceof TimeoutException) {
                return true;
            }
        }
        return false;
    }

    private Product toDomainProduct(ProductDetail productDetail) {
        return new Product(
                new ProductId(Long.valueOf(productDetail.getId())),
                new ProductName(productDetail.getName()),
                new ProductPrice(productDetail.getPrice()),
                new ProductAvailability(productDetail.getAvailability()));
    }

    private Optional<Product> getProductResult(Future<Optional<Product>> productFuture) {
        try {
            return productFuture.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ProductRepositoryError("Interrupted while retrieving similar product details", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new ProductRepositoryError("Error retrieving similar product details", cause);
        }
    }

    private Optional<Product> findSimilarProductById(ProductId productId) {
        Optional<Product> product = findById(productId);
        if (product.isEmpty()) {
            LOGGER.warn("Similar product details not found for productId={}", productId.value());
        }
        return product;
    }

    private <T> T requireResponse(T response) {
        return Objects.requireNonNull(response, "Product API returned an empty response body");
    }

    private <T> T requireSuccessfulResponse(ResponseEntity<T> response, String operation, ProductId productId) {
        requireSuccessfulStatus(response, operation, productId);
        return requireResponse(response.getBody());
    }

    private void requireSuccessfulStatus(ResponseEntity<?> response, String operation, ProductId productId) {
        if (!response.getStatusCode().is2xxSuccessful()) {
            LOGGER.error(
                    "Product API returned status {} while {} for productId={}",
                    response.getStatusCode(),
                    operation,
                    productId.value());
            throw new ProductRepositoryError(
                    "Product API returned status " + response.getStatusCode() + " while " + operation + " for ID: "
                            + productId.value());
        }
    }
}
