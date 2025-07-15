package com.exemple.rinha_backend_v1.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentQueueItem(UUID correlationId, BigDecimal amount, Instant requestedAt) {

}
