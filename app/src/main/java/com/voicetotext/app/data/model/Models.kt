package com.voicetotext.app.data.model

enum class CaptureState {
    IDLE,
    PREPARING,
    RECORDING,
    PAUSED,
    SAVING,
    COMPLETED
}

enum class SyncStatus {
    SYNCED,
    SYNCING,
    PENDING,
    ERROR
}

enum class AppLanguage(
    val code: String,
    val displayName: String,
    val nativeName: String,
    val flag: String,
    val badgeText: String
) {
    AUTO("auto", "자동 감지", "Auto", "🌐", "자동"),
    KOREAN("ko-KR", "한국어", "한국어", "🇰🇷", "한국어"),
    ENGLISH("en-US", "영어", "English", "🇺🇸", "English"),
    JAPANESE("ja-JP", "일본어", "日本語", "🇯🇵", "日本語");

    val isAuto: Boolean get() = this == AUTO

    companion object {
        fun fromCode(code: String?): AppLanguage {
            if (code.isNullOrBlank() || code.equals("auto", ignoreCase = true)) return AUTO
            val lower = code.lowercase()
            return when {
                lower.startsWith("ko") -> KOREAN
                lower.startsWith("en") -> ENGLISH
                lower.startsWith("ja") -> JAPANESE
                else -> entries.find { it.code.equals(code, ignoreCase = true) } ?: AUTO
            }
        }

        /**
         * 텍스트 문자셋을 분석하여 가장 지배적인(dominant) 언어를 감지
         * 환각(hallucination) 방지를 위해 문자 수 비율 및 최소 임계치 적용
         */
        fun detectDominantLanguage(text: String): AppLanguage? {
            if (text.isBlank()) return null

            var hangulCount = 0
            var kanaCount = 0
            var latinCount = 0

            for (c in text) {
                when {
                    c in '\uAC00'..'\uD7A3' || c in '\u1100'..'\u11FF' || c in '\u3130'..'\u318F' -> hangulCount++
                    c in '\u3040'..'\u309F' || c in '\u30A0'..'\u30FF' -> kanaCount++
                    c in 'a'..'z' || c in 'A'..'Z' -> latinCount++
                }
            }

            val maxCount = maxOf(hangulCount, kanaCount, latinCount)
            if (maxCount < 2) return null

            return when {
                hangulCount >= maxCount && hangulCount > (latinCount / 2) -> KOREAN
                latinCount >= maxCount && latinCount > (hangulCount / 2) && latinCount > (kanaCount / 2) -> ENGLISH
                kanaCount >= maxCount && kanaCount > (latinCount / 2) -> JAPANESE
                else -> null
            }
        }

        fun detectLanguageFromText(text: String): AppLanguage? = detectDominantLanguage(text)
    }
}

data class UserAccount(
    val email: String = "",
    val displayName: String = "",
    val photoUrl: String? = null,
    val isDefaultDeviceAccount: Boolean = true,
    val folderId: String? = null
)
