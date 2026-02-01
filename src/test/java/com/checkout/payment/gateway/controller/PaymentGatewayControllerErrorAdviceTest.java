package com.checkout.payment.gateway.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.service.PaymentGatewayService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Tag;

@SpringBootTest
@AutoConfigureMockMvc
@Tag("integration")
class PaymentGatewayControllerErrorAdviceTest {

  @Autowired
  private MockMvc mvc;

  @MockBean
  private PaymentGatewayService paymentGatewayService;

  @Test
  @DisplayName("Unhandled runtime error in service → 500 Internal Server Error")
  // Ensures generic exceptions are mapped to a 500 ErrorResponse
  void postUnhandledRuntimeException_returns500() throws Exception {
    when(paymentGatewayService.processPayment(any(PostPaymentRequest.class)))
        .thenThrow(new RuntimeException("boom"));

    String body = "{\n" +
        "  \"card_number\": \"2222405343248877\",\n" +
        "  \"expiry_month\": 12,\n" +
        "  \"expiry_year\": 2030,\n" +
        "  \"currency\": \"USD\",\n" +
        "  \"amount\": 100,\n" +
        "  \"cvv\": \"123\"\n" +
        "}";

    mvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON).header("X-API-Key","test-key").content(body))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.message").value("Internal server error"));
  }
}
