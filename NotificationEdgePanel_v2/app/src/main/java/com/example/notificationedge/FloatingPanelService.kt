package com.example.notificationedge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.*
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.NotificationCompat

/**
 * 플로팅 패널 서비스 (엣지 패널 미지원 기기용 또는 보조 기능)
 *
 * 화면 우측에 작은 핸들을 표시하고, 탭하면 알림 목록 패널이 열림.
 * Samsung Edge Panel API가 없는 기기에서도 동일한 UX 제공.
 */
class FloatingPanelService : Service() {

    private lateinit var windowManager: WindowManager
    private var handleView: View? = null
    private var panelView: View? = null
    private var isPanelOpen = false

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (isPanelOpen) refreshPanel()
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startForegroundService()
        addHandle()
        registerReceiver(updateReceiver,
            IntentFilter("com.example.notificationedge.UPDATE_PANEL"),
            RECEIVER_NOT_EXPORTED
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        removeHandle()
        removePanel()
        unregisterReceiver(updateReceiver)
    }

    // ──────────────────────────────────────────
    // 핸들 (우측 탭 버튼)
    // ──────────────────────────────────────────
    private fun addHandle() {
        val params = WindowManager.LayoutParams(
            dpToPx(28),
            dpToPx(72),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            x = 0
            y = 0
        }

        handleView = View(this).apply {
            setBackgroundColor(0xFF5E35B1.toInt())  // 보라색 핸들
            // 터치 이벤트
            setOnTouchListener(HandleTouchListener(params))
        }

        // 안읽은 뱃지 텍스트뷰
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.bg_handle)
        }
        val badgeText = TextView(this).apply {
            id = R.id.badge_count
            setTextColor(Color.WHITE)
            textSize = 10f
            gravity = Gravity.CENTER
        }
        container.addView(badgeText)
        container.setOnClickListener {
            if (isPanelOpen) closePanel() else openPanel()
        }

        windowManager.addView(container, params)
        handleView = container
        updateBadge()
    }

    private inner class HandleTouchListener(
        private val params: WindowManager.LayoutParams
    ) : View.OnTouchListener {
        private var lastY = 0f
        private var moved = false

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastY = event.rawY
                    moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = event.rawY - lastY
                    if (Math.abs(dy) > 8) {
                        moved = true
                        params.y += dy.toInt()
                        lastY = event.rawY
                        windowManager.updateViewLayout(v, params)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        if (isPanelOpen) closePanel() else openPanel()
                    }
                }
            }
            return true
        }
    }

    private fun removeHandle() {
        handleView?.let {
            try { windowManager.removeView(it) } catch (e: Exception) {}
            handleView = null
        }
    }

    // ──────────────────────────────────────────
    // 알림 패널
    // ──────────────────────────────────────────
    private fun openPanel() {
        if (panelView != null) return
        isPanelOpen = true

        val displayWidth = resources.displayMetrics.widthPixels
        val panelWidth = (displayWidth * 0.75).toInt()

        val params = WindowManager.LayoutParams(
            panelWidth,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END
        }

        val panel = buildPanelLayout()
        windowManager.addView(panel, params)
        panelView = panel
        refreshPanel()
    }

    private fun buildPanelLayout(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xF01A1A2E.toInt())
            setPadding(0, dpToPx(48), 0, dpToPx(16))
        }

        // 헤더
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
        }
        val title = TextView(this).apply {
            text = "알림"
            setTextColor(Color.WHITE)
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        val clearBtn = TextView(this).apply {
            text = "전체삭제"
            setTextColor(0xFF9E9E9E.toInt())
            textSize = 12f
            setOnClickListener {
                NotificationStore.clearAll(this@FloatingPanelService)
                refreshPanel()
            }
        }
        val closeBtn = TextView(this).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(dpToPx(12), 0, 0, 0)
            setOnClickListener { closePanel() }
        }
        header.addView(title)
        header.addView(clearBtn)
        header.addView(closeBtn)
        root.addView(header)

        // 구분선
        root.addView(View(this).apply {
            setBackgroundColor(0xFF333333.toInt())
            layoutParams = LinearLayout.LayoutParams(-1, dpToPx(1))
        })

        // 스크롤 뷰 (알림 목록)
        val scrollView = ScrollView(this).apply {
            id = R.id.panel_scroll_view
        }
        val listContainer = LinearLayout(this).apply {
            id = R.id.panel_notif_container
            orientation = LinearLayout.VERTICAL
        }
        scrollView.addView(listContainer)
        root.addView(scrollView, LinearLayout.LayoutParams(-1, 0, 1f))

        return root
    }

    private fun refreshPanel() {
        val panel = panelView ?: return
        val listContainer = panel.findViewById<LinearLayout>(R.id.panel_notif_container) ?: return

        listContainer.removeAllViews()

        val notifications = NotificationStore.getNotifications(this)

        if (notifications.isEmpty()) {
            val emptyText = TextView(this).apply {
                text = "새 알림 없음"
                setTextColor(0xFF9E9E9E.toInt())
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(0, dpToPx(32), 0, 0)
            }
            listContainer.addView(emptyText, LinearLayout.LayoutParams(-1, -2))
            return
        }

        notifications.forEach { item ->
            val itemView = buildNotificationItemView(item)
            listContainer.addView(itemView)
        }

        updateBadge()
    }

    private fun buildNotificationItemView(item: NotificationItem): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12))
            if (!item.isRead) setBackgroundColor(0x1A5E35B1) else setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener {
                NotificationStore.markAsRead(this@FloatingPanelService, item.id)
                val launchIntent = packageManager.getLaunchIntentForPackage(item.packageName)
                launchIntent?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(it)
                }
                closePanel()
            }
        }

        // 상단: 앱이름 + 시간
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val appNameView = TextView(this).apply {
            text = item.appName
            setTextColor(0xFF9E9E9E.toInt())
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        val timeView = TextView(this).apply {
            text = item.getTimeString()
            setTextColor(0xFF666666.toInt())
            textSize = 11f
        }
        topRow.addView(appNameView)
        topRow.addView(timeView)
        root.addView(topRow)

        // 발신자 이름 (굵게)
        val titleView = TextView(this).apply {
            text = item.title
            setTextColor(if (!item.isRead) Color.WHITE else 0xFFCCCCCC.toInt())
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, dpToPx(2), 0, 0)
        }
        root.addView(titleView)

        // 메시지 내용 (핵심!)
        val textView = TextView(this).apply {
            text = item.text
            setTextColor(0xFFAAAAAA.toInt())
            textSize = 13f
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, dpToPx(2), 0, 0)
        }
        root.addView(textView)

        // 구분선
        val divider = View(this).apply {
            setBackgroundColor(0xFF222222.toInt())
            layoutParams = LinearLayout.LayoutParams(-1, dpToPx(1)).apply {
                topMargin = dpToPx(8)
            }
        }
        root.addView(divider)

        return root
    }

    private fun closePanel() {
        isPanelOpen = false
        removePanel()
        updateBadge()
    }

    private fun removePanel() {
        panelView?.let {
            try { windowManager.removeView(it) } catch (e: Exception) {}
            panelView = null
        }
    }

    private fun updateBadge() {
        val unread = NotificationStore.getUnreadCount(this)
        val badgeText = handleView?.findViewById<TextView>(R.id.badge_count)
        badgeText?.text = if (unread > 0) if (unread > 99) "99+" else unread.toString() else ""
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun startForegroundService() {
        val channelId = "notification_panel_service"
        val channel = NotificationChannel(channelId, "알림 패널", NotificationManager.IMPORTANCE_MIN)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("알림 패널 실행 중")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

        startForeground(1, notification)
    }
}
