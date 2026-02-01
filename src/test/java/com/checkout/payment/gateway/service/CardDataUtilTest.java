package com.checkout.payment.gateway.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.checkout.payment.gateway.utils.CardDataUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CardDataUtilTest {

  @Test
  @DisplayName("maskPan hides all but last 4 digits (or **** when unavailable)")
  void maskPan_masksAllButLastFour() {
    assertThat(CardDataUtil.maskPan("4242424242424242")).isEqualTo("************4242");
    assertThat(CardDataUtil.maskPan("1234")).isEqualTo("1234");
    assertThat(CardDataUtil.maskPan("123")).isEqualTo("****");
    assertThat(CardDataUtil.maskPan(null)).isEqualTo("****");
  }

  @Test
  @DisplayName("maskCvv always returns *** regardless of input")
  void maskCvv_alwaysMasks() {
    assertThat(CardDataUtil.maskCvv("123")).isEqualTo("***");
    assertThat(CardDataUtil.maskCvv("9876")).isEqualTo("***");
    assertThat(CardDataUtil.maskCvv(null)).isEqualTo("***");
  }

  @Test
  @DisplayName("extractLast4 returns digits or 0 for invalid/short PAN")
  void extractLast4_handlesDigitsAndInvalids() {
    assertThat(CardDataUtil.extractLast4("0000000000004242")).isEqualTo(4242);
    assertThat(CardDataUtil.extractLast4("abcdEFGHijklMNOP")).isEqualTo(0);
    assertThat(CardDataUtil.extractLast4("12")).isEqualTo(0);
    assertThat(CardDataUtil.extractLast4(null)).isEqualTo(0);
  }
}
