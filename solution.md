# Similar Product Finder

## Overview

The service implements `GET /product/{productId}/similar` on port `5000`. It obtains the ordered similar-product IDs from the existing product API and resolves every product detail before returning the response defined in [similarProducts.yaml](./similarProducts.yaml).

The implementation follows hexagonal architecture:

- `src/domain/model`: domain model, value objects, exceptions, and repository port.
- `src/application/use-cases`: use case and its input/output ports.
- `src/infrastructure/product-repository-rest`: generated HTTP client contract and REST repository adapter.
- `src/boot/similar-product-finder-service`: Spring Boot composition, generated inbound API, REST controller, Actuator, Swagger UI, and container packaging.

OpenAPI contracts are copied into each module's `src/main/resources/openapi` directory and Maven generates the corresponding interfaces and DTOs during `generate-sources`.

## Prerequisites

- JDK 25
- Maven
- Docker and Docker Compose

## Local execution

Start the provided mock API and observability stack:

```bash
docker compose up -d simulado influxdb grafana
```

Run the application locally:

```bash
cd src/boot/similar-product-finder-service
mvn spring-boot:run
```

The application listens on `http://localhost:5000`. Its upstream endpoint defaults to `http://localhost:3001`; override it with `SIMILAR_PRODUCTS_API_BASE_URL` when needed.

### Upstream client and cache properties

The REST client uses configurable connection and response timeouts. The defaults are 5 seconds to establish the connection and 10 seconds to receive a response. Similar-product IDs are cached with Caffeine through Spring's `@Cacheable` abstraction; the cache can be disabled without changing the adapter behavior.

| Property | Environment variable | Default                 |
| --- | --- |-------------------------|
| `similar-products.api.base-url` | `SIMILAR_PRODUCTS_API_BASE_URL` | `http://localhost:3001` |
| `similar-products.api.connect-timeout` | `SIMILAR_PRODUCTS_API_CONNECT_TIMEOUT` | `5s`                    |
| `similar-products.api.read-timeout` | `SIMILAR_PRODUCTS_API_READ_TIMEOUT` | `10s`                   |
| `similar-products.cache.enabled` | `SIMILAR_PRODUCTS_CACHE_ENABLED` | `true`                  |
| `similar-products.cache.ttl` | `SIMILAR_PRODUCTS_CACHE_TTL` | `5m`                    |
| `similar-products.cache.not-found-ttl` | `SIMILAR_PRODUCTS_CACHE_NOT_FOUND_TTL` | `1m`                    |

The similar-product ID cache stores successful ID lists for the configured TTL. The adapter also negatively caches `existsById` results when the upstream returns `404`, using a separate cache with a 1-minute TTL by default. This avoids repeated upstream checks for a missing requested product while limiting staleness if that product is subsequently created. This negative cache applies only to the initial existence check; missing details for individual similar products are still requested each time and omitted from the successful response. Successful existence checks, product details, and failed upstream requests are not cached, so availability is always fetched fresh.

### Resilience and error responses

The outbound product API is protected by a Resilience4j circuit breaker shared by product-detail, existence, and similar-ID calls. `ProductApiGateway` is a separate Spring bean with `@CircuitBreaker` on each upstream operation, so adapter calls pass through Spring's resilience proxy, including detail lookups made on virtual threads. The infrastructure module includes Spring Boot's AspectJ starter to enable the annotation-based AOP interception. Product-detail `404` responses are converted to normal gateway responses before the annotated method returns, so expected missing products do not count as breaker failures.

With the defaults below, the breaker evaluates a count-based window of 20 calls after at least 10 calls; it opens when at least 50% fail or at least 50% are slower than 2 seconds. While open, calls fail fast and return `503`. After 10 seconds it permits 3 probe calls in the half-open state; successful probes close the breaker and failures reopen it.

