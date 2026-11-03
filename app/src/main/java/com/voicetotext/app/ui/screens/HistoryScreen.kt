package com.voicetotext.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicetotext.app.VoiceToTextApplication
import com.voicetotext.app.data.local.DocumentEntity
import com.voicetotext.app.data.local.TranscriptEntity
import kotlinx.coroutines.launch
import com.voicetotext.app.data.model.SyncStatus
import com.voicetotext.app.ui.components.InAppTextViewerDialog
import com.voicetotext.app.ui.components.LanguageBadge
import com.voicetotext.app.ui.components.SaveAsDialog
import com.voicetotext.app.ui.components.SyncStatusBadge
import com.voicetotext.app.ui.components.formatFileSize
import com.voicetotext.app.ui.theme.AppOutline
import com.voicetotext.app.ui.theme.AppSurface
import com.voicetotext.app.ui.theme.AppSurfaceContainer
import com.voicetotext.app.ui.theme.OnPrimaryBlueContainer
import com.voicetotext.app.ui.theme.PrimaryBlue
import com.voicetotext.app.ui.theme.PrimaryBlueContainer
import com.voicetotext.app.ui.theme.StatusLive
import com.voicetotext.app.ui.theme.TextMuted
import com.voicetotext.app.ui.theme.TextPrimary
import com.voicetotext.app.ui.theme.TextSecondary
import com.voicetotext.app.ui.viewmodel.HistoryFilter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    documents: List<DocumentEntity>,
    searchQuery: String,
    selectedFilter: HistoryFilter,
    selectedDetailDoc: DocumentEntity?,
    detailTranscripts: List<TranscriptEntity>,
    isLandscape: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onFilterChange: (HistoryFilter) -> Unit,
    onDocumentClick: (DocumentEntity) -> Unit,
    onDeleteDocument: (String) -> Unit,
    onRenameDocument: ((String, String) -> Unit)? = null,
    onSaveDocument: (DocumentEntity) -> Unit = {},
    onCancelSaveDocument: (DocumentEntity) -> Unit = {},
    onTranslateDocument: ((String, String, (Result<DocumentEntity>) -> Unit) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var inAppViewingDoc by remember { mutableStateOf<DocumentEntity?>(null) }
    var inAppViewingContent by remember { mutableStateOf("") }
    var docToDelete by remember { mutableStateOf<DocumentEntity?>(null) }
    var docToRename by remember { mutableStateOf<DocumentEntity?>(null) }

    if (inAppViewingDoc != null) {
        InAppTextViewerDialog(
            title = inAppViewingDoc?.title ?: "VoiceToText",
            content = inAppViewingContent,
            document = inAppViewingDoc,
            onTranslate = { targetLang, cb ->
                onTranslateDocument?.invoke(inAppViewingDoc!!.id, targetLang, cb)
            },
            onDismiss = { inAppViewingDoc = null }
        )
    }

    if (docToRename != null) {
        val target = docToRename!!
        SaveAsDialog(
            title = "파일 이름 변경",
            initialName = target.title,
            confirmButtonText = "변경",
            onConfirm = { newTitle ->
                docToRename = null
                onRenameDocument?.invoke(target.id, newTitle)
            },
            onDismiss = { docToRename = null }
        )
    }

    // 삭제 확인 다이얼로그 (실수로 삭제되는 문제 방지)
    if (docToDelete != null) {
        val target = docToDelete!!
        val displayTitle = if (target.title.endsWith(".txt")) target.title else "${target.title}.txt"
        AlertDialog(
            onDismissRequest = { docToDelete = null },
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
                        val id = target.id
                        docToDelete = null
                        onDeleteDocument(id)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusLive),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("삭제", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { docToDelete = null },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("취소", color = TextPrimary, fontWeight = FontWeight.Medium)
                }
            },
            shape = RoundedCornerShape(18.dp),
            containerColor = Color.White
        )
    }

    val scope = rememberCoroutineScope()
    val openFileLambda: (DocumentEntity) -> Unit = { doc ->
        scope.launch {
            val text = VoiceToTextApplication.instance.repository.buildFullDocumentText(doc)
            inAppViewingDoc = doc
            inAppViewingContent = text
        }
    }

    if (isLandscape) {
        Row(modifier = modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .width(320.dp)
                    .fillMaxHeight()
                    .border(width = 1.dp, color = AppOutline)
            ) {
                HistoryListContent(
                    documents = documents,
                    searchQuery = searchQuery,
                    selectedFilter = selectedFilter,
                    selectedDocId = selectedDetailDoc?.id,
                    onSearchQueryChange = onSearchQueryChange,
                    onFilterChange = onFilterChange,
                    onDocumentClick = onDocumentClick,
                    onOpenFile = openFileLambda,
                    onRenameDocument = { docToRename = it },
                    onDeleteDocument = { docId ->
                        val doc = documents.find { it.id == docId }
                        if (doc != null) docToDelete = doc else onDeleteDocument(docId)
                    },
                    onSaveDocument = onSaveDocument,
                    onCancelSaveDocument = onCancelSaveDocument
                )
            }

            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                if (selectedDetailDoc != null) {
                    DocumentDetailContent(
                        doc = selectedDetailDoc,
                        transcripts = detailTranscripts,
                        onOpenFile = { openFileLambda(selectedDetailDoc) },
                        onTranslateDocument = onTranslateDocument
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("목록에서 문서를 선택하면 내용을 볼 수 있습니다.", color = TextSecondary, fontSize = 14.sp)
                    }
                }
            }
        }
    } else {
        HistoryListContent(
            documents = documents,
            searchQuery = searchQuery,
            selectedFilter = selectedFilter,
            selectedDocId = null,
            onSearchQueryChange = onSearchQueryChange,
            onFilterChange = onFilterChange,
            onDocumentClick = onDocumentClick,
            onOpenFile = openFileLambda,
            onRenameDocument = { docToRename = it },
            onDeleteDocument = { docId ->
                val doc = documents.find { it.id == docId }
                if (doc != null) docToDelete = doc else onDeleteDocument(docId)
            },
            onSaveDocument = onSaveDocument,
            onCancelSaveDocument = onCancelSaveDocument,
            modifier = modifier
        )
    }
}

