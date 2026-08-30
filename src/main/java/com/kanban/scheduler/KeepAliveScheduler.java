package com.kanban.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Sends a self-ping to the health endpoint every 4 minutes.
 * Prevents the container from sleeping on free-tier platforms (Render, Railway).
 *
 * Set APP_PUBLIC_URL in your environment variables to your deployed URL.
 * Example: https://your-app.onrender.com
 *
 * If APP_PUBLIC_URL is not set, the ping is skipped (safe for local dev).
 */
@Slf4j
@Component
public class KeepAliveScheduler {

    private final RestTemplate restTemplate;

    @Value("${APP_PUBLIC_URL:}")
    private String appPublicUrl;

    public KeepAliveScheduler(RestTemplateBuilder builder) {
        this.restTemplate = builder
                .setConnectTimeout(java.time.Duration.ofSeconds(5))
                .setReadTimeout(java.time.Duration.ofSeconds(5))
                .build();
    }

    // Every 4 minutes — keeps the container alive before the 5-min idle timeout kicks in
    @Scheduled(fixedDelay = 4 * 60 * 1000)
    public void keepAlive() {
        if (appPublicUrl == null || appPublicUrl.isBlank()) {
            return; // Skip in local dev
        }

        String url = appPublicUrl + "/api/v1/actuator/health/liveness";
        try {
            restTemplate.getForObject(url, String.class);
            log.debug("Keep-alive ping OK -> {}", url);
        } catch (Exception e) {
            log.warn("Keep-alive ping failed: {}", e.getMessage());
        }
    }
}
