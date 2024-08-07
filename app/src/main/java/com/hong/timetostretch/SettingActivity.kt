package com.hong.timetostretch

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.hong.timetostretch.databinding.ActivitySettingBinding
import java.util.Timer
import java.util.TimerTask

class SettingActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingBinding
    private lateinit var audioManager: AudioManager
    private lateinit var vibrator: Vibrator
    private var mediaPlayer: MediaPlayer? = null
    private var vibrationTimer: Timer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingBinding.inflate(layoutInflater)
        setContentView(binding.root)


        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator

        // 초기 알람 스트림 볼륨을 설정합니다.
        val initialVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM) * 0.5 // 최대 볼륨의 50%
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, initialVolume.toInt(), 0)

        // 프로그래스바의 기본값은 50으로 유지합니다.
        binding.volumeSeekBar.progress = 50
        binding.vibrationSeekBar.progress = 50

        binding.volumeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                saveSettings()
                updateVolumeTestSound()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                startVolumeTest()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                stopVolumeTest()
            }
        })

        binding.vibrationSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                saveSettings()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                startVibrationTest()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                stopVibrationTest()
            }
        })

        loadSettings()

        // 뒤로 가기 버튼 클릭 시 메인 액티비티로 돌아가기
        binding.backButton.setOnClickListener {
            finish() // 현재 액티비티 종료
        }
        MobileAds.initialize(this) {}

        // Load an ad into the AdView
        val adRequest = AdRequest.Builder().build()
        binding.adViewSetting.loadAd(adRequest)
    }

    private fun startVolumeTest() {
        if (mediaPlayer == null) {
            val soundUri: Uri = Uri.parse("android.resource://com.hong.timetostretch/raw/my_alarm_sound")
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@SettingActivity, soundUri)
                setAudioStreamType(AudioManager.STREAM_ALARM)
                isLooping = true
                prepare()
                start()
            }
        }
    }

    private fun stopVolumeTest() {
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun updateVolumeTestSound() {
        mediaPlayer?.setVolume(binding.volumeSeekBar.progress / 100f, binding.volumeSeekBar.progress / 100f)
    }

    private fun startVibrationTest() {
        val pattern = longArrayOf(0, 100, 1000)
        vibrationTimer = Timer()
        vibrationTimer?.schedule(object : TimerTask() {
            override fun run() {
                if (binding.vibrationSeekBar.progress > 0 && vibrator.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(pattern, -1)
                    }
                }
            }
        }, 0, 1000)
    }

    private fun stopVibrationTest() {
        vibrationTimer?.cancel()
        vibrationTimer = null
        vibrator.cancel()
    }

    private fun saveSettings() {
        val sharedPreferences = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putInt("VOLUME_LEVEL", binding.volumeSeekBar.progress)
        editor.putInt("VIBRATION_INTENSITY", binding.vibrationSeekBar.progress)
        editor.apply()
    }

    private fun loadSettings() {
        val sharedPreferences = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        val savedVolume = sharedPreferences.getInt("VOLUME_LEVEL", 50) // 기본값을 50으로 유지
        val savedVibration = sharedPreferences.getInt("VIBRATION_INTENSITY", 50)

        binding.volumeSeekBar.progress = savedVolume
        binding.vibrationSeekBar.progress = savedVibration
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVolumeTest()
    }
}
