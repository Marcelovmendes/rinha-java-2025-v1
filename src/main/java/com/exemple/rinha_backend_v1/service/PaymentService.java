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
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;


@Service
public class PaymentService {
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private static final String HEALTH_KEY = "processor:health:";
    private static final String PAYMENT_QUEUE = "payment:queue";
    private static final String PROCESSED_PAYMENTS = "payments:processed:";

    private final RedissonClient redisson;
    private final RestTemplate restTemplate;
    private final RBucket<Processor> summaryCache;

    @Value("${PAYMENT_PROCESSOR_URL_DEFAULT:http://payment-processor-default:8080}")
    private String defaultProcessorUrl;

    @Value("${PAYMENT_PROCESSOR_URL_FALLBACK:http://payment-processor-fallback:8080}")
    private String fallbackProcessorUrl;

    public PaymentService(RedissonClient redisson) {
        this.redisson = redisson;
        this.restTemplate = new RestTemplate();
        this.summaryCache = redisson.getBucket("summary:cache");
        startPaymentProcessor();
    }
    private void startPaymentProcessor() {
        for (int i = 0; i < 4; i++) {
            final int workerId = i;
            Thread.startVirtualThread(() -> {
                RBlockingQueue<PaymentQueueItem> queue = redisson.getBlockingQueue(PAYMENT_QUEUE);
                log.info("Payment processor worker {} started", workerId);

                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        PaymentQueueItem item = queue.poll(1, TimeUnit.SECONDS);
                        if (item != null) {
                            log.debug("Worker {} processing payment: {}", workerId, item.getCorrelationId());
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
                log.warn("Default processor failed for {}, trying fallback", item.getCorrelationId());
                success = sendToProcessor(item, ProcessorType.FALLBACK);
                if (success) {
                    selectedProcessor = ProcessorType.FALLBACK;
                    log.info("Fallback processor succeeded for {}", item.getCorrelationId());
                }
            }

            if (success) {
                updateSummary(selectedProcessor, item);
                log.info("Payment {} processed successfully with {}", item.getCorrelationId(), selectedProcessor.getName());
            } else {
                log.error("FAILED TO PROCESS PAYMENT: {} - Both processors failed", item.getCorrelationId());
            }

        } catch (Exception e) {
            log.error("Error processing payment async for {}: {}", item.getCorrelationId(), e.getMessage());
        }
    }
    public void processPayment(PaymentRequest request) {
        try {
            RSet<String> processedIds = redisson.getSet("processed:ids");
            if (processedIds.contains(request.getCorrelationId().toString())) {
                log.debug("Payment already processed: {}", request.getCorrelationId());
                return;
            }

            // Adiciona na fila para processamento assíncrono
            RQueue<PaymentQueueItem> queue = redisson.getQueue(PAYMENT_QUEUE);
            PaymentQueueItem item = new PaymentQueueItem(
                    request.getCorrelationId(),
                    request.getAmount(),
                    Instant.now()
            );

            boolean added = queue.offer(item);
            if (added) {
                processedIds.add(request.getCorrelationId().toString());
                log.debug("Payment queued successfully: {}", request.getCorrelationId());
            }

        } catch (Exception e) {
            log.error("Error processing payment: {}", e.getMessage());
            throw new RuntimeException("Payment processing failed", e);
        }
    }

    private ProcessorType selectProcessor() {
        try {
            RBucket<ProcessorHealth> defaultHealth = redisson.getBucket(HEALTH_KEY + ProcessorType.DEFAULT.getName());
            ProcessorHealth health = defaultHealth.get();

            ProcessorType selected;

            if (health == null) {
                selected = ProcessorType.DEFAULT;
                log.debug("No health info for default processor - using default");
            }
            assert health != null;
            if (health.isFailing()) {
                selected = ProcessorType.FALLBACK;
                log.info("Default processor is failing, using fallback");
            } else {
                selected = ProcessorType.DEFAULT;
                log.debug("Default processor healthy - using default");
            }

            // LOG ESSENCIAL PARA DEBUG
            log.info("Selected processor {} for payment", selected.getName());
            return selected;

        } catch (Exception e) {
            log.error("Error selecting processor: {}", e.getMessage());
            return ProcessorType.DEFAULT;
        }
    }
    private boolean sendToProcessor(PaymentQueueItem item, ProcessorType processorType) {
        String url = processorType == ProcessorType.DEFAULT ? defaultProcessorUrl : fallbackProcessorUrl;

        try {
            // CORREÇÃO: Usar UUID sem conversão para string e BigDecimal corretamente
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("correlationId", item.getCorrelationId()); // UUID direto
            payload.put("amount", item.getAmount()); // BigDecimal direto
            payload.put("requestedAt", DateTimeFormatter.ISO_INSTANT.format(item.getRequestedAt()));

            // Headers explícitos
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
                        item.getCorrelationId(), processorType.getName(), response.getStatusCode());
            } else {
                log.error("Payment {} failed with status {} to {}",
                        item.getCorrelationId(), response.getStatusCode(), processorType.getName());
            }

            return success;

        } catch (Exception e) {
            log.error("Exception sending payment {} to {}: {}",
                    item.getCorrelationId(), processorType.getName(), e.getMessage());
            return false;
        }
    }
    private String formatTimestampForProcessor(Instant timestamp) {
        return DateTimeFormatter.ISO_INSTANT.format(timestamp);
    }

