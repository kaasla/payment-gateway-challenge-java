package com.checkout.payment.gateway.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.PaymentRejectedException;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MockitoExtension.class)
class PaymentGatewayServiceTest {

  @Mock
  PaymentsRepository paymentsRepository;
  @Mock
  BankClient bankClient;
  @Mock
  PaymentValidator paymentValidator;

  PaymentGatewayService service;

  @BeforeEach
  void setup() {
    service = new PaymentGatewayService(paymentsRepository, bankClient, paymentValidator);
  }

  private PostPaymentRequest.PostPaymentRequestBuilder validRequest() {
    return PostPaymentRequest.builder()
        .cardNumber("2222405343248877")
        .expiryMonth(12)
        .expiryYear(2030)
        .currency("USD")
        .amount(100)
        .cvv("123");
  }

  @Test
  void whenValidationFails_thenRejected_andNoBankCallOrPersist() {
    var req = validRequest().currency("SEK").build();
    when(paymentValidator.validate(req)).thenReturn(Collections.singletonList("bad currency"));

    assertThatThrownBy(() -> service.processPayment(req))
        .isInstanceOf(PaymentRejectedException.class);

    verify(bankClient, never()).authorize(any());
    verify(paymentsRepository, never()).add(any());
  }

  @Test
  void whenAuthorized_thenStatusAuthorized_andPersisted() {
    var req = validRequest().build();
    when(paymentValidator.validate(req)).thenReturn(Collections.emptyList());
    when(bankClient.authorize(any())).thenReturn(BankPaymentResponse.builder()
        .authorized(true)
        .authorization_code("auth")
        .build());

    PostPaymentResponse resp = service.processPayment(req);

    assertThat(resp.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
    assertThat(resp.getCardNumberLastFour()).isEqualTo(8877);

    ArgumentCaptor<PostPaymentResponse> captor = ArgumentCaptor.forClass(PostPaymentResponse.class);
    verify(paymentsRepository, times(1)).add(captor.capture());
    assertThat(captor.getValue().getId()).isEqualTo(resp.getId());
  }

  @Test
  void whenDeclined_thenStatusDeclined_andPersisted() {
    var req = validRequest().build();
    when(paymentValidator.validate(req)).thenReturn(Collections.emptyList());
    when(bankClient.authorize(any())).thenReturn(BankPaymentResponse.builder()
        .authorized(false)
        .authorization_code("unauth")
        .build());

    PostPaymentResponse resp = service.processPayment(req);
    assertThat(resp.getStatus()).isEqualTo(PaymentStatus.DECLINED);
    verify(paymentsRepository, times(1)).add(any());
  }

  @Test
  void whenPanNonDigit_last4FallsBackToZero() {
    var req = validRequest().cardNumber("abcdEFGHijklMNOP").build();
    when(paymentValidator.validate(req)).thenReturn(Collections.emptyList());
    when(bankClient.authorize(any())).thenReturn(BankPaymentResponse.builder()
        .authorized(false)
        .authorization_code("x")
        .build());

    PostPaymentResponse resp = service.processPayment(req);
    assertThat(resp.getCardNumberLastFour()).isEqualTo(0);
  }
}
