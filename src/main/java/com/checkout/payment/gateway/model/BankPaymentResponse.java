package com.checkout.payment.gateway.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(name = "BankPaymentResponse", description = "Response shape from bank simulator")
public record BankPaymentResponse(
    @JsonProperty("authorized") boolean authorized,
    @JsonProperty("authorization_code") String authorization_code
) {}

