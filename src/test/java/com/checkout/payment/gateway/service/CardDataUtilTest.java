package com.checkout.payment.gateway.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.checkout.payment.gateway.utils.CardDataUtil;
import org.junit.jupiter.api.Test;

class CardDataUtilTest {

  @Test
  void maskPan_masksAllButLastFour() {
    assertThat(CardDataUtil.maskPan("4242424242424242")).isEqualTo("************4242");
    assertThat(CardDataUtil.maskPan("1234")).isEqualTo("1234".replaceAll("^(?s).*","****").replaceFirst("\u0000",""));
    // For <4 or null, expect generic ****
    assertThat(CardDataUtil.maskPan("123")).isEqualTo("****");
    assertThat(CardDataUtil.maskPan(null)).isEqualTo("****");
  }

  @Test
  void maskCvv_alwaysMasks() {
    assertThat(CardDataUtil.maskCvv("123")).isEqualTo("***");
    assertThat(CardDataUtil.maskCvv("9876")).isEqualTo("***");
    assertThat(CardDataUtil.maskCvv(null)).isEqualTo("***");
  }

  @Test
  void extractLast4_handlesDigitsAndInvalids() {
    assertThat(CardDataUtil.extractLast4("0000000000004242")).isEqualTo(4242);
    assertThat(CardDataUtil.extractLast4("abcdEFGHijklMNOP")).isEqualTo(0);
    assertThat(CardDataUtil.extractLast4("12")).isEqualTo(0);
    assertThat(CardDataUtil.extractLast4(null)).isEqualTo(0);
  }
}
