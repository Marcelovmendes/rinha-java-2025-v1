package com.exemple.rinha_backend_v1.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;


public class PaymentRequest {
     
    @NotNull
    private UUID correlationId;

    @NotNull
    @Positive
    private BigDecimal amount;

    public UUID getCorrelationId() {
        return correlationId;
    }
    public BigDecimal getAmount() {
        return amount;
    }

}
