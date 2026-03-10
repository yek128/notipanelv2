package com.example.notificationedge

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

/**
 * 메인 화면 - 권한 설정 및 앱 필터 관리
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }
        scroll.addView(root)
        setContentView(scroll)

        // 타이틀
        root.addView(TextView(this).apply {
            text = "알림 엣지 패널"
            textSize = 24f
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        root.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 24)
        })

        // ──────────────────────────────────────
        // 1단계: 알림 접근 권한
        // ──────────────────────────────────────
        addSectionTitle(root, "1단계: 알림 접근 권한 설정")
        addDescription(root,
            "카카오톡, 문자 등의 알림 내용을 읽으려면 '알림 접근 허용'이 필요합니다.\n" +
            "아래 버튼을 누르면 설정 화면이 열립니다."
        )
        val notifPermBtn = Button(this).apply {
            text = if (isNotificationListenerEnabled()) "✅ 알림 접근 허용됨" else "⚠️ 알림 접근 허용하기"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }
        root.addView(notifPermBtn)

        // ──────────────────────────────────────
        // 2단계: 오버레이 권한 (플로팅 패널용)
        // ──────────────────────────────────────
        addSeparator(root)
        addSectionTitle(root, "2단계: 화면 위에 표시 권한")
        addDescription(root, "플로팅 패널 기능 사용 시 필요합니다.")
        val overlayBtn = Button(this).apply {
            text = if (Settings.canDrawOverlays(this@MainActivity)) "✅ 오버레이 허용됨" else "⚠️ 오버레이 허용하기"
            setOnClickListener {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }
        root.addView(overlayBtn)

        // ──────────────────────────────────────
        // 3단계: 갤럭시 엣지 패널 추가 방법 안내
        // ──────────────────────────────────────
        addSeparator(root)
        addSectionTitle(root, "3단계: 엣지 패널에 추가하기")
        addDescription(root,
            "1. 화면 우측 엣지 핸들을 드래그하여 엣지 패널 열기\n" +
            "2. 패널 하단의 '편집(연필 아이콘)' 탭\n" +
            "3. '패널 다운로드' 또는 + 버튼\n" +
            "4. '알림 엣지 패널' 선택 후 추가\n\n" +
            "* 또는 설정 > 디스플레이 > 엣지 패널 > 패널 에서 추가"
        )

        // ──────────────────────────────────────
        // 플로팅 패널 시작/중지 버튼
        // ──────────────────────────────────────
        addSeparator(root)
        addSectionTitle(root, "플로팅 패널 (엣지 패널 대안)")
        addDescription(root, "화면 우측에 탭 가능한 핸들이 나타나고,\n누르면 알림 내용 목록이 펼쳐집니다.")
        val floatBtn = Button(this).apply {
            text = "▶ 플로팅 패널 시작"
            setOnClickListener {
                if (Settings.canDrawOverlays(this@MainActivity)) {
                    startService(Intent(this@MainActivity, FloatingPanelService::class.java))
                    Toast.makeText(this@MainActivity, "화면 우측에 핸들이 나타납니다", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "먼저 2단계 권한을 허용해주세요", Toast.LENGTH_SHORT).show()
                }
            }
        }
        root.addView(floatBtn)

        // ──────────────────────────────────────
        // 알림 필터 앱 설정
        // ──────────────────────────────────────
        addSeparator(root)
        addSectionTitle(root, "알림 수신 앱 설정")

        val appPackages = mapOf(
            "카카오톡" to "com.kakao.talk",
            "문자(삼성)" to "com.samsung.android.messaging",
            "인스타그램 DM" to "com.instagram.android",
            "페이스북 메신저" to "com.facebook.orca",
            "텔레그램" to "org.telegram.messenger",
            "왓츠앱" to "com.whatsapp",
            "디스코드" to "com.discord"
        )

        val currentFilter = NotificationStore.getFilterPackages(this)

        appPackages.forEach { (name, pkg) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 8, 0, 8)
            }
            val label = TextView(this).apply {
                text = name
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            }
            val switch = Switch(this).apply {
                isChecked = pkg in currentFilter
                setOnCheckedChangeListener { _, checked ->
                    val filter = NotificationStore.getFilterPackages(this@MainActivity).toMutableSet()
                    if (checked) filter.add(pkg) else filter.remove(pkg)
                    NotificationStore.setFilterPackages(this@MainActivity, filter)
                }
            }
            row.addView(label)
            row.addView(switch)
            root.addView(row)
        }

        addSeparator(root)

        // 현재 저장된 알림 수 표시
        val count = NotificationStore.getNotifications(this).size
        root.addView(TextView(this).apply {
            text = "현재 저장된 알림: ${count}개"
            setTextColor(0xFF666666.toInt())
            textSize = 12f
        })
    }

    override fun onResume() {
        super.onResume()
        // 권한 상태 새로고침
        recreate()
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val enabledListeners = Settings.Secure.getString(
            contentResolver, "enabled_notification_listeners"
        ) ?: return false
        return enabledListeners.contains(ComponentName(this, NotificationListener::class.java).flattenToString())
    }

    private fun addSectionTitle(parent: LinearLayout, text: String) {
        parent.addView(TextView(this).apply {
            this.text = text
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 16, 0, 8)
        })
    }

    private fun addDescription(parent: LinearLayout, text: String) {
        parent.addView(TextView(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(0xFF666666.toInt())
            setPadding(0, 0, 0, 8)
        })
    }

    private fun addSeparator(parent: LinearLayout) {
        parent.addView(View(this).apply {
            setBackgroundColor(0xFFEEEEEE.toInt())
            layoutParams = LinearLayout.LayoutParams(-1, 2).apply {
                topMargin = 24
                bottomMargin = 8
            }
        })
    }
}
