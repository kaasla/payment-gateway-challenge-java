package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.exception.AcquiringBankUnavailableException;
import com.checkout.payment.gateway.model.BankPaymentRequest;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

public class BankService {

  private static final Logger LOG = LoggerFactory.getLogger(BankService.class);
  private static final String EVENT = "event";

  private final RestTemplate restTemplate;
  private final String baseUrl;

  public BankService(RestTemplate restTemplate,
      String baseUrl) {
    this.restTemplate = restTemplate;
    this.baseUrl = baseUrl;
  }

  public BankPaymentResponse requestAuthorization(BankPaymentRequest request) {
    LOG.info("{}=bank.call.started currency={} amount={}", EVENT, request.currency(), request.amount());
    try {
      ResponseEntity<BankPaymentResponse> response = restTemplate
          .postForEntity(baseUrl + "/payments", request, BankPaymentResponse.class);
      BankPaymentResponse body = response.getBody();
      boolean authorized = body != null && body.authorized();
      LOG.info("{}=bank.call.completed authorized={} status={}",
          EVENT, authorized, response.getStatusCode().value());
      if (body == null) {
        throw new AcquiringBankUnavailableException("Empty bank response body");
      }
      return body;
    } catch (HttpStatusCodeException e) {
      LOG.error("{}=bank.call.failed status={}", EVENT, e.getStatusCode().value());
      if (e.getStatusCode().value() == 503) {
        throw new AcquiringBankUnavailableException("Bank returned 503", e);
      }
      throw e;
    } catch (ResourceAccessException e) {
      LOG.error("{}=bank.call.timeout", EVENT);
      throw new AcquiringBankUnavailableException("Bank request timed out", e);
    }
  }
}
