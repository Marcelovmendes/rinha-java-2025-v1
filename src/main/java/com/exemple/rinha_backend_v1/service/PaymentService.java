package com.exemple.rinha_backend_v1.service;

import com.exemple.rinha_backend_v1.model.*;
import org.redisson.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;


@Service
public class PaymentService {
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private static final String HEALTH_KEY = "processor:health:";
    private static final String PAYMENT_QUEUE = "payment:queue";
    private static final String PROCESSED_PAYMENTS = "payments:processed:";
    private static final String COUNTER_REQUESTS = "counter:requests:";
    private static final String COUNTER_AMOUNT = "counter:amount:";
    private static final String COUNTER_FEE = "counter:fee:";
    private static final String PAYMENTS_LOG = "payments:log";

    private final RedissonClient redisson;
    private final RestTemplate restTemplate;

    @Value("${PAYMENT_PROCESSOR_URL_DEFAULT:http://payment-processor-default:8080}")
    private String defaultProcessorUrl;

    @Value("${PAYMENT_PROCESSOR_URL_FALLBACK:http://payment-processor-fallback:8080}")
    private String fallbackProcessorUrl;

    public PaymentService(RedissonClient redisson) {
        this.redisson = redisson;
        this.restTemplate = new RestTemplate();
        startPaymentProcessor();
    }

