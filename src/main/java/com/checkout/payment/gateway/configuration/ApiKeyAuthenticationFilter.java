package com.checkout.payment.gateway.configuration;

import com.checkout.payment.gateway.exception.ForbiddenException;
import com.checkout.payment.gateway.exception.UnauthorizedException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.springframework.web.filter.OncePerRequestFilter;

public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

  public static final String HEADER = "X-API-Key";
  private final Map<String, String> apiKeys;

  public ApiKeyAuthenticationFilter(String commaSeparatedKeys) {
    this.apiKeys = parseKeys(commaSeparatedKeys);
  }

  private Map<String, String> parseKeys(String s) {
    if (s == null || s.isBlank()) return Collections.emptyMap();
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
    // Allow docs and health without API key
    if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
      return true;
    }
    return path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs") || path.startsWith("/actuator");
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    if (apiKeys.isEmpty()) {
      throw new UnauthorizedException("No API keys configured");
    }
    String provided = request.getHeader(HEADER);
    if (provided == null || provided.isBlank()) {
      throw new UnauthorizedException("Missing API key");
    }
    String merchantId = apiKeys.get(provided);
    if (merchantId == null) {
      throw new ForbiddenException("Invalid API key");
    }
    request.setAttribute("merchantId", merchantId);
    filterChain.doFilter(request, response);
  }
}
