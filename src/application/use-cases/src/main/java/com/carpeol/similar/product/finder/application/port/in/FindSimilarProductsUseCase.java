package com.carpeol.similar.product.finder.application.port.in;

import com.carpeol.similar.product.finder.application.query.FindSimilarProductsQuery;
import com.carpeol.similar.product.finder.application.result.SimilarProduct;

import java.util.List;

public interface FindSimilarProductsUseCase {

    List<SimilarProduct> findSimilarProducts(FindSimilarProductsQuery query);
}
