package com.carpeol.similar.product.finder.application.query;

import com.carpeol.similar.product.finder.domain.valueobject.ProductId;

import java.util.Objects;

public record FindSimilarProductsQuery(ProductId productId) {

    public FindSimilarProductsQuery {
        Objects.requireNonNull(productId, "productId must not be null");
    }
}
