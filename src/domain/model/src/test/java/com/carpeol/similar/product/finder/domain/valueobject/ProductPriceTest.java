package com.carpeol.similar.product.finder.domain.valueobject;

import com.carpeol.similar.product.finder.domain.exception.InvalidProductField;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductPriceTest {

    @Test
    void acceptsPositivePrice() {
        BigDecimal price = new BigDecimal("12.50");

        assertEquals(price, new ProductPrice(price).value());
    }

    @Test
    void acceptsZeroPrice() {
        assertEquals(BigDecimal.ZERO, new ProductPrice(BigDecimal.ZERO).value());
    }

    @Test
    void rejectsNullPrice() {
        assertThrows(InvalidProductField.class, () -> new ProductPrice(null));
    }

    @Test
    void rejectsNegativePrice() {
        assertThrows(InvalidProductField.class, () -> new ProductPrice(new BigDecimal("-0.01")));
    }
}
