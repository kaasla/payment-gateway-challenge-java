package com.checkout.payment.gateway.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "PostPaymentRequest", description = "Payment processing request")
public class PostPaymentRequest implements Serializable {

  @Schema(description = "Card number (14-19 digits)", example = "4242424242424242")
  @NotBlank
  @Pattern(regexp = "^\\d{14,19}$")
  @JsonProperty("card_number")
  private String cardNumber;

  @Schema(description = "Expiry month (1-12)", example = "12")
  @Min(1)
  @Max(12)
  @JsonProperty("expiry_month")
  private int expiryMonth;

  @Schema(description = "Expiry year (must be in the future)", example = "2030")
  @Min(2000)
  @JsonProperty("expiry_year")
  private int expiryYear;

  @Schema(description = "ISO 4217 currency code (3 letters)", example = "USD")
  @NotBlank
  @Pattern(regexp = "^[A-Z]{3}$")
  private String currency;

  @Schema(description = "Amount in minor units", example = "1050")
  @Positive
  private int amount;

  @Schema(description = "Card verification value (3-4 digits)", example = "123")
  @NotBlank
  @Pattern(regexp = "^\\d{3,4}$")
  private String cvv;

  @JsonProperty("expiry_date")
  @Schema(description = "Derived expiry date for bank format MM/YYYY", example = "04/2025")
  public String getExpiryDate() {
    return String.format("%02d/%d", expiryMonth, expiryYear);
  }

  @Override
  public String toString() {
    return "PostPaymentRequest{" +
        "cardNumber=" + maskCardNumber(cardNumber) +
        ", expiryMonth=" + expiryMonth +
        ", expiryYear=" + expiryYear +
        ", currency='" + currency + '\'' +
        ", amount=" + amount +
        ", cvv=" + maskCvv(cvv) +
        '}';
  }

  private static String maskCardNumber(String pan) {
    if (pan == null || pan.length() < 4) {
      return "****";
    }
    int unmasked = 4;
    String last4 = pan.substring(pan.length() - unmasked);
    return "*".repeat(Math.max(0, pan.length() - unmasked)) + last4;
  }

  private static String maskCvv(String c) {
    return c == null ? "***" : "***";
  }
}
