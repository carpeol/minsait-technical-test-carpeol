package com.carpeol.similar.product.finder.domain.valueobject;

import com.carpeol.similar.product.finder.domain.exception.InvalidProductField;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductAvailabilityTest {

    @Test
    void acceptsAvailableValue() {
        assertEquals(true, new ProductAvailability(true).value());
    }

    @Test
    void acceptsUnavailableValue() {
        assertEquals(false, new ProductAvailability(false).value());
    }

    @Test
    void rejectsNullAvailability() {
        assertThrows(InvalidProductField.class, () -> new ProductAvailability(null));
    }
}
