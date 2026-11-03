package com.voicetotext.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.voicetotext.app.ui.theme.AppOutline
import com.voicetotext.app.ui.theme.AppSurfaceContainer
import com.voicetotext.app.ui.theme.PrimaryBlue
import com.voicetotext.app.ui.theme.PrimaryBlueContainer
import com.voicetotext.app.ui.theme.StatusLive
import com.voicetotext.app.ui.theme.StatusLiveContainer
import com.voicetotext.app.ui.theme.StatusSynced
import com.voicetotext.app.ui.theme.StatusSyncedContainer
import com.voicetotext.app.ui.theme.TextPrimary
import com.voicetotext.app.ui.theme.TextSecondary

@Composable
fun OnboardingDialog(
    onConfirm: () -> Unit
) {
    Dialog(onDismissRequest = {}) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(AppSurfaceContainer)
                .padding(24.dp)
        ) {
            Text("시작하기", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "재생 중인 소리를\n텍스트로 바꾸려면\n권한이 필요해요",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary,
                lineHeight = 28.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "화면은 녹화·저장하지 않아요.\n재생되는 소리만 기기 안에서 텍스트로 바꿉니다.",
                fontSize = 13.sp,
                color = TextSecondary,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            PermissionItem(
                icon = Icons.Default.Mic,
                iconBg = StatusLiveContainer,
                iconTint = StatusLive,
                title = "오디오 캡처",
                description = "다른 앱에서 재생 중인 소리를 듣기 위해 필요해요. 마이크는 사용하지 않아요."
            )

            Spacer(modifier = Modifier.height(10.dp))

            PermissionItem(
                icon = Icons.Default.Notifications,
                iconBg = PrimaryBlueContainer,
                iconTint = PrimaryBlue,
                title = "알림",
                description = "백그라운드에서 변환 중일 때 상태바와 알림으로 알려드려요."
            )

            Spacer(modifier = Modifier.height(10.dp))

            PermissionItem(
                icon = Icons.Default.Security,
                iconBg = StatusSyncedContainer,
                iconTint = StatusSynced,
                title = "화면 캡처 동의 (변환 시작 시)",
                description = "시스템 팝업에 '녹화' 경고가 나오지만 소리만 사용합니다."
            )

            Spacer(modifier = Modifier.height(22.dp))

            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("권한 허용하고 계속", fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun PermissionItem(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFF8FAFC))
            .border(1.dp, AppOutline, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = TextPrimary)
            Spacer(modifier = Modifier.height(2.dp))
            Text(description, fontSize = 11.5.sp, color = TextSecondary, lineHeight = 16.sp)
        }
    }
}
