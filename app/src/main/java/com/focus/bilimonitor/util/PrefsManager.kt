package com.focus.bilimonitor.util

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PrefsManager(context: Context) {
    private val prefs = context.getSharedPreferences("bili_focus_prefs", Context.MODE_PRIVATE)

    var thresholdHours: Float
        get() = prefs.getFloat("threshold_hours", 1.5f)
        set(value) = prefs.edit().putFloat("threshold_hours", value).apply()

    var monitorEnabled: Boolean
        get() = prefs.getBoolean("monitor_enabled", false)
        set(value) = prefs.edit().putBoolean("monitor_enabled", value).apply()

    var preWarningEnabled: Boolean
        get() = prefs.getBoolean("pre_warning_enabled", true)
        set(value) = prefs.edit().putBoolean("pre_warning_enabled", value).apply()

    var strictLockEnabled: Boolean
        get() = prefs.getBoolean("strict_lock_enabled", true)
        set(value) = prefs.edit().putBoolean("strict_lock_enabled", value).apply()

    /** 当天额外延长的分钟数（每天自动重置） */
    var bonusMinutesToday: Int
        get() {
            val savedDate = prefs.getString("bonus_date", "")
            return if (savedDate == todayKey()) prefs.getInt("bonus_minutes", 0) else 0
        }
        set(value) {
            prefs.edit()
                .putString("bonus_date", todayKey())
                .putInt("bonus_minutes", value)
                .apply()
        }

    var preWarnSentDate: String
        get() = prefs.getString("pre_warn_date", "") ?: ""
        set(value) = prefs.edit().putString("pre_warn_date", value).apply()

    var lastPopupTimestamp: Long
        get() = prefs.getLong("last_popup_ts", 0L)
        set(value) = prefs.edit().putLong("last_popup_ts", value).apply()

    fun effectiveThresholdMillis(): Long {
        val baseMillis = (thresholdHours * 60f * 60_000f).toLong()
        val bonusMillis = bonusMinutesToday * 60_000L
        return baseMillis + bonusMillis
    }

    fun formatThresholdHours(): String {
        val h = thresholdHours
        return if (h % 1f == 0f) h.toInt().toString() else String.format(Locale.US, "%.1f", h)
    }

    fun todayKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
}
