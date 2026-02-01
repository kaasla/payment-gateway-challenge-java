package com.checkout.payment.gateway.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkout.payment.gateway.exception.AcquiringBankUnavailableException;
import com.checkout.payment.gateway.model.BankPaymentRequest;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import com.checkout.payment.gateway.service.BankService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Tag;

@SpringBootTest
@AutoConfigureMockMvc
@Tag("integration")
class PaymentGatewayControllerPostTest {

  @Autowired
  private MockMvc mvc;

  @MockBean
  private BankService bankService;

  @Test
  @DisplayName("POST valid payment → 201 Authorized with last4")
  void postValidAuthorized_returns201Authorized() throws Exception {
    when(bankService.requestAuthorization(any(BankPaymentRequest.class)))
        .thenReturn(BankPaymentResponse.builder().authorized(true).authorizationCode("auth").build());

    String body = "{\n" +
        "  \"card_number\": \"2222405343248877\",\n" +
        "  \"expiry_month\": 12,\n" +
        "  \"expiry_year\": 2030,\n" +
        "  \"currency\": \"USD\",\n" +
        "  \"amount\": 100,\n" +
        "  \"cvv\": \"123\"\n" +
        "}";

    mvc.perform(post("/api/v1/payments").contentType(MediaType.APPLICATION_JSON).header("X-API-Key","test-key").content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("Authorized"))
        .andExpect(jsonPath("$.cardNumberLastFour").value(8877));
  }

  @Test
  @DisplayName("POST valid payment → 201 Declined with last4")
  void postValidDeclined_returns201Declined() throws Exception {
    when(bankService.requestAuthorization(any(BankPaymentRequest.class)))
        .thenReturn(BankPaymentResponse.builder().authorized(false).authorizationCode("unauth").build());

    String body = "{\n" +
        "  \"card_number\": \"2222405343248878\",\n" +
        "  \"expiry_month\": 12,\n" +
        "  \"expiry_year\": 2030,\n" +
        "  \"currency\": \"USD\",\n" +
        "  \"amount\": 100,\n" +
        "  \"cvv\": \"123\"\n" +
        "}";

    mvc.perform(post("/api/v1/payments").contentType(MediaType.APPLICATION_JSON).header("X-API-Key","test-key").content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("Declined"))
        .andExpect(jsonPath("$.cardNumberLastFour").value(8878));
  }

  @Test
  @DisplayName("POST invalid payload → 400 Rejected with errors list")
  void postInvalid_returns400Rejected() throws Exception {
    // invalid: bad pan length and wrong currency and amount <= 0
    String body = "{\n" +
        "  \"card_number\": \"123\",\n" +
        "  \"expiry_month\": 0,\n" +
        "  \"expiry_year\": 2020,\n" +
        "  \"currency\": \"SEK\",\n" +
        "  \"amount\": 0,\n" +
        "  \"cvv\": \"12\"\n" +
        "}";

    mvc.perform(post("/api/v1/payments").contentType(MediaType.APPLICATION_JSON).header("X-API-Key","test-key").content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("Rejected"))
        .andExpect(jsonPath("$.errors").isArray());
  }

  @Test
  @DisplayName("POST when bank unavailable → 503 with message")
  void postWhenBankUnavailable_returns503() throws Exception {
    when(bankService.requestAuthorization(any(BankPaymentRequest.class)))
        .thenThrow(new AcquiringBankUnavailableException("down"));

    String body = "{\n" +
        "  \"card_number\": \"2222405343248870\",\n" +
        "  \"expiry_month\": 12,\n" +
        "  \"expiry_year\": 2030,\n" +
        "  \"currency\": \"USD\",\n" +
        "  \"amount\": 100,\n" +
        "  \"cvv\": \"123\"\n" +
        "}";

    mvc.perform(post("/api/v1/payments").contentType(MediaType.APPLICATION_JSON).header("X-API-Key","test-key").content(body))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.message").value("Payment processor unavailable, retry later"));
  }
}
