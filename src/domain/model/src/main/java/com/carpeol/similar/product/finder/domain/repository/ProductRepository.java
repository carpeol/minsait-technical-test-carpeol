package com.carpeol.similar.product.finder.domain.repository;

import com.carpeol.similar.product.finder.domain.exception.ProductNotFound;
import com.carpeol.similar.product.finder.domain.model.Product;
import com.carpeol.similar.product.finder.domain.valueobject.ProductId;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {

    Optional<Product> findById(ProductId productId);

    default Product getById(ProductId productId) {
        return findById(productId).orElseThrow(() -> new ProductNotFound(productId));
    }

    List<ProductId> findSimilarProductIds(ProductId productId);

    List<Product> findByIds(List<ProductId> productIds);

    boolean existsById(ProductId productId);
}
