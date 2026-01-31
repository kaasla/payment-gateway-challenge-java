package com.checkout.payment.gateway.configuration;

import java.time.Duration;
import java.time.Clock;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class ApplicationConfiguration {

  @Bean
  public RestTemplate restTemplate(RestTemplateBuilder builder) {
    return builder
        .setConnectTimeout(Duration.ofMillis(2000))
        .setReadTimeout(Duration.ofMillis(2000))
        .build();
  }

  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }
}
