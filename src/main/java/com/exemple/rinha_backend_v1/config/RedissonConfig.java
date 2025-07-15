package com.exemple.rinha_backend_v1.config;


import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + redisHost + ":" + redisPort)
                .setConnectionPoolSize(8)
                .setConnectionMinimumIdleSize(2)
                .setIdleConnectionTimeout(5000)
                .setTimeout(1000)
                .setRetryAttempts(2)
                .setRetryInterval(500)
                .setKeepAlive(true)
                .setTcpNoDelay(true)
                .setPingConnectionInterval(30000);

        config.setThreads(4);
        config.setNettyThreads(4);

        return Redisson.create(config);
    }
}
