package com.voicetotext.app.data.translation

import android.content.Context
import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.voicetotext.app.data.model.AppLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

data class SupportedLanguage(
    val code: String,
    val displayName: String,
    val flag: String,
    val mlKitCode: String
)

class TranslationManager(private val context: Context) {

    private val modelManager = RemoteModelManager.getInstance()
    private val translatorCache = ConcurrentHashMap<String, Translator>()

    companion object {
        private const val TAG = "TranslationManager"

        val AVAILABLE_TARGET_LANGUAGES = listOf(
            SupportedLanguage("en", "영어 (English)", "🇺🇸", TranslateLanguage.ENGLISH),
            SupportedLanguage("ko", "한국어", "🇰🇷", TranslateLanguage.KOREAN),
            SupportedLanguage("ja", "일본어 (日本語)", "🇯🇵", TranslateLanguage.JAPANESE),
            SupportedLanguage("zh", "중국어 (中文)", "🇨🇳", TranslateLanguage.CHINESE),
            SupportedLanguage("es", "스페인어 (Español)", "🇪🇸", TranslateLanguage.SPANISH),
            SupportedLanguage("de", "독일어 (Deutsch)", "🇩🇪", TranslateLanguage.GERMAN),
            SupportedLanguage("fr", "프랑스어 (Français)", "🇫🇷", TranslateLanguage.FRENCH)
        )

        fun toMlKitLanguage(code: String): String {
            val lower = code.lowercase()
            return when {
                lower.startsWith("ko") -> TranslateLanguage.KOREAN
                lower.startsWith("en") -> TranslateLanguage.ENGLISH
                lower.startsWith("ja") -> TranslateLanguage.JAPANESE
                lower.startsWith("zh") -> TranslateLanguage.CHINESE
                lower.startsWith("es") -> TranslateLanguage.SPANISH
                lower.startsWith("de") -> TranslateLanguage.GERMAN
                lower.startsWith("fr") -> TranslateLanguage.FRENCH
                else -> TranslateLanguage.ENGLISH
            }
        }

        fun getDisplayName(code: String): String {
            val lang = AVAILABLE_TARGET_LANGUAGES.find { it.code.equals(code, ignoreCase = true) || it.mlKitCode.equals(code, ignoreCase = true) }
            return lang?.displayName ?: code.uppercase()
        }

        fun getFlag(code: String): String {
            val lang = AVAILABLE_TARGET_LANGUAGES.find { it.code.equals(code, ignoreCase = true) || it.mlKitCode.equals(code, ignoreCase = true) }
            return lang?.flag ?: "🌐"
        }
    }

    /**
     * 특정 언어 모델이 온디바이스에 다운로드되어 있는지 확인
     */
    suspend fun isModelDownloaded(langCode: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val mlLang = toMlKitLanguage(langCode)
            val model = TranslateRemoteModel.Builder(mlLang).build()
            modelManager.isModelDownloaded(model).await()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking model for $langCode: ${e.message}")
            false
        }
    }

    /**
     * 언어 모델 다운로드 수행
     */
    suspend fun downloadModel(langCode: String, requireWifi: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        try {
            val mlLang = toMlKitLanguage(langCode)
            val model = TranslateRemoteModel.Builder(mlLang).build()
            val conditionsBuilder = DownloadConditions.Builder()
            if (requireWifi) {
                conditionsBuilder.requireWifi()
            }
            modelManager.download(model, conditionsBuilder.build()).await()
            Log.d(TAG, "Successfully downloaded translation model: $langCode")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download translation model $langCode: ${e.message}", e)
            false
        }
    }

    /**
     * 다운로드된 모델 삭제 (저장공간 확보)
     */
    suspend fun deleteModel(langCode: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val mlLang = toMlKitLanguage(langCode)
            val model = TranslateRemoteModel.Builder(mlLang).build()
            modelManager.deleteDownloadedModel(model).await()
            // 캐시된 번역기 제거
            translatorCache.keys.filter { it.contains(mlLang) }.forEach { key ->
                translatorCache.remove(key)?.close()
            }
            Log.d(TAG, "Deleted translation model: $langCode")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete translation model $langCode: ${e.message}", e)
            false
        }
    }

    /**
     * 기기에 다운로드되어 있는 언어 모델 목록 반환
     */
    suspend fun getDownloadedModels(): List<TranslateRemoteModel> = withContext(Dispatchers.IO) {
        try {
            modelManager.getDownloadedModels(TranslateRemoteModel::class.java).await().toList()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting downloaded models: ${e.message}")
            emptyList()
        }
    }

    /**
     * 단일 텍스트 또는 문장 번역
     */
    suspend fun translate(
        text: String,
        sourceLangCode: String,
        targetLangCode: String
    ): Result<String> = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext Result.success("")

        val sourceMlLang = toMlKitLanguage(sourceLangCode)
        val targetMlLang = toMlKitLanguage(targetLangCode)

        if (sourceMlLang == targetMlLang) {
            return@withContext Result.success(text)
        }

        val cacheKey = "${sourceMlLang}_$targetMlLang"
        try {
            val translator = translatorCache.getOrPut(cacheKey) {
                val options = TranslatorOptions.Builder()
                    .setSourceLanguage(sourceMlLang)
                    .setTargetLanguage(targetMlLang)
                    .build()
                Translation.getClient(options)
            }

            // 모델 다운로드 확인 및 다운로드
            translator.downloadModelIfNeeded().await()

            val translated = translator.translate(text).await()
            Result.success(translated)
        } catch (e: Exception) {
            Log.e(TAG, "Translation error ($sourceMlLang -> $targetMlLang): ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 문단 또는 문장 리스트 배치 번역
     */
    suspend fun translateBatch(
        texts: List<String>,
        sourceLangCode: String,
        targetLangCode: String,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        if (texts.isEmpty()) return@withContext Result.success(emptyList())

        val sourceMlLang = toMlKitLanguage(sourceLangCode)
        val targetMlLang = toMlKitLanguage(targetLangCode)

        if (sourceMlLang == targetMlLang) {
            return@withContext Result.success(texts)
        }

        val cacheKey = "${sourceMlLang}_$targetMlLang"
        try {
            val translator = translatorCache.getOrPut(cacheKey) {
                val options = TranslatorOptions.Builder()
                    .setSourceLanguage(sourceMlLang)
                    .setTargetLanguage(targetMlLang)
                    .build()
                Translation.getClient(options)
            }

            translator.downloadModelIfNeeded().await()

            val results = ArrayList<String>(texts.size)
            for ((index, text) in texts.withIndex()) {
                if (text.isBlank()) {
                    results.add("")
                } else {
                    val translated = translator.translate(text).await()
                    results.add(translated)
                }
                onProgress(index + 1, texts.size)
            }
            Result.success(results)
        } catch (e: Exception) {
            Log.e(TAG, "Batch translation error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 실시간 음성인식 문장 빠른 번역 (실시간 자막용)
     */
    suspend fun translateRealtimeSegment(
        segmentText: String,
        sourceLangCode: String,
        targetLangCode: String = "ko"
    ): String? = withContext(Dispatchers.IO) {
        if (segmentText.isBlank()) return@withContext null
        try {
            val res = translate(segmentText, sourceLangCode, targetLangCode)
            res.getOrNull()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 리소스 정리
     */
    fun close() {
        translatorCache.values.forEach { it.close() }
        translatorCache.clear()
    }
}
