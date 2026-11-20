package com.focus.bilimonitor.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.focus.bilimonitor.databinding.ActivityReminderBinding
import com.focus.bilimonitor.util.UsageHelper

class ReminderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReminderBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReminderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setFinishOnTouchOutside(false)

        val thresholdText = intent.getStringExtra(EXTRA_THRESHOLD_TEXT) ?: "1.5"
        val usedMillis = intent.getLongExtra(EXTRA_USED_MILLIS, 0L)

        binding.tvReminderMessage.text = "今日bilibili使用时长已达${thresholdText}h,快去复习！"
        binding.tvDetailUsage.text = "今日累计使用：${UsageHelper.formatDuration(usedMillis)}"

        binding.btnGoStudy.setOnClickListener {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(homeIntent)
            finish()
        }
    }

    companion object {
        const val EXTRA_THRESHOLD_TEXT = "extra_threshold_text"
        const val EXTRA_USED_MILLIS = "extra_used_millis"
    }
}
