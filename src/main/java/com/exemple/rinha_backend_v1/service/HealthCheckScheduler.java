package com.exemple.rinha_backend_v1.service;


import com.exemple.rinha_backend_v1.model.ProcessorType;
import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class HealthCheckScheduler {

    private final PaymentService paymentService;

    public HealthCheckScheduler(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostConstruct
    public void init() {
        checkHealth();
    }

    @Scheduled(fixedDelay = 4000) //a cada 4s
    public void checkHealth() {
        paymentService.updateHealthCache(ProcessorType.DEFAULT);
        paymentService.updateHealthCache(ProcessorType.FALLBACK);
    }
}
