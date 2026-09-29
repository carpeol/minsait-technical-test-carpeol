package com.carpeol.similar.product.finder.domain.repository;

import com.carpeol.similar.product.finder.domain.exception.ProductNotFound;
import com.carpeol.similar.product.finder.domain.model.Product;
import com.carpeol.similar.product.finder.domain.valueobject.ProductAvailability;
import com.carpeol.similar.product.finder.domain.valueobject.ProductId;
import com.carpeol.similar.product.finder.domain.valueobject.ProductName;
import com.carpeol.similar.product.finder.domain.valueobject.ProductPrice;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductRepositoryTest {

    private final ProductRepository repository = mock(ProductRepository.class);
    private final ProductId productId = new ProductId(1L);

    @Test
    void getByIdReturnsProductFoundById() {
        Product product = new Product(
                productId,
                new ProductName("Product"),
                new ProductPrice(new BigDecimal("12.50")),
                new ProductAvailability(true));
        when(repository.findById(productId)).thenReturn(Optional.of(product));
        doCallRealMethod().when(repository).getById(productId);

        Product result = repository.getById(productId);

        assertSame(product, result);
        verify(repository).findById(productId);
    }

    @Test
    void getByIdThrowsProductNotFoundWhenProductDoesNotExist() {
        when(repository.findById(productId)).thenReturn(Optional.empty());
        doCallRealMethod().when(repository).getById(productId);

        ProductNotFound exception = assertThrows(ProductNotFound.class, () -> repository.getById(productId));

        assertEquals("Product not found: ProductId[value=1]", exception.getMessage());
        verify(repository).findById(productId);
    }

    @Test
    void checksWhetherProductExistsById() {
        when(repository.existsById(productId)).thenReturn(true);

        boolean exists = repository.existsById(productId);

        assertTrue(exists);
        verify(repository).existsById(productId);
    }
}
