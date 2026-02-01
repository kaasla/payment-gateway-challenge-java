package com.checkout.payment.gateway.model;

import com.checkout.payment.gateway.utils.CardDataUtil;
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
  @NotBlank(message = "{validation.cardNumber.required}")
  @Pattern(regexp = "^\\d{14,19}$", message = "{validation.cardNumber.digits}")
  @JsonProperty("card_number")
  private String cardNumber;

  @Schema(description = "Expiry month (1-12)", example = "12")
  @Min(value = 1, message = "{validation.expiryMonth.range}")
  @Max(value = 12, message = "{validation.expiryMonth.range}")
  @JsonProperty("expiry_month")
  private int expiryMonth;

  @Schema(description = "Expiry year (must be in the future)", example = "2030")
  @JsonProperty("expiry_year")
  private int expiryYear;

  @Schema(description = "ISO 4217 currency code (3 letters)", example = "USD")
  @NotBlank(message = "{validation.currency.required}")
  @Pattern(regexp = "^[A-Z]{3}$", message = "{validation.currency.code}")
  private String currency;

  @Schema(description = "Amount in minor units", example = "1050")
  @Positive(message = "{validation.amount.positive}")
  private int amount;

  @Schema(description = "Card verification value (3-4 digits)", example = "123")
  @NotBlank(message = "{validation.cvv.required}")
  @Pattern(regexp = "^\\d{3,4}$", message = "{validation.cvv.digits}")
  private String cvv;

  @JsonProperty("expiry_date")
  @Schema(description = "Derived expiry date for bank format MM/YYYY", example = "04/2025")
  public String getExpiryDate() {
    return String.format("%02d/%d", expiryMonth, expiryYear);
  }

  @Override
  public String toString() {
    return "PostPaymentRequest{" +
        "cardNumber=" + CardDataUtil.maskPan(cardNumber) +
        ", expiryMonth=" + expiryMonth +
        ", expiryYear=" + expiryYear +
        ", currency='" + currency + '\'' +
        ", amount=" + amount +
        ", cvv=" + CardDataUtil.maskCvv(cvv) +
        '}';
  }
}
