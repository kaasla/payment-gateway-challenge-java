package com.checkout.payment.gateway.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Bean
  public AuthenticationEntryPoint jsonAuthenticationEntryPoint() {
    return (request, response, authException) -> {
      response.setStatus(401);
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response.setCharacterEncoding(StandardCharsets.UTF_8.name());
      response.getWriter().write("{\"message\":\"Unauthorized\"}");
    };
  }

  @Bean
  public AccessDeniedHandler jsonAccessDeniedHandler() {
    return (request, response, accessDeniedException) -> {
      response.setStatus(403);
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response.setCharacterEncoding(StandardCharsets.UTF_8.name());
      response.getWriter().write("{\"message\":\"Forbidden\"}");
    };
  }

  @Bean
  public ApiKeyAuthenticationFilter apiKeyAuthenticationFilter(
      @Value("${gateway.security.api-keys:}") String apiKeys,
      AuthenticationEntryPoint entryPoint,
      AccessDeniedHandler accessDeniedHandler) {
    return new ApiKeyAuthenticationFilter(apiKeys, entryPoint, accessDeniedHandler);
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http,
      ApiKeyAuthenticationFilter apiKeyAuthenticationFilter) throws Exception {
    http
        .csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
            .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/actuator/**").permitAll()
            .anyRequest().authenticated()
        )
        .exceptionHandling(ex -> ex
            .authenticationEntryPoint(jsonAuthenticationEntryPoint())
            .accessDeniedHandler(jsonAccessDeniedHandler())
        );

    http.addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }
}

