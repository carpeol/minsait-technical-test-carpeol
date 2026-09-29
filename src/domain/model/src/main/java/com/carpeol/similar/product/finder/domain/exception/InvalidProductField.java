package com.carpeol.similar.product.finder.domain.exception;

public class InvalidProductField extends RuntimeException {
    public InvalidProductField(String fieldName, String value) {
        super("Invalid value for field " + fieldName + ": " + value);
    }
}
