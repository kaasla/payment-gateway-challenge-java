package com.checkout.payment.gateway.configuration;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.slf4j.MDC;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.filter.OncePerRequestFilter;

public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

  public static final String HEADER = "X-API-Key";
  private final Map<String, String> apiKeys;
  private final AuthenticationEntryPoint authenticationEntryPoint;
  private final AccessDeniedHandler accessDeniedHandler;

  public ApiKeyAuthenticationFilter(String commaSeparatedKeys,
      AuthenticationEntryPoint authenticationEntryPoint,
      AccessDeniedHandler accessDeniedHandler) {
    this.apiKeys = parseKeys(commaSeparatedKeys);
    this.authenticationEntryPoint = authenticationEntryPoint;
    this.accessDeniedHandler = accessDeniedHandler;
  }

  private Map<String, String> parseKeys(String s) {
    if (s == null || s.isBlank()) {
      return Collections.emptyMap();
    }
    Map<String, String> map = new HashMap<>();
    Stream.of(s.split(","))
        .map(String::trim)
        .filter(str -> !str.isEmpty())
        .forEach(pair -> {
          String[] parts = pair.split(":", 2);
          if (parts.length == 2) {
            map.put(parts[0].trim(), parts[1].trim());
          }
        });
    return map;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
      return true;
    }
    return path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs") || path.startsWith("/actuator");
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    if (apiKeys.isEmpty()) {
      // Misconfiguration, treat as unauthenticated
      AuthenticationException ex = new InsufficientAuthenticationException("No API keys configured");
      authenticationEntryPoint.commence(request, response, ex);
      return;
    }

    String provided = request.getHeader(HEADER);
    if (provided == null || provided.isBlank()) {
      AuthenticationException ex = new InsufficientAuthenticationException("Missing API key");
      authenticationEntryPoint.commence(request, response, ex);
      return;
    }

    String merchantId = apiKeys.get(provided);
    if (merchantId == null) {
      accessDeniedHandler.handle(request, response, new AccessDeniedException("Invalid API key"));
      return;
    }

    request.setAttribute("merchantId", merchantId);
    MDC.put("merchant_id", merchantId);
    var auth = new UsernamePasswordAuthenticationToken(merchantId, provided,
        java.util.List.of(new SimpleGrantedAuthority("ROLE_MERCHANT")));
    SecurityContextHolder.getContext().setAuthentication(auth);
    filterChain.doFilter(request, response);
  }
}
