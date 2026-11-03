package com.voicetotext.app.ui.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.voicetotext.app.VoiceToTextApplication
import com.voicetotext.app.data.local.DocumentEntity
import com.voicetotext.app.data.local.TranscriptEntity
import com.voicetotext.app.data.model.AppLanguage
import com.voicetotext.app.data.model.CaptureState
import com.voicetotext.app.service.AudioCaptureService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar

enum class HistoryFilter {
    ALL, TODAY, THIS_WEEK, THIS_MONTH
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val repository = (application as VoiceToTextApplication).repository

    // 서비스 브릿지 상태
    val captureState: StateFlow<CaptureState> = AudioCaptureService.Bridge.captureState
    val currentDocument: StateFlow<DocumentEntity?> = AudioCaptureService.Bridge.currentDocument
    val isAppendMode: StateFlow<Boolean> = AudioCaptureService.Bridge.isAppendMode
    val elapsedTimeSeconds: StateFlow<Long> = AudioCaptureService.Bridge.elapsedTimeSeconds
    val interimText: StateFlow<String> = AudioCaptureService.Bridge.interimText
    val waveform: StateFlow<List<Float>> = AudioCaptureService.Bridge.waveform

    // 현재 활성 세션의 확정 문장 리스트
    @OptIn(ExperimentalCoroutinesApi::class)
    val activeTranscripts: StateFlow<List<TranscriptEntity>> = currentDocument.flatMapLatest { doc ->
        if (doc != null) {
            repository.getTranscripts(doc.id)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 변환 기록 검색 및 필터
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow(HistoryFilter.ALL)
    val selectedFilter: StateFlow<HistoryFilter> = _selectedFilter.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val historyDocuments: StateFlow<List<DocumentEntity>> = combine(
        _searchQuery.flatMapLatest { query -> repository.searchDocuments(query) },
        _selectedFilter
    ) { docs, filter ->
        filterDocuments(docs, filter)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 상세 보기 대상 문서
    private val _selectedDetailDoc = MutableStateFlow<DocumentEntity?>(null)
    val selectedDetailDoc: StateFlow<DocumentEntity?> = _selectedDetailDoc.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedDetailTranscripts: StateFlow<List<TranscriptEntity>> = _selectedDetailDoc.flatMapLatest { doc ->
        if (doc != null) {
            repository.getTranscripts(doc.id)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilter(filter: HistoryFilter) {
        _selectedFilter.value = filter
    }

    fun selectDocumentForDetail(doc: DocumentEntity?) {
        _selectedDetailDoc.value = doc
    }

    val selectedLanguage: StateFlow<AppLanguage> = repository.preferencesManager.languageFlow
        .map { AppLanguage.fromCode(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppLanguage.AUTO)

    val detectedLanguage: StateFlow<AppLanguage?> = AudioCaptureService.Bridge.detectedLanguage

    fun selectLanguage(language: AppLanguage) {
        viewModelScope.launch {
            repository.preferencesManager.saveLanguage(language.code)
            AudioCaptureService.Bridge.setSelectedLanguage(language)

            // 변환 중(녹음 중 또는 일시중지 중)일 경우 서비스로 즉시 동적 언어 전환 인텐트 전송
            val state = captureState.value
            if (state == CaptureState.RECORDING || state == CaptureState.PAUSED) {
                val context = getApplication<Application>()
                val intent = Intent(context, AudioCaptureService::class.java).apply {
                    action = AudioCaptureService.ACTION_CHANGE_LANGUAGE
                    putExtra(AudioCaptureService.EXTRA_LANGUAGE, language.code)
                }
                context.startService(intent)
            }
        }
    }

    suspend fun getFullTranscriptText(documentId: String): String {
        return repository.getFullTranscriptText(documentId)
    }

    suspend fun readTextFile(doc: DocumentEntity): String {
        return repository.buildFullDocumentText(doc)
    }

    fun startSession(resultCode: Int, data: Intent) {
        viewModelScope.launch {
            val langCode = selectedLanguage.value.code
            val doc = repository.createNewSession(langCode)
            AudioCaptureService.Bridge.setIsAppendMode(false)
            AudioCaptureService.Bridge.setCurrentDocument(doc)

            val context = getApplication<Application>()
            val intent = Intent(context, AudioCaptureService::class.java).apply {
                action = AudioCaptureService.ACTION_START
                putExtra(AudioCaptureService.EXTRA_RESULT_CODE, resultCode)
                putExtra(AudioCaptureService.EXTRA_RESULT_DATA, data)
                putExtra(AudioCaptureService.EXTRA_LANGUAGE, langCode)
                putExtra(AudioCaptureService.EXTRA_IS_APPEND, false)
            }
            context.startForegroundService(intent)
        }
    }

    fun startAppendSession(targetDoc: DocumentEntity, resultCode: Int, data: Intent) {
        viewModelScope.launch {
            val langCode = targetDoc.language
            val doc = repository.startAppendSession(targetDoc.id)
            AudioCaptureService.Bridge.setIsAppendMode(true)
            AudioCaptureService.Bridge.setCurrentDocument(doc)
            AudioCaptureService.Bridge.setElapsedTime(doc.durationSeconds)

            val context = getApplication<Application>()
            val intent = Intent(context, AudioCaptureService::class.java).apply {
                action = AudioCaptureService.ACTION_START
                putExtra(AudioCaptureService.EXTRA_RESULT_CODE, resultCode)
                putExtra(AudioCaptureService.EXTRA_RESULT_DATA, data)
                putExtra(AudioCaptureService.EXTRA_LANGUAGE, langCode)
                putExtra(AudioCaptureService.EXTRA_IS_APPEND, true)
            }
            context.startForegroundService(intent)
        }
    }

    fun pauseSession() {
        val context = getApplication<Application>()
        val intent = Intent(context, AudioCaptureService::class.java).apply {
            action = AudioCaptureService.ACTION_PAUSE
        }
        context.startService(intent)
    }

    fun resumeSession() {
        val context = getApplication<Application>()
        val intent = Intent(context, AudioCaptureService::class.java).apply {
            action = AudioCaptureService.ACTION_RESUME
        }
        context.startService(intent)
    }

    fun stopSession() {
        val context = getApplication<Application>()
        val intent = Intent(context, AudioCaptureService::class.java).apply {
            action = AudioCaptureService.ACTION_STOP
        }
        context.startService(intent)
    }

    fun resetToIdle() {
        AudioCaptureService.Bridge.reset()
    }

    fun finalizeDocument(doc: DocumentEntity) {
        viewModelScope.launch {
            repository.finalizeSession(doc.id, doc.durationSeconds)
            android.widget.Toast.makeText(getApplication(), "문서가 로컬에 저장 완료되었습니다.", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun cancelDocumentSave(doc: DocumentEntity) {
        viewModelScope.launch {
            repository.deleteDocument(doc.id)
            if (_selectedDetailDoc.value?.id == doc.id) {
                _selectedDetailDoc.value = null
            }
            android.widget.Toast.makeText(getApplication(), "저장이 취소되고 문서가 삭제되었습니다.", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun deleteDocument(docId: String) {
        viewModelScope.launch {
            repository.deleteDocument(docId)
            if (_selectedDetailDoc.value?.id == docId) {
                _selectedDetailDoc.value = null
            }
        }
    }

    fun deleteAllDocuments() {
        viewModelScope.launch {
            repository.deleteAllDocuments()
            _selectedDetailDoc.value = null
        }
    }

    fun renameDocument(
        documentId: String,
        newTitleWithoutExt: String,
        onComplete: (Result<DocumentEntity>) -> Unit = {}
    ) {
        viewModelScope.launch {
            val result = repository.renameDocument(documentId, newTitleWithoutExt)
            if (result.isSuccess) {
                val updated = result.getOrNull()
                if (_selectedDetailDoc.value?.id == documentId) {
                    _selectedDetailDoc.value = updated
                }
                if (currentDocument.value?.id == documentId) {
                    AudioCaptureService.Bridge.setCurrentDocument(updated)
                }
                android.widget.Toast.makeText(
                    getApplication(),
                    "파일명이 '${updated?.title}.txt'로 변경되었습니다.",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            } else {
                android.widget.Toast.makeText(
                    getApplication(),
                    "이름 변경 실패: ${result.exceptionOrNull()?.message}",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            onComplete(result)
        }
    }

    private val _isTranslating = MutableStateFlow(false)
    val isTranslating: StateFlow<Boolean> = _isTranslating.asStateFlow()

    fun translateDocument(
        documentId: String,
        targetLangCode: String = "en",
        onComplete: (Result<DocumentEntity>) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isTranslating.value = true
            try {
                val result = repository.translateDocument(documentId, targetLangCode)
                if (result.isSuccess) {
                    val updatedDoc = result.getOrNull()
                    if (_selectedDetailDoc.value?.id == documentId) {
                        _selectedDetailDoc.value = updatedDoc
                    }
                    android.widget.Toast.makeText(getApplication(), "번역이 완료되었습니다.", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    android.widget.Toast.makeText(getApplication(), "번역 실패: ${result.exceptionOrNull()?.message}", android.widget.Toast.LENGTH_LONG).show()
                }
                onComplete(result)
            } catch (e: Exception) {
                android.widget.Toast.makeText(getApplication(), "번역 오류: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                onComplete(Result.failure(e))
            } finally {
                _isTranslating.value = false
            }
        }
    }

    private fun filterDocuments(docs: List<DocumentEntity>, filter: HistoryFilter): List<DocumentEntity> {
        if (filter == HistoryFilter.ALL) return docs

        val calendar = Calendar.getInstance()
        val now = calendar.timeInMillis

        return docs.filter { doc ->
            when (filter) {
                HistoryFilter.TODAY -> {
                    val docCal = Calendar.getInstance().apply { timeInMillis = doc.createdAt }
                    calendar.get(Calendar.YEAR) == docCal.get(Calendar.YEAR) &&
                            calendar.get(Calendar.DAY_OF_YEAR) == docCal.get(Calendar.DAY_OF_YEAR)
                }
                HistoryFilter.THIS_WEEK -> {
                    now - doc.createdAt <= 7L * 24 * 3600 * 1000
                }
                HistoryFilter.THIS_MONTH -> {
                    val docCal = Calendar.getInstance().apply { timeInMillis = doc.createdAt }
                    calendar.get(Calendar.YEAR) == docCal.get(Calendar.YEAR) &&
                            calendar.get(Calendar.MONTH) == docCal.get(Calendar.MONTH)
                }
                HistoryFilter.ALL -> true
            }
        }
    }
}
