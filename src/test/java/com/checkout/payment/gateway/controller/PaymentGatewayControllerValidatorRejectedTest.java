package com.checkout.payment.gateway.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkout.payment.gateway.service.BankService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentGatewayControllerValidatorRejectedTest {

  @Autowired
  private MockMvc mvc;

  @MockBean
  private BankService bankService; // should not be called when validator rejects

  @Test
  void postRejectedByValidator_returns400AndDoesNotCallBank() throws Exception {
    // Valid by Bean Validation but invalid by cross-field rules (expiry in past, currency not whitelisted)
    String body = "{\n" +
        "  \"card_number\": \"2222405343248877\",\n" +
        "  \"expiry_month\": 1,\n" +
        "  \"expiry_year\": 2020,\n" +
        "  \"currency\": \"SEK\",\n" +
        "  \"amount\": 100,\n" +
        "  \"cvv\": \"123\"\n" +
        "}";

    mvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON).header("X-API-Key","test-key").content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("Rejected"))
        .andExpect(jsonPath("$.errors").isArray())
        .andExpect(header().exists("X-Correlation-Id"));

    verify(bankService, never()).requestAuthorization(any());
  }
}
