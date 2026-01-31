package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.model.PostPaymentRequest;
import java.time.Clock;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class PaymentValidator {

  private static final Set<String> ALLOWED_CURRENCIES = Set.of("USD", "EUR", "GBP");

  private final Clock clock;

  public PaymentValidator(Clock clock) {
    this.clock = clock;
  }

  public List<String> validate(PostPaymentRequest request) {
    List<String> errors = new ArrayList<>();

    YearMonth now = YearMonth.now(clock);
    try {
      YearMonth expiry = YearMonth.of(request.getExpiryYear(), request.getExpiryMonth());
      if (!expiry.isAfter(now)) {
        errors.add("Expiry date must be in the future");
      }
    } catch (RuntimeException e) {
      errors.add("Invalid expiry month/year");
    }

    String currency = request.getCurrency() == null ? "" : request.getCurrency().toUpperCase(Locale.ROOT);
    if (!ALLOWED_CURRENCIES.contains(currency)) {
      errors.add("Currency must be one of: USD, EUR, GBP");
    }

    if (request.getAmount() <= 0) {
      errors.add("Amount must be greater than 0");
    }

    return errors;
  }
}
