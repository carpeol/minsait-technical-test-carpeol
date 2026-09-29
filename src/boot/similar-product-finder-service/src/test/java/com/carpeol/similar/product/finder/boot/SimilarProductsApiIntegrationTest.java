package com.carpeol.similar.product.finder.boot;

import com.carpeol.similar.product.finder.infrastructure.product.repository.rest.generated.api.DefaultApi;
import com.carpeol.similar.product.finder.infrastructure.product.repository.rest.generated.model.ProductDetail;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(SimilarProductsApiIntegrationTest.ExternalProductApiMockConfiguration.class)
class SimilarProductsApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DefaultApi externalProductApi;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Test
    void returnsSimilarProductDetailsInSimilarityOrder() throws Exception {
        when(externalProductApi.getProductProductId("1")).thenReturn(ResponseEntity.ok(product("1", "Shirt", "9.99", true)));
        when(externalProductApi.getProductSimilarids("1"))
                .thenReturn(ResponseEntity.ok(new LinkedHashSet<>(List.of("2", "3"))));
        when(externalProductApi.getProductProductId("2")).thenReturn(ResponseEntity.ok(product("2", "Dress", "19.99", true)));
        when(externalProductApi.getProductProductId("3")).thenReturn(ResponseEntity.ok(product("3", "Blazer", "29.99", false)));

        mockMvc.perform(get("/product/1/similar"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$[0].id").value("2"))
                .andExpect(jsonPath("$[0].name").value("Dress"))
                .andExpect(jsonPath("$[0].price").value(19.99))
                .andExpect(jsonPath("$[0].availability").value(true))
                .andExpect(jsonPath("$[1].id").value("3"))
                .andExpect(jsonPath("$[1].name").value("Blazer"))
                .andExpect(jsonPath("$[1].price").value(29.99))
                .andExpect(jsonPath("$[1].availability").value(false));

        verify(externalProductApi).getProductProductId("1");
        verify(externalProductApi).getProductSimilarids("1");
        verify(externalProductApi).getProductProductId("2");
        verify(externalProductApi).getProductProductId("3");
    }

    @Test
    void returnsNotFoundWhenRequestedProductDoesNotExist() throws Exception {
        when(externalProductApi.getProductProductId("4")).thenReturn(ResponseEntity.status(HttpStatus.NOT_FOUND).build());

        mockMvc.perform(get("/product/4/similar"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Product not found"))
                .andExpect(jsonPath("$.detail").value("The requested product does not exist."));

        verify(externalProductApi).getProductProductId("4");
        verify(externalProductApi, never()).getProductSimilarids("4");
    }

    @Test
    void doesNotCountExpectedProductNotFoundAsCircuitBreakerFailure() throws Exception {
        CircuitBreaker circuitBreaker = productApiCircuitBreaker();
        circuitBreaker.reset();
        when(externalProductApi.getProductProductId("13")).thenThrow(
                HttpClientErrorException.create(
                        HttpStatus.NOT_FOUND, "Not Found", null, null, StandardCharsets.UTF_8));

        try {
            mockMvc.perform(get("/product/13/similar"))
                    .andExpect(status().isNotFound());

            org.junit.jupiter.api.Assertions.assertEquals(0, circuitBreaker.getMetrics().getNumberOfFailedCalls());
            org.junit.jupiter.api.Assertions.assertEquals(1, circuitBreaker.getMetrics().getNumberOfSuccessfulCalls());
        } finally {
            circuitBreaker.reset();
        }
    }

    @Test
    void returnsBadRequestForMalformedProductId() throws Exception {
        mockMvc.perform(get("/product/not-a-number/similar"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Invalid product field"));

        verify(externalProductApi, never()).getProductProductId("not-a-number");
    }

    @Test
    void returnsBadGatewayWhenProductApiReturnsServerError() throws Exception {
        when(externalProductApi.getProductProductId("8"))
                .thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "Upstream failure"));

        mockMvc.perform(get("/product/8/similar"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.title").value("Bad Gateway"));
    }

    @Test
    void returnsServiceUnavailableWhenProductApiCannotBeReached() throws Exception {
        when(externalProductApi.getProductProductId("9"))
                .thenThrow(new ResourceAccessException(
                        "Connection refused", new ConnectException("Connection refused")));

        mockMvc.perform(get("/product/9/similar"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.detail").value("The product service is temporarily unavailable."));
    }

    @Test
    void returnsGatewayTimeoutWhenProductApiTimesOut() throws Exception {
        when(externalProductApi.getProductProductId("10"))
                .thenThrow(new ResourceAccessException(
                        "Request timed out", new HttpTimeoutException("Response timeout")));

        mockMvc.perform(get("/product/10/similar"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.status").value(504))
                .andExpect(jsonPath("$.detail").value("The product service did not respond before the deadline."));
    }

    @Test
    void returnsGenericInternalErrorForUnexpectedExceptions() throws Exception {
        when(externalProductApi.getProductProductId("11"))
                .thenThrow(new IllegalStateException("internal implementation detail"));

        mockMvc.perform(get("/product/11/similar"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."));
    }

    @Test
    void returnsServiceUnavailableWhenCircuitBreakerIsOpen() throws Exception {
        CircuitBreaker productApiCircuitBreaker = productApiCircuitBreaker();
        productApiCircuitBreaker.reset();
        when(externalProductApi.getProductProductId("12"))
                .thenThrow(new ResourceAccessException("Connection refused"));

        try {
            for (int failure = 0; failure < 10; failure++) {
                mockMvc.perform(get("/product/12/similar"))
                        .andExpect(status().isServiceUnavailable());
            }

            mockMvc.perform(get("/product/12/similar"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.status").value(503))
                    .andExpect(jsonPath("$.detail").value("The product service is temporarily unavailable."));

            org.junit.jupiter.api.Assertions.assertEquals(CircuitBreaker.State.OPEN, productApiCircuitBreaker.getState());
            verify(externalProductApi, times(10)).getProductProductId("12");
        } finally {
            productApiCircuitBreaker.reset();
        }
    }

    private CircuitBreaker productApiCircuitBreaker() {
        return circuitBreakerRegistry.circuitBreaker("product-api");
    }

    @Test
    void omitsSimilarProductsWhoseDetailsAreNotFound() throws Exception {
        when(externalProductApi.getProductProductId("1")).thenReturn(ResponseEntity.ok(product("1", "Shirt", "9.99", true)));
        when(externalProductApi.getProductSimilarids("1"))
                .thenReturn(ResponseEntity.ok(new LinkedHashSet<>(List.of("2", "3"))));
        when(externalProductApi.getProductProductId("2")).thenReturn(ResponseEntity.ok(product("2", "Dress", "19.99", true)));
        when(externalProductApi.getProductProductId("3")).thenReturn(ResponseEntity.notFound().build());

        mockMvc.perform(get("/product/1/similar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("2"))
                .andExpect(jsonPath("$[0].name").value("Dress"));
    }

    @Test
    void doesNotCacheNotFoundSimilarProductDetails() throws Exception {
        when(externalProductApi.getProductProductId("21"))
                .thenReturn(ResponseEntity.ok(product("21", "Shirt", "9.99", true)));
        when(externalProductApi.getProductSimilarids("21"))
                .thenReturn(ResponseEntity.ok(new LinkedHashSet<>(List.of("22"))));
        when(externalProductApi.getProductProductId("22")).thenReturn(ResponseEntity.notFound().build());

        for (int request = 0; request < 2; request++) {
            mockMvc.perform(get("/product/21/similar"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        verify(externalProductApi, times(2)).getProductProductId("21");
        verify(externalProductApi, times(1)).getProductSimilarids("21");
        verify(externalProductApi, times(2)).getProductProductId("22");
    }

    @Test
    void exposesPrometheusMetricsThroughActuator() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/plain"));
    }

    @Test
    void exposesTheGeneratedOpenApiContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.paths['/product/{productId}/similar'].get.operationId")
                        .value("getProductSimilar"))
                .andExpect(jsonPath("$.paths['/product/{productId}/similar'].get.responses.200").exists())
                .andExpect(jsonPath("$.paths['/product/{productId}/similar'].get.responses.404").exists())
                .andExpect(jsonPath("$.paths['/product/{productId}/similar'].get.responses.404.content['application/problem+json'].schema.$ref")
                        .value("#/components/schemas/ProblemDetail"))
                .andExpect(jsonPath("$.paths['/product/{productId}/similar'].get.responses.400").exists())
                .andExpect(jsonPath("$.paths['/product/{productId}/similar'].get.responses.502").exists())
                .andExpect(jsonPath("$.paths['/product/{productId}/similar'].get.responses.503").exists())
                .andExpect(jsonPath("$.paths['/product/{productId}/similar'].get.responses.504").exists())
                .andExpect(jsonPath("$.paths['/product/{productId}/similar'].get.responses.500").exists())
                .andExpect(jsonPath("$.components.schemas.ProductDetail.required").isArray());
    }

    @Test
    void servesSwaggerUi() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());
    }

    private ProductDetail product(String id, String name, String price, boolean availability) {
        return new ProductDetail(id, name, new BigDecimal(price), availability);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ExternalProductApiMockConfiguration {

        @Bean
        @Primary
        DefaultApi externalProductApi() {
            return mock(DefaultApi.class);
        }
    }
}
