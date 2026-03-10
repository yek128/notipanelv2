package com.example.notificationedge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * 엣지 패널에서 알림 클릭 처리
 */
class NotificationClickReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_CLEAR_ALL = "com.example.notificationedge.CLEAR_ALL"
        const val ACTION_OPEN_APP = "com.example.notificationedge.OPEN_APP"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_CLEAR_ALL -> {
                NotificationStore.clearAll(context)
            }
            else -> {
                val packageName = intent.getStringExtra("package_name") ?: return
                val notificationId = intent.getStringExtra("notification_id") ?: ""

                // 읽음 처리
                if (notificationId.isNotEmpty()) {
                    NotificationStore.markAsRead(context, notificationId)
                }

                // 해당 앱 실행
                val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                }

                EdgePanelProvider.updatePanel(context)
            }
        }
    }
}
