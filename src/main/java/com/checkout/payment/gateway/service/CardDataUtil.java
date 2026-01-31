package com.checkout.payment.gateway.service;

public final class CardDataUtil {

  private CardDataUtil() {}

  public static String maskPan(String pan) {
    if (pan == null || pan.length() < 4) {
      return "****";
    }
    int unmasked = 4;
    String last4 = pan.substring(pan.length() - unmasked);
    return "*".repeat(Math.max(0, pan.length() - unmasked)) + last4;
  }

  public static String maskCvv(String cvv) {
    return "***";
  }

  public static int extractLast4(String pan) {
    if (pan == null || pan.length() < 4) {
      return 0;
    }
    String last4 = pan.substring(pan.length() - 4);
    try {
      return Integer.parseInt(last4);
    } catch (NumberFormatException e) {
      return 0;
    }
  }
}

