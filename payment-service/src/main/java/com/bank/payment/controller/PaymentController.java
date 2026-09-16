package com.bank.payment.controller;

import com.bank.payment.dto.ApiResponse;
import com.bank.payment.dto.PaymentRequest;
import com.bank.payment.dto.PaymentResponse;
import com.bank.payment.service.PaymentService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * Accepts a payment request with mandatory Idempotency-Key header.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<PaymentResponse>> initiatePayment(
            @RequestHeader(value = "Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PaymentRequest request) {

        log.info("Received payment request with Idempotency-Key: {}, source: {}, dest: {}, amount: {}",
                idempotencyKey, request.getSourceAccountId(), request.getDestinationAccountId(), request.getAmount());

        PaymentResponse response = paymentService.initiatePayment(idempotencyKey, request);

        HttpStatus status = response.isIdempotentReplay() ? HttpStatus.OK : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status)
                .body(ApiResponse.ok(response, response.getMessage()));
    }

    /**
     * Query payment status by ID.
     */
    @GetMapping("/{paymentId}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPaymentStatus(@PathVariable UUID paymentId) {
        PaymentResponse response = paymentService.getPaymentById(paymentId);
        return ResponseEntity.ok(ApiResponse.ok(response, "Payment details retrieved successfully"));
    }

    /**
     * Demonstration endpoint for Resilience4j Circuit Breaker.
     * Simulates calls to an external banking network/acquirer.
     */
    @GetMapping("/test-circuit-breaker")
    @CircuitBreaker(name = "externalAcquirer", fallbackMethod = "acquirerFallback")
    public ResponseEntity<ApiResponse<String>> testCircuitBreaker(@RequestParam(defaultValue = "false") boolean simulateFailure) {
        if (simulateFailure) {
            log.warn("Simulating 3rd party acquirer outage...");
            throw new RuntimeException("External banking partner timeout / service unavailable");
        }
        return ResponseEntity.ok(ApiResponse.ok("External Acquirer is healthy and responding", "Success"));
    }

    public ResponseEntity<ApiResponse<String>> acquirerFallback(boolean simulateFailure, Throwable t) {
        log.error("Circuit breaker fallback triggered due to: {}", t.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error("Circuit Breaker OPEN: External banking network currently degraded. Fallback applied."));
    }
}
