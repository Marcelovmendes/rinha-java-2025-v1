package com.exemple.rinha_backend_v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public class PaymentSummary {

    @JsonProperty("default")
    private ProcessorSummary defaultProcessor;

    @JsonProperty("fallback")
    private ProcessorSummary fallbackProcessor;

    public static class ProcessorSummary {
        private long totalRequests;
        private BigDecimal totalAmount;

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
