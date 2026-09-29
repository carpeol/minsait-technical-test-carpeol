package com.carpeol.similar.product.finder.domain.model;

import com.carpeol.similar.product.finder.domain.exception.InvalidProductField;
import com.carpeol.similar.product.finder.domain.valueobject.ProductAvailability;
import com.carpeol.similar.product.finder.domain.valueobject.ProductId;
import com.carpeol.similar.product.finder.domain.valueobject.ProductName;
import com.carpeol.similar.product.finder.domain.valueobject.ProductPrice;

import java.util.Objects;

public record Product(ProductId productId, ProductName productName, ProductPrice productPrice,
                      ProductAvailability productAvailability) {

    public Product(ProductId productId, ProductName productName, ProductPrice productPrice, ProductAvailability productAvailability) {

        require("productId", productId);
        require("productName", productName);
        require("productPrice", productPrice);
        require("productAvailability", productAvailability);

        this.productId = Objects.requireNonNull(productId, "productId must not be null");
        this.productName = Objects.requireNonNull(productName, "productName must not be null");
        this.productPrice = Objects.requireNonNull(productPrice, "productPrice must not be null");
        this.productAvailability = Objects.requireNonNull(productAvailability, "productAvailability must not be null");
    }

    private void require(String fieldName, Object value) {
        if (value == null) {
            throw new InvalidProductField(fieldName, null);
        }
    }
}
