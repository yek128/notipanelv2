package com.example.notificationedge

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.RemoteViews
import android.widget.RemoteViewsService

/**
 * 엣지 패널 ListView용 RemoteViews Factory
 * 각 알림 아이템을 개별 RemoteViews로 생성
 */
class NotificationRemoteViewsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return NotificationViewsFactory(applicationContext)
    }
}

class NotificationViewsFactory(
    private val context: android.content.Context
) : RemoteViewsService.RemoteViewsFactory {

    private var notifications: List<NotificationItem> = emptyList()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        // 패널 업데이트 시 최신 데이터 로드
        notifications = NotificationStore.getNotifications(context)
    }

    override fun onDestroy() {}

    override fun getCount() = notifications.size

    override fun getViewAt(position: Int): RemoteViews {
        if (position >= notifications.size) return getLoadingView()

        val item = notifications[position]
        val views = RemoteViews(context.packageName, R.layout.notification_item)

        // 앱 아이콘
        views.setImageViewResource(R.id.iv_app_icon, item.getAppIcon())

        // 앱 이름
        views.setTextViewText(R.id.tv_app_name, item.appName)

        // 발신자 / 제목
        views.setTextViewText(R.id.tv_title, item.title)

        // 메시지 내용 (핵심!)
        views.setTextViewText(R.id.tv_message, item.text)

        // 시간
        views.setTextViewText(R.id.tv_time, item.getTimeString())

        // 읽음 여부에 따른 배경색
        if (!item.isRead) {
            views.setInt(R.id.item_root, "setBackgroundColor", 0xFF1A1A2E.toInt())
        } else {
            views.setInt(R.id.item_root, "setBackgroundColor", 0xFF0D0D0D.toInt())
        }

        // 클릭 시 해당 앱으로 이동 (fillInIntent 방식)
        val fillIntent = Intent().apply {
            putExtra("package_name", item.packageName)
            putExtra("notification_id", item.id)
        }
        views.setOnClickFillInIntent(R.id.item_root, fillIntent)

        return views
    }

    override fun getLoadingView(): RemoteViews {
        return RemoteViews(context.packageName, R.layout.notification_item_loading)
    }

    override fun getViewTypeCount() = 1

    override fun getItemId(position: Int) = position.toLong()

    override fun hasStableIds() = false
}
