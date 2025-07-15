package com.exemple.rinha_backend_v1.controller;


import com.exemple.rinha_backend_v1.model.PaymentRequest;
import com.exemple.rinha_backend_v1.model.Processor;
import com.exemple.rinha_backend_v1.service.PaymentService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@RestController
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/payments")
    public ResponseEntity<Void> processPayment(@Valid @RequestBody PaymentRequest request) {
        paymentService.processPayment(request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/payments-summary")
    public ResponseEntity<Processor> processPaymentSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) String from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) String to) {
        long startTime = System.currentTimeMillis();

        Instant fromInstant = parseTimestamp(from);
        Instant toInstant = parseTimestamp(to);

        try {
            var summary = paymentService.getSummary(fromInstant, toInstant);

            long duration = System.currentTimeMillis() - startTime;
            log.info("Summary endpoint completed in {}ms", duration);
            return ResponseEntity.ok(summary);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }

    }


    private Instant parseTimestamp(String timestamp) {
        if (timestamp == null || timestamp.trim().isEmpty()) {
            return null;
        }

        try {
            return Instant.parse(timestamp);
        } catch (DateTimeParseException e1) {
            try {
                String withZ = timestamp.endsWith("Z") ? timestamp : timestamp + "Z";
                return Instant.parse(withZ);
            } catch (DateTimeParseException e2) {
                try {
                    String normalized = timestamp;
                    if (!normalized.contains(".") && !normalized.endsWith("Z")) {
                        normalized = normalized + ".000Z";
                    } else if (!normalized.endsWith("Z")) {
                        normalized = normalized + "Z";
                    }
                    return Instant.parse(normalized);
                } catch (DateTimeParseException e3) {

                    System.err.println("Failed to parse timestamp: " + timestamp + " - " + e3.getMessage());
                    return null;
                }
            }
        }
    }

}