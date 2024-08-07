package com.hong.timetostretch


import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.hong.timetostretch.databinding.ActivityBreakBinding

class BreakActivity : AppCompatActivity() {
    private lateinit var binding: ActivityBreakBinding
    private var timerRunning: Boolean = false
    private var timeLeftInMillis: Long = 0
    private var totalDuration: Long = 0
    private var workTime: Long = 0
    private val CHANNEL_ID = "BreakForegroundServiceChannel"

    private val timerUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                timeLeftInMillis = it.getLongExtra("millisUntilFinished", 0L)
                totalDuration = it.getLongExtra("duration", 0L)
                Log.d("BreakActivity", "onReceive: timeLeftInMillis=$timeLeftInMillis, totalDuration=$totalDuration")
                updateTimerUI(timeLeftInMillis)
                updateProgressBar(totalDuration, timeLeftInMillis)

                if (timeLeftInMillis <= 0) {
                    Log.d("BreakActivity", "Timer finished, scheduling alarm")

                }
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBreakBinding.inflate(layoutInflater)
        setContentView(binding.root)
        createNotificationChannel()



        if (savedInstanceState != null) {
            timeLeftInMillis = savedInstanceState.getLong("TIME_LEFT_IN_MILLIS")
            timerRunning = savedInstanceState.getBoolean("TIMER_RUNNING")
            totalDuration = savedInstanceState.getLong("TOTAL_DURATION")
            workTime = savedInstanceState.getLong("WORK_TIME")
            Log.d("BreakActivity", "onCreate: Restoring state, timeLeftInMillis=$timeLeftInMillis, timerRunning=$timerRunning")
        } else {
            val breakTime = intent.getLongExtra("BREAK_TIME", 0L)
            timeLeftInMillis = intent.getLongExtra("TIME_LEFT_IN_MILLIS", breakTime)
            totalDuration = intent.getLongExtra("TOTAL_DURATION", breakTime)
            workTime = intent.getLongExtra("WORK_TIME", 0L)
            timerRunning = intent.getBooleanExtra("TIMER_RUNNING", false)
            Log.d("BreakActivity", "onCreate: BREAK_TIME=$breakTime, TIME_LEFT_IN_MILLIS=$timeLeftInMillis, TOTAL_DURATION=$totalDuration, TIMER_RUNNING=$timerRunning")
        }

        initializeActivity()

        binding.btnDeleteBreak.setOnClickListener {
            stopForegroundService()
            goToMainActivity()
        }

        binding.btnPauseBreak.setOnClickListener {
            toggleTimer()
        }

        val filter = IntentFilter("UPDATE_BREAK_TIMER")
        registerReceiver(timerUpdateReceiver, filter, Context.RECEIVER_EXPORTED)
        MobileAds.initialize(this) {}

