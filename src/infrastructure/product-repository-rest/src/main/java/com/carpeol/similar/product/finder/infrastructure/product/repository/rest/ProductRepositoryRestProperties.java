package com.carpeol.similar.product.finder.infrastructure.product.repository.rest;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties("similar-products.api")
public record ProductRepositoryRestProperties(
        URI baseUrl,
        @DefaultValue("5s") Duration connectTimeout,
        @DefaultValue("10s") Duration readTimeout) {

    public ProductRepositoryRestProperties {
        if (baseUrl == null) {
            throw new IllegalArgumentException("similar-products.api.base-url must not be null");
        }
        if (connectTimeout == null || connectTimeout.isZero() || connectTimeout.isNegative()) {
            throw new IllegalArgumentException("similar-products.api.connect-timeout must be positive");
        }
        if (readTimeout == null || readTimeout.isZero() || readTimeout.isNegative()) {
            throw new IllegalArgumentException("similar-products.api.read-timeout must be positive");
        }
    }
}
