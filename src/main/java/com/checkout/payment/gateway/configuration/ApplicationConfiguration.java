package com.checkout.payment.gateway.configuration;

import java.time.Duration;
import java.time.Clock;
import org.slf4j.MDC;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class ApplicationConfiguration {

  @Bean
  public RestTemplate restTemplate(RestTemplateBuilder builder) {
    return builder
        .setConnectTimeout(Duration.ofMillis(10000))
        .setReadTimeout(Duration.ofMillis(10000))
        .additionalInterceptors((request, body, execution) -> {
          String corr = MDC.get(CorrelationIdFilter.MDC_KEY);
          if (corr != null && !corr.isBlank()) {
            request.getHeaders().add(CorrelationIdFilter.HEADER, corr);
          }
          return execution.execute(request, body);
        })
        .build();
  }

  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  public CorrelationIdFilter correlationIdFilter() {
    return new CorrelationIdFilter();
  }

  @Bean
  public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilterRegistration(
      CorrelationIdFilter filter) {
    FilterRegistrationBean<CorrelationIdFilter> reg = new FilterRegistrationBean<>(filter);
    reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
    reg.addUrlPatterns("/*");
    return reg;
  }
}
