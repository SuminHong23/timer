package com.hong.timetostretch

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.CountDownTimer
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat

class BreakForegroundService : Service() {
    private var timer: CountDownTimer? = null
    private var timeLeftInMillis: Long = 0
    private var workTime: Long = 0
    private var breakTime: Long = 0
    private var totalDuration: Long = 0

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("BreakForegroundService", "onStartCommand called with action: ${intent?.action}")
        if (intent != null) {
            when (intent.action) {
                "PAUSE_TIMER" -> {
                    Log.d("BreakForegroundService", "PAUSE_TIMER action received")
                    pauseTimer()
                }
                "STOP_FOREGROUND_SERVICE" -> {
                    Log.d("BreakForegroundService", "STOP_FOREGROUND_SERVICE action received")
                    stopForegroundService()
                }
                else -> {
                    timeLeftInMillis = intent.getLongExtra("BREAK_TIME", 0L)
                    workTime = intent.getLongExtra("WORK_TIME", 0L)
                    breakTime = intent.getLongExtra("BREAK_TIME", 0L)
                    totalDuration = intent.getLongExtra("TOTAL_DURATION", breakTime)
                    Log.d("BreakForegroundService", "Starting foreground with breakTime: $breakTime, workTime: $workTime, totalDuration: $totalDuration")
                    startForeground(2, buildNotification(timeLeftInMillis, totalDuration))
                    startTimer(timeLeftInMillis, workTime, breakTime)
                    saveServiceState(true)
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        Log.d("BreakForegroundService", "onBind called")
        return null
    }

    private fun startTimer(duration: Long, workTime: Long, breakTime: Long) {
        Log.d("BreakForegroundService", "startTimer called with duration: $duration")
        timer?.cancel()
        timer = object : CountDownTimer(duration, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeftInMillis = millisUntilFinished
                Log.d("BreakForegroundService", "Timer onTick: $millisUntilFinished ms left")
                updateNotification(millisUntilFinished, totalDuration)
                updateUI(millisUntilFinished)
            }

            override fun onFinish() {
                Log.d("BreakForegroundService", "Timer finished")
                timeLeftInMillis = 0
                updateNotification(0, totalDuration)
                saveServiceState(false)

                if (Settings.canDrawOverlays(this@BreakForegroundService)) {
                    Log.d("BreakForegroundService", "Launching AlarmActivity")
                    val alarmIntent = Intent(this@BreakForegroundService, Alarm2Activity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        putExtra("WORK_TIME", workTime)
                        putExtra("BREAK_TIME", breakTime)
                    }
                    startActivity(alarmIntent)
                } else {
                    // Notify the user to grant SYSTEM_ALERT_WINDOW permission
                    val notificationIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                    val pendingIntent = PendingIntent.getActivity(
                        this@BreakForegroundService, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    val notification = NotificationCompat.Builder(this@BreakForegroundService, "BreakForegroundServiceChannel")
                        .setContentTitle("Permission Required")
                        .setContentText("Grant overlay permission to continue.")
                        .setSmallIcon(R.drawable.ic_timer)
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(true)
                        .build()

                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.notify(3, notification)
                }

                stopSelf()
            }
        }.start()
    }

    private fun buildNotification(duration: Long, totalDuration: Long): Notification {
        Log.d("BreakForegroundService", "buildNotification called with duration: $duration")
        val notificationIntent = Intent(this, BreakActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("BREAK_TIME", timeLeftInMillis)
            putExtra("WORK_TIME", workTime)
            putExtra("TOTAL_DURATION", totalDuration)
            putExtra("TIMER_RUNNING", true)  // 타이머가 실행 중임을 알리는 플래그
            putExtra("TIME_LEFT_IN_MILLIS", timeLeftInMillis)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val minutes = (duration / 1000) / 60
        val seconds = (duration / 1000) % 60
        val timeText = String.format("%02d:%02d", minutes, seconds)

        return NotificationCompat.Builder(this, "BreakForegroundServiceChannel")
            .setContentTitle("Break Timer")
            .setContentText("Time left: $timeText")
            .setSmallIcon(R.drawable.ic_timer)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(duration: Long, totalDuration: Long) {
        Log.d("BreakForegroundService", "updateNotification called with duration: $duration")
        val notification = buildNotification(duration, totalDuration)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(2, notification)
    }

    private fun updateUI(millisUntilFinished: Long) {
        Log.d("BreakForegroundService", "updateUI called with millisUntilFinished: $millisUntilFinished")
        val intent = Intent("UPDATE_BREAK_TIMER").apply {
            putExtra("millisUntilFinished", millisUntilFinished)
            putExtra("duration", totalDuration)
        }
        sendBroadcast(intent)
    }

    private fun pauseTimer() {
        Log.d("BreakForegroundService", "pauseTimer called")
        timer?.cancel()
        stopForeground(true)
    }

    private fun stopForegroundService() {
        Log.d("BreakForegroundService", "stopForegroundService called")
        timer?.cancel()
        stopForeground(true)
        stopSelf()
        saveServiceState(false)
    }

    private fun saveServiceState(isRunning: Boolean) {
        Log.d("BreakForegroundService", "saveServiceState called with isRunning: $isRunning")
        val sharedPreferences = getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        sharedPreferences.edit().apply {
            putBoolean("BreakServiceRunning", isRunning)
            apply()
        }
    }
}

