package com.exemple.rinha_backend_v1.service;

import com.exemple.rinha_backend_v1.model.*;
import org.redisson.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;

import java.util.concurrent.TimeUnit;


@Service
public class PaymentService {
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private static final String SUMMARY_KEY = "payment:summary";
    private static final String HEALTH_KEY = "processor:health:";

    private final RedissonClient redisson;
    private final RestTemplate restTemplate;
    private final Processor paymentSummary;

    @Value("${PAYMENT_PROCESSOR_URL_DEFAULT:http://payment-processor-default:8080}")
    private String defaultProcessorUrl;

    @Value("${PAYMENT_PROCESSOR_URL_FALLBACK:http://payment-processor-fallback:8080}")
    private String fallbackProcessorUrl;

    public PaymentService(RedissonClient redisson) {
        this.redisson = redisson;
        this.paymentSummary = new Processor();
        this.restTemplate = new RestTemplate();
    }

    public void processPayment(PaymentRequest request) {
        try {
            ProcessorType selectedProcessor = selectProcessor();
            boolean success = sendToProcessor(request, selectedProcessor);

            if (!success && selectedProcessor == ProcessorType.DEFAULT) {
                success = sendToProcessor(request, ProcessorType.FALLBACK);

                if (success) selectedProcessor = ProcessorType.FALLBACK;
            }

            if (success) updateSummary(request.getAmount(), selectedProcessor);

        } catch (Exception e) {
            log.error("Error selecting payment processor: {}", e.getMessage());
            throw  new RuntimeException("Error selecting payment processor: " + e.getMessage());
        }
    }

    private ProcessorType selectProcessor() {
        RBucket<ProcessorHealth> defaultHealth = redisson.getBucket(HEALTH_KEY + ProcessorType.DEFAULT.getName());
        ProcessorHealth health = defaultHealth.get();
        if (health == null || !health.isFailing()) {
            log.info("Using default payment processor: {}", ProcessorType.DEFAULT.getName());
            return ProcessorType.DEFAULT;
        }
        return ProcessorType.FALLBACK;
    }

    private boolean sendToProcessor(PaymentRequest paymentRequest, ProcessorType processorType) {
        String url = processorType == ProcessorType.DEFAULT ? defaultProcessorUrl : fallbackProcessorUrl;
        try {
            ResponseEntity<Void> response = restTemplate.postForEntity(
                url + "/payments",
                paymentRequest,
                Void.class
            );
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("Error sending payment to {}: {}", processorType.getName(), e.getMessage());
            return false;
        }
    }

    private void updateSummary(BigDecimal amount, ProcessorType processor) {
        RScoredSortedSet<PaymentRecord> sortedSet = redisson.getScoredSortedSet("payment:" + processor.getName());

        PaymentRecord record = new PaymentRecord();
        record.setAmount(amount);
        record.setTimestamp(Instant.now());

        sortedSet.add((double) record.getTimestamp().toEpochMilli(), record);
    }

    public Processor getSummary(Instant from, Instant to) {
        Processor paymentSummary = new Processor();

        double fromScore = from != null ? from.toEpochMilli() : Double.NEGATIVE_INFINITY;
        double toScore = to != null ? to.toEpochMilli() : Double.POSITIVE_INFINITY;

        paymentSummary.defaultProcessor(processProcessorSummary(ProcessorType.DEFAULT, fromScore, toScore));
        paymentSummary.fallbackProcessor(processProcessorSummary(ProcessorType.FALLBACK, fromScore, toScore));

        return paymentSummary;
    }
    private Processor.Summary processProcessorSummary(ProcessorType processor, double from, double to) {
        RScoredSortedSet<PaymentRecord> sortedSet = redisson.getScoredSortedSet("payment:" + processor.getName());

        Collection<PaymentRecord> paymentsInRange = sortedSet.valueRange(from, true, to, true);

        Processor.Summary summary = new Processor.Summary();
        for (PaymentRecord record : paymentsInRange) {
            summary.computeTotalRequests();
            summary.computeTotalAmount(record.getAmount());
        }

        return summary;
    }

    public void updateHealthCache(ProcessorType processor) {
        String url = processor == ProcessorType.DEFAULT ? defaultProcessorUrl : fallbackProcessorUrl;
        RBucket<ProcessorHealth> healthBucket = redisson.getBucket(HEALTH_KEY + processor.getName());

        try {
            ResponseEntity<ProcessorHealth> response = restTemplate.getForEntity(
                url + "payments/service-health",
                ProcessorHealth.class
            );
            ProcessorHealth health = response.getBody();
            if (health != null) {
                healthBucket.set(health, 10, TimeUnit.SECONDS);
                log.info("Updated health cache for {}: {}", processor.getName(), health);
            }
        } catch (Exception e) {
            log.error("Error updating health cache for {}: {}", processor.getName(), e.getMessage());
            ProcessorHealth health = new ProcessorHealth();
            health.faling(true);
            healthBucket.set(health, 10, TimeUnit.SECONDS);
        }

    }
}