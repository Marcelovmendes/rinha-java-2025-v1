package com.exemple.rinha_backend_v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public class Processor {

    @JsonProperty("default")
    private Summary defaultProcessor;

    @JsonProperty("fallback")
    private Summary fallbackProcessor;

    public Processor() {
        this.defaultProcessor = new Summary();
        this.fallbackProcessor = new Summary();
    }

    public static class Summary {

        private long totalRequests;
        private BigDecimal totalAmount;


        public Summary() {
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
        public void computeTotalAmount(BigDecimal amount) {
            if (amount != null) {
                this.totalAmount = (this.totalAmount == null) ? amount : this.totalAmount.add(amount);
            }
        }

    }

    public Summary getDefaultProcessor() {
        return defaultProcessor;
    }

    public void defaultProcessor(Summary defaultProcessor) {
        this.defaultProcessor = defaultProcessor;
    }
    public Summary getFallbackProcessor() {
        return fallbackProcessor;
    }
    public void fallbackProcessor(Summary fallbackProcessor) {
        this.fallbackProcessor = fallbackProcessor;
    }
}