    private void updateSummary(ProcessorType processor, PaymentQueueItem item) {
        try {
            String correlationIdStr = item.getCorrelationId().toString();

            // Armazena usando Hash para melhor performance nas consultas de summary
            RMap<String, String> paymentData = redisson.getMap(PROCESSED_PAYMENTS + processor.getName() + ":" + correlationIdStr);
            paymentData.put("amount", item.getAmount().toString());
            paymentData.put("timestamp", Long.toString(item.getRequestedAt().toEpochMilli()));
            paymentData.put("processor", processor.getName());

            // Também mantém em SortedSet para queries por range de tempo
            RScoredSortedSet<String> timeIndex = redisson.getScoredSortedSet("payments:time:" + processor.getName());
            timeIndex.add(item.getRequestedAt().toEpochMilli(), correlationIdStr);

            // Contadores atômicos para performance
            RAtomicLong totalRequests = redisson.getAtomicLong("counter:requests:" + processor.getName());
            RAtomicDouble totalAmount = redisson.getAtomicDouble("counter:amount:" + processor.getName());

            long newRequestCount = totalRequests.incrementAndGet();
            double newAmountTotal = totalAmount.addAndGet(item.getAmount().doubleValue());

            log.info("Updated summary for {}: requests={}, totalAmount={}, payment={}",
                    processor.getName(), newRequestCount, newAmountTotal, item.getAmount());

        } catch (Exception e) {
            log.error("Error updating summary for {} payment {}: {}",
                    processor.getName(), item.getCorrelationId(), e.getMessage());
        }
    }

    public Processor getSummary(Instant from, Instant to) {
        try {
            long startTime = System.currentTimeMillis();

            if (from == null && to == null) {
                Processor cached = summaryCache.get();
                if (cached != null) {
                    long duration = System.currentTimeMillis() - startTime;
                    log.info("Returned cached summary in {}ms", duration);
                    return cached;
                }
            }

            Processor summary = new Processor();

            Processor.Summary defaultSummary = getProcessorSummary(ProcessorType.DEFAULT, from, to);
            Processor.Summary fallbackSummary = getProcessorSummary(ProcessorType.FALLBACK, from, to);

            summary.defaultProcessor(defaultSummary);
            summary.fallbackProcessor(fallbackSummary);

            if (from == null && to == null) {
                summaryCache.set(summary, 2, TimeUnit.SECONDS);
            }

            long elapsedTime = System.currentTimeMillis() - startTime;

            log.info("Generated summary in {}ms - Default: {} requests, {} amount | Fallback: {} requests, {} amount",
                    elapsedTime,
                    defaultSummary.getTotalRequests(), defaultSummary.getTotalAmount(),
                    fallbackSummary.getTotalRequests(), fallbackSummary.getTotalAmount());

            return summary;
        } catch (Exception e) {
            log.error("Error getting summary: {}", e.getMessage(), e);
            Processor summary = new Processor();
            summary.defaultProcessor(new Processor.Summary());
            summary.fallbackProcessor(new Processor.Summary());
            return summary;
        }
    }


