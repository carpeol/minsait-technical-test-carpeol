package com.carpeol.similar.product.finder.application.service;

import com.carpeol.similar.product.finder.application.port.in.FindSimilarProductsUseCase;
import com.carpeol.similar.product.finder.application.query.FindSimilarProductsQuery;
import com.carpeol.similar.product.finder.application.result.SimilarProduct;
import com.carpeol.similar.product.finder.domain.exception.ProductNotFound;
import com.carpeol.similar.product.finder.domain.model.Product;
import com.carpeol.similar.product.finder.domain.repository.ProductRepository;
import com.carpeol.similar.product.finder.domain.valueobject.ProductId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

public class FindSimilarProductsService implements FindSimilarProductsUseCase {

    private static final Logger LOGGER = LoggerFactory.getLogger(FindSimilarProductsService.class);

    private final ProductRepository productRepository;

    public FindSimilarProductsService(ProductRepository productRepository) {
        this.productRepository = Objects.requireNonNull(productRepository, "productRepository must not be null");
    }

    @Override
    public List<SimilarProduct> findSimilarProducts(FindSimilarProductsQuery query) {
        Objects.requireNonNull(query, "query must not be null");

        LOGGER.info("Starting similar product lookup for productId={}", query.productId().value());

        if (!productRepository.existsById(query.productId())) {
            throw new ProductNotFound(query.productId());
        }

        List<ProductId> similarProductIds = productRepository.findSimilarProductIds(query.productId());

        return productRepository.findByIds(similarProductIds).stream()
                .map(this::toSimilarProduct)
                .toList();
    }

    private SimilarProduct toSimilarProduct(Product product) {
        return new SimilarProduct(
                product.productId().value(),
                product.productName().value(),
                product.productPrice().value(),
                product.productAvailability().value());
    }
}
