package com.carpeol.similar.product.finder.domain.valueobject;

import com.carpeol.similar.product.finder.domain.exception.InvalidProductField;

public record ProductName(String value) {

    public ProductName {
        if (value == null || value.isBlank()) {
            throw new InvalidProductField("name", String.valueOf(value));
        }
    }
}
