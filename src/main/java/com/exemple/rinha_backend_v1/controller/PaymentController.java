package com.exemple.rinha_backend_v1.controller;


import com.exemple.rinha_backend_v1.model.PaymentRequest;
import com.exemple.rinha_backend_v1.model.Processor;
import com.exemple.rinha_backend_v1.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
public class PaymentController {

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
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ResponseEntity.ok(paymentService.getSummary(from, to));
    }
    @PostMapping("/admin/purge-payments")
    public ResponseEntity<Map<String, String>> purgePayments() {
        Map<String, String> response = new HashMap<>();
        response.put("message", "All payments purged.");
        return ResponseEntity.ok(response);
    }
}