| Property | Environment variable | Default |
| --- | --- | --- |
| `resilience4j.circuitbreaker.instances.product-api.sliding-window-type` | `RESILIENCE4J_CIRCUITBREAKER_INSTANCES_PRODUCT_API_SLIDING_WINDOW_TYPE` | `COUNT_BASED` |
| `resilience4j.circuitbreaker.instances.product-api.sliding-window-size` | `RESILIENCE4J_CIRCUITBREAKER_INSTANCES_PRODUCT_API_SLIDING_WINDOW_SIZE` | `20` |
| `resilience4j.circuitbreaker.instances.product-api.minimum-number-of-calls` | `RESILIENCE4J_CIRCUITBREAKER_INSTANCES_PRODUCT_API_MINIMUM_NUMBER_OF_CALLS` | `10` |
| `resilience4j.circuitbreaker.instances.product-api.failure-rate-threshold` | `RESILIENCE4J_CIRCUITBREAKER_INSTANCES_PRODUCT_API_FAILURE_RATE_THRESHOLD` | `50` (%) |
| `resilience4j.circuitbreaker.instances.product-api.slow-call-duration-threshold` | `RESILIENCE4J_CIRCUITBREAKER_INSTANCES_PRODUCT_API_SLOW_CALL_DURATION_THRESHOLD` | `2s` |
| `resilience4j.circuitbreaker.instances.product-api.slow-call-rate-threshold` | `RESILIENCE4J_CIRCUITBREAKER_INSTANCES_PRODUCT_API_SLOW_CALL_RATE_THRESHOLD` | `50` (%) |
| `resilience4j.circuitbreaker.instances.product-api.wait-duration-in-open-state` | `RESILIENCE4J_CIRCUITBREAKER_INSTANCES_PRODUCT_API_WAIT_DURATION_IN_OPEN_STATE` | `10s` |
| `resilience4j.circuitbreaker.instances.product-api.permitted-number-of-calls-in-half-open-state` | `RESILIENCE4J_CIRCUITBREAKER_INSTANCES_PRODUCT_API_PERMITTED_NUMBER_OF_CALLS_IN_HALF_OPEN_STATE` | `3` |

These thresholds are initial operating defaults, not universal capacity targets. Tune them from scenario-specific load-test results and upstream latency/error objectives. The breaker does not retry requests or make a slow upstream call faster; it limits repeated calls while the dependency is failing or slow. The existing connect/read timeouts still bound individual outbound calls.

The REST exception handler returns a `404` Problem Detail for a missing requested product, returns `400` for malformed IDs, and uses RFC 9457 Problem Details for other failures: `502` for invalid/upstream error responses, `503` for an unavailable upstream or open circuit, and `504` for upstream timeouts. Missing details for individual similar products continue to be omitted from the successful `200` response. Unexpected application failures return a generic `500` Problem Detail without exposing internal exception text.

Useful endpoints:

- API: `http://localhost:5000/product/1/similar`
- Swagger UI: `http://localhost:5000/swagger-ui.html`
- OpenAPI document: `http://localhost:5000/v3/api-docs`
- Health: `http://localhost:5000/actuator/health`
- Prometheus metrics: `http://localhost:5000/actuator/prometheus`

## Container execution

Build and start every service, including the application:

```bash
docker compose up --build -d
```

Inside Compose, the service uses `http://simulado` as its upstream product API and publishes port `5000` on the host.

Stop the stack with:

```bash
docker compose down
```

## Tests

Run all unit, adapter, contract-generation, Spring context, and MockMvc tests from the repository root:

```bash
mvn clean install
```

## Similar product lookup behavior

The requested product is checked first; if it does not exist, the endpoint returns `404` and does not request similar IDs. Missing details for a similar-product ID are different: the REST repository adapter logs a warning, omits that product from the result, and continues returning the other available products with `200`.

The application service requests all similar product details through the `ProductRepository` port. The REST adapter resolves those details concurrently using virtual threads, then returns the found products in the original similarity order. This keeps transport-level concurrency in the infrastructure adapter and leaves the use case responsible for application orchestration and response mapping.

## Performance self-evaluation

With the application available on port `5000`, execute the provided test:

```bash
docker compose run --rm k6 run scripts/test.js
```

Inspect the K6 dashboard at `http://localhost:3000/d/Le2Ku9NMk/k6-performance-test`.
