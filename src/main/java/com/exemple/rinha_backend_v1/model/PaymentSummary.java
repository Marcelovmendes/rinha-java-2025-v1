package com.exemple.rinha_backend_v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public class PaymentSummary {

    @JsonProperty("default")
    private ProcessorSummary defaultProcessor;

    @JsonProperty("fallback")
    private ProcessorSummary fallbackProcessor;

    public PaymentSummary () {
        this.defaultProcessor = new ProcessorSummary();
        this.fallbackProcessor = new ProcessorSummary();
    }

    public static class ProcessorSummary {
        private final long totalRequests;
        private final BigDecimal totalAmount;

        public ProcessorSummary() {
            this.totalRequests = 0L;
            this.totalAmount = BigDecimal.ZERO;
        }
        public long getTotalRequests() {
            return totalRequests;
        }
        public BigDecimal getTotalAmount() {
            return totalAmount;
        }
    }

    public ProcessorSummary getDefaultProcessor() {
        return defaultProcessor;
    }
    public ProcessorSummary getFallbackProcessor() {
        return fallbackProcessor;
    }
}
