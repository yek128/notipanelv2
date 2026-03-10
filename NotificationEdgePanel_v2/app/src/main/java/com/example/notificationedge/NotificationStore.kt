package com.example.notificationedge

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 알림 데이터 모델
 */
data class NotificationItem(
    val id: String,           // 고유 ID
    val packageName: String,  // 앱 패키지명 (ex: com.kakao.talk)
    val appName: String,      // 앱 이름 (ex: 카카오톡)
    val title: String,        // 발신자 이름 또는 제목
    val text: String,         // 메시지 내용
    val timestamp: Long,      // 수신 시각
    var isRead: Boolean = false
) {
    fun getTimeString(): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        return when {
            diff < 60_000 -> "방금"
            diff < 3_600_000 -> "${diff / 60_000}분 전"
            diff < 86_400_000 -> "${diff / 3_600_000}시간 전"
            else -> SimpleDateFormat("MM/dd HH:mm", Locale.KOREA).format(Date(timestamp))
        }
    }

    fun getAppIcon(): Int = when (packageName) {
        "com.kakao.talk"             -> R.drawable.ic_kakao
        "com.samsung.android.messaging",
        "com.android.mms"           -> R.drawable.ic_sms
        "com.instagram.android"      -> R.drawable.ic_instagram
        "com.facebook.katana",
        "com.facebook.orca"          -> R.drawable.ic_facebook
        "com.discord"                -> R.drawable.ic_discord
        else                         -> R.drawable.ic_notification
    }
}

/**
 * 알림 저장소 - SharedPreferences 기반
 * 앱 간 데이터 공유를 위해 MODE_MULTI_PROCESS 사용
 */
object NotificationStore {

    private const val PREF_NAME = "notification_store"
    private const val KEY_NOTIFICATIONS = "notifications"
    private const val MAX_NOTIFICATIONS = 50  // 최대 50개 보관

    // 필터할 앱 패키지 목록 (설정에서 변경 가능)
    private const val KEY_FILTER_PACKAGES = "filter_packages"

    private val gson = Gson()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 알림 추가
     */
    fun addNotification(context: Context, item: NotificationItem) {
        val list = getNotifications(context).toMutableList()

        // 같은 앱 + 같은 제목의 최신 알림이 있으면 업데이트 (중복 방지)
        val existingIndex = list.indexOfFirst {
            it.packageName == item.packageName && it.title == item.title
        }
        if (existingIndex >= 0) {
            list.removeAt(existingIndex)
        }

        // 최신이 위로 오도록 맨 앞에 추가
        list.add(0, item)

        // 최대 개수 제한
        val trimmed = if (list.size > MAX_NOTIFICATIONS) list.take(MAX_NOTIFICATIONS) else list

        saveNotifications(context, trimmed)
        broadcastUpdate(context)
    }

    /**
     * 알림 목록 조회 (최신순)
     */
    fun getNotifications(context: Context): List<NotificationItem> {
        val json = getPrefs(context).getString(KEY_NOTIFICATIONS, "[]") ?: "[]"
        val type = object : TypeToken<List<NotificationItem>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 읽음 처리
     */
    fun markAsRead(context: Context, id: String) {
        val list = getNotifications(context).toMutableList()
        val index = list.indexOfFirst { it.id == id }
        if (index >= 0) {
            list[index] = list[index].copy(isRead = true)
            saveNotifications(context, list)
        }
    }

    /**
     * 특정 알림 삭제
     */
    fun removeNotification(context: Context, id: String) {
        val list = getNotifications(context).filter { it.id != id }
        saveNotifications(context, list)
        broadcastUpdate(context)
    }

    /**
     * 전체 삭제
     */
    fun clearAll(context: Context) {
        saveNotifications(context, emptyList())
        broadcastUpdate(context)
    }

    /**
     * 허용 앱 목록 설정
     */
    fun setFilterPackages(context: Context, packages: Set<String>) {
        getPrefs(context).edit()
            .putStringSet(KEY_FILTER_PACKAGES, packages)
            .apply()
    }

    /**
     * 허용 앱 목록 조회
     */
    fun getFilterPackages(context: Context): Set<String> {
        return getPrefs(context).getStringSet(KEY_FILTER_PACKAGES, getDefaultPackages()) ?: getDefaultPackages()
    }

    private fun getDefaultPackages(): Set<String> = setOf(
        "com.kakao.talk",
        "com.samsung.android.messaging",
        "com.android.mms",
        "com.instagram.android",
        "com.facebook.orca",  // 메신저
        "com.discord",
        "org.telegram.messenger",
        "com.whatsapp"
    )

    private fun saveNotifications(context: Context, list: List<NotificationItem>) {
        getPrefs(context).edit()
            .putString(KEY_NOTIFICATIONS, gson.toJson(list))
            .apply()
    }

    private fun broadcastUpdate(context: Context) {
        val intent = android.content.Intent("com.example.notificationedge.UPDATE_PANEL")
        context.sendBroadcast(intent)
    }

    fun getUnreadCount(context: Context): Int =
        getNotifications(context).count { !it.isRead }
}
