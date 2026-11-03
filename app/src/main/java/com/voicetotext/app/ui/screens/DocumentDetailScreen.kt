package com.voicetotext.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicetotext.app.data.local.DocumentEntity
import com.voicetotext.app.data.local.TranscriptEntity
import com.voicetotext.app.ui.components.InAppTextViewerDialog
import com.voicetotext.app.ui.components.LanguageBadge
import com.voicetotext.app.ui.components.SaveAsDialog
import com.voicetotext.app.ui.components.SyncStatusBadge
import com.voicetotext.app.ui.components.formatFileSize
import com.voicetotext.app.ui.theme.AppOutline
import com.voicetotext.app.ui.theme.AppSurface
import com.voicetotext.app.ui.theme.PrimaryBlue
import com.voicetotext.app.ui.theme.StatusLive
import com.voicetotext.app.ui.theme.TextMuted
import com.voicetotext.app.ui.theme.TextPrimary
import com.voicetotext.app.ui.theme.TextSecondary
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentDetailScreen(
    doc: DocumentEntity,
    transcripts: List<TranscriptEntity>,
    onBackClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onRenameDocument: ((String, String) -> Unit)? = null,
    onAppendToDocument: ((DocumentEntity) -> Unit)? = null,
    onTranslateDocument: ((String, String, (Result<DocumentEntity>) -> Unit) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    BackHandler {
        onBackClick()
    }

    val displayTitle = if (doc.title.endsWith(".txt")) doc.title else "${doc.title}.txt"
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    if (showRenameDialog) {
        SaveAsDialog(
            title = "파일 이름 변경",
            initialName = doc.title,
            confirmButtonText = "변경",
            onConfirm = { newTitle ->
                showRenameDialog = false
                onRenameDocument?.invoke(doc.id, newTitle)
            },
            onDismiss = { showRenameDialog = false }
        )
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = {
                Text(
                    text = "문서 삭제 확인",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    text = "'$displayTitle' 문서를 삭제하시겠습니까?\n저장된 텍스트 파일과 변환 기록이 모두 삭제됩니다.",
                    fontSize = 13.5.sp,
                    color = TextSecondary,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDeleteClick()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusLive),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("삭제", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDeleteConfirmDialog = false },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("취소", color = TextPrimary, fontWeight = FontWeight.Medium)
                }
            },
            shape = RoundedCornerShape(18.dp),
            containerColor = Color.White
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(displayTitle, maxLines = 1, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로가기")
                    }
                },
                actions = {
                    IconButton(onClick = { showRenameDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "이름 변경", tint = TextSecondary)
                    }
                    IconButton(onClick = { showDeleteConfirmDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "삭제", tint = TextMuted)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppSurface)
            )
        }
    ) { padding ->
        DocumentDetailContent(
            doc = doc,
            transcripts = transcripts,
            onAppendToDocument = onAppendToDocument,
            onTranslateDocument = onTranslateDocument,
            modifier = Modifier.padding(padding)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DocumentDetailContent(
    doc: DocumentEntity,
    transcripts: List<TranscriptEntity>,
    onOpenFile: (() -> Unit)? = null,
    onAppendToDocument: ((DocumentEntity) -> Unit)? = null,
    onTranslateDocument: ((String, String, (Result<DocumentEntity>) -> Unit) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showInAppViewer by remember { mutableStateOf(false) }

    val dateFormat = SimpleDateFormat("yyyy.MM.dd a h:mm", Locale.KOREA)
    val dateStr = dateFormat.format(Date(doc.createdAt))
    val m = doc.durationSeconds / 60
    val s = doc.durationSeconds % 60
    val durationStr = if (m > 0) "${m}분 ${s}초" else "${s}초"
    val displayTitle = if (doc.title.endsWith(".txt")) doc.title else "${doc.title}.txt"

    val fullText = remember(doc, transcripts) {
        val file = doc.localFilePath?.let { File(it) }
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

    if (showInAppViewer) {
        InAppTextViewerDialog(
            title = displayTitle,
            content = fullText,
            document = doc,
            onTranslate = if (onTranslateDocument != null) { targetLang, cb -> onTranslateDocument(doc.id, targetLang, cb) } else null,
            onDismiss = { showInAppViewer = false }
        )
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(modifier = Modifier.height(10.dp))

        Text(displayTitle, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
        Spacer(modifier = Modifier.height(6.dp))

        // 메타 정보 칩스 (가로 모드 좁은 화면에서도 FlowRow로 줄바꿈되어 글자가 세로로 쪼개지지 않음)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFFEEF2F7))
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(
                    text = dateStr,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    maxLines = 1,
                    softWrap = false
                )
            }
            LanguageBadge(langCode = doc.language)
            if (!doc.translatedText.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFEEF2FF))
                        .border(0.8.dp, Color(0xFF6366F1), RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "🌐 ${doc.targetLanguage?.uppercase() ?: "EN"} 번역됨",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF6366F1),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFFEEF2F7))
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(
                    text = durationStr,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    softWrap = false
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFFEEF2F7))
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(
                    text = formatFileSize(doc.fileSizeBytes),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    softWrap = false
                )
            }
            SyncStatusBadge(status = doc.syncStatus)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 저장 경로 정보 배너
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFF8FAFC))
                .border(1.dp, AppOutline, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Folder, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                "내 파일 › 문서 › VoiceToText",
                fontSize = 12.sp,
                color = TextSecondary,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 액션 버튼 바 (앱 내 텍스트 열기: 하늘색, 복사: 아웃라인, 공유: 파란색 - FlowRow로 줄바꿈 방지)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (onAppendToDocument != null) {
                Button(
                    onClick = { onAppendToDocument(doc) },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("이어서 작성", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, softWrap = false)
                }
            }

            Button(
                onClick = {
                    if (onOpenFile != null) onOpenFile() else showInAppViewer = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color.White)
                Spacer(modifier = Modifier.width(4.dp))
                Text("텍스트 열기", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, softWrap = false)
            }

            OutlinedButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("VoiceToText Transcript", fullText))
                    Toast.makeText(context, "전체 텍스트가 복사되었습니다.", Toast.LENGTH_SHORT).show()
                },
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp), tint = TextPrimary)
                Spacer(modifier = Modifier.width(4.dp))
                Text("복사", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1, softWrap = false)
            }

            Button(
                onClick = {
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, fullText)
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "변환 텍스트 공유"))
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color.White)
                Spacer(modifier = Modifier.width(4.dp))
                Text("공유", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, softWrap = false)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(AppOutline))
        Spacer(modifier = Modifier.height(14.dp))

        // 스크립트 본문 목록
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(transcripts, key = { it.id }) { item ->
                Column {
                    Text(item.formattedTime, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = TextMuted)
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(item.text, fontSize = 15.5.sp, lineHeight = 25.sp, color = TextPrimary)
                }
            }
            item { Spacer(modifier = Modifier.height(20.dp)) }
        }
    }
}
