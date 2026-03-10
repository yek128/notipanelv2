package com.example.notificationedge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // 부팅 후 플로팅 패널 자동 시작 (옵션)
            // context.startForegroundService(Intent(context, FloatingPanelService::class.java))
        }
    }
}
