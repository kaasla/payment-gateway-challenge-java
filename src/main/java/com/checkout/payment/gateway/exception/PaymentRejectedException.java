package com.checkout.payment.gateway.exception;

import java.util.List;
import lombok.Getter;

@Getter
public class PaymentRejectedException extends RuntimeException {
  private final List<String> errors;

  public PaymentRejectedException(List<String> errors) {
    super("Payment rejected due to validation errors");
    this.errors = errors;
  }
}