@Composable
fun HistoryListContent(
    documents: List<DocumentEntity>,
    searchQuery: String,
    selectedFilter: HistoryFilter,
    selectedDocId: String?,
    onSearchQueryChange: (String) -> Unit,
    onFilterChange: (HistoryFilter) -> Unit,
    onDocumentClick: (DocumentEntity) -> Unit,
    onOpenFile: (DocumentEntity) -> Unit,
    onRenameDocument: (DocumentEntity) -> Unit = {},
    onDeleteDocument: (String) -> Unit,
    onSaveDocument: (DocumentEntity) -> Unit = {},
    onCancelSaveDocument: (DocumentEntity) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Spacer(modifier = Modifier.height(8.dp))

        // 검색바 (글자 수직 중앙 정렬 및 하단 잘림 완전 방지)
        BasicTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color(0xFFEEF2F7))
                .padding(horizontal = 14.dp),
            singleLine = true,
            textStyle = TextStyle(
                fontSize = 13.5.sp,
                color = TextPrimary,
                fontWeight = FontWeight.Medium
            ),
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                "제목이나 내용으로 검색",
                                fontSize = 13.sp,
                                color = TextMuted
                            )
                        }
                        innerTextField()
                    }
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { onSearchQueryChange("") },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "검색어 지우기",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 필터 칩스
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val filters = listOf(
                HistoryFilter.ALL to "전체",
                HistoryFilter.TODAY to "오늘",
                HistoryFilter.THIS_WEEK to "이번 주",
                HistoryFilter.THIS_MONTH to "이번 달"
            )
            items(filters) { (filter, label) ->
                val isSelected = selectedFilter == filter
                FilterChip(
                    selected = isSelected,
                    onClick = { onFilterChange(filter) },
                    label = {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false
                        )
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PrimaryBlueContainer,
                        selectedLabelColor = OnPrimaryBlueContainer
                    ),
                    border = FilterChipDefaults.filterChipBorder(enabled = true, selected = isSelected, borderColor = AppOutline)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (documents.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(48.dp), tint = TextMuted)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("변환된 문서가 없습니다.", color = TextSecondary, fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(documents, key = { it.id }) { doc ->
                    val isSelected = doc.id == selectedDocId
                    DocumentCard(
                        doc = doc,
                        isSelected = isSelected,
                        onClick = { onDocumentClick(doc) },
                        onOpenFile = { onOpenFile(doc) },
                        onRename = { onRenameDocument(doc) },
                        onDelete = { onDeleteDocument(doc.id) },
                        onSave = { onSaveDocument(doc) },
                        onCancelSave = { onCancelSaveDocument(doc) }
                    )
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DocumentCard(
    doc: DocumentEntity,
    isSelected: Boolean,
    onClick: () -> Unit,
    onOpenFile: () -> Unit,
    onRename: () -> Unit = {},
    onDelete: () -> Unit,
    onSave: () -> Unit = {},
    onCancelSave: () -> Unit = {}
) {
    val dateFormat = SimpleDateFormat("yyyy.MM.dd a h:mm", Locale.KOREA)
    val dateStr = dateFormat.format(Date(doc.createdAt))

    val m = doc.durationSeconds / 60
    val s = doc.durationSeconds % 60
    val durationStr = if (m > 0) "${m}분 ${s}초" else "${s}초"
    val displayTitle = if (doc.title.endsWith(".txt")) doc.title else "${doc.title}.txt"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) Color(0xFFF5F9FF) else AppSurfaceContainer)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) PrimaryBlue else AppOutline,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 텍스트 파일 (.txt) 미니 아이콘
                Box(
                    modifier = Modifier
                        .size(width = 22.dp, height = 28.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(PrimaryBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(modifier = Modifier.width(12.dp).height(1.5.dp).background(Color.White))
                        Spacer(modifier = Modifier.height(2.dp))
                        Box(modifier = Modifier.width(12.dp).height(1.5.dp).background(Color.White))
                        Spacer(modifier = Modifier.height(2.dp))
                        Box(modifier = Modifier.width(8.dp).height(1.5.dp).background(Color.White))
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = displayTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                IconButton(onClick = onRename, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "이름 변경", tint = TextSecondary, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onOpenFile, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.OpenInNew, contentDescription = "텍스트 보기", tint = Color(0xFF0284C7), modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "삭제", tint = TextMuted, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = doc.summaryPreview,
                fontSize = 12.5.sp,
                color = TextSecondary,
                lineHeight = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 메타 정보 영역 (날짜, 언어, 녹음시간, 파일크기 - FlowRow로 여백 부족 시 칩 단위 줄바꿈 및 글자 쪼개짐 방지)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = dateStr,
                    fontSize = 11.sp,
                    color = TextMuted,
                    maxLines = 1,
                    softWrap = false
                )
                LanguageBadge(langCode = doc.language)
                if (!doc.translatedText.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFEEF2FF))
                            .border(0.8.dp, Color(0xFF6366F1), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "🌐 ${doc.targetLanguage?.uppercase() ?: "EN"} 번역됨",
                            fontSize = 10.5.sp,
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
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = durationStr,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFEEF2F7))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = formatFileSize(doc.fileSizeBytes),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 한 줄 아래로 분리된 상태 및 작업 버튼 영역 (크기 통일 & 저장취소/저장 기능)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SyncStatusBadge(status = doc.syncStatus)

                if (doc.syncStatus == SyncStatus.SYNCING || doc.syncStatus == SyncStatus.PENDING) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 저장 취소 버튼
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
                                .background(Color.White)
                                .clickable { onCancelSave() }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "저장 취소",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextSecondary,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        // 저장(확정) 버튼
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(PrimaryBlue)
                                .clickable { onSave() }
                                .padding(horizontal = 12.dp, vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                                Text(
                                    text = "저장",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                } else if (doc.syncStatus == SyncStatus.ERROR) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(PrimaryBlue)
                            .clickable { onSave() }
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "다시 저장",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}
