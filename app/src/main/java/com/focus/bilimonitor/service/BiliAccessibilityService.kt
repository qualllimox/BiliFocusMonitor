package com.focus.bilimonitor.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityEvent
import com.focus.bilimonitor.ui.ReminderActivity
import com.focus.bilimonitor.util.PrefsManager
import com.focus.bilimonitor.util.UsageHelper

class BiliAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in UsageHelper.BILI_PACKAGES) return

        val prefs = PrefsManager(this)
        if (!prefs.monitorEnabled || !prefs.strictLockEnabled) return

        val usedMillis = UsageHelper.getBiliUsageMillisForDay(this, 0)
        val limitMillis = prefs.effectiveThresholdMillis()
        if (usedMillis >= limitMillis) {
            closeBilibiliAndNotify(this, prefs, usedMillis)
        }
    }

    override fun onInterrupt() {
        // No-op
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) instance = null
    }

    fun forceBackToHome() {
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    companion object {
        @Volatile
        var instance: BiliAccessibilityService? = null

        fun isEnabled(context: Context): Boolean {
            val expected = "${context.packageName}/${BiliAccessibilityService::class.java.name}"
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val splitter = TextUtils.SimpleStringSplitter(':')
            splitter.setString(enabledServices)
            while (splitter.hasNext()) {
                if (splitter.next().equals(expected, ignoreCase = true)) {
                    return true
                }
            }
            return false
        }

        fun closeBilibiliAndNotify(context: Context, prefs: PrefsManager, usedMillis: Long) {
            val now = System.currentTimeMillis()
            if (now - prefs.lastPopupTimestamp < 2000L) return
            prefs.lastPopupTimestamp = now

            instance?.forceBackToHome()

            val intent = Intent(context, ReminderActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(ReminderActivity.EXTRA_THRESHOLD_TEXT, prefs.formatThresholdHours())
                putExtra(ReminderActivity.EXTRA_USED_MILLIS, usedMillis)
            }
            context.startActivity(intent)
        }
    }
}
