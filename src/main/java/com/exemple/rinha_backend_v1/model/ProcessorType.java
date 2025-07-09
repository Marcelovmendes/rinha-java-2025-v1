package com.exemple.rinha_backend_v1.model;

public enum ProcessorType {
    DEFAULT("default", 0.01),
    FALLBACK("fallback", 0.10);

    private final String name;
    private final double feeRate;

    ProcessorType(String name, double feeRate) {
        this.name = name;
        this.feeRate = feeRate;
    }
    public String getName() {
        return name;
    }
    public double getFeeRate() {
        return feeRate;
    }
}
