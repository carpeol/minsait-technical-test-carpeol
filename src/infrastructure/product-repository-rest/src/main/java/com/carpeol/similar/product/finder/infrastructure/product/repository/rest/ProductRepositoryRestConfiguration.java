package com.carpeol.similar.product.finder.infrastructure.product.repository.rest;

import com.carpeol.similar.product.finder.domain.repository.ProductRepository;
import com.carpeol.similar.product.finder.infrastructure.product.repository.rest.generated.api.DefaultApi;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.net.http.HttpClient;

@Configuration(proxyBeanMethods = false)
@EnableCaching
@EnableConfigurationProperties({ProductRepositoryRestProperties.class, SimilarProductIdsCacheProperties.class})
public class ProductRepositoryRestConfiguration {

    @Bean
    DefaultApi productApi(ProductRepositoryRestProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());

        RestClient restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
        HttpServiceProxyFactory proxyFactory = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build();

        return proxyFactory.createClient(DefaultApi.class);
    }

    @Bean
    ProductApiGateway productApiGateway(DefaultApi productApi) {
        return new ProductApiGateway(productApi);
    }

    @Bean
    CacheManager cacheManager(SimilarProductIdsCacheProperties properties) {
        if (!properties.enabled()) {
            return new NoOpCacheManager();
        }

        CaffeineCacheManager cacheManager = new CaffeineCacheManager("similar_product_ids");
        cacheManager.setCaffeine(Caffeine.newBuilder().expireAfterWrite(properties.ttl()));
        cacheManager.registerCustomCache(
                "not_found_products",
                Caffeine.newBuilder().expireAfterWrite(properties.notFoundTtl()).build());
        return cacheManager;
    }

    @Bean
    ProductRepository productRepository(ProductApiGateway productApiGateway) {
        return new ProductRepositoryRestAdapter(productApiGateway);
    }
}
