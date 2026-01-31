package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.model.BankPaymentRequest;
import com.checkout.payment.gateway.model.BankPaymentResponse;

public interface BankClient {
  BankPaymentResponse authorize(BankPaymentRequest request);
}

