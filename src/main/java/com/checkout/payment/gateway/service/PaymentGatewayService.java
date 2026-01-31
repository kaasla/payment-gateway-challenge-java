package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.exception.EventProcessingException;
import com.checkout.payment.gateway.exception.PaymentRejectedException;
import com.checkout.payment.gateway.model.BankPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.model.GetPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

@Service
public class PaymentGatewayService {

  private static final Logger LOG = LoggerFactory.getLogger(PaymentGatewayService.class);

  private final PaymentsRepository paymentsRepository;
  private final BankClient bankClient;
  private final PaymentValidator paymentValidator;

  public PaymentGatewayService(PaymentsRepository paymentsRepository, BankClient bankClient,
      PaymentValidator paymentValidator) {
    this.paymentsRepository = paymentsRepository;
    this.bankClient = bankClient;
    this.paymentValidator = paymentValidator;
  }

  public GetPaymentResponse getPaymentById(UUID id) {
    LOG.debug("event=payment.lookup id={}", id);
    var stored = paymentsRepository.get(id)
        .orElseThrow(() -> new EventProcessingException("Invalid ID"));
    return GetPaymentResponse.builder()
        .id(stored.getId())
        .status(stored.getStatus())
        .cardNumberLastFour(stored.getCardNumberLastFour())
        .expiryMonth(stored.getExpiryMonth())
        .expiryYear(stored.getExpiryYear())
        .currency(stored.getCurrency())
        .amount(stored.getAmount())
        .build();
  }

  public PostPaymentResponse processPayment(PostPaymentRequest paymentRequest) {
    LOG.info("event=payment.request.received currency={} amount={}",
        paymentRequest.getCurrency(), paymentRequest.getAmount());

    var errors = paymentValidator.validate(paymentRequest);
    if (!errors.isEmpty()) {
      LOG.warn("event=payment.rejected errors={}", errors.size());
      throw new PaymentRejectedException(errors);
    }

    var bankReq = BankPaymentRequest.builder()
        .card_number(paymentRequest.getCardNumber())
        .expiry_date(paymentRequest.getExpiryDate())
        .currency(paymentRequest.getCurrency())
        .amount(paymentRequest.getAmount())
        .cvv(paymentRequest.getCvv())
        .build();

    var bankResp = bankClient.authorize(bankReq);

    var status = bankResp.authorized()
        ? com.checkout.payment.gateway.enums.PaymentStatus.AUTHORIZED
        : com.checkout.payment.gateway.enums.PaymentStatus.DECLINED;

    int last4 = CardDataUtil.extractLast4(paymentRequest.getCardNumber());
    var id = UUID.randomUUID();
    MDC.put("payment_id", id.toString());
    var response = PostPaymentResponse.builder()
        .id(id)
        .status(status)
        .cardNumberLastFour(last4)
        .expiryMonth(paymentRequest.getExpiryMonth())
        .expiryYear(paymentRequest.getExpiryYear())
        .currency(paymentRequest.getCurrency())
        .amount(paymentRequest.getAmount())
        .build();

    paymentsRepository.add(response);

    LOG.info("event=payment.completed payment_id={} status={} currency={} amount={} last4={}",
        id, status.getName(), response.getCurrency(), response.getAmount(), last4);

    return response;
  }

}
