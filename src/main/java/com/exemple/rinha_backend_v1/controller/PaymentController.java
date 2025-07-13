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
            // TIMEOUT DE 2 SEGUNDOS - Se demorar mais, retorna summary básico
            CompletableFuture<Processor> summaryFuture = CompletableFuture.supplyAsync(() ->
                    paymentService.getSummary(fromInstant, toInstant)
            );

            Processor summary = summaryFuture.get(2, TimeUnit.SECONDS);

            long duration = System.currentTimeMillis() - startTime;
            log.info("Summary endpoint completed in {}ms", duration);

            return ResponseEntity.ok(summary);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.warn("Summary endpoint timeout/error after {}ms: {}", duration, e.getMessage());

            // FALLBACK: Retorna summary básico ao invés de erro
            Processor basicSummary = createBasicSummary();
            return ResponseEntity.ok(basicSummary);
        }

    }

    @PostMapping("/admin/purge-payments")
    public ResponseEntity<Map<String, String>> purgePayments() {
        paymentService.purgePayments();
        Map<String, String> response = new HashMap<>();
        response.put("message", "All payments purged.");
        return ResponseEntity.ok(response);
    }
    private Instant parseTimestamp(String timestamp) {
        if (timestamp == null || timestamp.trim().isEmpty()) {
            return null;
        }

        try {
            // Primeiro tenta parse direto (caso tenha Z ou offset)
            return Instant.parse(timestamp);
        } catch (DateTimeParseException e1) {
            try {
                // Se falhar, adiciona Z e tenta novamente (assume UTC)
                String withZ = timestamp.endsWith("Z") ? timestamp : timestamp + "Z";
                return Instant.parse(withZ);
            } catch (DateTimeParseException e2) {
                try {
                    // Último resort: tenta com .000Z se não tiver milissegundos
                    String normalized = timestamp;
                    if (!normalized.contains(".") && !normalized.endsWith("Z")) {
                        normalized = normalized + ".000Z";
                    } else if (!normalized.endsWith("Z")) {
                        normalized = normalized + "Z";
                    }
                    return Instant.parse(normalized);
                } catch (DateTimeParseException e3) {
                    // Se ainda falhar, log e retorna null
                    System.err.println("Failed to parse timestamp: " + timestamp + " - " + e3.getMessage());
                    return null;
                }
            }
        }
    }
    private Processor createBasicSummary() {
        try {
            // Simula summary básico - usar apenas dados mais simples
            Processor summary = new Processor();

            // Summary padrão vazio mas válido
            Processor.Summary defaultSummary = new Processor.Summary();
            defaultSummary.setTotalRequests(0L);
            defaultSummary.computeTotalAmount(BigDecimal.ZERO);

            Processor.Summary fallbackSummary = new Processor.Summary();
            fallbackSummary.setTotalRequests(0L);
            fallbackSummary.computeTotalAmount(BigDecimal.ZERO);

            summary.defaultProcessor(defaultSummary);
            summary.fallbackProcessor(fallbackSummary);

            log.info("Returned basic summary due to timeout");
            return summary;

        } catch (Exception e) {
            log.error("Error creating basic summary: {}", e.getMessage());
            return new Processor(); // Summary completamente vazio
        }
    }
}
