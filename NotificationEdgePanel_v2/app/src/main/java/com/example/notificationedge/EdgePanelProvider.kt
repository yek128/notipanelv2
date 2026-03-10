package com.example.notificationedge

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import android.util.Log

/**
 * Samsung Edge Panel (Cocktail) Provider
 *
 * 삼성 엣지 패널에 통합되는 핵심 컴포넌트.
 * 알림이 업데이트될 때마다 RemoteViews를 통해 엣지 패널 UI를 갱신함.
 *
 * 삼성 기기에서 엣지 패널 핸들을 당기면 이 패널이 나타남.
 */
class EdgePanelProvider : BroadcastReceiver() {

    companion object {
        private const val TAG = "EdgePanelProvider"

        // 엣지 패널 업데이트 트리거
        fun updatePanel(context: Context) {
            val intent = Intent("com.example.notificationedge.UPDATE_PANEL")
            context.sendBroadcast(intent)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive: ${intent.action}")

        when (intent.action) {
            "com.samsung.android.cocktailbar.action.COCKTAIL_UPDATE",
            "com.samsung.android.cocktailbar.action.COCKTAIL_ENABLED",
            "com.example.notificationedge.UPDATE_PANEL" -> {
                updateEdgePanel(context)
            }
        }
    }

    private fun updateEdgePanel(context: Context) {
        try {
            val cocktailIds = getCocktailIds(context)
            if (cocktailIds.isEmpty()) {
                Log.d(TAG, "No cocktail IDs found")
                return
            }

            val notifications = NotificationStore.getNotifications(context)
            val views = buildPanelViews(context, notifications)

            // 삼성 Cocktail Manager에 업데이트
            val cocktailManager = getCocktailManager(context)
            cocktailManager?.let { manager ->
                for (id in cocktailIds) {
                    updateCocktail(manager, id, views)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "updateEdgePanel failed: ${e.message}")
        }
    }

    /**
     * 엣지 패널 RemoteViews 구성
     * 알림 목록을 스크롤 가능한 리스트로 표시
     */
    private fun buildPanelViews(context: Context, notifications: List<NotificationItem>): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.edge_panel)

        if (notifications.isEmpty()) {
            // 알림 없을 때 빈 화면
            views.setViewVisibility(R.id.list_container, android.view.View.GONE)
            views.setViewVisibility(R.id.empty_view, android.view.View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.list_container, android.view.View.VISIBLE)
            views.setViewVisibility(R.id.empty_view, android.view.View.GONE)

            // 최대 20개 표시
            val displayList = notifications.take(20)

            // RemoteViews ListView adapter 설정
            val serviceIntent = Intent(context, NotificationRemoteViewsService::class.java)
            views.setRemoteAdapter(R.id.notification_list, serviceIntent)

            // 아이템 클릭 시 해당 앱 열기
            val clickIntent = Intent(context, NotificationClickReceiver::class.java)
            val clickPending = PendingIntent.getBroadcast(
                context, 0, clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            views.setPendingIntentTemplate(R.id.notification_list, clickPending)
        }

        // 전체 삭제 버튼
        val clearIntent = Intent(context, NotificationClickReceiver::class.java).apply {
            action = NotificationClickReceiver.ACTION_CLEAR_ALL
        }
        val clearPending = PendingIntent.getBroadcast(
            context, 99, clearIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_clear_all, clearPending)

        // 안 읽은 수 배지
        val unreadCount = NotificationStore.getUnreadCount(context)
        if (unreadCount > 0) {
            views.setViewVisibility(R.id.badge_count, android.view.View.VISIBLE)
            views.setTextViewText(R.id.badge_count, if (unreadCount > 99) "99+" else unreadCount.toString())
        } else {
            views.setViewVisibility(R.id.badge_count, android.view.View.GONE)
        }

        return views
    }

    // =============================================
    // Samsung Cocktail API 리플렉션 래퍼
    // SDK 없이도 리플렉션으로 삼성 API 호출 가능
    // =============================================

    private fun getCocktailManager(context: Context): Any? {
        return try {
            val clazz = Class.forName("com.samsung.android.sdk.look.cocktailbar.SlookCocktailManager")
            val method = clazz.getMethod("getInstance", Context::class.java)
            method.invoke(null, context)
        } catch (e: Exception) {
            Log.w(TAG, "CocktailManager not available (non-Samsung device?): ${e.message}")
            null
        }
    }

    private fun getCocktailIds(context: Context): IntArray {
        return try {
            val manager = getCocktailManager(context) ?: return IntArray(0)
            val component = ComponentName(context, EdgePanelProvider::class.java)
            val method = manager.javaClass.getMethod("getCocktailIds", ComponentName::class.java)
            (method.invoke(manager, component) as? IntArray) ?: IntArray(0)
        } catch (e: Exception) {
            IntArray(0)
        }
    }

    private fun updateCocktail(manager: Any, cocktailId: Int, views: RemoteViews) {
        try {
            val method = manager.javaClass.getMethod(
                "updateCocktail",
                ComponentName::class.java,
                Int::class.java,
                RemoteViews::class.java
            )
            // ComponentName은 null로 전달 가능한 오버로드 사용
            val updateMethod = manager.javaClass.getMethod(
                "updateCocktail", Int::class.java, RemoteViews::class.java
            )
            updateMethod.invoke(manager, cocktailId, views)
        } catch (e: Exception) {
            Log.e(TAG, "updateCocktail failed: ${e.message}")
        }
    }
}
