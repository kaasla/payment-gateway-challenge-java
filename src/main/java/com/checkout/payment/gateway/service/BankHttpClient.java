package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.exception.AcquiringBankUnavailableException;
import com.checkout.payment.gateway.model.BankPaymentRequest;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@Service
public class BankHttpClient implements BankClient {

  private static final Logger LOG = LoggerFactory.getLogger(BankHttpClient.class);

  private final RestTemplate restTemplate;
  private final String baseUrl;

  public BankHttpClient(RestTemplate restTemplate,
      @Value("${bank.base-url:http://localhost:8080}") String baseUrl) {
    this.restTemplate = restTemplate;
    this.baseUrl = baseUrl;
  }

  @Override
  public BankPaymentResponse authorize(BankPaymentRequest request) {
    long start = System.nanoTime();
    try {
      ResponseEntity<BankPaymentResponse> response = restTemplate
          .postForEntity(baseUrl + "/payments", request, BankPaymentResponse.class);
      BankPaymentResponse body = response.getBody();
      long durMs = (System.nanoTime() - start) / 1_000_000;
      boolean authorized = body != null && body.authorized();
      LOG.info("event=bank.call.completed authorized={} status={} duration_ms={}",
          authorized, response.getStatusCode().value(), durMs);
      if (body == null) {
        throw new AcquiringBankUnavailableException("Empty bank response body");
      }
      return body;
    } catch (HttpStatusCodeException e) {
      long durMs = (System.nanoTime() - start) / 1_000_000;
      LOG.error("event=bank.call.failed status={} duration_ms={}", e.getStatusCode().value(), durMs);
      if (e.getStatusCode().value() == 503) {
        throw new AcquiringBankUnavailableException("Bank returned 503", e);
      }
      throw e;
    } catch (ResourceAccessException e) {
      long durMs = (System.nanoTime() - start) / 1_000_000;
      LOG.error("event=bank.call.timeout duration_ms={}", durMs);
      throw new AcquiringBankUnavailableException("Bank request timed out", e);
    }
  }
}

