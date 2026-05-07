package org.danielesteban.worldcupbetbackend.integration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({FootballDataProperties.class, FootballDataSeedProperties.class})
public class FootballDataClientConfig {

    @Bean
    public RestClient footballDataRestClient(FootballDataProperties properties, RateLimiter rateLimiter) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectionTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());

        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader("X-Auth-Token", properties.key())
                .requestInterceptor(new RateLimitInterceptor(rateLimiter))
                .requestInterceptor(new RetryInterceptor(properties.maxRetries()))
                .requestFactory(requestFactory)
                .build();
    }
}
