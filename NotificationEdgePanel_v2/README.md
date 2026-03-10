# 알림 엣지 패널 (Notification Edge Panel)

카카오톡, 문자 등의 **메시지 내용을 엣지 패널에서 바로 확인**할 수 있는 앱.
Notification Edge 앱과 동일한 기능을 구현한 오픈소스 버전.

---

## 빌드 방법

### 1. 환경 요구사항
- Android Studio Hedgehog (2023.1.1) 이상
- JDK 17
- Android SDK 34

### 2. 프로젝트 열기
```
File → Open → 이 폴더 선택
```

### 3. 빌드 & 설치
```
Run → Run 'app'  (또는 Shift+F10)
```
또는 터미널에서:
```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## 앱 사용 방법

### 권한 설정 (필수)
1. 앱 실행 후 **"알림 접근 허용하기"** 버튼 탭
2. 목록에서 **"알림 엣지 패널"** 찾아서 스위치 ON
3. 경고 팝업에서 **"허용"** 확인

### 엣지 패널 추가 (삼성 갤럭시 전용)
1. 화면 우측 엣지 핸들 드래그
2. 패널 하단 **편집(연필) 아이콘** 탭
3. **+** 버튼 → **"알림 엣지 패널"** 추가

### 플로팅 패널 사용 (모든 안드로이드 기기)
1. 앱 메인화면에서 **"오버레이 허용하기"** 권한 설정
2. **"플로팅 패널 시작"** 버튼 탭
3. 화면 우측에 보라색 핸들이 나타남
4. 핸들 탭 → 알림 내용 목록 펼쳐짐

---

## 주요 기능

| 기능 | 설명 |
|------|------|
| 메시지 내용 표시 | 발신자명 + 메시지 내용 전체 표시 |
| 앱 필터 | 카카오톡, 문자, 인스타, 텔레그램 등 선택 |
| 안읽은 수 뱃지 | 핸들에 안읽은 알림 수 표시 |
| 탭으로 앱 열기 | 알림 탭하면 해당 앱 바로 실행 |
| 전체 삭제 | 패널 상단에서 한번에 지우기 |
| 최대 50개 보관 | 최신 50개 알림 저장 |

---

## 파일 구조

```
app/src/main/java/com/example/notificationedge/
├── MainActivity.kt              - 설정 화면 (권한, 필터)
├── NotificationListener.kt      - 알림 수신 서비스 (핵심)
├── NotificationStore.kt         - 알림 데이터 저장/조회
├── EdgePanelProvider.kt         - 삼성 엣지 패널 연동
├── NotificationRemoteViewsService.kt - 엣지 패널 리스트 어댑터
├── NotificationClickReceiver.kt - 클릭 이벤트 처리
├── FloatingPanelService.kt      - 플로팅 패널 (비삼성 기기용)
└── BootReceiver.kt              - 부팅 후 자동시작
```

---

## 커스터마이징

### 앱 패키지명 추가
`NotificationStore.kt`의 `getDefaultPackages()` 함수에 추가:
```kotlin
private fun getDefaultPackages(): Set<String> = setOf(
    "com.kakao.talk",
    "com.naver.line",   // 라인 추가 예시
    // ...
)
```

### 최대 저장 개수 변경
`NotificationStore.kt`:
```kotlin
private const val MAX_NOTIFICATIONS = 100  // 50 → 100으로 변경
```

### 패널 색상 변경
`edge_panel.xml`의 `android:background` 값 변경
