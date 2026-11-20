package com.focus.bilimonitor.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.focus.bilimonitor.R
import com.focus.bilimonitor.ui.MainActivity
import com.focus.bilimonitor.util.PrefsManager
import com.focus.bilimonitor.util.UsageHelper

class MonitorForegroundService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: PrefsManager

    private val checkRunnable = object : Runnable {
        override fun run() {
            if (!prefs.monitorEnabled) {
                stopSelf()
                return
            }
            performCheck()
            handler.postDelayed(this, CHECK_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = PrefsManager(this)
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildForegroundNotification("正在监测哔哩哔哩使用时长...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        handler.removeCallbacks(checkRunnable)
        handler.post(checkRunnable)
        return START_STICKY
    }

    private fun performCheck() {
        if (!UsageHelper.hasUsageStatsPermission(this)) return

        val usedMillis = UsageHelper.getBiliUsageMillisForDay(this, 0)
        val limitMillis = prefs.effectiveThresholdMillis()
        val remainingMillis = limitMillis - usedMillis

        val statusText = if (remainingMillis > 0) {
            "今日B站：${UsageHelper.formatDuration(usedMillis)} / 阈值 ${prefs.formatThresholdHours()}h"
        } else {
            "今日B站已达阈值 (${prefs.formatThresholdHours()}h)，已开启拦截"
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildForegroundNotification(statusText))

        if (prefs.preWarningEnabled && remainingMillis in 1..300_000L) {
            val today = prefs.todayKey()
            if (prefs.preWarnSentDate != today) {
                prefs.preWarnSentDate = today
                sendPreWarningNotification(remainingMillis)
            }
        }

        if (usedMillis >= limitMillis) {
            val fgPkg = UsageHelper.getCurrentForegroundPackage(this)
            if (fgPkg != null && fgPkg in UsageHelper.BILI_PACKAGES) {
                BiliAccessibilityService.closeBilibiliAndNotify(this, prefs, usedMillis)
            }
        }
    }

    private fun sendPreWarningNotification(remainingMillis: Long) {
        val mins = (remainingMillis / 60_000L).coerceAtLeast(1)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, CHANNEL_ALERT)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("B站时长即将到达上限")
            .setContentText("距离今日阈值仅剩约 ${mins} 分钟，请注意安排复习时间。")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        nm.notify(ALERT_NOTIFICATION_ID, notification)
    }

    private fun buildForegroundNotification(contentText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_MONITOR)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(contentText)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun createNotificationChannels() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val monitorChannel = NotificationChannel(
            CHANNEL_MONITOR,
            "后台监控服务",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "保持应用在后台实时统计 B站 使用时长"
        }
        val alertChannel = NotificationChannel(
            CHANNEL_ALERT,
            "阈值预警提醒",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "即将达到使用时长阈值时发出通知"
        }
        nm.createNotificationChannel(monitorChannel)
        nm.createNotificationChannel(alertChannel)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(checkRunnable)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_MONITOR = "bili_monitor_service"
        private const val CHANNEL_ALERT = "bili_monitor_alert"
        private const val NOTIFICATION_ID = 1001
        private const val ALERT_NOTIFICATION_ID = 1002
        private const val CHECK_INTERVAL_MS = 3000L

        fun start(context: Context) {
            val intent = Intent(context, MonitorForegroundService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MonitorForegroundService::class.java))
        }
    }
}
