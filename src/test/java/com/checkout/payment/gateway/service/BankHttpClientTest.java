package com.checkout.payment.gateway.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.checkout.payment.gateway.exception.AcquiringBankUnavailableException;
import com.checkout.payment.gateway.model.BankPaymentRequest;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
 
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class BankHttpClientTest {

  @Test
  void authorize_when200AuthorizedFalse_returnsUnauthorized() {
    RestTemplate rt = new RestTemplate();
    rt.getMessageConverters().add(new MappingJackson2HttpMessageConverter());
    MockRestServiceServer server = MockRestServiceServer.bindTo(rt).build();

    String baseUrl = "http://localhost:9999";
    server.expect(ExpectedCount.once(), requestTo(baseUrl + "/payments"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withStatus(HttpStatus.OK)
            .contentType(MediaType.APPLICATION_JSON)
            .body("{\"authorized\":false,\"authorization_code\":\"def\"}"));

    BankHttpClient client = new BankHttpClient(rt, baseUrl);
    BankPaymentResponse resp = client.authorize(BankPaymentRequest.builder()
        .cardNumber("4242424242424242")
        .expiryDate("01/2030")
        .currency("USD")
        .amount(50)
        .cvv("123")
        .build());

    assertThat(resp.authorized()).isFalse();
    assertThat(resp.authorizationCode()).isEqualTo("def");
    server.verify();
  }

  @Test
  void authorize_when200AuthorizedTrue_returnsAuthorized() {
    RestTemplate rt = new RestTemplate();
    rt.getMessageConverters().add(new MappingJackson2HttpMessageConverter());
    MockRestServiceServer server = MockRestServiceServer.bindTo(rt).build();

    String baseUrl = "http://localhost:9999";
    server.expect(ExpectedCount.once(), requestTo(baseUrl + "/payments"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withStatus(HttpStatus.OK)
            .contentType(MediaType.APPLICATION_JSON)
            .body("{\"authorized\":true,\"authorization_code\":\"abc\"}"));

    BankHttpClient client = new BankHttpClient(rt, baseUrl);
    BankPaymentResponse resp = client.authorize(BankPaymentRequest.builder()
        .cardNumber("4242424242424241")
        .expiryDate("12/2030")
        .currency("USD")
        .amount(100)
        .cvv("123")
        .build());

    assertThat(resp.authorized()).isTrue();
    assertThat(resp.authorizationCode()).isEqualTo("abc");
    server.verify();
  }

  @Test
  void authorize_when503_throwsBankUnavailable() {
    RestTemplate rt = new RestTemplate();
    rt.getMessageConverters().add(new MappingJackson2HttpMessageConverter());
    MockRestServiceServer server = MockRestServiceServer.bindTo(rt).build();
    String baseUrl = "http://localhost:9999";
    server.expect(ExpectedCount.once(), requestTo(baseUrl + "/payments"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

    BankHttpClient client = new BankHttpClient(rt, baseUrl);
    assertThatThrownBy(() -> client.authorize(BankPaymentRequest.builder()
        .cardNumber("4242424242424241")
        .expiryDate("12/2030")
        .currency("USD")
        .amount(100)
        .cvv("123")
        .build()))
        .isInstanceOf(AcquiringBankUnavailableException.class);
    server.verify();
  }

  @Test
  void authorize_whenTimeout_throwsBankUnavailable() {
    RestTemplate rt = Mockito.mock(RestTemplate.class);
    String baseUrl = "http://localhost:9999";
    Mockito.when(rt.postForEntity(Mockito.eq(baseUrl + "/payments"), Mockito.any(), Mockito.eq(BankPaymentResponse.class)))
        .thenThrow(new ResourceAccessException("timeout"));

    BankHttpClient client = new BankHttpClient(rt, baseUrl);

    assertThatThrownBy(() -> client.authorize(BankPaymentRequest.builder()
        .cardNumber("4242424242424241")
        .expiryDate("12/2030")
        .currency("USD")
        .amount(100)
        .cvv("123")
        .build()))
        .isInstanceOf(AcquiringBankUnavailableException.class);
  }
}
