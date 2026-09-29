package com.carpeol.similar.product.finder.application.service;

import com.carpeol.similar.product.finder.application.query.FindSimilarProductsQuery;
import com.carpeol.similar.product.finder.application.result.SimilarProduct;
import com.carpeol.similar.product.finder.domain.exception.ProductNotFound;
import com.carpeol.similar.product.finder.domain.model.Product;
import com.carpeol.similar.product.finder.domain.repository.ProductRepository;
import com.carpeol.similar.product.finder.domain.valueobject.ProductAvailability;
import com.carpeol.similar.product.finder.domain.valueobject.ProductId;
import com.carpeol.similar.product.finder.domain.valueobject.ProductName;
import com.carpeol.similar.product.finder.domain.valueobject.ProductPrice;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FindSimilarProductsServiceTest {

    private final ProductRepository productRepository = mock(ProductRepository.class, CALLS_REAL_METHODS);
    private final FindSimilarProductsService service = new FindSimilarProductsService(productRepository);
    private final ProductId productId = new ProductId(1L);

    @Test
    void returnsSimilarProductsInRepositoryOrder() {
        ProductId firstSimilarProductId = new ProductId(2L);
        ProductId secondSimilarProductId = new ProductId(3L);
        when(productRepository.existsById(productId)).thenReturn(true);
        when(productRepository.findSimilarProductIds(productId))
                .thenReturn(List.of(firstSimilarProductId, secondSimilarProductId));
        when(productRepository.findByIds(List.of(firstSimilarProductId, secondSimilarProductId)))
                .thenReturn(List.of(
                        product(firstSimilarProductId, "First similar product"),
                        product(secondSimilarProductId, "Second similar product")));

        List<SimilarProduct> result = service.findSimilarProducts(new FindSimilarProductsQuery(productId));

        assertEquals(List.of(
                new SimilarProduct(2L, "First similar product", new BigDecimal("12.50"), true),
                new SimilarProduct(3L, "Second similar product", new BigDecimal("12.50"), true)), result);
        verify(productRepository).existsById(productId);
        verify(productRepository).findSimilarProductIds(productId);
        verify(productRepository).findByIds(List.of(firstSimilarProductId, secondSimilarProductId));
    }

    @Test
    void returnsExistingProductsWhenSomeSimilarProductDetailsAreMissing() {
        ProductId existingSimilarProductId = new ProductId(2L);
        ProductId missingSimilarProductId = new ProductId(3L);
        when(productRepository.existsById(productId)).thenReturn(true);
        when(productRepository.findSimilarProductIds(productId))
                .thenReturn(List.of(existingSimilarProductId, missingSimilarProductId));
        when(productRepository.findByIds(List.of(existingSimilarProductId, missingSimilarProductId)))
                .thenReturn(List.of(product(existingSimilarProductId, "Existing similar product")));

        List<SimilarProduct> result = service.findSimilarProducts(new FindSimilarProductsQuery(productId));

        assertEquals(List.of(
                new SimilarProduct(2L, "Existing similar product", new BigDecimal("12.50"), true)), result);
        verify(productRepository).findByIds(List.of(existingSimilarProductId, missingSimilarProductId));
    }

    @Test
    void propagatesProductNotFoundWhenRequestedProductDoesNotExist() {
        when(productRepository.existsById(productId)).thenReturn(false);

        assertThrows(
                ProductNotFound.class,
                () -> service.findSimilarProducts(new FindSimilarProductsQuery(productId)));

        verify(productRepository).existsById(productId);
        verify(productRepository, never()).findSimilarProductIds(productId);
        verify(productRepository, never()).findByIds(anyList());
    }

    private Product product(ProductId id, String name) {
        return new Product(
                id,
                new ProductName(name),
                new ProductPrice(new BigDecimal("12.50")),
                new ProductAvailability(true));
    }
}
