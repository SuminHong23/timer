package com.hong.timetostretch


import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.hong.timetostretch.databinding.ActivityMainBinding
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.AdRequest
import com.hong.timetostretch.databinding.CustomExitDialogBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var workTime: Int = 0 // workTime in minutes
    private var breakTime: Int = 0 // breakTime in seconds

    companion object {
        private const val REQUEST_PERMISSIONS = 1
        private const val REQUEST_IGNORE_BATTERY_OPTIMIZATIONS = 2
        private const val REQUEST_SYSTEM_ALERT_WINDOW_PERMISSION = 3
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        checkAndStartRunningActivity()
        MobileAds.initialize(this) {}

        // Request all necessary permissions
        requestAllPermissions()

        loadTimes()

        binding.work30.setOnClickListener { updateWorkTime(30) }
        binding.work45.setOnClickListener { updateWorkTime(45) }
        binding.work1h.setOnClickListener { updateWorkTime(60) }
        binding.workCustom.setOnClickListener { openWorkTimePicker() }

        binding.break5.setOnClickListener { updateBreakTime(5 * 60) }
        binding.break10.setOnClickListener { updateBreakTime(10 * 60) }
        binding.break15.setOnClickListener { updateBreakTime(15 * 60) }
        binding.breakCustom.setOnClickListener { openBreakTimePicker() }

        binding.settingsButton.setOnClickListener {
            val intent = Intent(this, SettingActivity::class.java)
            startActivity(intent)
        }

        binding.btnStart.setOnClickListener {
            // Check if workTime or breakTime is not set
            if (workTime == 0) {
                Toast.makeText(this, "Please set workTime.", Toast.LENGTH_SHORT).show()
            } else if (breakTime == 0) {
                Toast.makeText(this, "Please set breakTime.", Toast.LENGTH_SHORT).show()
            } else {
                // Both times are set, proceed to start WorkActivity
                val intent = Intent(this, WorkActivity::class.java).apply {
                    // Convert workTime to milliseconds
                    putExtra("WORK_TIME", workTime * 60000L)
                    // Convert breakTime to milliseconds
                    putExtra("BREAK_TIME", breakTime * 1000L)
                }

                Log.d("MainActivity", "Starting WorkActivity with WORK_TIME: ${workTime * 60000L}, BREAK_TIME: ${breakTime * 1000L}")
                startActivity(intent)
            }
        }
    }

    private fun requestAllPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), REQUEST_PERMISSIONS)
        } else {
            // 요청할 권한이 없으면 SYSTEM_ALERT_WINDOW 권한 요청으로 넘어감
            checkSystemAlertWindowPermission()
        }
    }

    private fun checkSystemAlertWindowPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                startActivityForResult(intent, REQUEST_SYSTEM_ALERT_WINDOW_PERMISSION)
            } else {
                // Proceed to request battery optimization exclusion
                requestBatteryOptimizationExclusion()
            }
        }
    }

    private fun requestBatteryOptimizationExclusion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:$packageName"))
                startActivityForResult(intent, REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_PERMISSIONS -> {
                permissions.indices.forEach { i ->
                    if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                        if (permissions[i] == Manifest.permission.POST_NOTIFICATIONS) {
                            showPermissionRationale()
                        }
                    }
                }
                // 모든 권한 요청이 처리된 후 SYSTEM_ALERT_WINDOW 권한 요청을 계속 진행
                checkSystemAlertWindowPermission()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_IGNORE_BATTERY_OPTIMIZATIONS -> {
                // Handle battery optimizations permission result
            }
            REQUEST_SYSTEM_ALERT_WINDOW_PERMISSION -> {
                if (!Settings.canDrawOverlays(this)) {
                    // 권한이 부여되지 않은 경우
                    Toast.makeText(this, "Overlay permission is required for this app to function properly.", Toast.LENGTH_SHORT).show()
                } else {
                    // Proceed to request battery optimization exclusion
                    requestBatteryOptimizationExclusion()
                }
            }
        }
    }

    private fun checkAndStartRunningActivity() {
        val sharedPreferences = getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        val isWorkServiceRunning = sharedPreferences.getBoolean("WorkServiceRunning", false)
        val isBreakServiceRunning = sharedPreferences.getBoolean("BreakServiceRunning", false)
        val workTime = sharedPreferences.getInt("WORK_TIME", 0)
        val breakTime = sharedPreferences.getInt("BREAK_TIME", 0)

        if (isWorkServiceRunning) {
            val intent = Intent(this, WorkActivity::class.java).apply {
                putExtra("WORK_TIME", workTime)
                putExtra("BREAK_TIME", breakTime)
            }
            startActivity(intent)
            finish()
        } else if (isBreakServiceRunning) {
            val intent = Intent(this, BreakActivity::class.java).apply {
                putExtra("WORK_TIME", workTime)
                putExtra("BREAK_TIME", breakTime)
            }
            startActivity(intent)
            finish()
        }
    }

    private fun showPermissionRationale() {
        AlertDialog.Builder(this).apply {
            setTitle("Permission required")
            setMessage("Notification permission is required for this app to function properly. Do you want to enable it?")
            setPositiveButton("OK") { _, _ ->
                ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_PERMISSIONS)
            }
            setNegativeButton("Cancel") { _, _ ->
                showManualPermissionSettingDialog()
            }
            setCancelable(false)
            show()
        }
    }

    private fun showManualPermissionSettingDialog() {
        AlertDialog.Builder(this).apply {
            setTitle("Manual Permission Setting")
            setMessage("Notification permission is required for this app to function properly. You can enable it manually in the app settings.")
            setPositiveButton("Open Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
            setNegativeButton("Cancel") { _, _ ->
                Toast.makeText(this@MainActivity, "Notification permission denied. The app cannot function properly without it.", Toast.LENGTH_LONG).show()
            }
            setCancelable(false)
            show()
        }
    }

    private fun saveTimes() {
        val sharedPreferences = getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putInt("WORK_TIME", workTime)
        editor.putInt("BREAK_TIME", breakTime)
        editor.apply()
    }

    private fun loadTimes() {
        val sharedPreferences = getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        workTime = sharedPreferences.getInt("WORK_TIME", 0) // default to 0 if not found
        breakTime = sharedPreferences.getInt("BREAK_TIME", 0) // default to 0 if not found

        // Update displayed times
        updateWorkTime(workTime)
        updateBreakTime(breakTime)
    }

    private fun updateWorkTime(minutes: Int) {
        val formattedTime = String.format("%02d:%02d", minutes / 60, minutes % 60)
        binding.timeWorkMain.text = formattedTime
        workTime = minutes
        saveTimes()
    }

    private fun updateBreakTime(seconds: Int) {
        val formattedTime = String.format("%02d:%02d", seconds / 60, seconds % 60)
        binding.timeBreakMain.text = formattedTime
        breakTime = seconds
        saveTimes()
    }

    private fun openWorkTimePicker() {
        // Create an instance of the custom time picker dialog fragment
        val customTimePickerDialog = CustomTimePickerDialogFragment().apply {
            listener = { selectedHour, selectedMinute ->
                // Update work time with the selected time converted to total minutes
                updateWorkTime(selectedHour * 60 + selectedMinute)
            }
        }
        customTimePickerDialog.show(supportFragmentManager, "CustomTimePickerDialog")
    }

    private fun openBreakTimePicker() {
        val breakTimePickerDialog = BreakTimePickerDialogFragment().apply {
            listener = { minutes, seconds ->
                updateBreakTime(minutes * 60 + seconds)
                println("Selected break time: $minutes minutes, $seconds seconds")
            }
        }
        breakTimePickerDialog.defaultMinutes = 0
        breakTimePickerDialog.defaultSeconds = 0

        breakTimePickerDialog.show(supportFragmentManager, "BreakTimePickerDialog")
    }

    override fun onBackPressed() {
        // Inflate the custom layout using view binding
        val dialogBinding = CustomExitDialogBinding.inflate(layoutInflater)
        val adView: AdView = dialogBinding.adView

        // Initialize AdMob and load an ad
        MobileAds.initialize(this) {}
        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setCancelable(false)
            .create()

        dialogBinding.btnYes.setOnClickListener {
            dialog.dismiss()
            moveTaskToBack(true)
            finishAndRemoveTask()
            android.os.Process.killProcess(android.os.Process.myPid())
        }

        dialogBinding.btnNo.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }
}
