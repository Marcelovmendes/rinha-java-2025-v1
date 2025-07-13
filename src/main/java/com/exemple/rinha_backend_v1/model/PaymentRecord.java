package com.exemple.rinha_backend_v1.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

public class PaymentRecord implements Serializable {
    private BigDecimal amount;
    private Instant timestamp;

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

}
