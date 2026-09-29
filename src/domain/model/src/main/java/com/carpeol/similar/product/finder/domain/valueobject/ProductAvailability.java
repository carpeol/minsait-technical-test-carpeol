package com.carpeol.similar.product.finder.domain.valueobject;

import com.carpeol.similar.product.finder.domain.exception.InvalidProductField;

public record ProductAvailability(Boolean value) {

    public ProductAvailability {
        if (value == null) {
            throw new InvalidProductField("availability", null);
        }
    }
}
