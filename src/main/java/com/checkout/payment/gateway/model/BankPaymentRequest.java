package com.checkout.payment.gateway.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(name = "BankPaymentRequest", description = "Request shape for bank simulator")
public record BankPaymentRequest(
    @JsonProperty("card_number") String cardNumber,
    @JsonProperty("expiry_date") String expiryDate,
    @JsonProperty("currency") String currency,
    @JsonProperty("amount") int amount,
    @JsonProperty("cvv") String cvv
) {}
