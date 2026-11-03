package com.voicetotext.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.voicetotext.app.data.local.DocumentEntity
import com.voicetotext.app.ui.screens.MainScreen
import com.voicetotext.app.ui.screens.OnboardingDialog
import com.voicetotext.app.ui.theme.VoiceToTextTheme
import com.voicetotext.app.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val projectionManager by lazy {
        getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    private var orientationChangedForProjection = false
    private var pendingAppendDoc: DocumentEntity? = null

    // MediaProjection 화면/오디오 캡처 승인 런처
    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (orientationChangedForProjection) {
            orientationChangedForProjection = false
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val appendDoc = pendingAppendDoc
            if (appendDoc != null) {
                viewModel.startAppendSession(appendDoc, result.resultCode, result.data!!)
                pendingAppendDoc = null
            } else {
                viewModel.startSession(result.resultCode, result.data!!)
            }
        } else {
            pendingAppendDoc = null
            Toast.makeText(this, "오디오 캡처가 취소되었습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    // 런타임 권한 (RECORD_AUDIO, POST_NOTIFICATIONS) 런처
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordAudioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        if (!recordAudioGranted) {
            Toast.makeText(this, "내부 오디오 캡처를 위해 오디오 권한이 필수적입니다.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            VoiceToTextTheme {
                var showOnboarding by remember { mutableStateOf(!hasRequiredPermissions()) }

                if (showOnboarding) {
                    OnboardingDialog(
                        onConfirm = {
                            showOnboarding = false
                            requestRequiredPermissions()
                        }
                    )
                }

                MainScreen(
                    viewModel = viewModel,
                    onRequestMediaProjection = { targetDoc -> checkAndStartMediaProjection(targetDoc) }
                )
            }
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        val audio = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val noti = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
        return audio && noti
    }

    private fun requestRequiredPermissions() {
        val list = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(list.toTypedArray())
    }

    private fun checkAndStartMediaProjection(targetDoc: DocumentEntity? = null) {
        pendingAppendDoc = targetDoc
        if (!hasRequiredPermissions()) {
            requestRequiredPermissions()
            return
        }

        // 가로 모드에서 삼성 One UI MediaProjection 시스템 권한 다이얼로그의 하단 버튼('지금 시작')이
        // 화면 높이 부족으로 잘려 클릭할 수 없는 시스템 이슈 방지:
        // 권한 요청 시 일시적으로 세로(PORTRAIT)로 전환하여 하단 버튼이 100% 정상 표시되도록 보장
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (isLandscape) {
            orientationChangedForProjection = true
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val config = MediaProjectionConfig.createConfigForDefaultDisplay()
            projectionManager.createScreenCaptureIntent(config)
        } else {
            projectionManager.createScreenCaptureIntent()
        }
        mediaProjectionLauncher.launch(intent)
    }
}
