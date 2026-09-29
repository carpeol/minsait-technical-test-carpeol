package com.carpeol.similar.product.finder.boot.configuration;

import com.carpeol.similar.product.finder.application.port.in.FindSimilarProductsUseCase;
import com.carpeol.similar.product.finder.application.service.FindSimilarProductsService;
import com.carpeol.similar.product.finder.domain.repository.ProductRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ApplicationConfiguration {

    @Bean
    FindSimilarProductsUseCase findSimilarProductsUseCase(ProductRepository productRepository) {
        return new FindSimilarProductsService(productRepository);
    }
}
