package com.hong.timetostretch

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.hong.timetostretch.databinding.ActivityAlarmBinding
class AlarmActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAlarmBinding
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var vibrator: Vibrator
    private lateinit var wakeLock: PowerManager.WakeLock

    companion object {
        private const val REQUEST_SYSTEM_ALERT_WINDOW_PERMISSION = 3
        private const val TAG = "AlarmActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: Starting AlarmActivity")

        // 시스템 오버레이 설정
        if (Settings.canDrawOverlays(this)) {
            Log.d(TAG, "onCreate: Overlay permission granted")
            setOverlayWindow()
        } else {
            Log.d(TAG, "onCreate: Requesting overlay permission")
            requestOverlayPermission()
            return // Exit onCreate to wait for permission result
        }

        binding = ActivityAlarmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set flags to show activity above lock screen and turn screen on
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            Log.d(TAG, "onCreate: Setting flags for Android O_MR1 and above")
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).apply {
                requestDismissKeyguard(this@AlarmActivity, null)
            }
        } else {
            Log.d(TAG, "onCreate: Setting flags for below Android O_MR1")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        // WakeLock 설정
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
            "MyApp::AlarmWakeLock"
        )
        wakeLock.acquire(10 * 60 * 1000L /*10 minutes*/)
        Log.d(TAG, "onCreate: WakeLock acquired")

        // 설정 값을 불러옵니다.
        val sharedPreferences = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        val savedVolume = sharedPreferences.getInt("VOLUME_LEVEL", 50) / 100f
        val savedVibration = sharedPreferences.getInt("VIBRATION_INTENSITY", 50)
        Log.d(TAG, "onCreate: Volume level = $savedVolume, Vibration intensity = $savedVibration")

        // 미디어 플레이어 설정
        val alarmSound: Uri = Uri.parse("android.resource://com.hong.timetostretch/raw/my_alarm_sound")
        mediaPlayer = MediaPlayer().apply {
            setDataSource(this@AlarmActivity, alarmSound)
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            setVolume(savedVolume, savedVolume)
            prepare()
            start()
            isLooping = true
        }
        Log.d(TAG, "onCreate: MediaPlayer started")

        // Audio Focus 요청
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val result = audioManager.requestAudioFocus(null, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Log.d(TAG, "onCreate: Failed to gain audio focus")
        } else {
            Log.d(TAG, "onCreate: Audio focus gained")
        }

        // 진동기 설정
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (vibrator.hasVibrator()) {
            val pattern = longArrayOf(0, savedVibration.toLong() * 2, 1000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, 0)
            }
            Log.d(TAG, "onCreate: Vibrator started with pattern $pattern")
        }

        // 알람 정지 버튼 설정
        binding.stopButton.setOnClickListener {
            Log.d(TAG, "Stop button clicked")
            stopAlarm()
            goToBreakActivity()
        }
    }

    private fun setOverlayWindow() {
        Log.d(TAG, "setOverlayWindow: Setting window overlay flags")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        } else {
            @Suppress("DEPRECATION")
            window.setType(WindowManager.LayoutParams.TYPE_PHONE)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
    }

    private fun requestOverlayPermission() {
        Log.d(TAG, "requestOverlayPermission: Requesting overlay permission")
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        startActivityForResult(intent, REQUEST_SYSTEM_ALERT_WINDOW_PERMISSION)
    }

    private fun stopAlarm() {
        Log.d(TAG, "stopAlarm: Stopping alarm")
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        vibrator.cancel()
        if (wakeLock.isHeld) {
            wakeLock.release()
            Log.d(TAG, "stopAlarm: WakeLock released")
        }
    }

    private fun goToBreakActivity() {
        Log.d(TAG, "goToBreakActivity: Going to BreakActivity")
        val breakTime = intent.getLongExtra("BREAK_TIME", 0L)
        val workTime = intent.getLongExtra("WORK_TIME", 0L)
        val intent = Intent(this, BreakActivity::class.java)
        intent.putExtra("BREAK_TIME", breakTime)
        intent.putExtra("WORK_TIME", workTime)
        startActivity(intent)
        finish()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_SYSTEM_ALERT_WINDOW_PERMISSION) {
            if (Settings.canDrawOverlays(this)) {
                Log.d(TAG, "onActivityResult: Overlay permission granted")
                setOverlayWindow()
                // Retry initialization after permission is granted
                onCreate(null)
            } else {
                Log.d(TAG, "onActivityResult: Overlay permission denied")
                Toast.makeText(this, "Overlay permission is required for the alarm to function properly.", Toast.LENGTH_LONG).show()
                finish() // Close the activity if permission is not granted
            }
        }
    }
    override fun onPause() {
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        stopAlarm()
        goToBreakActivity()
    }
    override fun onDestroy() {
        super.onDestroy()
        stopAlarm()
        goToBreakActivity()

    }

    override fun onBackPressed() {
        super.onBackPressed()
        stopAlarm()
        goToBreakActivity()

    }


}

