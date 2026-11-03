# VoiceToText (안드로이드 실시간 내부 오디오 텍스트 변환 및 번역 앱)

기기에서 재생되는 모든 소리(유튜브, 강의, 팟캐스트, 영상 등)를 실시간으로 텍스트로 변환(STT)하고, 로컬 문서 저장 및 오프라인 온디바이스 번역(ML Kit)을 지원하는 안드로이드 앱입니다.

---

## 📱 주요 기능

1. **실시간 내부 오디오 캡처 & 텍스트 변환 (STT)**
   - Android MediaProjection 및 AudioPlaybackCapture 기반 내부 미디어 오디오 캡처 (16kHz 16-bit Mono PCM).
   - On-Device SpeechRecognizer(SODA) 기반 초저지연 오프라인 음성인식 지원.
   - 다국어 지원: 자동 감지(AUTO), 한국어(ko-KR), 영어(en-US), 일본어(ja-JP) 지원 및 실시간 동적 언어 전환.
   - 문단 분할 및 무손실 오디오 연속성 큐(`audioPreRollQueue`)를 통한 끊김 없는 영구 연속 변환.

2. **온디바이스 오프라인 번역 (Google ML Kit)**
   - 데이터 소모 없이 기기 내 신경망 모델을 통해 영어/일본어/한국어 상호 번역 지원.
   - 인앱 3-Way 뷰어: [원문] | [번역문] | [대조 보기] 지원.

3. **로컬 문서 자동 저장 및 동기화**
   - 변환된 텍스트는 표준 UTF-8 `.txt` 파일로 사용자 접근 가능한 공용 폴더(`내 파일 > 문서 > VoiceToText`)에 자동 저장.
   - Android MediaScanner 연동으로 PC 연결 및 서드파티 파일 탐색기에서 즉시 확인 가능.

4. **변환 이력 및 검색**
   - Room DB 기반 로컬 영구 보관.
   - 기간별 필터(전체/오늘/이번 주/이번 달) 및 전문 검색(Search) 지원.

5. **최신 Material Design 3 & Jetpack Compose UI**
   - 가로(Landscape) 및 세로(Portrait) 화면 회전 완벽 지원.
   - 실시간 오디오 파형(Waveform) 시각화 및 자동 스크롤, 실시간 커서 위치 트래킹.

---

## 🛠 기술 스택

- **OS / Target**: Android 14+ (minSdk: 33, targetSdk: 35)
- **Language**: Kotlin 2.0+ (JVM 21)
- **UI Toolkit**: Jetpack Compose, Material 3
- **Audio Capture**: MediaProjection, AudioPlaybackCapture, AudioRecord
- **Speech-to-Text**: Android SpeechRecognizer, SODA (Speech On Device Assistant)
- **Translation**: Google ML Kit On-Device Translation
- **Database**: Android Jetpack Room DB (SQLite)
- **Architecture**: MVVM + Repository Pattern, StateFlow / Coroutines

---

## 🚀 빌드 및 실행

```bash
# 클린 빌드
./gradlew clean

# 릴리즈 APK 빌드
./gradlew assembleRelease

# 기기 설치
adb install -r app/build/outputs/apk/release/app-release.apk
```
