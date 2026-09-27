package com.example

import com.example.core.safety.SafetyTextFilter
import com.example.core.safety.ViolationType
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun testSafetyTextFilter_masksPhoneNumberAndProfanity() {
    val input = "내 번호 010-1234-5678 이니까 카톡 friend123 으로 연락해 시발"
    val result = SafetyTextFilter.maskSensitiveContent(input)

    assertTrue(result.hasViolation)
    assertTrue(result.violationTypes.contains(ViolationType.PHONE_NUMBER))
    assertTrue(result.violationTypes.contains(ViolationType.PROFANITY))
    assertTrue(result.violationTypes.contains(ViolationType.SOCIAL_ID))

    assertTrue(result.filteredText.contains("***-****-****"))
    assertTrue(result.filteredText.contains("[연락처 공유 제한]"))
    assertFalse(result.filteredText.contains("010-1234-5678"))
    assertFalse(result.filteredText.contains("시발"))
  }

  @Test
  fun testSafetyTextFilter_cleanTextPasses() {
    val input = "오늘 날씨 정말 좋네요! 한강 러닝 같이 하실 분 계신가요?"
    val result = SafetyTextFilter.maskSensitiveContent(input)

    assertFalse(result.hasViolation)
    assertEquals(input, result.filteredText)
  }
}
