package com.focus.bilimonitor.util

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object UsageHelper {

    val BILI_PACKAGES = setOf(
        "tv.danmaku.bili",       // 哔哩哔哩国内版
        "com.bilibili.app.in",   // 哔哩哔哩国际版
        "tv.danmaku.bilibilihd"  // 哔哩哔哩HD
    )

    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * 通过 UsageEvents 精确计算指定日期（默认今天 00:00 至当前）的 B站前台使用总时长（毫秒），
     * 与系统“应用使用详情”统计逻辑保持一致，避免 INTERVAL_DAILY 跨天桶延迟问题。
     */
    fun getBiliUsageMillisForDay(context: Context, daysAgo: Int = 0): Long {
        if (!hasUsageStatsPermission(context)) return 0L
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -daysAgo)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startTime = cal.timeInMillis
        val endTime = if (daysAgo == 0) {
            System.currentTimeMillis()
        } else {
            startTime + 24 * 60 * 60 * 1000L - 1
        }

        val events = usm.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()
        val activeStartMap = mutableMapOf<String, Long>()
        var totalMillis = 0L

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            if (pkg !in BILI_PACKAGES) continue

            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    if (!activeStartMap.containsKey(pkg)) {
                        activeStartMap[pkg] = event.timeStamp
                    }
                }
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED -> {
                    val start = activeStartMap.remove(pkg)
                    if (start != null && event.timeStamp > start) {
                        totalMillis += (event.timeStamp - start)
                    }
                }
            }
        }

        if (daysAgo == 0) {
            val now = System.currentTimeMillis()
            for ((_, start) in activeStartMap) {
                if (now > start) {
                    totalMillis += (now - start)
                }
            }
        }

        return totalMillis
    }

    fun getCurrentForegroundPackage(context: Context): String? {
        if (!hasUsageStatsPermission(context)) return null
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - 10_000L, now)
        val event = UsageEvents.Event()
        var lastPkg: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                lastPkg = event.packageName
            }
        }
        return lastPkg
    }

    fun getLast7DaysSummary(context: Context): String {
        val sdf = SimpleDateFormat("MM-dd (E)", Locale.SIMPLIFIED_CHINESE)
        val sb = StringBuilder()
        var weekTotal = 0L
        for (i in 0..6) {
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -i) }
            val label = if (i == 0) "今天" else sdf.format(cal.time)
            val ms = getBiliUsageMillisForDay(context, i)
            weekTotal += ms
            sb.append("• ").append(label).append("：").append(formatDuration(ms))
            if (i < 6) sb.append("\n")
        }
        sb.append("\n\n近7天日均：").append(formatDuration(weekTotal / 7))
        return sb.toString()
    }

    fun formatDuration(millis: Long): String {
        val totalSeconds = (millis / 1000).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return when {
            hours > 0 -> "${hours}小时${minutes}分钟"
            minutes > 0 -> "${minutes}分钟${seconds}秒"
            else -> "${seconds}秒"
        }
    }
}
