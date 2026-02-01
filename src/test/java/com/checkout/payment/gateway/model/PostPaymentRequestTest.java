package com.checkout.payment.gateway.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PostPaymentRequestTest {

  @Test
  @DisplayName("PostPaymentRequest.toString masks PAN/CVV and formats expiry")
  void toString_masksSensitiveFields_andExpiryDateIsFormatted() {
    PostPaymentRequest req = PostPaymentRequest.builder()
        .cardNumber("4242424242424242")
        .expiryMonth(4)
        .expiryYear(2025)
        .currency("USD")
        .amount(100)
        .cvv("123")
        .build();

    String s = req.toString();
    assertThat(s).doesNotContain("4242424242424242");
    assertThat(s).contains("************4242");
    assertThat(s).doesNotContain("\"cvv\": \"123\"");
    assertThat(req.getExpiryDate()).isEqualTo("04/2025");
  }
}
