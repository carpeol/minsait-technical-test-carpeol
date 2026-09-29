package com.carpeol.similar.product.finder.boot;

import com.carpeol.similar.product.finder.infrastructure.product.repository.rest.ProductRepositoryRestConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication(scanBasePackages = "com.carpeol.similar.product.finder")
@Import(ProductRepositoryRestConfiguration.class)
public class SimilarProductFinderApplication {

    static void main(String[] args) {
        SpringApplication.run(SimilarProductFinderApplication.class, args);
    }
}
