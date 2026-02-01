package com.checkout.payment.gateway.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Tag("integration")
class ApiKeyAuthFilterTest {

  @Autowired
  private MockMvc mvc;

  @Test
  @DisplayName("Missing API key → 401 Unauthorized")
  // Ensures requests without X-API-Key are rejected with 401
  void missingApiKey_returns401() throws Exception {
    String body = "{\n" +
        "  \"card_number\": \"2222405343248877\",\n" +
        "  \"expiry_month\": 12,\n" +
        "  \"expiry_year\": 2030,\n" +
        "  \"currency\": \"USD\",\n" +
        "  \"amount\": 100,\n" +
        "  \"cvv\": \"123\"\n" +
        "}";

    mvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Invalid API key → 403 Forbidden")
  // Ensures requests with an invalid X-API-Key are rejected with 403
  void invalidApiKey_returns403() throws Exception {
    String body = "{\n" +
        "  \"card_number\": \"2222405343248877\",\n" +
        "  \"expiry_month\": 12,\n" +
        "  \"expiry_year\": 2030,\n" +
        "  \"currency\": \"USD\",\n" +
        "  \"amount\": 100,\n" +
        "  \"cvv\": \"123\"\n" +
        "}";

    mvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON).header("X-API-Key","wrong-key").content(body))
        .andExpect(status().isForbidden());
  }
}

