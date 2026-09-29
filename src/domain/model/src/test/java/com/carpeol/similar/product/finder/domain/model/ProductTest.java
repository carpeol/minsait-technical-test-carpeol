package com.carpeol.similar.product.finder.domain.model;

import com.carpeol.similar.product.finder.domain.exception.InvalidProductField;
import com.carpeol.similar.product.finder.domain.valueobject.ProductAvailability;
import com.carpeol.similar.product.finder.domain.valueobject.ProductId;
import com.carpeol.similar.product.finder.domain.valueobject.ProductName;
import com.carpeol.similar.product.finder.domain.valueobject.ProductPrice;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductTest {

    private final ProductId productId = new ProductId(1L);
    private final ProductName productName = new ProductName("Product");
    private final ProductPrice productPrice = new ProductPrice(new BigDecimal("12.50"));
    private final ProductAvailability productAvailability = new ProductAvailability(true);

    @Test
    void createsProductWithAllRequiredFields() {
        Product product = new Product(productId, productName, productPrice, productAvailability);

        assertEquals(productId, product.productId());
        assertEquals(productName, product.productName());
        assertEquals(productPrice, product.productPrice());
        assertEquals(productAvailability, product.productAvailability());
    }

    @Test
    void rejectsNullProductId() {
        InvalidProductField exception = assertThrows(
                InvalidProductField.class,
                () -> new Product(null, productName, productPrice, productAvailability));

        assertEquals("Invalid value for field productId: null", exception.getMessage());
    }

    @Test
    void rejectsNullProductName() {
        InvalidProductField exception = assertThrows(
                InvalidProductField.class,
                () -> new Product(productId, null, productPrice, productAvailability));

        assertEquals("Invalid value for field productName: null", exception.getMessage());
    }

    @Test
    void rejectsNullProductPrice() {
        InvalidProductField exception = assertThrows(
                InvalidProductField.class,
                () -> new Product(productId, productName, null, productAvailability));

        assertEquals("Invalid value for field productPrice: null", exception.getMessage());
    }

    @Test
    void rejectsNullProductAvailability() {
        InvalidProductField exception = assertThrows(
                InvalidProductField.class,
                () -> new Product(productId, productName, productPrice, null));

        assertEquals("Invalid value for field productAvailability: null", exception.getMessage());
    }
}
