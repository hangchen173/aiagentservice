package com.intern.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class AiHttpClientConfig {
    @Bean
    public RestClientCustomizer aiRestClientCustomizer(
            @Value("${nexusmind.ai.http.connect-timeout-seconds:5}") long connectTimeoutSeconds,
            @Value("${nexusmind.ai.http.read-timeout-seconds:90}") long readTimeoutSeconds) {
        return builder -> {
            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                    .build();
            JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
            requestFactory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));
            builder.requestFactory(requestFactory);
        };
    }
}
