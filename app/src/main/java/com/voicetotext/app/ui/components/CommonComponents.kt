package com.voicetotext.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.widget.Toast
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.voicetotext.app.data.local.DocumentEntity
import com.voicetotext.app.data.model.AppLanguage
import com.voicetotext.app.data.model.SyncStatus
import com.voicetotext.app.ui.theme.AppOutline
import com.voicetotext.app.ui.theme.AppSurfaceContainer
import com.voicetotext.app.ui.theme.OnPrimaryBlueContainer
import com.voicetotext.app.ui.theme.OnStatusLive
import com.voicetotext.app.ui.theme.OnStatusPaused
import com.voicetotext.app.ui.theme.OnStatusSynced
import com.voicetotext.app.ui.theme.PrimaryBlue
import com.voicetotext.app.ui.theme.PrimaryBlueContainer
import com.voicetotext.app.ui.theme.StatusLive
import com.voicetotext.app.ui.theme.StatusLiveContainer
import com.voicetotext.app.ui.theme.StatusPaused
import com.voicetotext.app.ui.theme.StatusPausedContainer
import com.voicetotext.app.ui.theme.StatusSynced
import com.voicetotext.app.ui.theme.StatusSyncedContainer
import com.voicetotext.app.ui.theme.TextPrimary
import com.voicetotext.app.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun SyncStatusBadge(status: SyncStatus) {
    val (bgColor, textColor, icon, label) = when (status) {
        SyncStatus.SYNCED -> Quadruple(StatusSyncedContainer, OnStatusSynced, Icons.Default.Check, "저장 완료")
        SyncStatus.SYNCING -> Quadruple(PrimaryBlueContainer, OnPrimaryBlueContainer, Icons.Default.Sync, "저장 중")
        SyncStatus.PENDING -> Quadruple(StatusPausedContainer, OnStatusPaused, Icons.Default.Folder, "대기")
        SyncStatus.ERROR -> Quadruple(StatusLiveContainer, OnStatusLive, Icons.Default.ErrorOutline, "오류")
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bgColor)
            .padding(horizontal = 9.dp, vertical = 4.5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(13.dp), tint = textColor)
        Text(
            text = label,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
fun LanguageBadge(langCode: String) {
    val lang = AppLanguage.fromCode(langCode)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFFEEF2F7))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(lang.flag, fontSize = 10.5.sp)
        Text(
            text = lang.displayName,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
fun LiveBadge() {
    val transition = rememberInfiniteTransition(label = "live_blink")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blink"
    )

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(StatusLiveContainer)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(StatusLive.copy(alpha = alpha))
        )
        Text("LIVE", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = OnStatusLive, maxLines = 1, softWrap = false)
    }
}

@Composable
fun PausedBadge() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(StatusPausedContainer)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(13.dp), tint = OnStatusPaused)
        Text("일시중지됨", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = OnStatusPaused, maxLines = 1, softWrap = false)
    }
}

/**
 * 기기 로컬 텍스트 파일 저장 스트립 (v2.0)
 */
