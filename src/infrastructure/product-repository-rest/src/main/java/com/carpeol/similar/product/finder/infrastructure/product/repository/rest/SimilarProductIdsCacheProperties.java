package com.carpeol.similar.product.finder.infrastructure.product.repository.rest;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("similar-products.cache")
public record SimilarProductIdsCacheProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("5m") Duration ttl,
        @DefaultValue("1m") Duration notFoundTtl) {

    public SimilarProductIdsCacheProperties {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("similar-products.cache.ttl must be positive");
        }
        if (notFoundTtl == null || notFoundTtl.isZero() || notFoundTtl.isNegative()) {
            throw new IllegalArgumentException("similar-products.cache.not-found-ttl must be positive");
        }
    }
}
