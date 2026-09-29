package com.carpeol.similar.product.finder.domain.exception;

import com.carpeol.similar.product.finder.domain.valueobject.ProductId;

public class ProductNotFound extends RuntimeException {
    public ProductNotFound(ProductId productId) {
        super("Product not found: " + productId);
    }
}
