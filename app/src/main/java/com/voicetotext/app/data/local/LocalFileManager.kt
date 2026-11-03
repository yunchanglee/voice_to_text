package com.voicetotext.app.data.local

import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LocalFileManager(private val context: Context) {

    /**
     * 기기 공용 문서(Documents) 하위 VoiceToText 디렉토리 가져오기 (없으면 자동 생성)
     */
    fun getVoiceToTextDirectory(): File {
        val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val v2tDir = File(docsDir, "VoiceToText")
        if (!v2tDir.exists()) {
            val created = v2tDir.mkdirs()
            Log.d("LocalFileManager", "Created VoiceToText dir: $created (${v2tDir.absolutePath})")
        }
        return v2tDir
    }

    /**
     * 새로운 텍스트(.txt) 파일 생성 및 초기 시작 정보 작성
     */
    suspend fun createNewTextFile(title: String, languageCode: String = "ko-KR"): File = withContext(Dispatchers.IO) {
        val dir = getVoiceToTextDirectory()
        var file = File(dir, "$title.txt")
        var counter = 1
        while (file.exists()) {
            file = File(dir, "${title}_$counter.txt")
            counter++
        }

        val langDisplay = when {
            languageCode.startsWith("ko", ignoreCase = true) -> "한국어"
            languageCode.startsWith("en", ignoreCase = true) -> "영어"
            languageCode.startsWith("ja", ignoreCase = true) -> "일본어"
            else -> "자동 감지"
        }

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA)
        val header = "[변환 시작: ${dateFormat.format(Date())} | 언어: $langDisplay]\n\n"

        val parent = file.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }
        FileOutputStream(file, false).writer(Charsets.UTF_8).use { writer ->
            writer.write(header)
        }
        scanFileWithSystem(file)
        file
    }

    /**
     * 앱 내에서 텍스트 파일 내용 직접 읽기
     */
    suspend fun readTextFile(file: File): String = withContext(Dispatchers.IO) {
        if (file.exists()) {
            try {
                file.readText(Charsets.UTF_8)
            } catch (e: Exception) {
                ""
            }
        } else {
            ""
        }
    }

    /**
     * 실시간 확정 문장 이어쓰기 (타임스탬프 없이 순수 변환 텍스트만 기록하여 자동 번역 완벽 호환)
     */
    suspend fun appendTranscript(file: File, formattedTime: String, text: String) = withContext(Dispatchers.IO) {
        try {
            val parent = file.parentFile
            if (parent != null && !parent.exists()) {
                parent.mkdirs()
            }
            if (!file.exists()) {
                file.createNewFile()
            }
            FileOutputStream(file, true).writer(Charsets.UTF_8).use { writer ->
                writer.append("$text\n\n")
            }
            scanFileWithSystem(file)
        } catch (e: Exception) {
            Log.e("LocalFileManager", "Failed to append transcript: ${e.message}", e)
        }
    }

    /**
     * 언어 전환 표시 기록
     */
    suspend fun appendLanguageChangeMarker(file: File, newLangName: String) = withContext(Dispatchers.IO) {
        // 자동 번역 시 혼선 방지를 위해 본문 내 별도 로그 마커는 기록하지 않음
    }

    /**
     * 여러 문장 일괄 이어쓰기
     */
    suspend fun appendTranscripts(file: File, segments: List<Pair<String, String>>) = withContext(Dispatchers.IO) {
        if (segments.isEmpty()) return@withContext
        try {
            val parent = file.parentFile
            if (parent != null && !parent.exists()) {
                parent.mkdirs()
            }
            FileOutputStream(file, true).writer(Charsets.UTF_8).use { writer ->
                for ((_, text) in segments) {
                    writer.append("$text\n\n")
                }
            }
            scanFileWithSystem(file)
        } catch (e: Exception) {
            Log.e("LocalFileManager", "Failed to append batch transcripts: ${e.message}", e)
        }
    }

    /**
     * 세션 종료 후 미디어 스캐너 인덱싱 및 변환 종료 정보 작성
     */
    suspend fun finalizeFile(file: File, durationFormatted: String, segmentCount: Int, charCount: Int) = withContext(Dispatchers.IO) {
        try {
            FileOutputStream(file, true).writer(Charsets.UTF_8).use { writer ->
                writer.append("[변환 종료: 소요 시간 $durationFormatted]\n")
            }
            scanFileWithSystem(file)
        } catch (e: Exception) {
            Log.e("LocalFileManager", "Failed to finalize file: ${e.message}", e)
        }
    }

    /**
     * DB의 전체 전사 문장을 포함하여 로컬 .txt 파일을 온전하게 동기화/재작성
     * 시작 정보 + 순수 전사 텍스트 본문 + 종료 정보 구성 (자동 번역 최적화)
     */
    suspend fun syncFullDocumentToFile(
        file: File,
        title: String,
        languageCode: String,
        createdAtMs: Long,
        transcripts: List<Pair<String, String>>,
        durationFormatted: String,
        segmentCount: Int,
        charCount: Int
    ) = withContext(Dispatchers.IO) {
        try {
            val parent = file.parentFile
            if (parent != null && !parent.exists()) {
                parent.mkdirs()
            }

            val langDisplay = when {
                languageCode.startsWith("ko", ignoreCase = true) -> "한국어"
                languageCode.startsWith("en", ignoreCase = true) -> "영어"
                languageCode.startsWith("ja", ignoreCase = true) -> "일본어"
                else -> "자동 감지"
            }

            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA)
            val dateStr = dateFormat.format(Date(createdAtMs))

            val fullContent = buildString {
                append("[변환 시작: $dateStr | 언어: $langDisplay]\n\n")

                if (transcripts.isEmpty()) {
                    append("(변환된 음성 내용이 없습니다.)\n\n")
                } else {
                    for ((_, text) in transcripts) {
                        if (text.isNotBlank()) {
                            append("$text\n\n")
                        }
                    }
                }

                append("[변환 종료: 소요 시간 $durationFormatted]\n")
            }

            FileOutputStream(file, false).writer(Charsets.UTF_8).use { writer ->
                writer.write(fullContent)
            }
            scanFileWithSystem(file)
            Log.d("LocalFileManager", "Full document synced to ${file.absolutePath} (${file.length()} bytes)")
        } catch (e: Exception) {
            Log.e("LocalFileManager", "Failed to sync full document: ${e.message}", e)
        }
    }

    /**
     * 번역 완료된 텍스트를 독립 파일(VTT_..._trans_[lang].txt)로 저장
     */
    suspend fun saveTranslatedFile(
        baseTitle: String,
        sourceLangCode: String,
        targetLangCode: String,
        translatedParagraphs: List<String>
    ): File = withContext(Dispatchers.IO) {
        val dir = getVoiceToTextDirectory()
        val targetFileName = "${baseTitle}_trans_${targetLangCode.lowercase()}.txt"
        val file = File(dir, targetFileName)

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA)
        val dateStr = dateFormat.format(Date())

        val content = buildString {
            append("[번역 시작: $dateStr | 원문: $sourceLangCode -> 번역: $targetLangCode | 온디바이스 ML Kit]\n\n")
            for (p in translatedParagraphs) {
                if (p.isNotBlank()) {
                    append("$p\n\n")
                }
            }
            append("[번역 완료: ${translatedParagraphs.size}개 문단]\n")
        }

        FileOutputStream(file, false).writer(Charsets.UTF_8).use { writer ->
            writer.write(content)
        }
        scanFileWithSystem(file)
        file
    }

    /**
     * 원문과 번역문을 나란히 매칭한 대조 파일(VTT_..._bilingual.txt) 저장
     */
    suspend fun saveBilingualFile(
        baseTitle: String,
        sourceLangCode: String,
        targetLangCode: String,
        bilingualPairs: List<Pair<String, String>>
    ): File = withContext(Dispatchers.IO) {
        val dir = getVoiceToTextDirectory()
        val targetFileName = "${baseTitle}_bilingual.txt"
        val file = File(dir, targetFileName)

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA)
        val dateStr = dateFormat.format(Date())

        val content = buildString {
            append("[대조 리포트: $sourceLangCode / $targetLangCode | $baseTitle | $dateStr]\n\n")
            for ((idx, pair) in bilingualPairs.withIndex()) {
                val num = String.format("%02d", idx + 1)
                append("[$num] 원문: ${pair.first}\n")
                append("[$num] 번역: ${pair.second}\n\n")
            }
        }

        FileOutputStream(file, false).writer(Charsets.UTF_8).use { writer ->
            writer.write(content)
        }
        scanFileWithSystem(file)
        file
    }

    /**
     * 안드로이드 시스템 미디어 스캐너 등록 (내 파일 앱에 즉시 보이도록 함)
     */
    fun scanFileWithSystem(file: File) {
        try {
            MediaScannerConnection.scanFile(
                context,
                arrayOf(file.absolutePath),
                arrayOf("text/plain")
            ) { path, uri ->
                Log.d("LocalFileManager", "Scanned file: $path -> $uri")
            }
        } catch (e: Exception) {
            Log.w("LocalFileManager", "MediaScanner error: ${e.message}")
        }
    }

    /**
     * 기기 기본 텍스트 뷰어나 메모장으로 파일 열기
     */
    fun openFile(context: Context, file: File): Boolean {
        if (!file.exists()) {
            Toast.makeText(context, "파일이 존재하지 않습니다: ${file.name}", Toast.LENGTH_SHORT).show()
            return false
        }

        return try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "text/plain")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "파일 열기"))
            true
        } catch (e: Exception) {
            Log.e("LocalFileManager", "Failed to open file intent: ${e.message}", e)
            Toast.makeText(context, "텍스트를 열 수 있는 앱이 없습니다.", Toast.LENGTH_SHORT).show()
            false
        }
    }

    /**
     * 기기 기본 파일 관리자(내 파일)에서 VoiceToText 폴더 열기 시도
     */
    fun openVoiceToTextFolder(context: Context) {
        val dir = getVoiceToTextDirectory()
        try {
            val uri = Uri.parse(dir.absolutePath)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "resource/folder")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                // 대체 인텐트: 시스템 파일 선택기/브라우저
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(intent, "내 파일 열기"))
            } catch (ex: Exception) {
                Toast.makeText(context, "폴더 경로: Documents/VoiceToText", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * 파일명 유효성 검사 (공백 금지, OS 금지문자 금지)
     */
    fun isValidFileName(name: String): Boolean {
        val trimmed = name.trim().removeSuffix(".txt")
        return trimmed.isNotBlank() && !FORBIDDEN_FILE_CHARS.containsMatchIn(trimmed)
    }

    /**
     * 파일명 정제 (금지문자 제거, 앞뒤 공백 제거, 60자 제한)
     */
    fun sanitizeFileName(name: String): String {
        val base = name.trim().removeSuffix(".txt")
        val sanitized = base.replace(FORBIDDEN_FILE_CHARS, "_")
            .trim()
            .take(60)
        return sanitized.ifBlank { "VTT_문서" }
    }

    /**
     * 텍스트 파일 이름 변경 (물리 파일 rename 및 연동 번역 파일 일괄 rename)
     */
    suspend fun renameTextFile(oldFile: File, newNameWithoutExt: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            if (!oldFile.exists()) {
                return@withContext Result.failure(java.io.FileNotFoundException("원본 파일이 존재하지 않습니다: ${oldFile.name}"))
            }

            val sanitized = sanitizeFileName(newNameWithoutExt)
            val parentDir = oldFile.parentFile ?: getVoiceToTextDirectory()
            val newFile = File(parentDir, "$sanitized.txt")

            // 이름이 동일하면 그대로 성공 처리
            if (oldFile.canonicalPath == newFile.canonicalPath) {
                return@withContext Result.success(oldFile)
            }

            // 파일 중복 검사
            var targetFile = newFile
            var counter = 1
            while (targetFile.exists() && targetFile.canonicalPath != oldFile.canonicalPath) {
                targetFile = File(parentDir, "${sanitized}_$counter.txt")
                counter++
            }

            val oldBaseName = oldFile.nameWithoutExtension
            val newBaseName = targetFile.nameWithoutExtension

            val renamed = oldFile.renameTo(targetFile)
            if (!renamed) {
                return@withContext Result.failure(java.io.IOException("파일 이름 변경에 실패했습니다."))
            }

            // 구 경로 및 신규 경로 미디어 스캐너 인덱싱 갱신
            scanFileWithSystem(oldFile)
            scanFileWithSystem(targetFile)

            // 연계 번역 파일 일괄 이름 변경 (_trans_*.txt, _bilingual.txt)
            val allFiles = parentDir.listFiles() ?: emptyArray()
            for (f in allFiles) {
                if (f.name.startsWith("${oldBaseName}_trans_") && f.name.endsWith(".txt")) {
                    val suffix = f.name.removePrefix("${oldBaseName}_trans_")
                    val targetTransFile = File(parentDir, "${newBaseName}_trans_$suffix")
                    if (f.renameTo(targetTransFile)) {
                        scanFileWithSystem(f)
                        scanFileWithSystem(targetTransFile)
                    }
                } else if (f.name == "${oldBaseName}_bilingual.txt") {
                    val targetBilingualFile = File(parentDir, "${newBaseName}_bilingual.txt")
                    if (f.renameTo(targetBilingualFile)) {
                        scanFileWithSystem(f)
                        scanFileWithSystem(targetBilingualFile)
                    }
                }
            }

            Result.success(targetFile)
        } catch (e: Exception) {
            Log.e("LocalFileManager", "Error renaming file: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 저장 통계 (파일 개수, 총 용량)
     */
    suspend fun getStorageStats(): Pair<Int, Long> = withContext(Dispatchers.IO) {
        val dir = getVoiceToTextDirectory()
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".txt") } ?: emptyArray()
        val count = files.size
        val totalBytes = files.sumOf { it.length() }
        count to totalBytes
    }

    /**
     * 전체 로컬 텍스트 파일 삭제
     */
    suspend fun deleteAllFiles() = withContext(Dispatchers.IO) {
        val dir = getVoiceToTextDirectory()
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".txt") } ?: emptyArray()
        for (f in files) {
            f.delete()
            scanFileWithSystem(f)
        }
    }

    companion object {
        private val FORBIDDEN_FILE_CHARS = Regex("[\\\\/:*?\"<>|]")
    }
}
