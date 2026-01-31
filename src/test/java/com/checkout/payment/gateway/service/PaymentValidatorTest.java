package com.checkout.payment.gateway.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.checkout.payment.gateway.model.PostPaymentRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PaymentValidatorTest {

  private PaymentValidator validator;
  private Clock fixedClock;

  @BeforeEach
  void setup() {
    // Fix time to 2025-01-15 for deterministic tests
    fixedClock = Clock.fixed(Instant.parse("2025-01-15T12:00:00Z"), ZoneId.of("UTC"));
    validator = new PaymentValidator(fixedClock, "USD,EUR,GBP");
  }

  private PostPaymentRequest.PostPaymentRequestBuilder validRequest() {
    return PostPaymentRequest.builder()
        .cardNumber("4242424242424241")
        .expiryMonth(12)
        .expiryYear(2030)
        .currency("USD")
        .amount(100)
        .cvv("123");
  }

  @Test
  void expiryInThePast_isError() {
    var req = validRequest()
        .expiryMonth(12)
        .expiryYear(2024)
        .build();

    List<String> errors = validator.validate(req);
    assertThat(errors).anyMatch(msg -> msg.toLowerCase().contains("expiry"));
  }

  @Test
  void expiryInCurrentMonth_isError() {
    LocalDate fixed = LocalDate.now(fixedClock);
    var req = validRequest()
        .expiryMonth(fixed.getMonthValue())
        .expiryYear(fixed.getYear())
        .build();
    List<String> errors = validator.validate(req);
    assertThat(errors).anyMatch(msg -> msg.toLowerCase().contains("expiry"));
  }

  @Test
  void expiryInFuture_isValid() {
    var req = validRequest().build();
    List<String> errors = validator.validate(req);
    assertThat(errors).isEmpty();
  }

  @Test
  void currencyNotInWhitelist_isError() {
    var req = validRequest().currency("SEK").build();
    List<String> errors = validator.validate(req);
    assertThat(errors).anyMatch(msg -> msg.contains("Currency must be one of"));
  }

  @Test
  void amountZero_isError() {
    var req = validRequest().amount(0).build();
    List<String> errors = validator.validate(req);
    assertThat(errors).anyMatch(msg -> msg.toLowerCase().contains("amount"));
  }

  @Test
  void multipleErrors_areAggregated() {
    // Past expiry and bad currency and non-positive amount
    var req = validRequest()
        .expiryMonth(1)
        .expiryYear(2020)
        .currency("ABC")
        .amount(-10)
        .build();
    List<String> errors = validator.validate(req);
    assertThat(errors).hasSizeGreaterThanOrEqualTo(3);
  }
}

