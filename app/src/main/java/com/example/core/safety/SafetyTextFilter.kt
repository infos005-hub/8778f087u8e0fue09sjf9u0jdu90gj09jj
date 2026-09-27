package com.example.core.safety

/**
 * 텍스트 보안 및 비속어/개인정보 실시간 감지 & 마스킹 엔진
 * - 전화번호 (010-XXXX-XXXX 및 변형 패턴)
 * - 주민등록번호
 * - 외부 SNS 메신저 ID 유도
 * - 비속어 및 욕설 필터링
 */
data class TextFilterResult(
    val originalText: String,
    val filteredText: String,
    val hasViolation: Boolean,
    val violationTypes: List<ViolationType>
)

enum class ViolationType(val label: String) {
    PHONE_NUMBER("연락처 감지"),
    PROFANITY("비속어 감지"),
    SOCIAL_ID("SNS ID 유도 감지"),
    RRN("주민번호 감지")
}

object SafetyTextFilter {
    // 1. 한국 휴대전화 정규식 (010, 011, 016, 017, 018, 019 등)
    private val PHONE_REGEX = Regex("""01[016789][\s.-]?\d{3,4}[\s.-]?\d{4}""")

    // 2. 주민등록번호 패턴
    private val RRN_REGEX = Regex("""\b\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\d|3[01])[\s-][1-4]\d{6}\b""")

    // 3. SNS 및 메신저 계정 공유 패턴
    private val SOCIAL_ID_REGEX = Regex("""(?i)(카[톡텃톧]|텔레|라인|인스타|오픈채팅|디코|discord)[\s:;_-]*[a-zA-Z0-9_.]{4,20}""")

    // 4. 주요 비속어 / 욕설 패턴
    private val PROFANITY_PATTERNS = listOf(
        Regex("""시[발벌빨팔]|씨[발벌빨팔]|ㅅㅂ|ㅆㅂ"""),
        Regex("""개[새세]끼|ㄱㅅㄲ"""),
        Regex("""병[신쉰]|ㅂㅅ"""),
        Regex("""지[랄럴]|ㅈㄹ"""),
        Regex("""존[나나]|ㅈㄴ"""),
        Regex("""닥[쳐처]"""),
        Regex("""미[친칭]""")
    )

    fun maskSensitiveContent(text: String): TextFilterResult {
        if (text.isBlank()) {
            return TextFilterResult(text, text, false, emptyList())
        }

        var processed = text
        val violations = mutableListOf<ViolationType>()

        // 1. 전화번호 마스킹
        if (PHONE_REGEX.containsMatchIn(processed)) {
            violations.add(ViolationType.PHONE_NUMBER)
            processed = PHONE_REGEX.replace(processed) { matchResult ->
                "***-****-****"
            }
        }

        // 2. 주민번호 마스킹
        if (RRN_REGEX.containsMatchIn(processed)) {
            violations.add(ViolationType.RRN)
            processed = RRN_REGEX.replace(processed, "******-*******")
        }

        // 3. SNS ID 마스킹
        if (SOCIAL_ID_REGEX.containsMatchIn(processed)) {
            violations.add(ViolationType.SOCIAL_ID)
            processed = SOCIAL_ID_REGEX.replace(processed, "[연락처 공유 제한]")
        }

        // 4. 비속어 마스킹
        for (pattern in PROFANITY_PATTERNS) {
            if (pattern.containsMatchIn(processed)) {
                if (!violations.contains(ViolationType.PROFANITY)) {
                    violations.add(ViolationType.PROFANITY)
                }
                processed = pattern.replace(processed) { matchResult ->
                    "*".repeat(matchResult.value.length)
                }
            }
        }

        return TextFilterResult(
            originalText = text,
            filteredText = processed,
            hasViolation = violations.isNotEmpty(),
            violationTypes = violations
        )
    }
}
