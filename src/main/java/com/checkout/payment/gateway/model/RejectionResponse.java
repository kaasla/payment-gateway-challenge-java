package com.checkout.payment.gateway.model;

import com.checkout.payment.gateway.enums.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
@Schema(name = "RejectionResponse", description = "Validation failure response")
public class RejectionResponse {
  @Schema(description = "Payment status", example = "Rejected")
  private PaymentStatus status;

  @Schema(description = "List of validation error messages")
  private List<String> errors;
}
