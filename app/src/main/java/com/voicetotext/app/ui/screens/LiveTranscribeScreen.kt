package com.voicetotext.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicetotext.app.data.local.DocumentEntity
import com.voicetotext.app.data.local.TranscriptEntity
import com.voicetotext.app.data.model.AppLanguage
import com.voicetotext.app.data.model.CaptureState
import com.voicetotext.app.data.model.SyncStatus
import com.voicetotext.app.ui.components.InAppTextViewerDialog
import com.voicetotext.app.ui.components.LanguageBadge
import com.voicetotext.app.ui.components.LiveBadge
import com.voicetotext.app.ui.components.LocalFileStrip
import com.voicetotext.app.ui.components.PausedBadge
import com.voicetotext.app.ui.components.WaveformVisualizer
import com.voicetotext.app.ui.components.formatFileSize
import com.voicetotext.app.ui.theme.AppOutline
import com.voicetotext.app.ui.theme.AppSurfaceContainer
import com.voicetotext.app.ui.theme.OnPrimaryBlueContainer
import com.voicetotext.app.ui.theme.OnStatusSynced
import com.voicetotext.app.ui.theme.PrimaryBlue
import com.voicetotext.app.ui.theme.PrimaryBlueContainer
import com.voicetotext.app.ui.theme.StatusLive
import com.voicetotext.app.ui.theme.StatusPaused
import com.voicetotext.app.ui.theme.StatusSynced
import com.voicetotext.app.ui.theme.StatusSyncedContainer
import com.voicetotext.app.ui.theme.TextMuted
import com.voicetotext.app.ui.theme.TextPrimary
import com.voicetotext.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

