package com.hong.timetostretch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class TimerUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val millisUntilFinished = intent?.getLongExtra("TIME_LEFT", 0L) ?: 0L
        val totalDuration = intent?.getLongExtra("TOTAL_DURATION", 0L) ?: 0L

        // 업데이트 UI를 위한 인텐트
        val updateIntent = Intent("UPDATE_UI")
        updateIntent.putExtra("TIME_LEFT", millisUntilFinished)
        updateIntent.putExtra("TOTAL_DURATION", totalDuration)
        context?.sendBroadcast(updateIntent)
    }
}