package com.carpeol.similar.product.finder.domain.valueobject;

import com.carpeol.similar.product.finder.domain.exception.InvalidProductField;

import java.math.BigDecimal;

public record ProductPrice(BigDecimal value) {

    public ProductPrice {
        if (value == null || value.signum() < 0) {
            throw new InvalidProductField("price", String.valueOf(value));
        }
    }
}
