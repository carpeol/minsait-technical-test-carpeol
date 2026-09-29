package com.carpeol.similar.product.finder.application.result;

import java.math.BigDecimal;

public record SimilarProduct(
        Long id,
        String name,
        BigDecimal price,
        boolean availability) {
}
