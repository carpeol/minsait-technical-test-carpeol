package com.carpeol.similar.product.finder.domain.valueobject;

import com.carpeol.similar.product.finder.domain.exception.InvalidProductField;

public record ProductId(Long value) {

    public ProductId {
        if (value == null || value <= 0) {
            throw new InvalidProductField("productId", String.valueOf(value));
        }
    }
}