    public void processPayment(PaymentRequest request) {
        try {
            RSet<String> processedIds = redisson.getSet("processed:ids");
            String idStr = request.getCorrelationId().toString();

            if (!processedIds.add(idStr)) {
                log.debug("Payment already processed: {}", request.getCorrelationId());
                return;
            }

            if (processedIds.size() > 10000) {
                processedIds.remove(processedIds.iterator().next());
            }

            RQueue<PaymentQueueItem> queue = redisson.getQueue(PAYMENT_QUEUE);
            PaymentQueueItem item = new PaymentQueueItem(
                    request.getCorrelationId(),
                    request.getAmount(),
                    Instant.now()
            );

           boolean added = queue.offer(item);

           if (!added) {
                log.error("Failed to add payment to queue: {}", request.getCorrelationId());
                processedIds.remove(idStr);
                throw new RuntimeException("Payment queue is full");
            }
            log.debug("Payment queued: {}", request.getCorrelationId());

        } catch (Exception e) {
            log.error("Error processing payment: {}", e.getMessage());
            throw new RuntimeException("Payment processing failed", e);
        }
    }
    private ProcessorType selectProcessor() {
        try {
            RBucket<ProcessorHealth> defaultHealth = redisson.getBucket(HEALTH_KEY + ProcessorType.DEFAULT.getName());
            ProcessorHealth health = defaultHealth.get();

            if (health != null && health.isFailing()) {
                log.debug("Default processor failing, using fallback");
                return ProcessorType.FALLBACK;
            }

            return ProcessorType.DEFAULT;

        } catch (Exception e) {
            log.error("Error selecting processor: {}", e.getMessage());
            return ProcessorType.DEFAULT;
        }
    }
    private void startPaymentProcessor() {
        for (int i = 0; i < 2; i++) {
            final int workerId = i;
            Thread.startVirtualThread(() -> {
                RBlockingQueue<PaymentQueueItem> queue = redisson.getBlockingQueue(PAYMENT_QUEUE);
                log.info("Payment processor worker {} started", workerId);

                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        PaymentQueueItem item = queue.poll(1, TimeUnit.SECONDS);
                        if (item != null) {
                            log.debug("Worker {} processing payment: {}", workerId, item.correlationId());
                            processPaymentAsync(item);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        log.error("Worker {} error processing payment from queue: {}", workerId, e.getMessage());
                    }
                }
            });
        }
    }
    private void processPaymentAsync(PaymentQueueItem item) {
        try {
            ProcessorType selectedProcessor = selectProcessor();

            boolean success = sendToProcessor(item, selectedProcessor);

            if (!success && selectedProcessor == ProcessorType.DEFAULT) {
                log.warn("Default processor failed for {}, trying fallback", item.correlationId());
                success = sendToProcessor(item, ProcessorType.FALLBACK);
                if (success) {
                    selectedProcessor = ProcessorType.FALLBACK;
                    log.info("Fallback processor succeeded for {}", item.correlationId());
                }
            }

            if (success) {
                updateSummary(selectedProcessor, item);
                log.info("Payment {} processed successfully with {}", item.correlationId(), selectedProcessor.getName());
            } else {
                log.error("FAILED TO PROCESS PAYMENT: {} - Both processors failed", item.correlationId());
            }

        } catch (Exception e) {
            log.error("Error processing payment async for {}: {}", item.correlationId(), e.getMessage());
        }
    }
    private boolean sendToProcessor(PaymentQueueItem item, ProcessorType processorType) {
        String url = processorType == ProcessorType.DEFAULT ? defaultProcessorUrl : fallbackProcessorUrl;

        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("correlationId", item.correlationId());
            payload.put("amount", item.amount());
            payload.put("requestedAt", DateTimeFormatter.ISO_INSTANT.format(item.requestedAt()));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

            log.info("Sending to {}: URL={}, payload={}", processorType.getName(), url + "/payments", payload);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    url + "/payments",
                    entity,
                    Map.class
            );

            boolean success = response.getStatusCode().is2xxSuccessful();
            if (success) {
                log.info("Payment {} sent successfully to {} - Status: {}",
                        item.correlationId(), processorType.getName(), response.getStatusCode());
            }
            return success;

        } catch (Exception e) {
            log.error("Exception sending payment {} to {}: {}",
                    item.correlationId(), processorType.getName(), e.getMessage());
            return false;
        }
    }
    private void updateSummary(ProcessorType processor, PaymentQueueItem item) {
        try {

            RScoredSortedSet<String> paymentsLog = redisson.getScoredSortedSet(PAYMENTS_LOG);

            String logEntry = String.format("%s|%s|%s",
                    processor.getName(),
                    item.amount().toString(),
                    item.correlationId().toString()
            );

            double score = item.requestedAt().toEpochMilli();
            paymentsLog.add(score, logEntry);

            log.debug("Stored payment log: {} with timestamp {}", logEntry, item.requestedAt());

            if (paymentsLog.size() > 10000) {
                paymentsLog.removeRangeByRank(0, 1000);
            }

        } catch (Exception e) {
            log.error("Error storing payment log: {}", e.getMessage());
        }
    }


    public Processor getSummary(String from, String to) {
        try {
            Instant fromInstant = from != null ? Instant.parse(from) : Instant.EPOCH;
            Instant toInstant = to != null ? Instant.parse(to) : Instant.now();

            log.info("Getting summary from {} to {}", fromInstant, toInstant);

            Processor summary = new Processor();
            Processor.Summary defaultSummary = new Processor.Summary();
            Processor.Summary fallbackSummary = new Processor.Summary();

            RScoredSortedSet<String> paymentsLog = redisson.getScoredSortedSet(PAYMENTS_LOG);

            var entries = paymentsLog.entryRange(
                    fromInstant.toEpochMilli(),
                    true,
                    toInstant.toEpochMilli(),
                    true
            );

            for (var entry : entries) {
                String[] parts = entry.getValue().split("\\|");
                if (parts.length >= 3) {
                    String processorName = parts[0];
                    BigDecimal amount = new BigDecimal(parts[1]);

                    if (processorName.equals(ProcessorType.DEFAULT.getName())) {
                        defaultSummary.setTotalRequests(defaultSummary.getTotalRequests() + 1);
                        defaultSummary.computeTotalAmount(amount);
                    }
                    if (processorName.equals(ProcessorType.FALLBACK.getName())) {
                        fallbackSummary.setTotalRequests(fallbackSummary.getTotalRequests() + 1);
                        fallbackSummary.computeTotalAmount(amount);
                    }
                }
            }

            summary.defaultProcessor(defaultSummary);
            summary.fallbackProcessor(fallbackSummary);

            log.info("Summary result - Default: {} requests, {} amount | Fallback: {} requests, {} amount",
                    defaultSummary.getTotalRequests(), defaultSummary.getTotalAmount(),
                    fallbackSummary.getTotalRequests(), fallbackSummary.getTotalAmount());

            return summary;

        } catch (Exception e) {
            log.error("Error generating summary: {}", e.getMessage(), e);
            return new Processor();
        }
    }

    public void updateHealthCache(ProcessorType processor) {
        String url = processor == ProcessorType.DEFAULT ? defaultProcessorUrl : fallbackProcessorUrl;
        RBucket<ProcessorHealth> healthBucket = redisson.getBucket(HEALTH_KEY + processor.getName());

        try {
            long startTime = System.currentTimeMillis();

            ResponseEntity<ProcessorHealth> response = restTemplate.getForEntity(
                    url + "/payments/service-health",
                    ProcessorHealth.class
            );

            long duration = System.currentTimeMillis() - startTime;

            ProcessorHealth health = response.getBody();
            if (health != null) {
                healthBucket.set(health, 8, TimeUnit.SECONDS);

                log.info("Health check for {}: failing={}, minResponseTime={}ms, checkDuration={}ms",
                        processor.getName(),
                        health.isFailing(),
                        health.getMinResponseTime(),
                        duration);
            }

        } catch (Exception e) {
            log.error("Health check failed for {}: {}", processor.getName(), e.getMessage());

            ProcessorHealth health = new ProcessorHealth();
            health.failing(true);
            healthBucket.set(health, 3, TimeUnit.SECONDS);
        }
    }
}