package com.example.notificationedge

import android.app.Notification
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import java.util.UUID

/**
 * 알림 리스너 서비스
 *
 * 카카오톡, 문자, 기타 메시지 앱의 알림이 올 때마다 자동으로 호출됨.
 * 알림 제목(발신자)과 내용(메시지 텍스트)을 추출해서 저장.
 *
 * ※ 설정 > 알림 > 알림 접근 허용에서 이 앱을 활성화해야 작동함.
 */
class NotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "NotificationListener"
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName

        // 필터: 허용된 앱만 처리
        val allowedPackages = NotificationStore.getFilterPackages(applicationContext)
        if (packageName !in allowedPackages) return

        // 시스템/그룹 서머리 알림 무시
        val extras = sbn.notification?.extras ?: return
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        // 알림 내용 추출
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()
            ?: ""

        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString()
            ?: ""

        // 내용이 없으면 무시
        if (title.isBlank() && text.isBlank()) return

        // 앱 이름 가져오기
        val appName = getAppName(packageName)

        Log.d(TAG, "알림 수신: [$appName] $title: $text")

        // 저장
        val item = NotificationItem(
            id = "${packageName}_${sbn.id}_${sbn.postTime}",
            packageName = packageName,
            appName = appName,
            title = title,
            text = text,
            timestamp = sbn.postTime
        )

        NotificationStore.addNotification(applicationContext, item)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // 알림이 시스템에서 제거될 때 (사용자가 스와이프로 지웠을 때 등)
        // 여기서는 저장된 기록은 유지하고 싶으므로 아무것도 안 함
        // 만약 연동하고 싶다면 아래 주석 해제:
        // NotificationStore.removeNotification(applicationContext, "${sbn.packageName}_${sbn.id}_${sbn.postTime}")
    }

    private fun getAppName(packageName: String): String {
        return try {
            val pm = packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            when (packageName) {
                "com.kakao.talk" -> "카카오톡"
                "com.samsung.android.messaging", "com.android.mms" -> "문자"
                else -> packageName.substringAfterLast(".")
            }
        }
    }
}