        // Load an ad into the AdView
        val adRequest = AdRequest.Builder().build()
        binding.adViewBreak.loadAd(adRequest)

    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("BreakActivity", "onDestroy")
        unregisterReceiver(timerUpdateReceiver)
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)  // 새로운 인텐트를 설정

        val breakTime = intent.getLongExtra("BREAK_TIME", 0L)
        val savedTimeLeft = intent.getLongExtra("TIME_LEFT_IN_MILLIS", breakTime)
        val savedTotalDuration = intent.getLongExtra("TOTAL_DURATION", breakTime)
        val timerRunning = intent.getBooleanExtra("TIMER_RUNNING", false)
        if (breakTime > 0) {
            timeLeftInMillis = savedTimeLeft
            totalDuration = savedTotalDuration
            workTime = intent.getLongExtra("WORK_TIME", 0L)
            this.timerRunning = timerRunning
            Log.d("BreakActivity", "onNewIntent: breakTime=$breakTime, savedTimeLeft=$savedTimeLeft, savedTotalDuration=$savedTotalDuration, workTime=$workTime, timerRunning=$timerRunning")
            initializeActivity()  // 필요한 경우 UI를 다시 초기화
        }
    }

    private fun initializeActivity() {
        Log.d("BreakActivity", "initializeActivity: timeLeftInMillis=$timeLeftInMillis, totalDuration=$totalDuration, timerRunning=$timerRunning, workTime=$workTime")
        updateTimerUI(timeLeftInMillis)
        updateProgressBar(totalDuration, timeLeftInMillis)

        if (timerRunning) {
            startForegroundService(timeLeftInMillis, totalDuration, workTime)
        } else {
            if (timeLeftInMillis > 0) {
                startForegroundService(timeLeftInMillis, totalDuration, workTime)
            }
        }
    }

    private fun startForegroundService(breakTime: Long, duration: Long, workTime: Long) {
        val serviceIntent = Intent(this, BreakForegroundService::class.java).apply {
            putExtra("BREAK_TIME", breakTime)
            putExtra("WORK_TIME", workTime)
            putExtra("TOTAL_DURATION", duration)
        }
        Log.d("BreakActivity", "startForegroundService: breakTime=$breakTime, workTime=$workTime, totalDuration=$duration")
        ContextCompat.startForegroundService(this, serviceIntent)
        timerRunning = true
        updatePauseButton()
    }

    private fun toggleTimer() {
        if (timerRunning) {
            pauseTimer()
        } else {
            resumeTimer()
        }
    }

    private fun pauseTimer() {
        val serviceIntent = Intent(this, BreakForegroundService::class.java).apply {
            action = "PAUSE_TIMER"
            putExtra("BREAK_TIME", timeLeftInMillis)
        }
        Log.d("BreakActivity", "pauseTimer: timeLeftInMillis=$timeLeftInMillis")
        startService(serviceIntent)
        timerRunning = false
        updatePauseButton()
    }

    private fun resumeTimer() {
        startForegroundService(timeLeftInMillis, totalDuration, workTime)
    }

    private fun stopForegroundService() {
        val serviceIntent = Intent(this, BreakForegroundService::class.java).apply {
            action = "STOP_FOREGROUND_SERVICE"
        }
        Log.d("BreakActivity", "stopForegroundService")
        startService(serviceIntent)
        timerRunning = false
        updatePauseButton()
    }

    private fun updatePauseButton() {
        if (timerRunning) {
            binding.btnPauseBreak.text = "Pause"
        } else {
            binding.btnPauseBreak.text = "Resume"
        }
    }

    private fun updateTimerUI(millisUntilFinished: Long) {
        runOnUiThread {
            val minutes = (millisUntilFinished / 1000) / 60
            val seconds = (millisUntilFinished / 1000) % 60
            binding.textViewTimerBreak.text = String.format("%02d:%02d", minutes, seconds)
            Log.d("BreakActivity", "updateTimerUI: $minutes:$seconds")
        }
    }

    private fun updateProgressBar(duration: Long, millisUntilFinished: Long) {
        runOnUiThread {
            val progress = ((duration - millisUntilFinished).toDouble() / duration * 100).toInt()
            binding.breakProgressBar.progress = progress
            Log.d("BreakActivity", "updateProgressBar: $progress%")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Work Timer Service Channel",
                NotificationManager.IMPORTANCE_LOW // 중요도를 낮음으로 설정
            ).apply {
                setSound(null, null) // 알림음 비활성화
                enableVibration(false) // 진동 비활성화
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
            Log.d("WorkActivity", "createNotificationChannel")
        }
    }


    private fun goToMainActivity() {
        AlertDialog.Builder(this).apply {
            setTitle("Confirm")
            setMessage("Do you really want to go back to the main menu?")
            setPositiveButton("OK") { dialog, which ->
                val intent = Intent(this@BreakActivity, MainActivity::class.java)
                startActivity(intent)
                finish()
                Log.d("BreakActivity", "goToMainActivity: Navigating to MainActivity")
            }
            setNegativeButton("Cancel") { dialog, which ->
                toggleTimer() // 타이머를 다시 시작하도록 호출
                dialog.dismiss() // 다이얼로그를 닫음
            }
            show()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong("TIME_LEFT_IN_MILLIS", timeLeftInMillis)
        outState.putBoolean("TIMER_RUNNING", timerRunning)
        outState.putLong("TOTAL_DURATION", totalDuration)
        outState.putLong("WORK_TIME", workTime)
        Log.d("BreakActivity", "onSaveInstanceState: timeLeftInMillis=$timeLeftInMillis, timerRunning=$timerRunning, totalDuration=$totalDuration, workTime=$workTime")
    }

    override fun onBackPressed() {
        super.onBackPressed()
        moveTaskToBack(true)
        Log.d("BreakActivity", "onBackPressed: Moving task to back")
    }
}




