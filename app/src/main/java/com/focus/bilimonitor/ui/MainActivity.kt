package com.focus.bilimonitor.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.focus.bilimonitor.R
import com.focus.bilimonitor.databinding.ActivityMainBinding
import com.focus.bilimonitor.service.BiliAccessibilityService
import com.focus.bilimonitor.service.MonitorForegroundService
import com.focus.bilimonitor.util.PrefsManager
import com.focus.bilimonitor.util.UsageHelper
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PrefsManager

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && binding.switchMonitor.isChecked) {
            startMonitorService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = PrefsManager(this)

        binding.tvDeviceInfo.text = "当前设备：${Build.MODEL} (Android ${Build.VERSION.RELEASE})"

        setupThresholdControls()
        setupTogglesAndActions()
        setupPermissionButtons()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStates()
        refreshUsageData()
    }

    private fun setupThresholdControls() {
        binding.sliderThreshold.value = prefs.thresholdHours.coerceIn(0.5f, 6.0f)
        updateThresholdLabel()

        binding.sliderThreshold.addOnChangeListener { _, value, _ ->
            prefs.thresholdHours = value
            updateThresholdLabel()
            refreshUsageData()
        }

        binding.btnPreset05.setOnClickListener { setPresetThreshold(0.5f) }
        binding.btnPreset10.setOnClickListener { setPresetThreshold(1.0f) }
        binding.btnPreset15.setOnClickListener { setPresetThreshold(1.5f) }
        binding.btnPreset20.setOnClickListener { setPresetThreshold(2.0f) }
    }

    private fun setPresetThreshold(hours: Float) {
        prefs.thresholdHours = hours
        binding.sliderThreshold.value = hours
        updateThresholdLabel()
        refreshUsageData()
    }

    private fun updateThresholdLabel() {
        val h = prefs.thresholdHours
        val minutes = (h * 60).roundToInt()
        val bonus = prefs.bonusMinutesToday
        val bonusText = if (bonus > 0) " (+延时${bonus}分)" else ""
        binding.tvThresholdValue.text = "${prefs.formatThresholdHours()} 小时 (${minutes}分钟)$bonusText"
    }

    private fun setupTogglesAndActions() {
        binding.switchMonitor.isChecked = prefs.monitorEnabled
        binding.switchPreWarn.isChecked = prefs.preWarningEnabled
        binding.switchStrictLock.isChecked = prefs.strictLockEnabled

        binding.switchMonitor.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!UsageHelper.hasUsageStatsPermission(this)) {
                    binding.switchMonitor.isChecked = false
                    Toast.makeText(this, "请先开启「使用情况访问权限」", Toast.LENGTH_LONG).show()
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    return@setOnCheckedChangeListener
                }
                prefs.monitorEnabled = true
                requestNotificationAndStartService()
            } else {
                prefs.monitorEnabled = false
                MonitorForegroundService.stop(this)
            }
            refreshUsageData()
        }

        binding.switchPreWarn.setOnCheckedChangeListener { _, isChecked ->
            prefs.preWarningEnabled = isChecked
        }

        binding.switchStrictLock.setOnCheckedChangeListener { _, isChecked ->
            prefs.strictLockEnabled = isChecked
        }

        binding.btnRefresh.setOnClickListener {
            refreshUsageData()
            Toast.makeText(this, "已刷新使用时长", Toast.LENGTH_SHORT).show()
        }

        binding.btnTestPopup.setOnClickListener {
            val used = UsageHelper.getBiliUsageMillisForDay(this, 0)
            val intent = Intent(this, ReminderActivity::class.java).apply {
                putExtra(ReminderActivity.EXTRA_THRESHOLD_TEXT, prefs.formatThresholdHours())
                putExtra(ReminderActivity.EXTRA_USED_MILLIS, used)
            }
            startActivity(intent)
        }

        binding.btnExtraTime.setOnClickListener {
            prefs.bonusMinutesToday += 15
            updateThresholdLabel()
            refreshUsageData()
            Toast.makeText(this, "已为今日临时延时 15 分钟", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupPermissionButtons() {
        binding.btnPermUsage.setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        binding.btnPermAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        binding.btnPermOverlay.setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
        binding.btnPermBattery.setOnClickListener {
            val intent = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun requestNotificationAndStartService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        startMonitorService()
    }

    private fun startMonitorService() {
        MonitorForegroundService.start(this)
    }

    private fun refreshPermissionStates() {
        val usageOk = UsageHelper.hasUsageStatsPermission(this)
        val accessOk = BiliAccessibilityService.isEnabled(this)
        val overlayOk = Settings.canDrawOverlays(this)
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        val batteryOk = pm.isIgnoringBatteryOptimizations(packageName)

        binding.btnPermUsage.text =
            if (usageOk) "1. 使用情况访问权限（已开启 ✓）" else "1. 使用情况访问权限（未开启，点击前往）"
        binding.btnPermAccessibility.text =
            if (accessOk) "2. 无障碍服务（已开启 ✓）" else "2. 无障碍服务（未开启，点击前往）"
        binding.btnPermOverlay.text =
            if (overlayOk) "3. 悬浮窗权限（已开启 ✓）" else "3. 悬浮窗权限（未开启，点击前往）"
        binding.btnPermBattery.text =
            if (batteryOk) "4. 忽略电池优化（已开启 ✓）" else "4. 忽略电池优化（未开启，点击前往）"
    }

    private fun refreshUsageData() {
        if (!UsageHelper.hasUsageStatsPermission(this)) {
            binding.tvTodayUsage.text = "未授权读取"
            binding.tvRemaining.text = "请先开启下方第1项「使用情况访问权限」"
            binding.tvWeeklySummary.text = "暂无权限读取应用使用详情"
            binding.progressUsage.progress = 0
            updateStatusBadge(false, false)
            return
        }

        val usedMillis = UsageHelper.getBiliUsageMillisForDay(this, 0)
        val limitMillis = prefs.effectiveThresholdMillis()
        val remainingMillis = (limitMillis - usedMillis).coerceAtLeast(0L)
        val percent = ((usedMillis.toDouble() / limitMillis.coerceAtLeast(1L)) * 100)
            .roundToInt().coerceIn(0, 100)

        binding.tvTodayUsage.text = UsageHelper.formatDuration(usedMillis)
        binding.progressUsage.progress = percent
        binding.tvRemaining.text = if (remainingMillis > 0) {
            "距离阈值还剩：${UsageHelper.formatDuration(remainingMillis)}"
        } else {
            "已达到今日阈值（${prefs.formatThresholdHours()}h），请专心复习！"
        }

        binding.tvWeeklySummary.text = UsageHelper.getLast7DaysSummary(this)
        updateStatusBadge(prefs.monitorEnabled, usedMillis >= limitMillis)
    }

    private fun updateStatusBadge(monitoring: Boolean, exceeded: Boolean) {
        when {
            exceeded -> {
                binding.tvStatusBadge.text = "已超限拦截"
                binding.tvStatusBadge.setTextColor(getColor(R.color.status_danger))
            }
            monitoring -> {
                binding.tvStatusBadge.text = "监控运行中"
                binding.tvStatusBadge.setTextColor(getColor(R.color.status_ok))
            }
            else -> {
                binding.tvStatusBadge.text = "监控未开启"
                binding.tvStatusBadge.setTextColor(getColor(R.color.text_secondary))
            }
        }
    }
}
