package com.voicetotext.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicetotext.app.VoiceToTextApplication
import com.voicetotext.app.ui.components.formatFileSize
import com.voicetotext.app.ui.theme.AppOutline
import com.voicetotext.app.ui.theme.AppSurfaceContainer
import com.voicetotext.app.ui.theme.OnPrimaryBlueContainer
import com.voicetotext.app.ui.theme.OnStatusSynced
import com.voicetotext.app.ui.theme.PrimaryBlue
import com.voicetotext.app.ui.theme.PrimaryBlueContainer
import com.voicetotext.app.ui.theme.StatusLive
import com.voicetotext.app.ui.theme.StatusSyncedContainer
import com.voicetotext.app.ui.theme.TextMuted
import com.voicetotext.app.ui.theme.TextPrimary
import com.voicetotext.app.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageBottomSheet(
    sheetState: SheetState,
    onDismissRequest: () -> Unit,
    onDeleteAllClick: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as VoiceToTextApplication
    val localFileManager = app.repository.localFileManager

    var fileCount by remember { mutableIntStateOf(0) }
    var totalBytes by remember { mutableLongStateOf(0L) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val stats = localFileManager.getStorageStats()
        fileCount = stats.first
        totalBytes = stats.second
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = {
                Text("전체 기록 및 파일 삭제", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            },
            text = {
                Text(
                    "기기 내부의 Documents/VoiceToText 폴더에 저장된 모든 .txt 파일과 앱 내 변환 기록이 영구적으로 삭제됩니다. 계속하시겠습니까?",
                    fontSize = 13.5.sp,
                    color = TextPrimary,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDeleteAllClick()
                        Toast.makeText(context, "모든 파일과 기록이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusLive)
                ) {
                    Text("전체 삭제", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("취소", color = TextSecondary)
                }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = AppSurfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            // 헤더
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFF1F5F9))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(PrimaryBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "로컬 저장소 관리",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "기기 내부 저장 · 완전 오프라인",
                        fontSize = 12.5.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(StatusSyncedContainer)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp), tint = OnStatusSynced)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "클라우드 연동 불필요",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = OnStatusSynced
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 저장 위치 카드
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFF8FAFC))
                    .border(1.dp, AppOutline, RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Text("저장 위치", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Folder, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "내 파일 › 문서 › VoiceToText",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                val dirPath = localFileManager.getVoiceToTextDirectory().absolutePath
                Text(dirPath, fontSize = 11.sp, color = TextMuted)

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .clickable { localFileManager.openVoiceToTextFolder(context) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("내 파일 앱에서 폴더 열기", color = PrimaryBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 저장 통계 카드
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFF8FAFC))
                    .border(1.dp, AppOutline, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$fileCount", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary, maxLines = 1, softWrap = false)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("저장된 파일 수", fontSize = 12.sp, color = TextSecondary, maxLines = 1, softWrap = false)
                }
                Box(modifier = Modifier.width(1.dp).height(32.dp).background(AppOutline))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(formatFileSize(totalBytes), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary, maxLines = 1, softWrap = false)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("총 사용 용량", fontSize = 12.sp, color = TextSecondary, maxLines = 1, softWrap = false)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 개인정보 및 보안 안내 카드
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFEFF6FF))
                    .padding(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "인터넷이나 외부 서버로 음성 및 텍스트 데이터가 전송되지 않으며, 스마트폰 내부에만 .txt 파일로 안전하게 보관됩니다.",
                    fontSize = 11.5.sp,
                    color = Color(0xFF1E3A8A),
                    lineHeight = 17.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 전체 삭제 버튼
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showDeleteConfirmDialog = true }
                    .padding(vertical = 12.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(20.dp), tint = StatusLive)
                Spacer(modifier = Modifier.width(10.dp))
                Text("전체 파일 및 변환 기록 삭제", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = StatusLive)
            }

            Spacer(modifier = Modifier.height(18.dp))
        }
    }
}