    private Processor.Summary getProcessorSummary(ProcessorType processor, Instant from, Instant to) {
        try {
            Processor.Summary summary = new Processor.Summary();

            if (from == null && to == null) {
                // Sem filtro de tempo - usa contadores atômicos
                RAtomicLong totalRequests = redisson.getAtomicLong("counter:requests:" + processor.getName());
                RAtomicDouble totalAmount = redisson.getAtomicDouble("counter:amount:" + processor.getName());

                long requests = totalRequests.get();
                double amount = totalAmount.get();

                summary.setTotalRequests(requests);
                summary.computeTotalAmount(BigDecimal.valueOf(amount));

                log.debug("Summary for {} (no filter): requests={}, amount={}",
                        processor.getName(), requests, amount);
            } else {
                // Com filtro de tempo - usa SortedSet
                double fromScore = from != null ? from.toEpochMilli() : Double.NEGATIVE_INFINITY;
                double toScore = to != null ? to.toEpochMilli() : Double.POSITIVE_INFINITY;

                RScoredSortedSet<String> timeIndex = redisson.getScoredSortedSet("payments:time:" + processor.getName());
                Collection<String> correlationIds = timeIndex.valueRange(fromScore, true, toScore, true);

                long count = 0;
                BigDecimal totalAmount = BigDecimal.ZERO;

                for (String correlationId : correlationIds) {
                    RMap<String, String> paymentData = redisson.getMap(PROCESSED_PAYMENTS + processor.getName() + ":" + correlationId);
                    String amountStr = paymentData.get("amount");
                    if (amountStr != null) {
                        count++;
                        totalAmount = totalAmount.add(new BigDecimal(amountStr));
                    }
                }

                summary.setTotalRequests(count);
                summary.computeTotalAmount(totalAmount);

                log.debug("Summary for {} (filtered): requests={}, amount={}, range=[{}, {}]",
                        processor.getName(), count, totalAmount, from, to);
            }

            return summary;
        } catch (Exception e) {
            log.error("Error getting processor summary for {}: {}", processor.getName(), e.getMessage());
            return new Processor.Summary();
        }
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
                    url + "/payments/service-health",
                    ProcessorHealth.class
            );

            ProcessorHealth health = response.getBody();
            if (health != null) {
                healthBucket.set(health, 10, TimeUnit.SECONDS);

                // LOG ÚTIL - mostra os dados reais
                log.info("Health check for {}: failing={}, minResponseTime={}ms",
                        processor.getName(),
                        health.isFailing(),
                        health.getMinResponseTime());
            } else {
                log.warn("Health check for {} returned null body", processor.getName());
            }

        } catch (Exception e) {
            log.error("Health check failed for {}: {}", processor.getName(), e.getMessage());

            // Marcar como falhando
            ProcessorHealth health = new ProcessorHealth();
            health.failing(true);
            healthBucket.set(health, 5, TimeUnit.SECONDS);

            log.warn("Marked {} as failing due to health check failure", processor.getName());
        }
    }
    public void purgePayments() {
        try {
            RQueue<PaymentQueueItem> queue = redisson.getQueue(PAYMENT_QUEUE);
            queue.clear();

            RScoredSortedSet<PaymentRecord> defaultSet = redisson.getScoredSortedSet("payment:" + ProcessorType.DEFAULT.getName());
            RScoredSortedSet<PaymentRecord> fallbackSet = redisson.getScoredSortedSet("payment:" + ProcessorType.FALLBACK.getName());

            defaultSet.clear();
            fallbackSet.clear();

            log.info("All payments purged from Redis");
        } catch (Exception e) {
            log.error("Error purging payments: {}", e.getMessage());
        }
    }
}