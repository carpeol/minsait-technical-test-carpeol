package com.carpeol.similar.product.finder.infrastructure.product.repository.rest;

import com.carpeol.similar.product.finder.domain.exception.ProductRepositoryError;
import com.carpeol.similar.product.finder.domain.model.Product;
import com.carpeol.similar.product.finder.domain.valueobject.ProductAvailability;
import com.carpeol.similar.product.finder.domain.valueobject.ProductId;
import com.carpeol.similar.product.finder.domain.valueobject.ProductName;
import com.carpeol.similar.product.finder.domain.valueobject.ProductPrice;
import com.carpeol.similar.product.finder.infrastructure.product.repository.rest.generated.api.DefaultApi;
import com.carpeol.similar.product.finder.infrastructure.product.repository.rest.generated.model.ProductDetail;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductRepositoryRestAdapterTest {

    private final DefaultApi productApi = mock(DefaultApi.class);
    private final ProductRepositoryRestAdapter repository =
            new ProductRepositoryRestAdapter(new ProductApiGateway(productApi));

    @Test
    void findsProductAndMapsGeneratedModelToTheDomainModel() {
        when(productApi.getProductProductId("1")).thenReturn(ResponseEntity.ok(productDetail("1", "Shirt")));

        Optional<Product> product = repository.findById(new ProductId(1L));

        assertEquals(new Product(
                        new ProductId(1L),
                        new ProductName("Shirt"),
                        new ProductPrice(new BigDecimal("9.99")),
                        new ProductAvailability(true)),
                product.orElseThrow());
    }

    @Test
    void returnsEmptyWhenProductResponseHasNotFoundStatus() {
        when(productApi.getProductProductId("5")).thenReturn(ResponseEntity.status(HttpStatus.NOT_FOUND).build());

        assertEquals(Optional.empty(), repository.findById(new ProductId(5L)));
    }

    @Test
    void returnsEmptyWhenClientThrowsNotFound() {
        when(productApi.getProductProductId("5")).thenThrow(notFoundException());

        assertEquals(Optional.empty(), repository.findById(new ProductId(5L)));
    }

    @Test
    void findsSimilarProductsConcurrentlyInRequestedOrderAndSkipsMissingDetails() {
        when(productApi.getProductProductId("2")).thenReturn(ResponseEntity.ok(productDetail("2", "First")));
        when(productApi.getProductProductId("3")).thenReturn(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
        when(productApi.getProductProductId("4")).thenReturn(ResponseEntity.ok(productDetail("4", "Last")));

        List<Product> products = repository.findByIds(List.of(
                new ProductId(2L),
                new ProductId(3L),
                new ProductId(4L)));

        assertEquals(List.of(
                new Product(new ProductId(2L), new ProductName("First"),
                        new ProductPrice(new BigDecimal("9.99")), new ProductAvailability(true)),
                new Product(new ProductId(4L), new ProductName("Last"),
                        new ProductPrice(new BigDecimal("9.99")), new ProductAvailability(true))), products);
    }

    @Test
    void retrievesProductDetailsConcurrently() {
        CountDownLatch started = new CountDownLatch(2);
        AtomicBoolean bothRequestsRanConcurrently = new AtomicBoolean(true);
        when(productApi.getProductProductId(anyString())).thenAnswer(invocation -> {
            String id = invocation.getArgument(0);
            started.countDown();
            if (!started.await(2, TimeUnit.SECONDS)) {
                bothRequestsRanConcurrently.set(false);
            }
            return ResponseEntity.ok(productDetail(id, "Product " + id));
        });

        repository.findByIds(List.of(new ProductId(2L), new ProductId(3L)));

        assertTrue(bothRequestsRanConcurrently.get(), "Product detail requests should overlap");
    }

    @Test
    void wrapsTransportErrorsWhileFindingProduct() {
        ResourceAccessException cause = new ResourceAccessException("Connection refused");
        when(productApi.getProductProductId("1")).thenThrow(cause);

        ProductRepositoryError exception = assertThrows(
                ProductRepositoryError.class,
                () -> repository.findById(new ProductId(1L)));

        assertEquals("Error retrieving product details for ID: 1", exception.getMessage());
        assertEquals(cause, exception.getCause());
    }

    @Test
    void rejectsUnsuccessfulProductResponseOtherThanNotFound() {
        when(productApi.getProductProductId("1")).thenReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());

        ProductRepositoryError exception = assertThrows(
                ProductRepositoryError.class,
                () -> repository.findById(new ProductId(1L)));

        assertEquals("Product API returned status 500 INTERNAL_SERVER_ERROR while retrieving product details for ID: 1",
                exception.getMessage());
    }

    @Test
    void wrapsInvalidProductDetailsReturnedByTheProductApi() {
        when(productApi.getProductProductId("1"))
                .thenReturn(ResponseEntity.ok(productDetail("invalid", "Shirt")));

        ProductRepositoryError exception = assertThrows(
                ProductRepositoryError.class,
                () -> repository.findById(new ProductId(1L)));

        assertEquals("Invalid product details returned for ID: 1", exception.getMessage());
    }

    @Test
    void preservesTheOrderFromTheGeneratedClient() {
        when(productApi.getProductSimilarids("1"))
                .thenReturn(ResponseEntity.ok(new LinkedHashSet<>(List.of("2", "3", "4"))));

        assertEquals(
                List.of(new ProductId(2L), new ProductId(3L), new ProductId(4L)),
                repository.findSimilarProductIds(new ProductId(1L)));
    }

    @Test
    void rejectsUnsuccessfulSimilarProductResponse() {
        when(productApi.getProductSimilarids("1"))
                .thenReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());

        ProductRepositoryError exception = assertThrows(
                ProductRepositoryError.class,
                () -> repository.findSimilarProductIds(new ProductId(1L)));

        assertEquals("Product API returned status 500 INTERNAL_SERVER_ERROR while retrieving similar product IDs for ID: 1",
                exception.getMessage());
    }

    @Test
    void wrapsInvalidSimilarProductIdsReturnedByTheProductApi() {
        when(productApi.getProductSimilarids("1"))
                .thenReturn(ResponseEntity.ok(new LinkedHashSet<>(List.of("not-a-number"))));

        ProductRepositoryError exception = assertThrows(
                ProductRepositoryError.class,
                () -> repository.findSimilarProductIds(new ProductId(1L)));

        assertEquals("Invalid similar product IDs returned for ID: 1", exception.getMessage());
    }

    @Test
    void returnsTrueWhenProductExists() {
        when(productApi.getProductProductId("1")).thenReturn(ResponseEntity.ok(productDetail("1", "Shirt")));

        assertTrue(repository.existsById(new ProductId(1L)));

        verify(productApi).getProductProductId("1");
    }

    @Test
    void returnsFalseWhenProductDoesNotExist() {
        when(productApi.getProductProductId("5")).thenReturn(ResponseEntity.status(HttpStatus.NOT_FOUND).build());

        assertFalse(repository.existsById(new ProductId(5L)));
    }

    @Test
    void rejectsUnsuccessfulExistenceResponseOtherThanNotFound() {
        when(productApi.getProductProductId("1")).thenReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());

        ProductRepositoryError exception = assertThrows(
                ProductRepositoryError.class,
                () -> repository.existsById(new ProductId(1L)));

        assertEquals("Product API returned status 500 INTERNAL_SERVER_ERROR while checking existence for ID: 1",
                exception.getMessage());
    }

    private ProductDetail productDetail(String id, String name) {
        return new ProductDetail()
                .id(id)
                .name(name)
                .price(new BigDecimal("9.99"))
                .availability(true);
    }

    private HttpClientErrorException notFoundException() {
        return HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", null, null, null);
    }
}
