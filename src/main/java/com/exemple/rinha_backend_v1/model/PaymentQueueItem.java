package com.exemple.rinha_backend_v1.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class PaymentQueueItem {
    private final UUID correlationId;
    private final BigDecimal amount;
    private final Instant requestedAt;

    public PaymentQueueItem(UUID correlationId, BigDecimal amount, Instant requestedAt) {
        this.correlationId = correlationId;
        this.amount = amount;
        this.requestedAt = requestedAt;
    }

    public UUID getCorrelationId() { return correlationId; }
    public BigDecimal getAmount() { return amount; }
    public Instant getRequestedAt() { return requestedAt; }

}
