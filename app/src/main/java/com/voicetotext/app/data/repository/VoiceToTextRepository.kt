package com.voicetotext.app.data.repository

import android.content.Context
import com.voicetotext.app.data.local.DocumentEntity
import com.voicetotext.app.data.local.LocalFileManager
import com.voicetotext.app.data.local.PreferencesManager
import com.voicetotext.app.data.local.TranscriptEntity
import com.voicetotext.app.data.local.VoiceToTextDatabase
import com.voicetotext.app.data.model.AppLanguage
import com.voicetotext.app.data.model.SyncStatus
import com.voicetotext.app.data.translation.TranslationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class VoiceToTextRepository(
    private val context: Context,
    private val database: VoiceToTextDatabase,
    val preferencesManager: PreferencesManager,
    val localFileManager: LocalFileManager,
    val translationManager: TranslationManager
) {
    private val documentDao = database.documentDao()
    private val transcriptDao = database.transcriptDao()
    private val repoScope = CoroutineScope(Dispatchers.IO + Job())

    private var activeDocId: String? = null
    private var activeLocalFile: File? = null

    val allDocuments: Flow<List<DocumentEntity>> = documentDao.getAllDocuments()

    fun searchDocuments(query: String): Flow<List<DocumentEntity>> =
        if (query.isBlank()) documentDao.getAllDocuments() else documentDao.searchDocuments(query)

    fun getTranscripts(documentId: String): Flow<List<TranscriptEntity>> =
        transcriptDao.getTranscriptsForDocument(documentId)

    suspend fun getDocument(documentId: String): DocumentEntity? =
        documentDao.getDocumentById(documentId)

    suspend fun getFullTranscriptText(documentId: String): String = withContext(Dispatchers.IO) {
        val list = transcriptDao.getTranscriptsListForDocument(documentId)
        list.joinToString("\n\n") { "[${it.formattedTime}] ${it.text}" }
    }

    /**
     * 새로운 변환 세션 시작: DB 레코드 생성 및 Documents/VoiceToText 폴더에 .txt 파일 생성
     */
    suspend fun createNewSession(languageCode: String = "auto"): DocumentEntity = withContext(Dispatchers.IO) {
        val docId = UUID.randomUUID().toString()
        val dateFormat = SimpleDateFormat("yyMMdd_HHmm", Locale.KOREA)
        val title = "VTT_${dateFormat.format(Date())}"

        // 로컬 Documents/VoiceToText/ 디렉토리에 .txt 파일 생성
        val textFile = localFileManager.createNewTextFile(title, languageCode)
        activeLocalFile = textFile
        activeDocId = docId

        val entity = DocumentEntity(
            id = docId,
            title = textFile.nameWithoutExtension,
            googleDocsFileId = null,
            localFilePath = textFile.absolutePath,
            fileSizeBytes = textFile.length(),
            language = languageCode,
            createdAt = System.currentTimeMillis(),
            durationSeconds = 0,
            segmentCount = 0,
            charCount = 0,
            syncStatus = SyncStatus.SYNCING,
            summaryPreview = "변환 준비 중..."
        )
        documentDao.insertDocument(entity)
        entity
    }

    /**
     * 기존 문서에 이어서 작성(Append) 세션 시작
     */
    suspend fun startAppendSession(existingDocId: String): DocumentEntity = withContext(Dispatchers.IO) {
        val doc = documentDao.getDocumentById(existingDocId)
            ?: throw IllegalArgumentException("문서를 찾을 수 없습니다: $existingDocId")

        val file = doc.localFilePath?.let { File(it) }
            ?: File(localFileManager.getVoiceToTextDirectory(), "${doc.title}.txt")

        activeDocId = doc.id
        activeLocalFile = file

        // 저장 상태를 SYNCING으로 갱신
        documentDao.updateSyncStatus(doc.id, SyncStatus.SYNCING, doc.googleDocsFileId)
        doc
    }

    /**
     * 파일 및 문서 제목 변경 (Save As / Rename)
     */
    suspend fun renameDocument(documentId: String, newTitleWithoutExt: String): Result<DocumentEntity> = withContext(Dispatchers.IO) {
        val doc = documentDao.getDocumentById(documentId)
            ?: return@withContext Result.failure(IllegalArgumentException("문서를 찾을 수 없습니다: $documentId"))

        val currentFile = doc.localFilePath?.let { File(it) }
            ?: File(localFileManager.getVoiceToTextDirectory(), "${doc.title}.txt")

        val renameResult = localFileManager.renameTextFile(currentFile, newTitleWithoutExt)
        if (renameResult.isFailure) {
            return@withContext Result.failure(renameResult.exceptionOrNull() ?: Exception("파일 이름 변경 실패"))
        }

        val newFile = renameResult.getOrNull()!!
        val newTitle = newFile.nameWithoutExtension

        // 번역 파일 경로가 존재했었다면 새 이름으로 업데이트
        val newTransPath = if (!doc.translatedFilePath.isNullOrBlank()) {
            val transFile = File(doc.translatedFilePath)
            val parent = transFile.parentFile ?: localFileManager.getVoiceToTextDirectory()
            val suffix = transFile.name.removePrefix("${doc.title}_trans_")
            File(parent, "${newTitle}_trans_$suffix").absolutePath
        } else {
            null
        }

        documentDao.updateTitleAndPaths(
            id = documentId,
            newTitle = newTitle,
            newFilePath = newFile.absolutePath,
            newTransPath = newTransPath
        )

        val updated = documentDao.getDocumentById(documentId)!!
        if (activeDocId == documentId) {
            activeLocalFile = newFile
        }
        Result.success(updated)
    }

    /**
     * 확정된 텍스트 문장 추가: Room DB 및 로컬 .txt 파일에 즉시 기록
     */
    suspend fun addFinalTranscript(
        documentId: String,
        timestampMs: Long,
        formattedTime: String,
        text: String
    ): Long = withContext(Dispatchers.IO) {
        val transcript = TranscriptEntity(
            documentId = documentId,
            timestampMs = timestampMs,
            formattedTime = formattedTime,
            text = text,
            isSynced = true
        )
        val id = transcriptDao.insertTranscript(transcript)

        // 로컬 .txt 파일 대상 찾기 (activeLocalFile 또는 DB에 저장된 localFilePath 사용)
        val doc = documentDao.getDocumentById(documentId)
        val targetFile = activeLocalFile ?: doc?.localFilePath?.let { File(it) }
            ?: File(localFileManager.getVoiceToTextDirectory(), "${doc?.title ?: "VTT"}.txt")

        localFileManager.appendTranscript(targetFile, formattedTime, text)

        // 문서 통계 및 프리뷰 업데이트
        if (doc != null) {
            val preview = if (doc.summaryPreview == "변환 준비 중...") text else "${doc.summaryPreview} $text".take(120)
            val currentFileSize = targetFile.length()
            documentDao.updateStats(
                id = documentId,
                duration = timestampMs / 1000,
                segments = doc.segmentCount + 1,
                chars = doc.charCount + text.length,
                preview = preview,
                filePath = targetFile.absolutePath,
                fileSize = currentFileSize
            )
        }
        id
    }

    /**
     * 세션 종료: DB의 모든 전사 문장을 바탕으로 로컬 .txt 파일을 완벽하게 동기화 저장
     */
    suspend fun finalizeSession(documentId: String, durationSeconds: Long) = withContext(Dispatchers.IO) {
        val doc = documentDao.getDocumentById(documentId)
        val file = activeLocalFile ?: (doc?.localFilePath?.let { File(it) })
            ?: File(localFileManager.getVoiceToTextDirectory(), "${doc?.title ?: "VTT"}.txt")
        val transcripts = transcriptDao.getTranscriptsListForDocument(documentId)

        val durationFormatted = formatSeconds(durationSeconds)
        val segmentCount = if (transcripts.isNotEmpty()) transcripts.size else (doc?.segmentCount ?: 0)
        val charCount = transcripts.sumOf { it.text.length }

        // 전체 전사 문장을 포함하여 .txt 파일 완벽하게 동기화 저장
        localFileManager.syncFullDocumentToFile(
            file = file,
            title = doc?.title ?: file.nameWithoutExtension,
            languageCode = doc?.language ?: "ko-KR",
            createdAtMs = doc?.createdAt ?: System.currentTimeMillis(),
            transcripts = transcripts.map { it.formattedTime to it.text },
            durationFormatted = durationFormatted,
            segmentCount = segmentCount,
            charCount = charCount
        )

        val finalFileSize = file.length()

        if (doc != null) {
            val preview = if (transcripts.isNotEmpty()) {
                transcripts.joinToString(" ") { it.text }.take(120)
            } else {
                doc.summaryPreview
            }
            documentDao.updateStats(
                id = documentId,
                duration = durationSeconds,
                segments = segmentCount,
                chars = charCount,
                preview = preview,
                filePath = file.absolutePath,
                fileSize = finalFileSize
            )
            documentDao.updateSyncStatus(documentId, SyncStatus.SYNCED, null)
        }

        activeDocId = null
        activeLocalFile = null
    }

    /**
     * 문서의 전체 텍스트 조합 (파일 내용 또는 Room DB 데이터 기반 보장)
     */
    suspend fun buildFullDocumentText(doc: DocumentEntity): String = withContext(Dispatchers.IO) {
        val file = doc.localFilePath?.let { File(it) }
        val diskText = if (file != null && file.exists()) {
            try { file.readText(Charsets.UTF_8) } catch (e: Exception) { "" }
        } else ""

        // 디스크 텍스트에 이미 문장 내용이 존재하면 그대로 반환
        if (diskText.isNotBlank() && diskText.contains("]")) {
            return@withContext diskText
        }

        // 디스크 텍스트가 헤더만 있거나 비어있다면 DB의 전체 문장 목록으로 복원
        val transcripts = transcriptDao.getTranscriptsListForDocument(doc.id)
        if (transcripts.isNotEmpty()) {
            val durationFormatted = formatSeconds(doc.durationSeconds)
            val segmentCount = transcripts.size
            val charCount = transcripts.sumOf { it.text.length }

            // 디스크 파일도 복구 저장
            val targetFile = file ?: File(localFileManager.getVoiceToTextDirectory(), "${doc.title}.txt")
            localFileManager.syncFullDocumentToFile(
                file = targetFile,
                title = doc.title,
                languageCode = doc.language,
                createdAtMs = doc.createdAt,
                transcripts = transcripts.map { it.formattedTime to it.text },
                durationFormatted = durationFormatted,
                segmentCount = segmentCount,
                charCount = charCount
            )

            // 복원된 텍스트 반환
            targetFile.readText(Charsets.UTF_8)
        } else {
            diskText.ifBlank { doc.summaryPreview }
        }
    }

    /**
     * 변환 중 또는 완료 후 문서 언어 업데이트
     */
    suspend fun updateDocumentLanguage(documentId: String, language: String) = withContext(Dispatchers.IO) {
        documentDao.updateLanguage(documentId, language)
        val doc = documentDao.getDocumentById(documentId)
        val file = activeLocalFile ?: (doc?.localFilePath?.let { File(it) })
        if (file != null && file.exists()) {
            val langObj = com.voicetotext.app.data.model.AppLanguage.fromCode(language)
            localFileManager.appendLanguageChangeMarker(file, "${langObj.displayName} (${langObj.nativeName})")
        }
    }

    /**
     * 문서 및 실제 로컬 파일 삭제
     */
    suspend fun deleteDocument(documentId: String) = withContext(Dispatchers.IO) {
        val doc = documentDao.getDocumentById(documentId)
        doc?.localFilePath?.let { path ->
            val f = File(path)
            if (f.exists()) {
                f.delete()
                localFileManager.scanFileWithSystem(f)
            }
        }
        documentDao.deleteDocument(documentId)
    }

    /**
     * 특정 문서에 대해 타겟 언어로 번역을 수행하고 독립/대조 파일 생성 및 DB 업데이트
     */
    suspend fun translateDocument(
        documentId: String,
        targetLangCode: String,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): Result<DocumentEntity> = withContext(Dispatchers.IO) {
        val doc = documentDao.getDocumentById(documentId)
            ?: return@withContext Result.failure(IllegalArgumentException("문서를 찾을 수 없습니다: $documentId"))

        val transcripts = transcriptDao.getTranscriptsListForDocument(documentId)
        val textsToTranslate = if (transcripts.isNotEmpty()) {
            transcripts.map { it.text }
        } else {
            // DB 문장이 비어있을 경우 파일에서 읽은 본문 단락 사용
            val fullText = buildFullDocumentText(doc)
            fullText.lines().filter { it.isNotBlank() && !it.startsWith("[") }
        }

        if (textsToTranslate.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("번역할 텍스트가 없습니다."))
        }

        // 원문 언어 판단
        val detectedLang = AppLanguage.detectDominantLanguage(textsToTranslate.joinToString(" "))
        val sourceLangCode = detectedLang?.code ?: doc.language

        // 번역 수행
        val transResult = translationManager.translateBatch(
            texts = textsToTranslate,
            sourceLangCode = sourceLangCode,
            targetLangCode = targetLangCode,
            onProgress = onProgress
        )

        if (transResult.isFailure) {
            return@withContext Result.failure(transResult.exceptionOrNull() ?: Exception("번역 실패"))
        }

        val translatedTexts = transResult.getOrNull() ?: emptyList()

        // DB 개별 전사 문장에 번역문 업데이트
        if (transcripts.isNotEmpty() && transcripts.size == translatedTexts.size) {
            for (i in transcripts.indices) {
                transcriptDao.updateTranscriptTranslation(transcripts[i].id, translatedTexts[i])
            }
        }

        // 파일 저장 (독립 번역 파일 및 대조 파일)
        val translatedFile = localFileManager.saveTranslatedFile(
            baseTitle = doc.title,
            sourceLangCode = sourceLangCode,
            targetLangCode = targetLangCode,
            translatedParagraphs = translatedTexts
        )

        val bilingualPairs = textsToTranslate.zip(translatedTexts)
        localFileManager.saveBilingualFile(
            baseTitle = doc.title,
            sourceLangCode = sourceLangCode,
            targetLangCode = targetLangCode,
            bilingualPairs = bilingualPairs
        )

        val fullTransText = translatedTexts.joinToString("\n\n")

        // 문서 DB 업데이트
        documentDao.updateTranslation(
            id = documentId,
            transText = fullTransText,
            targetLang = targetLangCode,
            filePath = translatedFile.absolutePath
        )

        val updatedDoc = documentDao.getDocumentById(documentId)!!
        Result.success(updatedDoc)
    }

    /**
     * 실시간 음성인식 문장에 대한 빠른 번역
     */
    suspend fun translateLiveSegment(
        segmentText: String,
        sourceLangCode: String,
        targetLangCode: String = "ko"
    ): String? {
        return translationManager.translateRealtimeSegment(segmentText, sourceLangCode, targetLangCode)
    }

    /**
     * 전체 변환 기록 및 파일 삭제
     */
    suspend fun deleteAllDocuments() = withContext(Dispatchers.IO) {
        localFileManager.deleteAllFiles()
        documentDao.deleteAllDocuments()
        transcriptDao.deleteAllTranscripts()
    }

    private fun formatSeconds(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) {
            String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", m, s)
        }
    }
}
