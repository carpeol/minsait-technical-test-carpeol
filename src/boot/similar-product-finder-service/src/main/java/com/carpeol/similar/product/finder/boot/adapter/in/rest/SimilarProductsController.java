package com.carpeol.similar.product.finder.boot.adapter.in.rest;

import com.carpeol.similar.product.finder.application.port.in.FindSimilarProductsUseCase;
import com.carpeol.similar.product.finder.application.query.FindSimilarProductsQuery;
import com.carpeol.similar.product.finder.application.result.SimilarProduct;
import com.carpeol.similar.product.finder.boot.adapter.in.rest.generated.api.ProductApi;
import com.carpeol.similar.product.finder.boot.adapter.in.rest.generated.model.ProductDetail;
import com.carpeol.similar.product.finder.domain.exception.InvalidProductField;
import com.carpeol.similar.product.finder.domain.valueobject.ProductId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashSet;
import java.util.Set;

@RestController
public class SimilarProductsController implements ProductApi {

    private final FindSimilarProductsUseCase findSimilarProductsUseCase;

    public SimilarProductsController(FindSimilarProductsUseCase findSimilarProductsUseCase) {
        this.findSimilarProductsUseCase = findSimilarProductsUseCase;
    }

    @Override
    public ResponseEntity<Set<ProductDetail>> getProductSimilar(String productId) {
        Set<ProductDetail> similarProducts = findSimilarProductsUseCase
                .findSimilarProducts(new FindSimilarProductsQuery(toProductId(productId)))
                .stream()
                .map(this::toProductDetail)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        return ResponseEntity.ok(similarProducts);
    }

    private ProductId toProductId(String productId) {
        try {
            return new ProductId(Long.valueOf(productId));
        } catch (NumberFormatException | InvalidProductField exception) {
            throw new InvalidProductField("productId", productId);
        }
    }

    private ProductDetail toProductDetail(SimilarProduct product) {
        return new ProductDetail(
                product.id().toString(),
                product.name(),
                product.price(),
                product.availability());
    }
}
