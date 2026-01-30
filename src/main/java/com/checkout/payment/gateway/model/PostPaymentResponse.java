package com.checkout.payment.gateway.model;

import com.checkout.payment.gateway.enums.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "PostPaymentResponse", description = "Payment processing result")
public class PostPaymentResponse {
  @Schema(description = "Payment identifier", example = "550e8400-e29b-41d4-a716-446655440000")
  private UUID id;

  @Schema(description = "Payment status", example = "Authorized")
  private PaymentStatus status;

  @Schema(description = "Last four digits of the card", example = "4242")
  private int cardNumberLastFour;

  @Schema(description = "Expiry month", example = "12")
  private int expiryMonth;

  @Schema(description = "Expiry year", example = "2030")
  private int expiryYear;

  @Schema(description = "ISO 4217 currency code", example = "USD")
  private String currency;

  @Schema(description = "Amount in minor units", example = "1050")
  private int amount;

  @Override
  public String toString() {
    return "PostPaymentResponse{" +
        "id=" + id +
        ", status=" + status +
        ", cardNumberLastFour=" + cardNumberLastFour +
        ", expiryMonth=" + expiryMonth +
        ", expiryYear=" + expiryYear +
        ", currency='" + currency + '\'' +
        ", amount=" + amount +
        '}';
  }
}
