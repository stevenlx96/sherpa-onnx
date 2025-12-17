package com.example.demo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.streamingasr.SherpaAsrManager

/**
 * AAR 测试 Demo
 *
 * 测试 SherpaAsrManager 的基本功能：
 * - KWS 唤醒检测
 * - VAD 智能断句
 * - 实时识别
 * - 句子完成回调
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "AARDemo"
    }

    // UI 组件
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvResult: TextView
    private lateinit var scrollView: ScrollView

    // SherpaAsrManager（来自 AAR）
    private lateinit var asr: SherpaAsrManager

    // 权限请求
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            initializeAsr()
        } else {
            updateStatus("❌ 需要录音权限才能使用")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        checkPermission()
    }

    private fun initViews() {
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        tvStatus = findViewById(R.id.tvStatus)
        tvResult = findViewById(R.id.tvResult)
        scrollView = findViewById(R.id.scrollView)

        btnStart.setOnClickListener {
            asr.startListening()
        }

        btnStop.setOnClickListener {
            asr.stopListening()
            updateStatus("✋ 已停止监听")
        }

        // 初始禁用按钮
        btnStart.isEnabled = false
        btnStop.isEnabled = false
    }

    private fun checkPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                initializeAsr()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun initializeAsr() {
        // 创建 SherpaAsrManager（来自 AAR）
        asr = SherpaAsrManager(this)

        // 自定义 VAD 参数（可选）
        asr.vadConfig = SherpaAsrManager.VadConfig(
            threshold = 0.5F,
            minSilenceDuration = 1.0F,  // 静音 1 秒算句子结束
            minSpeechDuration = 0.25F,
            maxSpeechDuration = 10.0F
        )

        // 设置回调
        setupCallbacks()

        // 检查模型
        if (!asr.isModelReady()) {
            updateStatus("""
                ❌ 模型未就绪

                模型路径：${asr.getModelDir().absolutePath}

                请使用 adb 部署模型文件：
                adb push models/ /sdcard/
                adb shell
                su
                cp -r /sdcard/models /data/data/com.example.demo.streamingasr/files/
            """.trimIndent())
            return
        }

        // 启用按钮
        btnStart.isEnabled = true
        btnStop.isEnabled = true

        updateStatus("""
            ✅ AAR 测试 Demo 已就绪

            模型路径：${asr.getModelDir().absolutePath}

            点击"开始监听"测试 AAR 功能
        """.trimIndent())
    }

    private fun setupCallbacks() {
        // 🎤 唤醒词检测
        asr.onWakeWordDetected = { keyword ->
            Log.i(TAG, "🔊 检测到唤醒词: $keyword")
            runOnUiThread {
                updateStatus("🔊 已唤醒！检测到: \"$keyword\"")
            }
        }

        // ✅ 句子完成（VAD 断句触发）
        asr.onSentenceComplete = { text ->
            Log.i(TAG, "✓ 句子完成: $text")
            runOnUiThread {
                // 追加到结果显示
                val currentText = tvResult.text.toString()
                val newText = if (currentText.isEmpty()) {
                    "✓ $text"
                } else {
                    "$currentText\n✓ $text"
                }
                tvResult.text = newText
                scrollToBottom()

                // 🎯 这里可以发送给 LLM
                // sendToLLM(text)
                Log.i(TAG, "📤 可以发送给 LLM: $text")
            }
        }

        // 📝 实时部分结果
        asr.onPartialResult = { text ->
            runOnUiThread {
                // 实时更新结果
                val lines = tvResult.text.toString().lines().toMutableList()

                // 替换最后一行为当前实时结果
                if (lines.isNotEmpty() && !lines.last().startsWith("✓")) {
                    lines[lines.lastIndex] = "⏳ $text"
                } else {
                    lines.add("⏳ $text")
                }

                tvResult.text = lines.joinToString("\n")
                scrollToBottom()
            }
        }

        // 🔄 状态变化
        asr.onStateChanged = { state ->
            runOnUiThread {
                when (state) {
                    SherpaAsrManager.State.STANDBY -> {
                        updateStatus("⏸️ 待机中，等待唤醒词...")
                        btnStart.text = "监听中..."
                    }
                    SherpaAsrManager.State.ACTIVE -> {
                        updateStatus("🎙️ 正在识别...")
                        btnStart.text = "识别中..."
                    }
                }
            }
        }

        // ❌ 错误处理
        asr.onError = { error ->
            Log.e(TAG, "错误: $error")
            runOnUiThread {
                updateStatus("❌ 错误: $error")
            }
        }
    }

    private fun updateStatus(status: String) {
        tvStatus.text = status
    }

    private fun scrollToBottom() {
        scrollView.post {
            scrollView.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::asr.isInitialized) {
            asr.release()
        }
    }
}
