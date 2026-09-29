package com.carpeol.similar.product.finder.boot.adapter.in.rest;

import com.carpeol.similar.product.finder.domain.exception.InvalidProductField;
import com.carpeol.similar.product.finder.domain.exception.ProductNotFound;
import com.carpeol.similar.product.finder.domain.exception.ProductRepositoryError;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.TimeoutException;

@RestControllerAdvice
public class RestExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RestExceptionHandler.class);

    @ExceptionHandler(ProductNotFound.class)
    ResponseEntity<ProblemDetail> handleProductNotFound(ProductNotFound exception) {
        LOGGER.warn("Returning 404 for missing product: {}", exception.getMessage());
        return problem(
                HttpStatus.NOT_FOUND,
                "Product not found",
                "The requested product does not exist.");
    }

    @ExceptionHandler(InvalidProductField.class)
    ResponseEntity<ProblemDetail> handleInvalidProductField(InvalidProductField exception) {
        LOGGER.warn("Rejecting invalid product field: " + exception.getMessage());
        return problem(HttpStatus.BAD_REQUEST, "Invalid product field", exception.getMessage());
    }

    @ExceptionHandler(ProductRepositoryError.class)
    ResponseEntity<ProblemDetail> handleProductRepositoryError(ProductRepositoryError exception) {
        HttpStatus status = upstreamFailureStatus(exception);
        String detail = switch (status) {
            case BAD_GATEWAY -> "The product service returned an invalid response.";
            case SERVICE_UNAVAILABLE -> "The product service is temporarily unavailable.";
            case GATEWAY_TIMEOUT -> "The product service did not respond before the deadline.";
            default -> "The product service request failed.";
        };
        if (status == HttpStatus.GATEWAY_TIMEOUT) {
            LOGGER.warn("Product repository request timed out: {}", exception.getMessage());
        } else {
            LOGGER.error("Product repository request failed with status {}", status.value(), exception);
        }
        return problem(status, status.getReasonPhrase(), detail);
    }

    @ExceptionHandler(CallNotPermittedException.class)
    ResponseEntity<ProblemDetail> handleOpenCircuitBreaker(CallNotPermittedException exception) {
        LOGGER.warn("Rejecting product API request because its circuit breaker is open");
        return problem(
                HttpStatus.SERVICE_UNAVAILABLE,
                HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase(),
                "The product service is temporarily unavailable.");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpectedException(Exception exception) {
        LOGGER.error("Unhandled request failure", exception);
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal server error",
                "An unexpected error occurred.");
    }

    private HttpStatus upstreamFailureStatus(ProductRepositoryError exception) {
        if (hasCause(exception, HttpTimeoutException.class)
                || hasCause(exception, SocketTimeoutException.class)
                || hasCause(exception, TimeoutException.class)) {
            return HttpStatus.GATEWAY_TIMEOUT;
        }
        if (hasCause(exception, ResourceAccessException.class)) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        return HttpStatus.BAD_GATEWAY;
    }

    private boolean hasCause(Throwable exception, Class<? extends Throwable> causeType) {
        for (Throwable cause = exception; cause != null && cause != cause.getCause(); cause = cause.getCause()) {
            if (causeType.isInstance(cause)) {
                return true;
            }
        }
        return false;
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return ResponseEntity.status(status).body(problem);
    }
}
