package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.model.PostPaymentRequest;
import java.time.Clock;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PaymentValidator {

  private final Clock clock;
  private final Set<String> allowedCurrencies;

  public PaymentValidator(
      Clock clock,
      @Value("${gateway.payments.allowed-currencies:USD,EUR,GBP}") String allowedCurrencies) {
    this.clock = clock;
    this.allowedCurrencies = Arrays.stream(allowedCurrencies.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .map(s -> s.toUpperCase(Locale.ROOT))
        .collect(Collectors.toUnmodifiableSet());
  }

  public List<String> validate(PostPaymentRequest request) {
    List<String> errors = new ArrayList<>();

    // Expiry month+year must be in the future
    YearMonth now = YearMonth.now(clock);
    try {
      YearMonth expiry = YearMonth.of(request.getExpiryYear(), request.getExpiryMonth());
      if (!expiry.isAfter(now)) {
        errors.add("Expiry date must be in the future");
      }
    } catch (RuntimeException e) {
      errors.add("Invalid expiry month/year");
    }

    // Currency must be in whitelist (≤ 3 codes)
    String currency = request.getCurrency() == null ? "" : request.getCurrency().toUpperCase(Locale.ROOT);
    if (!allowedCurrencies.contains(currency)) {
      errors.add("Currency must be one of: " + String.join(", ", allowedCurrencies));
    }

    // Amount must be > 0 (defense in depth; also enforced by @Positive)
    if (request.getAmount() <= 0) {
      errors.add("Amount must be greater than 0");
    }

    return errors;
  }
}

