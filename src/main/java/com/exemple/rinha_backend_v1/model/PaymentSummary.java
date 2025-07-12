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
        private long totalRequests;
        private final BigDecimal totalAmount;

        public ProcessorSummary() {
            this.totalRequests = 0L;
            this.totalAmount = BigDecimal.ZERO;
        }
        public long getTotalRequests() {
            return totalRequests;
        }
        public void setTotalRequests(long totalRequests) {
            this.totalRequests = totalRequests;
        }
        public BigDecimal getTotalAmount() {
            return totalAmount;
        }
        public void setTotalAmount(BigDecimal amount) {
            if (amount != null) {
                this.totalAmount.add(amount);
            }
        }

    }

    public ProcessorSummary getDefaultProcessor() {
        return defaultProcessor;
    }
    public void setDefaultProcessor(ProcessorSummary defaultProcessor) {
        this.defaultProcessor = defaultProcessor;
    }
    public ProcessorSummary getFallbackProcessor() {
        return fallbackProcessor;
    }
    public void setFallbackProcessor(ProcessorSummary fallbackProcessor) {
        this.fallbackProcessor = fallbackProcessor;
    }
}
