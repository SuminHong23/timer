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
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.initialization.InitializationStatus
import com.hong.timetostretch.databinding.ActivityWorkBinding


class WorkActivity : AppCompatActivity() {
    private lateinit var binding: ActivityWorkBinding
    private var timerRunning: Boolean = false
    private var timeLeftInMillis: Long = 0
    private var totalDuration: Long = 0
    private var breakTime: Long = 0
    private val CHANNEL_ID = "WorkForegroundServiceChannel"

    private val timerUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                timeLeftInMillis = it.getLongExtra("millisUntilFinished", 0L)
                totalDuration = it.getLongExtra("duration", 0L)
                Log.d("WorkActivity", "onReceive: timeLeftInMillis=$timeLeftInMillis, totalDuration=$totalDuration")
                updateTimerUI(timeLeftInMillis)
                updateProgressBar(totalDuration, timeLeftInMillis)

                if (timeLeftInMillis <= 0) {
                    Log.d("WorkActivity", "Timer finished, scheduling alarm")
                    // 추가 작업 필요 시 여기에 작성
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWorkBinding.inflate(layoutInflater)
        setContentView(binding.root)
        createNotificationChannel()

        MobileAds.initialize(this) {
            Log.d("WorkActivity", "MobileAds initialized")
        }

        val adView = findViewById<AdView>(R.id.adViewWork)
        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)
        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                Log.d("WorkActivity", "Ad loaded successfully")
            }

            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.d("WorkActivity", "Failed to load ad: ${adError.message}")
            }
        }

        if (savedInstanceState != null) {
            timeLeftInMillis = savedInstanceState.getLong("TIME_LEFT_IN_MILLIS")
            timerRunning = savedInstanceState.getBoolean("TIMER_RUNNING")
            totalDuration = savedInstanceState.getLong("TOTAL_DURATION")
            breakTime = savedInstanceState.getLong("BREAK_TIME")
            Log.d("WorkActivity", "onCreate: Restoring state, timeLeftInMillis=$timeLeftInMillis, timerRunning=$timerRunning")
        } else {
            val workTime = intent.getLongExtra("WORK_TIME", 0L)
            timeLeftInMillis = intent.getLongExtra("TIME_LEFT_IN_MILLIS", workTime)
            totalDuration = intent.getLongExtra("TOTAL_DURATION", workTime)
            breakTime = intent.getLongExtra("BREAK_TIME", 0L)
            timerRunning = intent.getBooleanExtra("TIMER_RUNNING", false)
            Log.d("WorkActivity", "onCreate: WORK_TIME=$workTime, BREAK_TIME=$breakTime, TIMER_RUNNING=$timerRunning")
        }

        initializeActivity()

        binding.btnDeleteWork.setOnClickListener {
            stopForegroundService()
            goToMainActivity()
        }

        binding.btnPauseWork.setOnClickListener {
            toggleTimer()
        }

        val filter = IntentFilter("UPDATE_TIMER")
        registerReceiver(timerUpdateReceiver, filter, Context.RECEIVER_EXPORTED)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("WorkActivity", "onDestroy")
        unregisterReceiver(timerUpdateReceiver)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)  // 새로운 인텐트를 설정

        val workTime = intent.getLongExtra("WORK_TIME", 0L)
        val savedTimeLeft = intent.getLongExtra("TIME_LEFT_IN_MILLIS", workTime)
        val savedTotalDuration = intent.getLongExtra("TOTAL_DURATION", workTime)
        val savedBreakTime = intent.getLongExtra("BREAK_TIME", 0L)
        val timerRunning = intent.getBooleanExtra("TIMER_RUNNING", false)
        if (workTime > 0) {
            timeLeftInMillis = savedTimeLeft
            totalDuration = savedTotalDuration
            breakTime = savedBreakTime
            this.timerRunning = timerRunning
            Log.d("WorkActivity", "onNewIntent: workTime=$workTime, savedTimeLeft=$savedTimeLeft, savedTotalDuration=$savedTotalDuration, breakTime=$breakTime, timerRunning=$timerRunning")
            initializeActivity()  // 필요한 경우 UI를 다시 초기화
        }
    }

    private fun initializeActivity() {
        Log.d("WorkActivity", "initializeActivity: timeLeftInMillis=$timeLeftInMillis, totalDuration=$totalDuration, timerRunning=$timerRunning, breakTime=$breakTime")
        updateTimerUI(timeLeftInMillis)
        updateProgressBar(totalDuration, timeLeftInMillis)

        if (timerRunning) {
            startForegroundService(timeLeftInMillis, totalDuration, breakTime)
        } else {
            if (timeLeftInMillis > 0) {
                startForegroundService(timeLeftInMillis, totalDuration, breakTime)
            }
        }
    }

    private fun startForegroundService(workTime: Long, duration: Long, breakTime: Long) {
        val serviceIntent = Intent(this, WorkForegroundService::class.java).apply {
            putExtra("WORK_TIME", workTime)
            putExtra("TOTAL_DURATION", duration)
            putExtra("BREAK_TIME", breakTime)
        }
        Log.d("WorkActivity", "startForegroundService: workTime=$workTime, breakTime=$breakTime, totalDuration=$duration")
        ContextCompat.startForegroundService(this, serviceIntent)
        timerRunning = true
        updateButton()
    }

    private fun toggleTimer() {
        if (timerRunning) {
            pauseTimer()
        } else {
            resumeTimer()
        }
    }

    private fun pauseTimer() {
        val serviceIntent = Intent(this, WorkForegroundService::class.java).apply {
            action = "PAUSE_TIMER"
            putExtra("WORK_TIME", timeLeftInMillis)
        }
        Log.d("WorkActivity", "pauseTimer: timeLeftInMillis=$timeLeftInMillis")
        startService(serviceIntent)
        timerRunning = false
        updateButton()
    }

    private fun resumeTimer() {
        startForegroundService(timeLeftInMillis, totalDuration, breakTime)
    }

    private fun stopForegroundService() {
        val serviceIntent = Intent(this, WorkForegroundService::class.java).apply {
            action = "STOP_FOREGROUND_SERVICE"
        }
        Log.d("WorkActivity", "stopForegroundService")
        startService(serviceIntent)
        timerRunning = false
        updateButton()
    }

    private fun updateButton() {
        if (timerRunning) {
            binding.btnPauseWork.text = "Pause"
        } else {
            binding.btnPauseWork.text = "Resume"
        }
    }

    private fun updateTimerUI(millisUntilFinished: Long) {
        runOnUiThread {
            val minutes = (millisUntilFinished / 1000) / 60
            val seconds = (millisUntilFinished / 1000) % 60
            binding.textViewTimer.text = String.format("%02d:%02d", minutes, seconds)
            Log.d("WorkActivity", "updateTimerUI: $minutes:$seconds")
        }
    }

    private fun updateProgressBar(duration: Long, millisUntilFinished: Long) {
        runOnUiThread {
            val progress = ((duration - millisUntilFinished).toDouble() / duration * 100).toInt()
            binding.WorkProgressBar.progress = progress
            Log.d("WorkActivity", "updateProgressBar: $progress%")
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
                val intent = Intent(this@WorkActivity, MainActivity::class.java)
                startActivity(intent)
                finish()
                Log.d("WorkActivity", "goToMainActivity: Navigating to MainActivity")
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
        outState.putLong("BREAK_TIME", breakTime)
        Log.d("WorkActivity", "onSaveInstanceState: timeLeftInMillis=$timeLeftInMillis, timerRunning=$timerRunning, totalDuration=$totalDuration, breakTime=$breakTime")
    }

    override fun onBackPressed() {
        super.onBackPressed()
        moveTaskToBack(true)
        Log.d("WorkActivity", "onBackPressed: Moving task to back")
    }
}