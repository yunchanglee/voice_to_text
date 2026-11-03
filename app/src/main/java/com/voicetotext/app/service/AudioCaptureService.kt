package com.voicetotext.app.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import com.voicetotext.app.MainActivity
import com.voicetotext.app.R
import com.voicetotext.app.VoiceToTextApplication
import com.voicetotext.app.data.local.DocumentEntity
import com.voicetotext.app.data.model.AppLanguage
import com.voicetotext.app.data.model.CaptureState
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.sqrt

class AudioCaptureService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var recordThread: Thread? = null

    private var audioRecord: AudioRecord? = null
    private var speechRecognizer: SpeechRecognizer? = null

    // 실시간 오디오 파이프 (세션 간 버퍼 적체 및 지연 방지)
    private var currentReadFd: ParcelFileDescriptor? = null
    private var currentWriteFd: ParcelFileDescriptor? = null
    private var currentPipeOut: FileOutputStream? = null

    // 세션 전환 시 무손실 오디오 연속성 보장 큐 (최대 500ms 버퍼)
    private val audioBufferLock = Any()
    private val audioPreRollQueue = java.util.ArrayDeque<ByteArray>()
    private val MAX_PRE_ROLL_CHUNKS = 8

    private var currentDocument: DocumentEntity? = null
    private var selectedLanguage: String = AppLanguage.AUTO.code
    private var sessionStartTime: Long = 0
    private var elapsedSecondsBeforePause: Long = 0
    private var pauseStartTime: Long = 0
    private var timerJob: Job? = null

    // 직전 확정 문장 캐시 (중복 작성 및 덮어쓰기 방지)
    private var lastCommittedText: String = ""
    private var lastCommittedTime: Long = 0L

    // 자동 감지(AUTO) 모드용 후보 언어 순환 상태
    private var autoCandidateIndex = 0
    private val autoCandidates = listOf("ko-KR", "en-US", "ja-JP")
    private var noMatchCountInAuto = 0

    private val repository get() = VoiceToTextApplication.instance.repository

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                selectedLanguage = intent.getStringExtra(EXTRA_LANGUAGE) ?: AppLanguage.AUTO.code
                val isAppend = intent.getBooleanExtra(EXTRA_IS_APPEND, false)
                Bridge.setIsAppendMode(isAppend)
                Bridge.setSelectedLanguage(AppLanguage.fromCode(selectedLanguage))
                Bridge.setDetectedLanguage(null)
                autoCandidateIndex = 0
                noMatchCountInAuto = 0
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }
                val doc = Bridge.currentDocument.value
                if (doc != null && resultCode != 0 && resultData != null) {
                    currentDocument = doc
                    startCapture(resultCode, resultData)
                }
            }
            ACTION_CHANGE_LANGUAGE -> {
                val newLang = intent.getStringExtra(EXTRA_LANGUAGE) ?: AppLanguage.AUTO.code
                changeLanguageInternal(newLang)
            }
            ACTION_PAUSE -> pauseCapture()
            ACTION_RESUME -> resumeCapture()
            ACTION_STOP -> stopCapture()
        }
        return START_NOT_STICKY
    }

    private fun changeLanguageInternal(newLang: String) {
        if (selectedLanguage == newLang) return
        Log.i(TAG, "Dynamic language change requested: $selectedLanguage -> $newLang")
        selectedLanguage = newLang
        Bridge.setSelectedLanguage(AppLanguage.fromCode(newLang))
        autoCandidateIndex = 0
        noMatchCountInAuto = 0
        if (newLang != AppLanguage.AUTO.code) {
            Bridge.setDetectedLanguage(null)
        }

        val doc = currentDocument
        if (doc != null) {
            serviceScope.launch {
                repository.updateDocumentLanguage(doc.id, newLang)
            }
        }

        if (Bridge.captureState.value == CaptureState.RECORDING) {
            scheduleRestart(delayMs = 150)
        }
        updateNotification()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_desc)
            setShowBadge(true)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(state: CaptureState, lastText: String): Notification {
        val appIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingAppIntent = PendingIntent.getActivity(
            this,
            0,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val docTitle = currentDocument?.title ?: "VoiceToText"
        val seconds = Bridge.elapsedTimeSeconds.value
        val timeFormatted = formatTime(seconds)

        val isPaused = state == CaptureState.PAUSED
        val title = if (isPaused) "일시중지됨 · $timeFormatted" else "실시간 변환 중 · $timeFormatted"
        val iconRes = if (isPaused) R.drawable.ic_stat_paused else R.drawable.ic_stat_transcribing

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(iconRes)
            .setContentTitle(title)
            .setContentText(if (lastText.isNotBlank()) "\"$lastText\"" else "📄 $docTitle")
            .setSubText("📁 $docTitle.txt · 로컬 저장 중")
            .setContentIntent(pendingAppIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        // 알림 제어 액션 버튼
        if (isPaused) {
            val resumeIntent = Intent(this, AudioCaptureService::class.java).apply { action = ACTION_RESUME }
            val resumePending = PendingIntent.getService(this, 1, resumeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(android.R.drawable.ic_media_play, "재실행", resumePending)
        } else {
            val pauseIntent = Intent(this, AudioCaptureService::class.java).apply { action = ACTION_PAUSE }
            val pausePending = PendingIntent.getService(this, 2, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(android.R.drawable.ic_media_pause, "일시중지", pausePending)
        }

        val stopIntent = Intent(this, AudioCaptureService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(this, 3, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "종료", stopPending)

        return builder.build()
    }

    private fun updateNotification() {
        val state = Bridge.captureState.value
        if (state == CaptureState.RECORDING || state == CaptureState.PAUSED) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, buildNotification(state, Bridge.latestSegmentText.value))
        }
    }

    @SuppressLint("MissingPermission")
    private fun startCapture(resultCode: Int, resultData: Intent) {
        val initialNoti = buildNotification(CaptureState.RECORDING, "")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                initialNoti,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, initialNoti)
        }

        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
        val mediaProjection = try {
            projectionManager.getMediaProjection(resultCode, resultData)
        } catch (e: Exception) {
            null
        }

        if (mediaProjection == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        Bridge.mediaProjection = mediaProjection

        mediaProjection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                super.onStop()
                stopCapture()
            }
        }, null)

        Bridge.setCaptureState(CaptureState.RECORDING)
        val initialOffset = if (Bridge.isAppendMode.value) (currentDocument?.durationSeconds ?: 0L) else 0L
        sessionStartTime = System.currentTimeMillis() - (initialOffset * 1000L)
        startTimer()

        initAudioPipeline(mediaProjection)
        startSpeechRecognizer()
    }

    @SuppressLint("MissingPermission")
    private fun initAudioPipeline(mediaProjection: MediaProjection) {
        val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()

        val sampleRate = 16000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = minBufferSize.coerceAtLeast(sampleRate * 2)

        val record = AudioRecord.Builder()
            .setAudioPlaybackCaptureConfig(config)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(audioFormat)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelConfig)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .build()

        audioRecord = record
        record.startRecording()

        // 캡처 루프 스레드 (PCM 16-bit Mono 16000Hz)
        recordThread = Thread {
            val buffer = ShortArray(1024)
            val byteBuffer = ByteArray(2048)
            val waveWindow = FloatArray(32)
            var waveIdx = 0

            while (Bridge.captureState.value == CaptureState.RECORDING || Bridge.captureState.value == CaptureState.PAUSED) {
                if (Bridge.captureState.value == CaptureState.PAUSED) {
                    try { Thread.sleep(100) } catch (e: InterruptedException) { break }
                    continue
                }

                val readCount = record.read(buffer, 0, buffer.size)
                if (readCount > 0) {
                    // 1. RMS 진폭 계산 (Waveform용)
                    var sum = 0.0
                    for (i in 0 until readCount) {
                        val s = buffer[i].toInt()
                        sum += s * s
                        // Short to Little-Endian Byte
                        byteBuffer[i * 2] = (s and 0xFF).toByte()
                        byteBuffer[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
                    }
                    val rms = sqrt(sum / readCount)
                    val normalized = (rms / 32767.0 * 100.0).toFloat().coerceIn(4f, 100f)

                    waveWindow[waveIdx] = normalized
                    waveIdx = (waveIdx + 1) % waveWindow.size

                    if (waveIdx == 0) {
                        Bridge.setWaveform(waveWindow.toList())
                    }

                    // 2. 실시간 오디오 파이프로 전송 (무손실 오디오 파이프라인)
                    if (Bridge.captureState.value == CaptureState.RECORDING) {
                        val chunk = byteBuffer.copyOf(readCount * 2)
                        val out = currentPipeOut
                        if (out != null && isListening) {
                            try {
                                out.write(chunk)
                            } catch (e: Exception) {
                                synchronized(audioBufferLock) {
                                    if (audioPreRollQueue.size >= MAX_PRE_ROLL_CHUNKS) {
                                        audioPreRollQueue.pollFirst()
                                    }
                                    audioPreRollQueue.addLast(chunk)
                                }
                            }
                        } else {
                            // 인식기 재시작 전환 시간 동안 오디오를 보류 큐에 보관하여 단어 잘림 방지
                            synchronized(audioBufferLock) {
                                if (audioPreRollQueue.size >= MAX_PRE_ROLL_CHUNKS) {
                                    audioPreRollQueue.pollFirst()
                                }
                                audioPreRollQueue.addLast(chunk)
                            }
                        }
                    }
                }
            }
        }.apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    private var isListening = false
    private var restartJob: Job? = null

    private fun resetAudioPipe(): ParcelFileDescriptor {
        val oldOut = currentPipeOut
        val oldWrite = currentWriteFd
        val oldRead = currentReadFd

        currentPipeOut = null
        currentWriteFd = null
        currentReadFd = null

        // 이전 파이프 안전 종료 -> write 중이었던 경우 즉시 차단 해제됨
        try { oldOut?.close() } catch (e: Exception) {}
        try { oldWrite?.close() } catch (e: Exception) {}
        try { oldRead?.close() } catch (e: Exception) {}

        val pipe = ParcelFileDescriptor.createPipe()
        currentReadFd = pipe[0]
        currentWriteFd = pipe[1]
        val newOut = FileOutputStream(currentWriteFd!!.fileDescriptor)

        // 세션 전환(약 30ms) 중 recordThread에서 수신된 오디오를 새 파이프에 먼저 주입하여 단어 잘림 방지
        synchronized(audioBufferLock) {
            while (audioPreRollQueue.isNotEmpty()) {
                val chunk = audioPreRollQueue.pollFirst() ?: break
                try {
                    newOut.write(chunk)
                } catch (e: Exception) {
                    break
                }
            }
        }

        currentPipeOut = newOut
        return currentReadFd!!
    }

    private fun closeAudioPipes() {
        val oldOut = currentPipeOut
        val oldWrite = currentWriteFd
        val oldRead = currentReadFd

        currentPipeOut = null
        currentWriteFd = null
        currentReadFd = null

        try { oldOut?.close() } catch (e: Exception) {}
        try { oldWrite?.close() } catch (e: Exception) {}
        try { oldRead?.close() } catch (e: Exception) {}

        synchronized(audioBufferLock) {
            audioPreRollQueue.clear()
        }
    }

    private fun destroySpeechRecognizer() {
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying speech recognizer: ${e.message}")
        } finally {
            speechRecognizer = null
            isListening = false
        }
    }

    private fun scheduleRestart(delayMs: Long = 30) {
        if (Bridge.captureState.value != CaptureState.RECORDING) return
        restartJob?.cancel()
        restartJob = serviceScope.launch {
            if (delayMs > 0) delay(delayMs)
            if (Bridge.captureState.value != CaptureState.RECORDING) return@launch
            startSpeechRecognizer()
        }
    }

    private fun startSpeechRecognizer() {
        destroySpeechRecognizer()

        val recognizer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(this@AudioCaptureService)) {
            Log.i(TAG, "Creating On-Device SpeechRecognizer for ultra-low latency")
            try {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(this@AudioCaptureService)
            } catch (e: Exception) {
                Log.w(TAG, "On-device failed, using default: ${e.message}")
                SpeechRecognizer.createSpeechRecognizer(this@AudioCaptureService)
            }
        } else {
            SpeechRecognizer.createSpeechRecognizer(this@AudioCaptureService)
        }
        speechRecognizer = recognizer

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d(TAG, "onReadyForSpeech")
                isListening = true
            }
            override fun onBeginningOfSpeech() {
                Log.d(TAG, "onBeginningOfSpeech")
                isListening = true
            }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                Log.d(TAG, "onEndOfSpeech")
                // VAD가 문장 끝의 작은 음성을 조기 감지했을 때 오디오 파이프를 차단하면
                // 문장 끝의 마지막 몇 단어가 잘려나가는 문제가 발생하므로 여기서 파이프(isListening)를 차단하지 않습니다.
            }

            override fun onError(error: Int) {
                isListening = false
                val errorDesc = getErrorDescription(error)
                Log.w(TAG, "SpeechRecognizer onError: $errorDesc ($error)")

                // 오류/타임아웃 발생 시에도 중간 인식 텍스트가 있다면 확정하여 유실 방지
                val pending = Bridge.interimText.value.trim()
                val doc = currentDocument
                if (pending.isNotBlank() && doc != null) {
                    val elapsedSec = Bridge.elapsedTimeSeconds.value
                    val formattedTime = formatTime(elapsedSec)
                    serviceScope.launch {
                        repository.addFinalTranscript(
                            documentId = doc.id,
                            timestampMs = elapsedSec * 1000,
                            formattedTime = formattedTime,
                            text = pending
                        )
                    }
                    Bridge.setInterimText("")
                    Bridge.setLatestSegmentText(pending)
                    lastCommittedText = pending
                    lastCommittedTime = System.currentTimeMillis()
                }

                if (Bridge.captureState.value == CaptureState.RECORDING) {
                    when (error) {
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                        SpeechRecognizer.ERROR_NO_MATCH -> {
                            scheduleRestart(delayMs = 30)
                        }
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                            scheduleRestart(delayMs = 250)
                        }
                        SpeechRecognizer.ERROR_CLIENT,
                        SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> {
                            scheduleRestart(delayMs = 200)
                        }
                        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> {
                            Log.w(TAG, "Language $selectedLanguage not available on this engine")
                            scheduleRestart(delayMs = 500)
                        }
                        else -> {
                            scheduleRestart(delayMs = 100)
                        }
                    }
                }
            }

            override fun onResults(results: Bundle?) {
                Log.d(TAG, "onResults")
                handleResults(results)
                if (Bridge.captureState.value == CaptureState.RECORDING) {
                    scheduleRestart(delayMs = 30)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull() ?: ""
                if (text.isNotBlank()) {
                    Bridge.setInterimText(text)
                    // 중간 결과 도중에는 세션을 끊지 않고, 자동 감지 모드일 때 언어만 감지
                    if (selectedLanguage == AppLanguage.AUTO.code && Bridge.detectedLanguage.value == null) {
                        val detected = AppLanguage.detectDominantLanguage(text)
                        if (detected != null && detected != AppLanguage.AUTO) {
                            Bridge.setDetectedLanguage(detected)
                        }
                    }
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {
                Log.d(TAG, "onEvent: $eventType")
            }

            override fun onSegmentResults(segmentResults: Bundle) {
                Log.d(TAG, "onSegmentResults")
                handleResults(segmentResults)
                if (Bridge.captureState.value == CaptureState.RECORDING) {
                    scheduleRestart(delayMs = 30)
                }
            }

            override fun onEndOfSegmentedSession() {
                Log.d(TAG, "onEndOfSegmentedSession")
                if (Bridge.captureState.value == CaptureState.RECORDING) {
                    scheduleRestart(delayMs = 30)
                }
            }

            override fun onLanguageDetection(results: Bundle) {
                super.onLanguageDetection(results)
                val detected = results.getString("detected_language")
                    ?: results.getString(SpeechRecognizer.DETECTED_LANGUAGE)
                Log.d(TAG, "System onLanguageDetection detected: $detected")
                if (!detected.isNullOrBlank()) {
                    val appLang = AppLanguage.fromCode(detected)
                    Bridge.setDetectedLanguage(appLang)
                }
            }
        })

        launchRecognizerIntent()
    }

    private fun launchRecognizerIntent() {
        val readFd = resetAudioPipe()
        val currentTargetLang = if (selectedLanguage == AppLanguage.AUTO.code) {
            val detected = Bridge.detectedLanguage.value
            detected?.code ?: "ko-KR"
        } else {
            selectedLanguage
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE)

            // AudioPlaybackCapture 오디오 소스 파이프 주입
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, readFd)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, 16000)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)

            // 언어 설정
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentTargetLang)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, currentTargetLang)
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, currentTargetLang)

            // 다중 언어 지원 힌트
            putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("ko-KR", "en-US", "ja-JP"))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, true)
                putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH, RecognizerIntent.LANGUAGE_SWITCH_BALANCED)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES, arrayOf("ko-KR", "en-US", "ja-JP"))
                putStringArrayListExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES,
                    arrayListOf("ko-KR", "en-US", "ja-JP")
                )
            }
        }

        try {
            isListening = true
            speechRecognizer?.startListening(intent)
            Log.d(TAG, "startListening invoked for lang=$selectedLanguage (target=$currentTargetLang)")
        } catch (e: Exception) {
            Log.e(TAG, "startListening error: ${e.message}")
            scheduleRestart(delayMs = 250)
        }
    }

    private fun getErrorDescription(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "ERROR_AUDIO"
        SpeechRecognizer.ERROR_CLIENT -> "ERROR_CLIENT"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "ERROR_INSUFFICIENT_PERMISSIONS"
        SpeechRecognizer.ERROR_NETWORK -> "ERROR_NETWORK"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "ERROR_NETWORK_TIMEOUT"
        SpeechRecognizer.ERROR_NO_MATCH -> "ERROR_NO_MATCH"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "ERROR_RECOGNIZER_BUSY"
        SpeechRecognizer.ERROR_SERVER -> "ERROR_SERVER"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "ERROR_SPEECH_TIMEOUT"
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "ERROR_LANGUAGE_NOT_SUPPORTED"
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "ERROR_LANGUAGE_UNAVAILABLE"
        SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "ERROR_SERVER_DISCONNECTED"
        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "ERROR_TOO_MANY_REQUESTS"
        else -> "UNKNOWN_ERROR_$error"
    }

    private fun handleResults(bundle: Bundle?) {
        val matches = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        var text = matches?.firstOrNull()?.trim() ?: return
        if (text.isBlank()) return

        // 실시간 중간 결과(interimText)가 최종 bundle보다 문단 끝의 단어를 더 많이 포함하고 있는 경우,
        // 문단 끝 단어 잘림을 방지하기 위해 더 완전한 interimText를 채택합니다.
        val currentInterim = Bridge.interimText.value.trim()
        if (currentInterim.isNotBlank() && currentInterim.startsWith(text) && currentInterim.length > text.length) {
            Log.d(TAG, "Adopting full interimText to prevent end-of-paragraph truncation: '$currentInterim' (was '$text')")
            text = currentInterim
        }

        val now = System.currentTimeMillis()

        // 1. 동일한 문장이 재수신되면 중복 무시 (onSegmentResults + onResults 중복 방지)
        if (text == lastCommittedText && now - lastCommittedTime < 5000L) {
            Bridge.setInterimText("")
            return
        }

        // 2. 직전문장과 누적된 결과일 경우 앞부분을 잘라내어 처음부터 다시 작성되는 현상 방지
        if (lastCommittedText.isNotBlank() && text.startsWith(lastCommittedText) && text.length > lastCommittedText.length) {
            val trimmed = text.removePrefix(lastCommittedText).trim()
            if (trimmed.isNotBlank()) {
                text = trimmed
            }
        }

        lastCommittedText = text
        lastCommittedTime = now

        // 3. AUTO 모드에서 언어 감지 및 동기화
        if (selectedLanguage == AppLanguage.AUTO.code) {
            val detected = AppLanguage.detectDominantLanguage(text)
            if (detected != null && detected != AppLanguage.AUTO) {
                Bridge.setDetectedLanguage(detected)
            }
        }

        val docId = currentDocument?.id ?: return
        val elapsedSec = Bridge.elapsedTimeSeconds.value
        val formattedTime = formatTime(elapsedSec)

        Bridge.setInterimText("")
        Bridge.setLatestSegmentText(text)

        serviceScope.launch {
            repository.addFinalTranscript(
                documentId = docId,
                timestampMs = elapsedSec * 1000,
                formattedTime = formattedTime,
                text = text
            )

            // 자동 감지 모드에서 언어 식별 시 DB 문서 언어 동기화
            val detected = Bridge.detectedLanguage.value
            if (detected != null && currentDocument?.language == AppLanguage.AUTO.code) {
                repository.updateDocumentLanguage(docId, detected.code)
            }
            updateNotification()
        }
    }

    private fun pauseCapture() {
        if (Bridge.captureState.value != CaptureState.RECORDING) return
        Bridge.setCaptureState(CaptureState.PAUSED)
        pauseStartTime = System.currentTimeMillis()
        timerJob?.cancel()
        restartJob?.cancel()
        isListening = false

        // 일시중지 전 현재까지 인식된 실시간 텍스트가 있다면 유실 방지를 위해 즉시 확정 저장
        val pending = Bridge.interimText.value.trim()
        val doc = currentDocument ?: Bridge.currentDocument.value
        if (pending.isNotBlank() && doc != null) {
            val elapsedSec = Bridge.elapsedTimeSeconds.value
            val formattedTime = formatTime(elapsedSec)
            serviceScope.launch {
                repository.addFinalTranscript(
                    documentId = doc.id,
                    timestampMs = elapsedSec * 1000,
                    formattedTime = formattedTime,
                    text = pending
                )
            }
            Bridge.setInterimText("")
            Bridge.setLatestSegmentText(pending)
            lastCommittedText = pending
            lastCommittedTime = System.currentTimeMillis()
        }

        destroySpeechRecognizer()
        closeAudioPipes()
        updateNotification()
    }

    private fun resumeCapture() {
        if (Bridge.captureState.value != CaptureState.PAUSED) return
        val pauseDuration = System.currentTimeMillis() - pauseStartTime
        sessionStartTime += pauseDuration
        Bridge.setCaptureState(CaptureState.RECORDING)
        startTimer()

        // 재실행 시 새로운 문장부터 깨끗하게 시작
        Bridge.setInterimText("")
        lastCommittedText = ""
        lastCommittedTime = 0L

        startSpeechRecognizer()
        updateNotification()
    }

    private fun stopCapture() {
        if (Bridge.captureState.value == CaptureState.COMPLETED || Bridge.captureState.value == CaptureState.SAVING) return
        Bridge.setCaptureState(CaptureState.SAVING)
        timerJob?.cancel()
        restartJob?.cancel()
        isListening = false

        serviceScope.launch {
            try {
                // 1. 마지막 남아있는 실시간 중간 인식 텍스트를 확정 문장으로 저장
                val remainingInterim = Bridge.interimText.value.trim()
                val doc = currentDocument ?: Bridge.currentDocument.value
                if (remainingInterim.isNotBlank() && doc != null) {
                    val elapsedSec = Bridge.elapsedTimeSeconds.value
                    repository.addFinalTranscript(
                        documentId = doc.id,
                        timestampMs = elapsedSec * 1000,
                        formattedTime = formatTime(elapsedSec),
                        text = remainingInterim
                    )
                    Bridge.setInterimText("")
                }

                // 2. 음성 인식기 안전 종료
                destroySpeechRecognizer()

                audioRecord?.stop()
                audioRecord?.release()
                audioRecord = null

                closeAudioPipes()

                Bridge.mediaProjection?.stop()
                Bridge.mediaProjection = null

                doc?.let { docEntity ->
                    val detected = Bridge.detectedLanguage.value
                    if (docEntity.language == AppLanguage.AUTO.code && detected != null) {
                        repository.updateDocumentLanguage(docEntity.id, detected.code)
                    }
                    repository.finalizeSession(docEntity.id, Bridge.elapsedTimeSeconds.value)
                    val updated = repository.getDocument(docEntity.id)
                    if (updated != null) {
                        Bridge.setCurrentDocument(updated)
                    }
                }
            } catch (e: Exception) {
                // 마무리 예외 무시
            } finally {
                Bridge.setCaptureState(CaptureState.COMPLETED)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (isActive) {
                val elapsed = (System.currentTimeMillis() - sessionStartTime) / 1000
                Bridge.setElapsedTime(elapsed)
                delay(1000)
            }
        }
    }

    private fun formatTime(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) {
            String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", m, s)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        destroySpeechRecognizer()
        closeAudioPipes()
        serviceScope.cancel()
    }

    companion object {
        const val TAG = "AudioCaptureService"
        const val ACTION_START = "com.voicetotext.app.action.START"
        const val ACTION_PAUSE = "com.voicetotext.app.action.PAUSE"
        const val ACTION_RESUME = "com.voicetotext.app.action.RESUME"
        const val ACTION_STOP = "com.voicetotext.app.action.STOP"
        const val ACTION_CHANGE_LANGUAGE = "com.voicetotext.app.action.CHANGE_LANGUAGE"

        const val EXTRA_RESULT_CODE = "com.voicetotext.app.extra.RESULT_CODE"
        const val EXTRA_RESULT_DATA = "com.voicetotext.app.extra.RESULT_DATA"
        const val EXTRA_LANGUAGE = "com.voicetotext.app.extra.LANGUAGE"
        const val EXTRA_IS_APPEND = "com.voicetotext.app.extra.IS_APPEND"

        private const val CHANNEL_ID = "voice_to_text_channel"
        private const val NOTIFICATION_ID = 1001
    }

    /**
     * UI와 서비스 간 상태 및 이벤트 공유 브릿지
     */
    object Bridge {
        var mediaProjection: MediaProjection? = null

        private val _currentDocument = MutableStateFlow<DocumentEntity?>(null)
        val currentDocument: StateFlow<DocumentEntity?> = _currentDocument.asStateFlow()

        private val _isAppendMode = MutableStateFlow(false)
        val isAppendMode: StateFlow<Boolean> = _isAppendMode.asStateFlow()

        private val _captureState = MutableStateFlow(CaptureState.IDLE)
        val captureState: StateFlow<CaptureState> = _captureState.asStateFlow()

        private val _selectedLanguage = MutableStateFlow(AppLanguage.AUTO)
        val selectedLanguage: StateFlow<AppLanguage> = _selectedLanguage.asStateFlow()

        private val _detectedLanguage = MutableStateFlow<AppLanguage?>(null)
        val detectedLanguage: StateFlow<AppLanguage?> = _detectedLanguage.asStateFlow()

        private val _elapsedTimeSeconds = MutableStateFlow(0L)
        val elapsedTimeSeconds: StateFlow<Long> = _elapsedTimeSeconds.asStateFlow()

        private val _interimText = MutableStateFlow("")
        val interimText: StateFlow<String> = _interimText.asStateFlow()

        private val _latestSegmentText = MutableStateFlow("")
        val latestSegmentText: StateFlow<String> = _latestSegmentText.asStateFlow()

        private val _waveform = MutableStateFlow<List<Float>>(emptyList())
        val waveform: StateFlow<List<Float>> = _waveform.asStateFlow()

        fun setCurrentDocument(doc: DocumentEntity?) {
            _currentDocument.value = doc
        }

        fun setCaptureState(state: CaptureState) {
            _captureState.value = state
        }

        fun setSelectedLanguage(lang: AppLanguage) {
            _selectedLanguage.value = lang
        }

        fun setDetectedLanguage(lang: AppLanguage?) {
            _detectedLanguage.value = lang
        }

        fun setElapsedTime(seconds: Long) {
            _elapsedTimeSeconds.value = seconds
        }

        fun setInterimText(text: String) {
            _interimText.value = text
        }

        fun setLatestSegmentText(text: String) {
            _latestSegmentText.value = text
        }

        fun setWaveform(values: List<Float>) {
            _waveform.value = values
        }

        fun setIsAppendMode(value: Boolean) {
            _isAppendMode.value = value
        }

        fun reset() {
            _currentDocument.value = null
            _isAppendMode.value = false
            _captureState.value = CaptureState.IDLE
            _detectedLanguage.value = null
            _elapsedTimeSeconds.value = 0L
            _interimText.value = ""
            _latestSegmentText.value = ""
            _waveform.value = emptyList()
            mediaProjection = null
        }
    }
}
