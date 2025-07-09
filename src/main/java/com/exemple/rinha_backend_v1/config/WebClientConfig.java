package com.exemple.rinha_backend_v1.config;


import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;



@Configuration
public class WebClientConfig {

    public WebClient.Builder webClientBuilder() {
        ConnectionProvider connectionProvider = ConnectionProvider.builder("custom-payment-pool")
                .maxConnections(500)
                .maxIdleTime(java.time.Duration.ofSeconds(30))
                .maxLifeTime(java.time.Duration.ofSeconds(60))
                .pendingAcquireTimeout(java.time.Duration.ofSeconds(60))
                .evictInBackground(java.time.Duration.ofSeconds(120))
                .build();

        HttpClient httpClient = HttpClient.create(connectionProvider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .responseTimeout(java.time.Duration.ofMillis(1000));

        ExchangeStrategies exchangeStrategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(16 * 1024 * 1024))
                .build();
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(exchangeStrategies);
    }
}
