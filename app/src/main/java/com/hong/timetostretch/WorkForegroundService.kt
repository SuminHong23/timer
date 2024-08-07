package com.hong.timetostretch

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.CountDownTimer
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat

class WorkForegroundService : Service() {

    private var timer: CountDownTimer? = null
    private var timeLeftInMillis: Long = 0
    private var breakTime: Long = 0
    private var workTime: Long = 0
    private var totalDuration: Long = 0

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("WorkForegroundService", "onStartCommand called with action: ${intent?.action}")
        if (intent != null) {
            when (intent.action) {
                "PAUSE_TIMER" -> {
                    Log.d("WorkForegroundService", "PAUSE_TIMER action received")
                    pauseTimer()
                }
                "STOP_FOREGROUND_SERVICE" -> {
                    Log.d("WorkForegroundService", "STOP_FOREGROUND_SERVICE action received")
                    stopForegroundService()
                }
                else -> {
                    timeLeftInMillis = intent.getLongExtra("WORK_TIME", 0L)
                    workTime = intent.getLongExtra("WORK_TIME", 0L)
                    breakTime = intent.getLongExtra("BREAK_TIME", 0L)
                    totalDuration = intent.getLongExtra("TOTAL_DURATION", workTime)
                    Log.d("WorkForegroundService", "Starting foreground with workTime: $workTime, breakTime: $breakTime, totalDuration: $totalDuration")
                    startForeground(1, buildNotification(timeLeftInMillis, totalDuration))
                    startTimer(timeLeftInMillis, breakTime, workTime)
                    saveServiceState(true)
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        Log.d("WorkForegroundService", "onBind called")
        return null
    }

    private fun startTimer(duration: Long, breakTime: Long, workTime: Long) {
        Log.d("WorkForegroundService", "startTimer called with duration: $duration")
        timer?.cancel()
        timer = object : CountDownTimer(duration, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeftInMillis = millisUntilFinished
                Log.d("WorkForegroundService", "Timer onTick: $millisUntilFinished ms left")
                updateNotification(millisUntilFinished, totalDuration)
                updateUI(millisUntilFinished)
            }

            override fun onFinish() {
                Log.d("WorkForegroundService", "Timer finished")
                timeLeftInMillis = 0
                updateNotification(0, totalDuration)
                saveServiceState(false)

                if (Settings.canDrawOverlays(this@WorkForegroundService)) {
                    Log.d("WorkForegroundService", "Launching AlarmActivity")
                    val alarmIntent = Intent(this@WorkForegroundService, AlarmActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        putExtra("BREAK_TIME", breakTime)
                        putExtra("WORK_TIME", workTime)
                    }
                    startActivity(alarmIntent)
                }

                stopSelf()
            }
        }.start()
    }

    private fun buildNotification(duration: Long, totalDuration: Long): Notification {
        Log.d("WorkForegroundService", "buildNotification called with duration: $duration")
        val notificationIntent = Intent(this, WorkActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("WORK_TIME", timeLeftInMillis)
            putExtra("TOTAL_DURATION", totalDuration)
            putExtra("BREAK_TIME", breakTime)
            putExtra("TIMER_RUNNING", true)  // 타이머가 실행 중임을 알리는 플래그
            putExtra("TIME_LEFT_IN_MILLIS", timeLeftInMillis)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val hours = (duration / (1000 * 60 * 60)) % 24
        val minutes = (duration / (1000 * 60)) % 60
        val seconds = (duration / 1000) % 60
        val timeText = String.format("%02d:%02d:%02d", hours, minutes, seconds)

        return NotificationCompat.Builder(this, "WorkForegroundServiceChannel")
            .setContentTitle("Work Timer")
            .setContentText("Time left: $timeText")
            .setSmallIcon(R.drawable.ic_timer)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(duration: Long, totalDuration: Long) {
        Log.d("WorkForegroundService", "updateNotification called with duration: $duration")
        val notification = buildNotification(duration, totalDuration)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1, notification)
    }

    private fun updateUI(millisUntilFinished: Long) {
        Log.d("WorkForegroundService", "updateUI called with millisUntilFinished: $millisUntilFinished")
        val intent = Intent("UPDATE_TIMER").apply {
            putExtra("millisUntilFinished", millisUntilFinished)
            putExtra("duration", totalDuration)
        }
        sendBroadcast(intent)
    }

    private fun pauseTimer() {
        Log.d("WorkForegroundService", "pauseTimer called")
        timer?.cancel()
        stopForeground(true)
    }

    private fun stopForegroundService() {
        Log.d("WorkForegroundService", "stopForegroundService called")
        timer?.cancel()
        stopForeground(true)
        stopSelf()
        saveServiceState(false)
    }

    private fun saveServiceState(isRunning: Boolean) {
        Log.d("WorkForegroundService", "saveServiceState called with isRunning: $isRunning")
        val sharedPreferences = getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        sharedPreferences.edit().apply {
            putBoolean("WorkServiceRunning", isRunning)
            apply()
        }
    }
}
