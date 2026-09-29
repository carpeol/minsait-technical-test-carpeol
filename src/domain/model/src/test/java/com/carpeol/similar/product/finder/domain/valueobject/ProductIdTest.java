package com.carpeol.similar.product.finder.domain.valueobject;

import com.carpeol.similar.product.finder.domain.exception.InvalidProductField;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductIdTest {

    @Test
    void acceptsPositiveId() {
        assertEquals(1L, new ProductId(1L).value());
    }

    @Test
    void rejectsNullId() {
        InvalidProductField exception = assertThrows(InvalidProductField.class, () -> new ProductId(null));

        assertEquals("Invalid value for field productId: null", exception.getMessage());
    }

    @Test
    void rejectsZeroId() {
        assertThrows(InvalidProductField.class, () -> new ProductId(0L));
    }

    @Test
    void rejectsNegativeId() {
        assertThrows(InvalidProductField.class, () -> new ProductId(-1L));
    }
}
