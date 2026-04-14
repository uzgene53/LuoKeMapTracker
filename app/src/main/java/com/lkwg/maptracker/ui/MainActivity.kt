package com.lkwg.maptracker.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.lkwg.maptracker.R
import com.lkwg.maptracker.service.ScreenCaptureService
import com.lkwg.maptracker.util.ConfigManager

/**
 * 主界面
 * 权限管理 + 启停控制 + 参数校准 + 文件选择
 */
class MainActivity : AppCompatActivity() {

    private lateinit var projectionManager: MediaProjectionManager

    private lateinit var tvStatus: TextView
    private lateinit var tvMapInfo: TextView
    private lateinit var tvCoords: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var btnSelectMap: Button

    // 校准 SeekBar
    private lateinit var sbX: SeekBar
    private lateinit var sbY: SeekBar
    private lateinit var sbW: SeekBar
    private lateinit var sbH: SeekBar
    private lateinit var tvCalInfo: TextView

    // 广播接收匹配结果和状态
    private val resultReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ScreenCaptureService.ACTION_MATCH_RESULT -> {
                    val x = intent.getDoubleExtra(ScreenCaptureService.EXTRA_POS_X, 0.0)
                    val y = intent.getDoubleExtra(ScreenCaptureService.EXTRA_POS_Y, 0.0)
                    val conf = intent.getFloatExtra(ScreenCaptureService.EXTRA_CONFIDENCE, 0f)
                    tvCoords.text = "📍 坐标: (${x.toInt()}, ${y.toInt()})  置信度: ${(conf * 100).toInt()}%"
                }
                ScreenCaptureService.ACTION_STATUS -> {
                    val msg = intent.getStringExtra(ScreenCaptureService.EXTRA_STATUS_MSG) ?: ""
                    tvStatus.text = "🟡 $msg"
                }
            }
        }
    }

    // MediaProjection 权限
    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            startCaptureService(result.resultCode, result.data!!)
            startFloatingWindow()
            tvStatus.text = "🟢 运行中"
            btnStart.isEnabled = false
            btnStop.isEnabled = true
        } else {
            Toast.makeText(this, "需要屏幕录制权限", Toast.LENGTH_SHORT).show()
        }
    }

    // 悬浮窗权限
    private val overlayLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            requestProjection()
        } else {
            Toast.makeText(this, "需要悬浮窗权限才能显示地图", Toast.LENGTH_SHORT).show()
        }
    }

    // 文件选择
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { handleMapFileSelected(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tv_status)
        tvCoords = findViewById(R.id.tv_coords)
        tvMapInfo = findViewById(R.id.tv_map_info)
        btnStart = findViewById(R.id.btn_start)
        btnStop = findViewById(R.id.btn_stop)
        btnSelectMap = findViewById(R.id.btn_select_map)
        sbX = findViewById(R.id.sb_minimap_x)
        sbY = findViewById(R.id.sb_minimap_y)
        sbW = findViewById(R.id.sb_minimap_w)
        sbH = findViewById(R.id.sb_minimap_h)
        tvCalInfo = findViewById(R.id.tv_calibration_info)

        projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        loadSavedConfig()
        setupCalibration()
        setupButtons()

        // 注册广播
        val filter = android.content.IntentFilter().apply {
            addAction(ScreenCaptureService.ACTION_MATCH_RESULT)
            addAction(ScreenCaptureService.ACTION_STATUS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(resultReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(resultReceiver, filter)
        }
    }

    private fun setupButtons() {
        btnStart.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                overlayLauncher.launch(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName"))
                )
                return@setOnClickListener
            }
            requestProjection()
        }

        btnStop.setOnClickListener {
            stopAll()
            tvStatus.text = "🔴 已停止"
            btnStart.isEnabled = true
            btnStop.isEnabled = false
        }

        btnSelectMap.setOnClickListener {
            filePickerLauncher.launch("image/*")
        }
    }

    private fun requestProjection() {
        projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun startCaptureService(resultCode: Int, data: Intent) {
        val intent = Intent(this, ScreenCaptureService::class.java).apply {
            putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun startFloatingWindow() {
        val intent = Intent(this, FloatingWindowService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopAll() {
        stopService(Intent(this, ScreenCaptureService::class.java))
        stopService(Intent(this, FloatingWindowService::class.java))
    }

    private fun handleMapFileSelected(uri: Uri) {
        try {
            // 复制到外部存储
            val destFile = java.io.File(getExternalFilesDir(null), "map_full.png")
            contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            ConfigManager.setMapFilePath(this, destFile.absolutePath)

            // 获取尺寸信息
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(destFile.absolutePath, opts)
            tvMapInfo.text = "✅ 地图: ${opts.outWidth}x${opts.outHeight}"
            Toast.makeText(this, "大地图已加载，请重新启动追踪", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "加载失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadSavedConfig() {
        val rect = ConfigManager.getMinimapRect(this)
        sbX.progress = rect[0]
        sbY.progress = rect[1]
        sbW.progress = rect[2]
        sbH.progress = rect[3]
        updateCalInfo()

        // 检查地图文件
        val mapFile = java.io.File(getExternalFilesDir(null), "map_full.png")
        if (mapFile.exists()) {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(mapFile.absolutePath, opts)
            tvMapInfo.text = "✅ 地图: ${opts.outWidth}x${opts.outHeight}"
        } else {
            tvMapInfo.text = "⚠️ 未选择大地图"
        }
    }

    private fun setupCalibration() {
        val listener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                saveCalibration()
                updateCalInfo()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        }
        sbX.setOnSeekBarChangeListener(listener)
        sbY.setOnSeekBarChangeListener(listener)
        sbW.setOnSeekBarChangeListener(listener)
        sbH.setOnSeekBarChangeListener(listener)
    }

    private fun saveCalibration() {
        ConfigManager.setMinimapRect(
            this,
            sbX.progress,
            sbY.progress,
            sbW.progress.coerceAtLeast(50),
            sbH.progress.coerceAtLeast(50)
        )
    }

    private fun updateCalInfo() {
        tvCalInfo.text = "小地图区域: (${sbX.progress}, ${sbY.progress}) ${sbW.progress.coerceAtLeast(50)}x${sbH.progress.coerceAtLeast(50)}"
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(resultReceiver) } catch (_: Exception) {}
    }
}
