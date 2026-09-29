package com.carpeol.similar.product.finder.infrastructure.product.repository.rest;

import com.carpeol.similar.product.finder.infrastructure.product.repository.rest.generated.api.DefaultApi;
import com.carpeol.similar.product.finder.infrastructure.product.repository.rest.generated.model.ProductDetail;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;

import java.util.Objects;
import java.util.Set;

public class ProductApiGateway {

    private final DefaultApi productApi;

    public ProductApiGateway(DefaultApi productApi) {
        this.productApi = Objects.requireNonNull(productApi, "productApi must not be null");
    }

    @CircuitBreaker(name = "product-api")
    public ResponseEntity<ProductDetail> getProduct(String productId) {
        try {
            return productApi.getProductProductId(productId);
        } catch (HttpClientErrorException.NotFound exception) {
            return ResponseEntity.notFound().build();
        }
    }

    @CircuitBreaker(name = "product-api")
    public ResponseEntity<Set<String>> getSimilarProductIds(String productId) {
        return productApi.getProductSimilarids(productId);
    }
}
