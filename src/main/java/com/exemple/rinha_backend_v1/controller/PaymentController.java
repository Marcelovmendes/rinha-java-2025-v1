package com.exemple.rinha_backend_v1.controller;


import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentController {

    @PostMapping("/payments")
    public ResponseEntity<Void> processPayment() {
        return ResponseEntity.ok().build();
    }

    @GetMapping("/payments-summary")
    public ResponseEntity<Void> processPaymentSummary() {
        return ResponseEntity.ok().build();
    }
}
