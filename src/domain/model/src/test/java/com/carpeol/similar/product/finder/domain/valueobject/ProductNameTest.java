package com.carpeol.similar.product.finder.domain.valueobject;

import com.carpeol.similar.product.finder.domain.exception.InvalidProductField;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductNameTest {

    @Test
    void acceptsNonBlankName() {
        assertEquals("Product name", new ProductName("Product name").value());
    }

    @Test
    void rejectsNullName() {
        assertThrows(InvalidProductField.class, () -> new ProductName(null));
    }

    @Test
    void rejectsEmptyName() {
        assertThrows(InvalidProductField.class, () -> new ProductName(""));
    }

    @Test
    void rejectsBlankName() {
        assertThrows(InvalidProductField.class, () -> new ProductName(" \t\n"));
    }
}
