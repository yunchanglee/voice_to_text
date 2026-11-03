package com.voicetotext.app.ui.screens

import android.app.Activity
import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicetotext.app.data.local.DocumentEntity
import com.voicetotext.app.data.model.CaptureState
import com.voicetotext.app.ui.components.AppendDocumentPickerSheet
import com.voicetotext.app.ui.components.LiveBadge
import com.voicetotext.app.ui.components.PausedBadge
import com.voicetotext.app.ui.theme.AppSurface
import com.voicetotext.app.ui.theme.AppSurfaceContainer
import com.voicetotext.app.ui.theme.OnPrimaryBlueContainer
import com.voicetotext.app.ui.theme.PrimaryBlue
import com.voicetotext.app.ui.theme.PrimaryBlueContainer
import com.voicetotext.app.ui.theme.TextPrimary
import com.voicetotext.app.ui.theme.TextSecondary
import com.voicetotext.app.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onRequestMediaProjection: (DocumentEntity?) -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showStorageSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    var showAppendPickerSheet by remember { mutableStateOf(false) }
    val appendSheetState = rememberModalBottomSheetState()

    val captureState by viewModel.captureState.collectAsState()
    val currentDocument by viewModel.currentDocument.collectAsState()
    val isAppendMode by viewModel.isAppendMode.collectAsState()
    val elapsedTimeSeconds by viewModel.elapsedTimeSeconds.collectAsState()
    val interimText by viewModel.interimText.collectAsState()
    val waveform by viewModel.waveform.collectAsState()
    val activeTranscripts by viewModel.activeTranscripts.collectAsState()
    val selectedLanguage by viewModel.selectedLanguage.collectAsState()
    val detectedLanguage by viewModel.detectedLanguage.collectAsState()

    val historyDocuments by viewModel.historyDocuments.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()

    val selectedDetailDoc by viewModel.selectedDetailDoc.collectAsState()
    val detailTranscripts by viewModel.selectedDetailTranscripts.collectAsState()

    val context = LocalContext.current
    val activity = context as? Activity
    var backPressedTime by remember { mutableLongStateOf(0L) }

    // 단일 통합 BackHandler: 모든 화면 상태에서 이전 화면으로 안전하게 복귀
    BackHandler(enabled = true) {
        when {
            // 1. 세로 모드 상세 화면 -> 기록 목록(이전 화면)으로 복귀
            !isLandscape && selectedDetailDoc != null -> {
                viewModel.selectDocumentForDetail(null)
            }
            // 2. 가로 모드 상세 선택 -> 선택 해제
            isLandscape && selectedDetailDoc != null -> {
                viewModel.selectDocumentForDetail(null)
            }
            // 3. 바텀시트 -> 닫기
            showAppendPickerSheet -> {
                showAppendPickerSheet = false
            }
            showStorageSheet -> {
                showStorageSheet = false
            }
            // 4. 변환 기록 탭 -> 검색어 삭제 또는 실시간(홈) 탭으로 복귀
            selectedTab != 0 -> {
                if (searchQuery.isNotBlank()) {
                    viewModel.setSearchQuery("")
                } else {
                    selectedTab = 0
                }
            }
            // 5. 변환 완료 화면 -> 대기(시작) 화면으로 복귀
            captureState == CaptureState.COMPLETED -> {
                viewModel.resetToIdle()
            }
            // 6. 녹음/변환 진행 중 -> 백그라운드로 안전하게 전환 (음성 캡처 지속)
            captureState == CaptureState.RECORDING || captureState == CaptureState.PAUSED -> {
                Toast.makeText(context, "백그라운드에서 실시간 음성 변환이 계속 진행됩니다.", Toast.LENGTH_SHORT).show()
                activity?.moveTaskToBack(true)
            }
            // 7. 실시간 대기(시작) 화면 -> 2초 내 재클릭 시 앱 종료
            else -> {
                val currentTime = System.currentTimeMillis()
                if (currentTime - backPressedTime < 2000L) {
                    activity?.finish()
                } else {
                    backPressedTime = currentTime
                    Toast.makeText(context, "뒤로 가기 버튼을 한 번 더 누르면 앱이 종료됩니다.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 상세 보기 화면 (세로 모드에서 문서 선택 시)
    if (!isLandscape && selectedDetailDoc != null) {
        DocumentDetailScreen(
            doc = selectedDetailDoc!!,
            transcripts = detailTranscripts,
            onBackClick = { viewModel.selectDocumentForDetail(null) },
            onDeleteClick = { viewModel.deleteDocument(selectedDetailDoc!!.id) },
            onRenameDocument = { docId, newTitle -> viewModel.renameDocument(docId, newTitle) },
            onAppendToDocument = { doc -> onRequestMediaProjection(doc) },
            onTranslateDocument = { docId, targetLang, cb -> viewModel.translateDocument(docId, targetLang, cb) }
        )
        return
    }

    if (isLandscape) {
        // 가로보기 (Landscape): NavigationRail + 컨텐츠 (상단 상태바 및 시스템 인셋 겹침 방지)
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(AppSurface)
                .statusBarsPadding()
                .navigationBarsPadding()
                .displayCutoutPadding()
        ) {
            NavigationRail(
                containerColor = AppSurfaceContainer,
                modifier = Modifier.fillMaxHeight().width(76.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                // 앱 로고
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(PrimaryBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))

                NavigationRailItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Mic, contentDescription = "실시간") },
                    label = { Text("실시간", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false) },
                    colors = NavigationRailItemDefaults.colors(
                        selectedIconColor = OnPrimaryBlueContainer,
                        selectedTextColor = TextPrimary,
                        indicatorColor = PrimaryBlueContainer,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    )
                )

                NavigationRailItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.History, contentDescription = "변환 기록") },
                    label = { Text("기록", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false) },
                    colors = NavigationRailItemDefaults.colors(
                        selectedIconColor = OnPrimaryBlueContainer,
                        selectedTextColor = TextPrimary,
                        indicatorColor = PrimaryBlueContainer,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    )
                )

                Spacer(modifier = Modifier.weight(1f))

                // 저장소 폴더 아이콘
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEEF2F7))
                        .clickable { showStorageSheet = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Folder, contentDescription = "저장소 관리", tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                if (selectedTab == 0) {
                    LiveTranscribeScreen(
                        captureState = captureState,
                        currentDocument = currentDocument,
                        elapsedTimeSeconds = elapsedTimeSeconds,
                        interimText = interimText,
                        waveform = waveform,
                        transcripts = activeTranscripts,
                        selectedLanguage = selectedLanguage,
                        detectedLanguage = detectedLanguage,
                        isLandscape = true,
                        isAppendMode = isAppendMode,
                        onSelectLanguage = { viewModel.selectLanguage(it) },
                        onStartClick = { onRequestMediaProjection(null) },
                        onAppendClick = { showAppendPickerSheet = true },
                        onPauseClick = { viewModel.pauseSession() },
                        onResumeClick = { viewModel.resumeSession() },
                        onStopClick = { viewModel.stopSession() },
                        onResetClick = { viewModel.resetToIdle() },
                        onRenameDocument = { docId, newTitle -> viewModel.renameDocument(docId, newTitle) },
                        onTranslateDocument = { docId, targetLang, cb -> viewModel.translateDocument(docId, targetLang, cb) }
                    )
                } else {
                    HistoryScreen(
                        documents = historyDocuments,
                        searchQuery = searchQuery,
                        selectedFilter = selectedFilter,
                        selectedDetailDoc = selectedDetailDoc,
                        detailTranscripts = detailTranscripts,
                        isLandscape = true,
                        onSearchQueryChange = { viewModel.setSearchQuery(it) },
                        onFilterChange = { viewModel.setFilter(it) },
                        onDocumentClick = { viewModel.selectDocumentForDetail(it) },
                        onDeleteDocument = { viewModel.deleteDocument(it) },
                        onRenameDocument = { docId, newTitle -> viewModel.renameDocument(docId, newTitle) },
                        onSaveDocument = { viewModel.finalizeDocument(it) },
                        onCancelSaveDocument = { viewModel.cancelDocumentSave(it) },
                        onTranslateDocument = { docId, targetLang, cb -> viewModel.translateDocument(docId, targetLang, cb) }
                    )
                }
            }
        }
    } else {
        // 세로보기 (Portrait): TopAppBar + Content + Bottom NavigationBar
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(PrimaryBlue),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Mic, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                if (selectedTab == 0) "VoiceToText" else "변환 기록",
                                fontSize = 19.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextPrimary
                            )
                        }
                    },
                    actions = {
                        if (selectedTab == 0) {
                            if (captureState == CaptureState.RECORDING) {
                                LiveBadge()
                                Spacer(modifier = Modifier.width(8.dp))
                            } else if (captureState == CaptureState.PAUSED) {
                                PausedBadge()
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                        }

                        // 저장소 폴더 아이콘
                        IconButton(onClick = { showStorageSheet = true }) {
                            Icon(
                                Icons.Outlined.Folder,
                                contentDescription = "저장소 관리",
                                tint = PrimaryBlue,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = AppSurface)
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = AppSurfaceContainer,
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.Mic, contentDescription = "실시간") },
                        label = { Text("실시간", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = OnPrimaryBlueContainer,
                            selectedTextColor = TextPrimary,
                            indicatorColor = PrimaryBlueContainer,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        )
                    )

                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.History, contentDescription = "변환 기록") },
                        label = { Text("변환 기록", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = OnPrimaryBlueContainer,
                            selectedTextColor = TextPrimary,
                            indicatorColor = PrimaryBlueContainer,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        )
                    )
                }
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                if (selectedTab == 0) {
                    LiveTranscribeScreen(
                        captureState = captureState,
                        currentDocument = currentDocument,
                        elapsedTimeSeconds = elapsedTimeSeconds,
                        interimText = interimText,
                        waveform = waveform,
                        transcripts = activeTranscripts,
                        selectedLanguage = selectedLanguage,
                        detectedLanguage = detectedLanguage,
                        isLandscape = false,
                        isAppendMode = isAppendMode,
                        onSelectLanguage = { viewModel.selectLanguage(it) },
                        onStartClick = { onRequestMediaProjection(null) },
                        onAppendClick = { showAppendPickerSheet = true },
                        onPauseClick = { viewModel.pauseSession() },
                        onResumeClick = { viewModel.resumeSession() },
                        onStopClick = { viewModel.stopSession() },
                        onResetClick = { viewModel.resetToIdle() },
                        onRenameDocument = { docId, newTitle -> viewModel.renameDocument(docId, newTitle) },
                        onTranslateDocument = { docId, targetLang, cb -> viewModel.translateDocument(docId, targetLang, cb) }
                    )
                } else {
                    HistoryScreen(
                        documents = historyDocuments,
                        searchQuery = searchQuery,
                        selectedFilter = selectedFilter,
                        selectedDetailDoc = null,
                        detailTranscripts = emptyList(),
                        isLandscape = false,
                        onSearchQueryChange = { viewModel.setSearchQuery(it) },
                        onFilterChange = { viewModel.setFilter(it) },
                        onDocumentClick = { viewModel.selectDocumentForDetail(it) },
                        onDeleteDocument = { viewModel.deleteDocument(it) },
                        onRenameDocument = { docId, newTitle -> viewModel.renameDocument(docId, newTitle) },
                        onSaveDocument = { viewModel.finalizeDocument(it) },
                        onCancelSaveDocument = { viewModel.cancelDocumentSave(it) },
                        onTranslateDocument = { docId, targetLang, cb -> viewModel.translateDocument(docId, targetLang, cb) }
                    )
                }
            }
        }
    }

    if (showAppendPickerSheet) {
        AppendDocumentPickerSheet(
            sheetState = appendSheetState,
            documents = historyDocuments,
            onSelectDocument = { doc ->
                showAppendPickerSheet = false
                onRequestMediaProjection(doc)
            },
            onDismissRequest = { showAppendPickerSheet = false }
        )
    }

    if (showStorageSheet) {
        StorageBottomSheet(
            sheetState = sheetState,
            onDismissRequest = { showStorageSheet = false },
            onDeleteAllClick = {
                viewModel.deleteAllDocuments()
                showStorageSheet = false
            }
        )
    }
}
