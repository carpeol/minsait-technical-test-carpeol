package com.carpeol.similar.product.finder.domain.exception;

public class ProductRepositoryError extends RuntimeException {
    public ProductRepositoryError(String message) {
        super(message);
    }

    public ProductRepositoryError(String message, Throwable cause) {
        super(message, cause);
    }
}
