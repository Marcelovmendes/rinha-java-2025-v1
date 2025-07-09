package com.exemple.rinha_backend_v1.model;

public class ProcessorHealth {

    private boolean failing;
    private Integer minResponseTime;

    public boolean isFailing() {
        return failing;
    }
    public Integer getMinResponseTime() {
        return minResponseTime;
    }
}