@Composable
fun LiveTranscribeScreen(
    captureState: CaptureState,
    currentDocument: DocumentEntity?,
    elapsedTimeSeconds: Long,
    interimText: String,
    waveform: List<Float>,
    transcripts: List<TranscriptEntity>,
    selectedLanguage: AppLanguage,
    detectedLanguage: AppLanguage? = null,
    isLandscape: Boolean,
    onSelectLanguage: (AppLanguage) -> Unit,
    onStartClick: () -> Unit,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onStopClick: () -> Unit,
    onResetClick: () -> Unit,
    onTranslateDocument: ((String, String, (Result<DocumentEntity>) -> Unit) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var inAppViewingDoc by remember { mutableStateOf<DocumentEntity?>(null) }
    var inAppViewingContent by remember { mutableStateOf("") }

    if (inAppViewingDoc != null) {
        BackHandler {
            inAppViewingDoc = null
        }
        InAppTextViewerDialog(
            title = inAppViewingDoc?.title ?: "VoiceToText",
            content = inAppViewingContent,
            document = inAppViewingDoc,
            onTranslate = if (onTranslateDocument != null) { targetLang, cb ->
                onTranslateDocument(inAppViewingDoc!!.id, targetLang, cb)
            } else null,
            onDismiss = { inAppViewingDoc = null }
        )
    }

    if (captureState == CaptureState.COMPLETED) {
        BackHandler {
            onResetClick()
        }
        CompletedView(
            document = currentDocument,
            transcripts = transcripts,
            elapsedSeconds = elapsedTimeSeconds,
            onOpenFileInApp = { doc, content ->
                inAppViewingDoc = doc ?: currentDocument
                inAppViewingContent = content
            },
            onNewSession = onResetClick
        )
        return
    }

    if (captureState == CaptureState.IDLE) {
        IdleView(
            isLandscape = isLandscape,
            selectedLanguage = selectedLanguage,
            onSelectLanguage = onSelectLanguage,
            onStartClick = onStartClick,
            recentDocument = currentDocument,
            onOpenRecentFile = { doc ->
                val file = doc.localFilePath?.let { File(it) }
                val content = if (file != null && file.exists()) {
                    try { file.readText(Charsets.UTF_8) } catch (e: Exception) { "" }
                } else {
                    doc.summaryPreview
                }
                inAppViewingDoc = doc
                inAppViewingContent = content
            }
        )
        return
    }

    if (isLandscape) {
        LandscapeTranscribeView(
            captureState = captureState,
            currentDocument = currentDocument,
            elapsedTimeSeconds = elapsedTimeSeconds,
            interimText = interimText,
            waveform = waveform,
            transcripts = transcripts,
            selectedLanguage = selectedLanguage,
            detectedLanguage = detectedLanguage,
            onSelectLanguage = onSelectLanguage,
            onPauseClick = onPauseClick,
            onResumeClick = onResumeClick,
            onStopClick = onStopClick,
            onOpenFile = {
                currentDocument?.let { doc ->
                    val file = doc.localFilePath?.let { File(it) }
                    val content = if (file != null && file.exists()) {
                        try { file.readText(Charsets.UTF_8) } catch (e: Exception) { "" }
                    } else {
                        transcripts.joinToString("\n\n") { "[${it.formattedTime}] ${it.text}" }
                    }
                    inAppViewingDoc = doc
                    inAppViewingContent = content
                }
            }
        )
    } else {
        PortraitTranscribeView(
            captureState = captureState,
            currentDocument = currentDocument,
            elapsedTimeSeconds = elapsedTimeSeconds,
            interimText = interimText,
            waveform = waveform,
            transcripts = transcripts,
            selectedLanguage = selectedLanguage,
            detectedLanguage = detectedLanguage,
            onSelectLanguage = onSelectLanguage,
            onPauseClick = onPauseClick,
            onResumeClick = onResumeClick,
            onStopClick = onStopClick,
            onOpenFile = {
                currentDocument?.let { doc ->
                    val file = doc.localFilePath?.let { File(it) }
                    val content = if (file != null && file.exists()) {
                        try { file.readText(Charsets.UTF_8) } catch (e: Exception) { "" }
                    } else {
                        transcripts.joinToString("\n\n") { "[${it.formattedTime}] ${it.text}" }
                    }
                    inAppViewingDoc = doc
                    inAppViewingContent = content
                }
            }
        )
    }
}

@Composable
fun PortraitTranscribeView(
    captureState: CaptureState,
    currentDocument: DocumentEntity?,
    elapsedTimeSeconds: Long,
    interimText: String,
    waveform: List<Float>,
    transcripts: List<TranscriptEntity>,
    selectedLanguage: AppLanguage,
    detectedLanguage: AppLanguage? = null,
    onSelectLanguage: (AppLanguage) -> Unit,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onStopClick: () -> Unit,
    onOpenFile: () -> Unit
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val isRecording = captureState == CaptureState.RECORDING
    val hasInterim = interimText.isNotBlank()
    val hasPlaceholder = transcripts.isEmpty() && !hasInterim && isRecording
    val expectedItemCount = (if (hasPlaceholder) 1 else 0) + transcripts.size + (if (hasInterim) 1 else 0) + 1

    val isAtBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val total = layoutInfo.totalItemsCount
            if (total <= 1) return@derivedStateOf true
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= total - 2
        }
    }

    LaunchedEffect(transcripts.size, interimText, isRecording) {
        if (isAtBottom && expectedItemCount > 0) {
            listState.scrollToItem(expectedItemCount - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            LocalFileStrip(
                title = currentDocument?.title ?: "VTT_새_문서",
                syncStatus = currentDocument?.syncStatus ?: SyncStatus.SYNCING,
                languageCode = currentDocument?.language,
                onOpenFileClick = onOpenFile
            )
        }

        // 실시간 동적 언어 전환 바 (변환 중 언제든지 언어 변경 가능)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xFFEEF2F7))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val hasDetectedLang = selectedLanguage == AppLanguage.AUTO && detectedLanguage != null
                AppLanguage.entries.forEach { lang ->
                    val isSelected = selectedLanguage == lang
                    val weight = when {
                        hasDetectedLang && lang == AppLanguage.AUTO -> 1.45f
                        hasDetectedLang -> 0.85f
                        else -> 1f
                    }
                    val label = when {
                        lang == AppLanguage.AUTO && detectedLanguage != null -> "🌐 자동(${detectedLanguage.badgeText})"
                        lang == AppLanguage.AUTO -> "🌐 자동"
                        else -> "${lang.flag} ${lang.badgeText}"
                    }
                    Box(
                        modifier = Modifier
                            .weight(weight)
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (isSelected) PrimaryBlue else Color.Transparent)
                            .clickable { onSelectLanguage(lang) }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = if (hasDetectedLang && lang == AppLanguage.AUTO) 11.sp else 11.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else TextPrimary,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 10.dp, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (hasPlaceholder) {
                    item(key = "listening_placeholder") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(StatusLive)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "음성을 듣고 변환 중입니다...",
                                fontSize = 15.sp,
                                color = TextSecondary,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                            BlinkingCaretBox()
                        }
                    }
                }

                itemsIndexed(transcripts, key = { _, item -> item.id }) { index, item ->
                    val isLastItem = index == transcripts.lastIndex
                    val hasCursor = isRecording && isLastItem && !hasInterim
                    TranscriptItemView(
                        formattedTime = item.formattedTime,
                        text = item.text,
                        isInterim = false,
                        showCursor = hasCursor
                    )
                }

                if (hasInterim) {
                    item(key = "interim") {
                        TranscriptItemView(
                            formattedTime = formatSeconds(elapsedTimeSeconds),
                            text = interimText,
                            isInterim = true,
                            showCursor = isRecording
                        )
                    }
                }

                item(key = "bottom_anchor") {
                    Spacer(modifier = Modifier.height(28.dp))
                }
            }

            if (!isAtBottom) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(TextPrimary)
                        .clickable {
                            scope.launch {
                                if (expectedItemCount > 0) listState.animateScrollToItem(expectedItemCount - 1)
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                    Text("실시간 위치로", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        ControlDock(
            captureState = captureState,
            elapsedTimeSeconds = elapsedTimeSeconds,
            waveform = waveform,
            onPauseClick = onPauseClick,
            onResumeClick = onResumeClick,
            onStopClick = onStopClick
        )
    }
}

@Composable
fun LandscapeTranscribeView(
    captureState: CaptureState,
    currentDocument: DocumentEntity?,
    elapsedTimeSeconds: Long,
    interimText: String,
    waveform: List<Float>,
    transcripts: List<TranscriptEntity>,
    selectedLanguage: AppLanguage,
    detectedLanguage: AppLanguage? = null,
    onSelectLanguage: (AppLanguage) -> Unit,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onStopClick: () -> Unit,
    onOpenFile: () -> Unit
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val isRecording = captureState == CaptureState.RECORDING
    val hasInterim = interimText.isNotBlank()
    val hasPlaceholder = transcripts.isEmpty() && !hasInterim && isRecording
    val expectedItemCount = (if (hasPlaceholder) 1 else 0) + transcripts.size + (if (hasInterim) 1 else 0) + 1

    val isAtBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val total = layoutInfo.totalItemsCount
            if (total <= 1) return@derivedStateOf true
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= total - 2
        }
    }

    LaunchedEffect(transcripts.size, interimText, isRecording) {
        if (isAtBottom && expectedItemCount > 0) {
            listState.scrollToItem(expectedItemCount - 1)
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        // 좌측 컨트롤 패널 (SpaceBetween 배치로 일시중지/종료 버튼이 항상 최하단에 정확히 노출됨)
        Column(
            modifier = Modifier
                .width(280.dp)
                .fillMaxHeight()
                .background(AppSurfaceContainer)
                .border(width = 1.dp, color = AppOutline)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 상단 영역: 상태 뱃지, 파일 스트립, 수평 컴팩트 언어 선택 바
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (captureState == CaptureState.PAUSED) PausedBadge() else LiveBadge()
                    Text(
                        text = "${transcripts.size}문장",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                LocalFileStrip(
                    title = currentDocument?.title ?: "VTT_새_문서",
                    syncStatus = currentDocument?.syncStatus ?: SyncStatus.SYNCING,
                    languageCode = currentDocument?.language,
                    onOpenFileClick = onOpenFile
                )

                // 가로 모드 수평 컴팩트 언어 전환 바 (세로 32dp만 사용하여 공간 절약)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFFEEF2F7))
                        .padding(2.5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val hasDetectedLang = selectedLanguage == AppLanguage.AUTO && detectedLanguage != null
                    AppLanguage.entries.forEach { lang ->
                        val isSelected = selectedLanguage == lang
                        val weight = when {
                            hasDetectedLang && lang == AppLanguage.AUTO -> 1.4f
                            hasDetectedLang -> 0.86f
                            else -> 1f
                        }
                        val label = when {
                            lang == AppLanguage.AUTO && detectedLanguage != null -> "🌐 자동(${detectedLanguage.badgeText})"
                            lang == AppLanguage.AUTO -> "🌐 자동"
                            else -> "${lang.flag} ${lang.badgeText}"
                        }
                        Box(
                            modifier = Modifier
                                .weight(weight)
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (isSelected) PrimaryBlue else Color.Transparent)
                                .clickable { onSelectLanguage(lang) }
                                .padding(vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = if (hasDetectedLang && lang == AppLanguage.AUTO) 10.5.sp else 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else TextPrimary,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            // 중앙 타이머 및 파형
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val timerColor = if (captureState == CaptureState.PAUSED) StatusPaused else TextPrimary
                Text(
                    text = formatSeconds(elapsedTimeSeconds),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = timerColor
                )
                Spacer(modifier = Modifier.height(2.dp))
                WaveformVisualizer(
                    amplitudes = waveform,
                    isPaused = captureState == CaptureState.PAUSED,
                    isIdle = false,
                    modifier = Modifier.fillMaxWidth().height(22.dp)
                )
            }

            // 하단 버튼 영역: 일시중지/재실행, 종료 (화면 내 상시 노출)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (captureState == CaptureState.RECORDING) {
                    Button(
                        onClick = onPauseClick,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEEF2F7), contentColor = TextPrimary),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                        modifier = Modifier.weight(1f).height(42.dp)
                    ) {
                        Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("일시중지", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, maxLines = 1, softWrap = false)
                    }
                } else {
                    Button(
                        onClick = onResumeClick,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                        modifier = Modifier.weight(1f).height(42.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("재실행", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, maxLines = 1, softWrap = false)
                    }
                }

                Button(
                    onClick = onStopClick,
                    colors = ButtonDefaults.buttonColors(containerColor = StatusLive),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    modifier = Modifier.weight(1f).height(42.dp)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("종료", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, maxLines = 1, softWrap = false)
                }
            }
        }

        // 우측 실시간 전사 내용 패널 + 스크롤 시 실시간 위치 이동 플로팅 버튼
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 6.dp, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (hasPlaceholder) {
                    item(key = "listening_placeholder") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(StatusLive)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "음성을 듣고 변환 중입니다...",
                                fontSize = 14.5.sp,
                                color = TextSecondary,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                            BlinkingCaretBox()
                        }
                    }
                }

                itemsIndexed(transcripts, key = { _, item -> item.id }) { index, item ->
                    val isLastItem = index == transcripts.lastIndex
                    val hasCursor = isRecording && isLastItem && !hasInterim
                    TranscriptItemView(
                        formattedTime = item.formattedTime,
                        text = item.text,
                        isInterim = false,
                        showCursor = hasCursor
                    )
                }

                if (hasInterim) {
                    item(key = "interim") {
                        TranscriptItemView(
                            formattedTime = formatSeconds(elapsedTimeSeconds),
                            text = interimText,
                            isInterim = true,
                            showCursor = isRecording
                        )
                    }
                }

                item(key = "bottom_anchor") {
                    Spacer(modifier = Modifier.height(28.dp))
                }
            }

            // 스크롤 시 실시간 최신 변환 위치 복귀 플로팅 버튼
            if (!isAtBottom) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(TextPrimary)
                        .clickable {
                            scope.launch {
                                if (expectedItemCount > 0) listState.animateScrollToItem(expectedItemCount - 1)
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                    Text("실시간 위치로", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun TranscriptItemView(
    formattedTime: String,
    text: String,
    isInterim: Boolean,
    showCursor: Boolean = isInterim
) {
    val inlineContent = remember {
        mapOf(
            "caret" to InlineTextContent(
                Placeholder(
                    width = 5.sp,
                    height = 18.sp,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                )
            ) {
                BlinkingCaretBox()
            }
        )
    }

    val annotatedText = remember(text, showCursor) {
        buildAnnotatedString {
            append(text)
            if (showCursor) {
                appendInlineContent("caret", "|")
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isInterim) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(StatusLive)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = formattedTime,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isInterim) StatusLive else TextMuted
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = annotatedText,
            inlineContent = inlineContent,
            fontSize = 15.5.sp,
            lineHeight = 25.sp,
            color = if (isInterim) TextSecondary else TextPrimary,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun BlinkingCaretBox(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "caret_blink")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    Box(
        modifier = modifier
            .padding(start = 2.dp)
            .width(2.5.dp)
            .height(18.dp)
            .background(StatusLive.copy(alpha = alpha), RoundedCornerShape(1.dp))
    )
}

@Composable
fun ControlDock(
    captureState: CaptureState,
    elapsedTimeSeconds: Long,
    waveform: List<Float>,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onStopClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .background(AppSurfaceContainer)
            .border(width = 1.dp, color = AppOutline, shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .padding(18.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                WaveformVisualizer(
                    amplitudes = waveform,
                    isPaused = captureState == CaptureState.PAUSED,
                    isIdle = false,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(12.dp))
                val timerColor = if (captureState == CaptureState.PAUSED) StatusPaused else TextPrimary
                Text(
                    text = formatSeconds(elapsedTimeSeconds),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = timerColor
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (captureState == CaptureState.RECORDING) {
                    Button(
                        onClick = onPauseClick,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEEF2F7), contentColor = TextPrimary),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f).height(52.dp)
                    ) {
                        Icon(Icons.Default.Pause, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("일시중지", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                } else {
                    Button(
                        onClick = onResumeClick,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f).height(52.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("재실행", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }

                Button(
                    onClick = onStopClick,
                    colors = ButtonDefaults.buttonColors(containerColor = StatusLive),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f).height(52.dp)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("종료", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
fun IdleView(
    isLandscape: Boolean = false,
    selectedLanguage: AppLanguage,
    onSelectLanguage: (AppLanguage) -> Unit,
    onStartClick: () -> Unit,
    recentDocument: DocumentEntity?,
    onOpenRecentFile: (DocumentEntity) -> Unit = {}
) {
    if (isLandscape) {
        // 가로 모드 IdleView (좌우 2열 분할 레이아웃으로 변환 시작 버튼 및 모든 메뉴 100% 노출)
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 좌측: 히어로 마이크 및 안내 문구
            Column(
                modifier = Modifier
                    .weight(0.9f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFFF1F1)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(StatusLive)
                            .shadow(elevation = 6.dp, shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "재생 중인 소리를\n텍스트로 바꿔보세요",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "유튜브, 강의, 팟캐스트 재생 후\n변환 시작을 누르면 실시간 기록됩니다.",
                    fontSize = 11.5.sp,
                    color = TextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 16.sp
                )
            }

            // 우측: 언어 선택 칩, 상태 뱃지, 최근 문서, 큰 변환 시작 CTA 버튼
            Column(
                modifier = Modifier
                    .weight(1.1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // 인식 언어 선택 칩스 (자동, 한국어, 영어, 일본어 - 4개 언어 균등 배치)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFFEEF2F7))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppLanguage.entries.forEach { lang ->
                        val isSelected = selectedLanguage == lang
                        val labelText = if (lang == AppLanguage.AUTO) "자동" else lang.badgeText
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (isSelected) PrimaryBlue else Color.Transparent)
                                .clickable { onSelectLanguage(lang) }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(lang.flag, fontSize = 11.sp)
                                Text(
                                    text = labelText,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else TextPrimary,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(StatusSyncedContainer)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = OnStatusSynced, modifier = Modifier.size(12.dp))
                    val statusLabel = if (selectedLanguage.isAuto) {
                        "🌐 실시간 언어 자동 감지 준비됨"
                    } else {
                        "${selectedLanguage.flag} ${selectedLanguage.displayName} (${selectedLanguage.nativeName}) 준비됨"
                    }
                    Text(
                        statusLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = OnStatusSynced
                    )
                }

                if (recentDocument != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val displayTitle = if (recentDocument.title.endsWith(".txt")) recentDocument.title else "${recentDocument.title}.txt"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(AppSurfaceContainer)
                            .border(1.dp, AppOutline, RoundedCornerShape(12.dp))
                            .clickable { onOpenRecentFile(recentDocument) }
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text("최근 변환", fontSize = 10.5.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
                                LanguageBadge(langCode = recentDocument.language)
                            }
                            Text(displayTitle, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1)
                        }
                        Icon(Icons.Default.OpenInNew, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 큰 CTA 변환 시작 버튼 (가로 모드에서도 잘림 없이 항상 노출)
                Button(
                    onClick = onStartClick,
                    colors = ButtonDefaults.buttonColors(containerColor = StatusLive),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("변환 시작", fontSize = 15.5.sp, fontWeight = FontWeight.ExtraBold)
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "📁 내 파일 › 문서 › VoiceToText 에 저장",
                    fontSize = 10.5.sp,
                    color = TextSecondary
                )
            }
        }
        return
    }

    // 세로 모드 (스크롤 지원으로 작은 화면에서도 변환 시작 버튼 잘림 방지)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // 중앙 히어로 마이크 아이콘
        Box(
            modifier = Modifier
                .size(110.dp)
                .clip(CircleShape)
                .background(Color(0xFFFFF1F1)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(StatusLive)
                    .shadow(elevation = 10.dp, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Mic, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "재생 중인 소리를\n텍스트로 바꿔보세요",
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextPrimary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "유튜브, 강의, 팟캐스트를 재생한 뒤\n아래 버튼을 누르면 실시간으로 받아 적어요.",
            fontSize = 13.sp,
            color = TextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 19.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 인식 언어 선택 칩스 (자동, 한국어, 영어, 일본어 - 4개 언어 균등 배치)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color(0xFFEEF2F7))
                .padding(3.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppLanguage.entries.forEach { lang ->
                val isSelected = selectedLanguage == lang
                val labelText = if (lang == AppLanguage.AUTO) "자동" else lang.badgeText
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (isSelected) PrimaryBlue else Color.Transparent)
                        .clickable { onSelectLanguage(lang) }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(lang.flag, fontSize = 11.5.sp)
                        Text(
                            text = labelText,
                            fontSize = 11.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else TextPrimary,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(StatusSyncedContainer)
                .padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(Icons.Default.Check, contentDescription = null, tint = OnStatusSynced, modifier = Modifier.size(13.dp))
            val statusLabel = if (selectedLanguage.isAuto) {
                "🌐 실시간 언어 자동 감지 (한국어·영어·일본어) 준비됨"
            } else {
                "${selectedLanguage.flag} ${selectedLanguage.displayName} (${selectedLanguage.nativeName}) · 인식 준비됨"
            }
            Text(
                statusLabel,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = OnStatusSynced
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 최근 변환 카드
        if (recentDocument != null) {
            val displayTitle = if (recentDocument.title.endsWith(".txt")) recentDocument.title else "${recentDocument.title}.txt"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(AppSurfaceContainer)
                    .border(1.dp, AppOutline, RoundedCornerShape(14.dp))
                    .clickable { onOpenRecentFile(recentDocument) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("최근 변환", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
                        LanguageBadge(langCode = recentDocument.language)
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(displayTitle, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
                Icon(Icons.Default.OpenInNew, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // 큰 CTA 시작 버튼
        Button(
            onClick = onStartClick,
            colors = ButtonDefaults.buttonColors(containerColor = StatusLive),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text("변환 시작", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "📁 내 파일 › 문서 › VoiceToText 에 저장",
            fontSize = 11.5.sp,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun CompletedView(
    document: DocumentEntity?,
    transcripts: List<TranscriptEntity>,
    elapsedSeconds: Long,
    onOpenFileInApp: (DocumentEntity?, String) -> Unit,
    onNewSession: () -> Unit
) {
    val context = LocalContext.current
    val displayTitle = if (document?.title?.endsWith(".txt") == true) document.title else "${document?.title ?: "VTT_문서"}.txt"

    val fullText = remember(document, transcripts) {
        val file = document?.localFilePath?.let { File(it) }
        val diskText = if (file != null && file.exists()) {
            try { file.readText(Charsets.UTF_8) } catch (e: Exception) { "" }
        } else ""
        if (diskText.isNotBlank() && (diskText.contains("]") || transcripts.isEmpty())) {
            diskText
        } else {
            val body = transcripts.joinToString("\n\n") { "[${it.formattedTime}] ${it.text}" }
            if (diskText.isNotBlank()) {
                val header = diskText.substringBefore("========================================\n\n") + "========================================\n\n"
                "$header$body"
            } else {
                body
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.weight(1f))

        // 상태 체크 아이콘 (녹색)
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(Color(0xFFD1FAE5)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(StatusSynced),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 제목 및 로컬 저장 위치 안내
        Text("기기 내 텍스트 파일로 저장했어요", fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(14.dp), tint = TextSecondary)
            Text("내 파일 › 문서 › VoiceToText", fontSize = 13.sp, color = TextSecondary)
            if (document != null) {
                Text("·", fontSize = 13.sp, color = TextSecondary)
                LanguageBadge(langCode = document.language)
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 요약 통계 카드
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(AppSurfaceContainer)
                .border(1.dp, AppOutline, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = displayTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(StatusSyncedContainer)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text("저장 완료", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = OnStatusSynced)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(AppOutline))
            Spacer(modifier = Modifier.height(12.dp))

            val segCount = if ((document?.segmentCount ?: 0) > 0) document!!.segmentCount else transcripts.size
            val fileBytes = if ((document?.fileSizeBytes ?: 0L) > 0) document!!.fileSizeBytes else (document?.localFilePath?.let { File(it).length() } ?: 0L)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                StatColumn(label = "변환 시간", value = formatSeconds(elapsedSeconds))
                StatColumn(label = "문장 수", value = "${segCount}개")
                StatColumn(label = "파일 용량", value = formatFileSize(fileBytes))
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // 주 액션: 앱 내부에서 텍스트 파일 바로 열기 (하늘색)
        Button(
            onClick = { onOpenFileInApp(document, fullText) },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Icon(Icons.Default.OpenInNew, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text("텍스트 파일 열기", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 보조 액션: 전체 복사 & 공유하기 (공유: 파란색)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("VoiceToText", fullText))
                    Toast.makeText(context, "텍스트가 클립보드에 복사되었습니다.", Toast.LENGTH_SHORT).show()
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(17.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("전체 복사", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
            }

            Button(
                onClick = {
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, fullText)
                        type = "text/plain"
                    }
                    val shareIntent = Intent.createChooser(sendIntent, "변환 텍스트 공유")
                    context.startActivity(shareIntent)
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("공유하기", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 새 변환 시작
        OutlinedButton(
            onClick = onNewSession,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Icon(Icons.Default.Mic, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("새 변환 시작", fontWeight = FontWeight.Bold, fontSize = 14.5.sp, color = TextPrimary)
        }

        Spacer(modifier = Modifier.height(18.dp))
    }
}

@Composable
fun StatColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary, maxLines = 1, softWrap = false)
        Spacer(modifier = Modifier.height(2.dp))
        Text(label, fontSize = 11.5.sp, color = TextSecondary, maxLines = 1, softWrap = false)
    }
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
