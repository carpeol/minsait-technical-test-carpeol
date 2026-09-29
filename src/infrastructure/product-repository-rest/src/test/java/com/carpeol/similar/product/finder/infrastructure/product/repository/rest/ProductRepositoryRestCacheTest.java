package com.carpeol.similar.product.finder.infrastructure.product.repository.rest;

import com.carpeol.similar.product.finder.domain.exception.ProductRepositoryError;
import com.carpeol.similar.product.finder.domain.repository.ProductRepository;
import com.carpeol.similar.product.finder.domain.valueobject.ProductId;
import com.carpeol.similar.product.finder.infrastructure.product.repository.rest.generated.api.DefaultApi;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductRepositoryRestCacheTest {

    @Test
    void cachesSimilarProductIdsAndExpiresThemAfterConfiguredTtl() throws InterruptedException {
        try (AnnotationConfigApplicationContext context = createContext(true, "100ms", "100ms")) {
            ProductRepository repository = context.getBean(ProductRepository.class);
            DefaultApi productApi = context.getBean("testProductApi", DefaultApi.class);
            when(productApi.getProductSimilarids("1"))
                    .thenReturn(ResponseEntity.ok(new LinkedHashSet<>(List.of("2", "3"))));

            List<ProductId> expected = List.of(new ProductId(2L), new ProductId(3L));
            assertEquals(expected, repository.findSimilarProductIds(new ProductId(1L)));
            assertEquals(expected, repository.findSimilarProductIds(new ProductId(1L)));
            verify(productApi, times(1)).getProductSimilarids("1");

            TimeUnit.MILLISECONDS.sleep(250);

            assertEquals(expected, repository.findSimilarProductIds(new ProductId(1L)));
            verify(productApi, times(2)).getProductSimilarids("1");
            assertInstanceOf(CaffeineCacheManager.class, context.getBean(CacheManager.class));
        }
    }

    @Test
    void bypassesCacheWhenDisabled() {
        try (AnnotationConfigApplicationContext context = createContext(false, "5m", "1m")) {
            ProductRepository repository = context.getBean(ProductRepository.class);
            DefaultApi productApi = context.getBean("testProductApi", DefaultApi.class);
            when(productApi.getProductSimilarids("1"))
                    .thenReturn(ResponseEntity.ok(new LinkedHashSet<>(List.of("2"))));
            when(productApi.getProductProductId("1")).thenReturn(ResponseEntity.notFound().build());

            repository.findSimilarProductIds(new ProductId(1L));
            repository.findSimilarProductIds(new ProductId(1L));
            assertFalse(repository.existsById(new ProductId(1L)));
            assertFalse(repository.existsById(new ProductId(1L)));

            verify(productApi, times(2)).getProductSimilarids("1");
            verify(productApi, times(2)).getProductProductId("1");
            assertInstanceOf(NoOpCacheManager.class, context.getBean(CacheManager.class));
        }
    }

    @Test
    void doesNotCacheFailedRequests() {
        try (AnnotationConfigApplicationContext context = createContext(true, "5m", "1m")) {
            ProductRepository repository = context.getBean(ProductRepository.class);
            DefaultApi productApi = context.getBean("testProductApi", DefaultApi.class);
            when(productApi.getProductSimilarids("1"))
                    .thenThrow(new ResourceAccessException("Connection refused"))
                    .thenReturn(ResponseEntity.ok(new LinkedHashSet<>(List.of("2"))));
            when(productApi.getProductProductId("9"))
                    .thenThrow(new ResourceAccessException("Connection refused"))
                    .thenReturn(ResponseEntity.notFound().build());

            assertThrows(ProductRepositoryError.class,
                    () -> repository.findSimilarProductIds(new ProductId(1L)));
            assertEquals(List.of(new ProductId(2L)),
                    repository.findSimilarProductIds(new ProductId(1L)));
            assertThrows(ProductRepositoryError.class,
                    () -> repository.existsById(new ProductId(9L)));
            assertFalse(repository.existsById(new ProductId(9L)));
            verify(productApi, times(2)).getProductSimilarids("1");
            verify(productApi, times(2)).getProductProductId("9");
        }
    }

    @Test
    void cachesNotFoundExistenceResultsAndExpiresThemAfterConfiguredTtl() throws InterruptedException {
        try (AnnotationConfigApplicationContext context = createContext(true, "5m", "100ms")) {
            ProductRepository repository = context.getBean(ProductRepository.class);
            DefaultApi productApi = context.getBean("testProductApi", DefaultApi.class);
            when(productApi.getProductProductId("9")).thenReturn(ResponseEntity.notFound().build());

            ProductId productId = new ProductId(9L);
            assertFalse(repository.existsById(productId));
            assertFalse(repository.existsById(productId));
            verify(productApi, times(1)).getProductProductId("9");

            TimeUnit.MILLISECONDS.sleep(250);

            assertFalse(repository.existsById(productId));
            verify(productApi, times(2)).getProductProductId("9");
        }
    }

    @Test
    void doesNotCacheNotFoundSimilarProductDetails() {
        try (AnnotationConfigApplicationContext context = createContext(true, "5m", "1m")) {
            ProductRepository repository = context.getBean(ProductRepository.class);
            DefaultApi productApi = context.getBean("testProductApi", DefaultApi.class);
            when(productApi.getProductProductId("9")).thenReturn(ResponseEntity.notFound().build());

            ProductId productId = new ProductId(9L);
            assertTrue(repository.findByIds(List.of(productId)).isEmpty());
            assertTrue(repository.findByIds(List.of(productId)).isEmpty());

            verify(productApi, times(2)).getProductProductId("9");
        }
    }

    @Test
    void doesNotCacheSuccessfulExistenceChecks() {
        try (AnnotationConfigApplicationContext context = createContext(true, "5m", "1m")) {
            ProductRepository repository = context.getBean(ProductRepository.class);
            DefaultApi productApi = context.getBean("testProductApi", DefaultApi.class);
            when(productApi.getProductProductId("1"))
                    .thenReturn(ResponseEntity.ok().build());

            assertTrue(repository.existsById(new ProductId(1L)));
            assertTrue(repository.existsById(new ProductId(1L)));

            verify(productApi, times(2)).getProductProductId("1");
        }
    }

    private AnnotationConfigApplicationContext createContext(
            boolean cacheEnabled, String cacheTtl, String notFoundCacheTtl) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                "test-properties",
                Map.of(
                        "similar-products.api.base-url", "http://localhost:3001",
                        "similar-products.cache.enabled", cacheEnabled,
                        "similar-products.cache.ttl", cacheTtl,
                        "similar-products.cache.not-found-ttl", notFoundCacheTtl)));
        context.register(TestConfiguration.class, ProductRepositoryRestConfiguration.class);
        context.refresh();
        return context;
    }

    @Configuration(proxyBeanMethods = false)
    static class TestConfiguration {

        @Bean
        @Primary
        DefaultApi testProductApi() {
            return mock(DefaultApi.class);
        }
    }
}