@Composable
fun LocalFileStrip(
    title: String,
    syncStatus: SyncStatus,
    languageCode: String? = null,
    onOpenFileClick: (() -> Unit)? = null
) {
    val displayTitle = if (title.endsWith(".txt")) title else "$title.txt"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppSurfaceContainer)
            .border(1.dp, AppOutline, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 로컬 .txt 파일 아이콘
        Box(
            modifier = Modifier
                .size(width = 24.dp, height = 30.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(PrimaryBlue),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(modifier = Modifier.width(14.dp).height(2.dp).background(Color.White))
                Spacer(modifier = Modifier.height(2.dp))
                Box(modifier = Modifier.width(14.dp).height(2.dp).background(Color.White))
                Spacer(modifier = Modifier.height(2.dp))
                Box(modifier = Modifier.width(10.dp).height(2.dp).background(Color.White))
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayTitle,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(12.dp), tint = TextSecondary)
                Text(
                    text = "내 파일 › 문서 › VoiceToText",
                    fontSize = 11.5.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (languageCode != null) {
                    val lang = AppLanguage.fromCode(languageCode)
                    val langLabel = if (lang == AppLanguage.AUTO) "· 🌐 자동" else "· ${lang.flag} ${lang.badgeText}"
                    Text(
                        text = langLabel,
                        fontSize = 11.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        SyncStatusBadge(status = syncStatus)

        if (onOpenFileClick != null) {
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                Icons.Default.OpenInNew,
                contentDescription = "파일 열기",
                modifier = Modifier
                    .size(18.dp)
                    .clickable { onOpenFileClick() },
                tint = TextSecondary
            )
        }
    }
}

// 하위 호환성을 위해 유지하되 LocalFileStrip으로 위임
@Composable
fun GoogleDocStrip(
    title: String,
    syncStatus: SyncStatus,
    onOpenDocsClick: (() -> Unit)? = null
) {
    LocalFileStrip(
        title = title,
        syncStatus = syncStatus,
        onOpenFileClick = onOpenDocsClick
    )
}

/**
 * 인앱 텍스트 뷰어 다이얼로그 (3-Way 원문/번역문/대조 탭 및 온디바이스 번역 지원)
 */
@Composable
fun InAppTextViewerDialog(
    title: String,
    content: String,
    document: DocumentEntity? = null,
    onTranslate: ((targetLang: String, onComplete: (Result<DocumentEntity>) -> Unit) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var selectedTab by remember { mutableIntStateOf(0) } // 0: 원문, 1: 번역문, 2: 대조
    var currentDoc by remember(document) { mutableStateOf(document) }
    var isTranslating by remember { mutableStateOf(false) }
    var targetLanguageCode by remember { mutableStateOf("en") }

    val hasTranslation = !currentDoc?.translatedText.isNullOrBlank()

    // 대조 텍스트 구성
    val bilingualText = remember(content, currentDoc?.translatedText) {
        val trans = currentDoc?.translatedText
        if (trans.isNullOrBlank()) {
            ""
        } else {
            val origLines = content.lines().filter { it.isNotBlank() && !it.startsWith("===") && !it.startsWith("■") }
            val transLines = trans.lines().filter { it.isNotBlank() }
            if (origLines.isNotEmpty() && transLines.isNotEmpty()) {
                origLines.zip(transLines).joinToString("\n\n") { (orig, tr) ->
                    "$orig\n↳ $tr"
                }
            } else {
                "$content\n\n[번역문]\n$trans"
            }
        }
    }

    // 현재 선택된 탭의 내용
    val activeDisplayText = when (selectedTab) {
        1 -> currentDoc?.translatedText ?: ""
        2 -> bilingualText
        else -> content
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(if (isLandscape) 0.88f else 0.94f)
                    .fillMaxHeight(if (isLandscape) 0.92f else 0.82f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(AppSurfaceContainer)
                    .border(1.dp, AppOutline, RoundedCornerShape(20.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // 상단 타이틀 바
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (selectedTab != 0 && hasTranslation) Color(0xFF6366F1) else PrimaryBlue),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (selectedTab != 0 && hasTranslation) Icons.Default.Translate else Icons.Default.Description,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column {
                                val displayTitle = when {
                                    selectedTab == 1 -> (if (title.endsWith(".txt")) title.removeSuffix(".txt") else title) + "_trans_${currentDoc?.targetLanguage ?: "en"}.txt"
                                    selectedTab == 2 -> (if (title.endsWith(".txt")) title.removeSuffix(".txt") else title) + "_bilingual.txt"
                                    else -> if (title.endsWith(".txt")) title else "$title.txt"
                                }
                                Text(
                                    text = displayTitle,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "내 파일 › 문서 › VoiceToText",
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "닫기", tint = TextPrimary)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 3-Way 탭 전환 바 (원문 | 번역문 | 대조 보기)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFEEF2F7))
                            .padding(2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val tabs = listOf("📄 원문", "🌐 번역문", "📑 대조 보기")
                        tabs.forEachIndexed { index, label ->
                            val isSelected = selectedTab == index
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) (if (index != 0) Color(0xFF6366F1) else PrimaryBlue) else Color.Transparent)
                                    .clickable { selectedTab = index }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else TextPrimary,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 본문 영역 (텍스트 또는 번역 유도 카드)
                    val scrollState = rememberScrollState()
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFF8FAFC))
                            .border(1.dp, AppOutline, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        if (selectedTab != 0 && !hasTranslation) {
                            // 번역문이 없을 때 온디바이스 번역 실행 유도 카드
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(scrollState),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFEEF2FF)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Translate, contentDescription = null, tint = Color(0xFF6366F1), modifier = Modifier.size(28.dp))
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "온디바이스 번역 (Google ML Kit)",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "데이터 소모 없이 기기 내 신경망으로 바로 번역합니다.",
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                // 타겟 언어 선택 칩
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("en" to "🇺🇸 English", "ja" to "🇯🇵 日本語", "ko" to "🇰🇷 한국어").forEach { (code, name) ->
                                        val isSel = targetLanguageCode == code
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(999.dp))
                                                .background(if (isSel) Color(0xFF6366F1) else Color(0xFFE2E8F0))
                                                .clickable { targetLanguageCode = code }
                                                .padding(horizontal = 10.dp, vertical = 5.dp)
                                        ) {
                                            Text(
                                                text = name,
                                                fontSize = 11.5.sp,
                                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSel) Color.White else TextPrimary
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                if (isTranslating) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color(0xFF6366F1), strokeWidth = 2.dp)
                                        Text("온디바이스 번역 진행 중...", fontSize = 13.sp, color = Color(0xFF6366F1), fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            val docId = currentDoc?.id
                                            if (docId != null && onTranslate != null) {
                                                isTranslating = true
                                                onTranslate(targetLanguageCode) { result ->
                                                    isTranslating = false
                                                    if (result.isSuccess) {
                                                        currentDoc = result.getOrNull()
                                                    }
                                                }
                                            } else {
                                                Toast.makeText(context, "번역할 문서 정보가 없습니다.", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("번역 시작 (무료 / 오프라인)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                            }
                        } else {
                            // 텍스트 내용 열람
                            Box(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
                                Text(
                                    text = activeDisplayText.ifBlank { "내용이 비어 있습니다." },
                                    fontSize = 13.5.sp,
                                    lineHeight = 22.sp,
                                    color = TextPrimary,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 하단 액션 버튼 (현재 탭 내용 복사 / 공유 / 닫기)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cb.setPrimaryClip(ClipData.newPlainText("VoiceToText", activeDisplayText))
                                Toast.makeText(context, "텍스트가 클립보드에 복사되었습니다.", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            modifier = Modifier.weight(1f).height(42.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp), tint = TextPrimary)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("복사", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextPrimary, maxLines = 1, softWrap = false)
                        }

                        Button(
                            onClick = {
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, activeDisplayText)
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "텍스트 공유"))
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = if (selectedTab != 0 && hasTranslation) Color(0xFF6366F1) else PrimaryBlue),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            modifier = Modifier.weight(1f).height(42.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("공유", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White, maxLines = 1, softWrap = false)
                        }

                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            modifier = Modifier.weight(1f).height(42.dp)
                        ) {
                            Text("닫기", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextPrimary, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WaveformVisualizer(
    amplitudes: List<Float>,
    isPaused: Boolean,
    isIdle: Boolean,
    modifier: Modifier = Modifier
) {
    val barColor = when {
        isPaused -> TextSecondary.copy(alpha = 0.5f)
        isIdle -> AppOutline
        else -> StatusLive
    }

    Row(
        modifier = modifier.height(34.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        val count = 30
        for (i in 0 until count) {
            val amp = amplitudes.getOrNull(i) ?: if (isIdle) 6f else 8f
            val heightDp = (amp * 0.34f).coerceIn(4f, 32f).dp

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(heightDp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColor)
            )
        }
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0\u00A0B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1.0 -> String.format(Locale.getDefault(), "%.1f\u00A0MB", mb)
        kb >= 1.0 -> String.format(Locale.getDefault(), "%.1f\u00A0KB", kb)
        else -> "$bytes\u00A0B"
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
