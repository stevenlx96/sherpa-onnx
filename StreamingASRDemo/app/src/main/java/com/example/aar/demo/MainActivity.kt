package com.example.aar.demo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.streamingasr.AudioRecorder
import com.example.streamingasr.ModelManager
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * StreamingASR AAR Demo
 *
 * 此应用演示如何使用 StreamingASR AAR 包
 * 功能：实时流式语音识别
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "AARDemo"
        const val SAMPLE_RATE = 16000
    }

    // UI 组件
    private lateinit var btnStartStop: Button
    private lateinit var btnClearCache: Button
    private lateinit var tvResult: TextView
    private lateinit var tvStatus: TextView
    private lateinit var scrollView: ScrollView

    // 核心组件（来自 AAR）
    private var recognizer: OnlineRecognizer? = null
    private var stream: OnlineStream? = null
    private var audioRecorder: AudioRecorder? = null
    private lateinit var modelManager: ModelManager

    // 状态
    private var isRecording = false
    private var recognitionJob: Job? = null

    // 权限请求
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            initializeRecognizer()
        } else {
            Toast.makeText(this, "需要录音权限才能使用语音识别", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupListeners()

        // 初始化模型管理器（来自 AAR）
        modelManager = ModelManager(this)

        Log.i(TAG, "StreamingASR AAR Demo Started")
        Log.i(TAG, "Model directory: ${modelManager.getModelDir().absolutePath}")

        // 检查权限
        checkPermission()
    }

    private fun initViews() {
        btnStartStop = findViewById(R.id.btnStartStop)
        btnClearCache = findViewById(R.id.btnClearCache)
        tvResult = findViewById(R.id.tvResult)
        tvStatus = findViewById(R.id.tvStatus)
        scrollView = findViewById(R.id.scrollView)
    }

    private fun setupListeners() {
        btnStartStop.setOnClickListener {
            if (isRecording) {
                stopRecording()
            } else {
                startRecording()
            }
        }

        btnClearCache.setOnClickListener {
            clearCache()
        }
    }

    private fun checkPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                initializeRecognizer()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    /**
     * 初始化识别器
     * 使用 AAR 中的 ModelManager 自动加载模型
     */
    private fun initializeRecognizer() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    updateStatus("正在加载模型...\n使用 StreamingASR AAR")
                }

                // 自动检测并加载模型
                recognizer = modelManager.createOnlineRecognizerAuto()

                withContext(Dispatchers.Main) {
                    if (recognizer != null) {
                        val modelDir = modelManager.getModelDir()
                        val files = modelManager.listModelFiles()
                        updateStatus(
                            "✅ 模型加载成功！\n" +
                            "模型路径: ${modelDir.absolutePath}\n" +
                            "已加载文件: ${files.joinToString(", ")}"
                        )
                        Log.i(TAG, "Recognizer initialized successfully using AAR")
                    } else {
                        val instructions = modelManager.getModelDownloadInstructions()
                        updateStatus("❌ 模型加载失败\n\n$instructions")
                        Log.e(TAG, "Failed to initialize recognizer - model files not found")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    updateStatus("❌ 初始化失败: ${e.message}")
                }
                Log.e(TAG, "Error initializing recognizer", e)
            }
        }
    }

    private fun startRecording() {
        if (recognizer == null) {
            Toast.makeText(this, "识别器未初始化，请先加载模型", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val cacheDir = File(filesDir, "audio_cache")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }

            audioRecorder = AudioRecorder(SAMPLE_RATE, cacheDir)
            stream = recognizer?.createStream()

            if (audioRecorder?.startRecording(savePcm = true) == true) {
                isRecording = true
                btnStartStop.text = "停止识别"
                btnStartStop.setBackgroundColor(getColor(android.R.color.holo_red_light))
                updateStatus("🎙️ 正在录音和识别...")
                tvResult.text = ""
                startRecognitionTask()
            } else {
                Toast.makeText(this, "启动录音失败", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording", e)
            Toast.makeText(this, "启动失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRecording() {
        isRecording = false
        recognitionJob?.cancel()
        audioRecorder?.stopRecording()
        stream?.release()
        stream = null

        btnStartStop.text = "开始识别"
        btnStartStop.setBackgroundColor(getColor(android.R.color.holo_green_light))

        val pcmFile = audioRecorder?.getCurrentPcmFile()
        updateStatus("⏹️ 录音已停止\nPCM文件: ${pcmFile?.absolutePath ?: "无"}")
    }

    private fun startRecognitionTask() {
        recognitionJob = lifecycleScope.launch(Dispatchers.IO) {
            var lastText = ""
            val completedText = StringBuilder()

            try {
                while (isActive && isRecording) {
                    val samples = audioRecorder?.readAudioData()

                    if (samples != null && samples.isNotEmpty()) {
                        stream?.acceptWaveform(samples, SAMPLE_RATE)

                        while (recognizer?.isReady(stream!!) == true) {
                            recognizer?.decode(stream!!)
                        }

                        val result = recognizer?.getResult(stream!!)
                        val currentText = result?.text ?: ""

                        val hasSentenceEnd = currentText.contains(Regex("[。！？.!?]"))
                        val isEndpoint = recognizer?.isEndpoint(stream!!) == true

                        if ((hasSentenceEnd || isEndpoint) && currentText.isNotEmpty()) {
                            if (completedText.isNotEmpty()) {
                                completedText.append("\n")
                            }
                            completedText.append(currentText)

                            withContext(Dispatchers.Main) {
                                tvResult.text = completedText.toString()
                                scrollToBottom()
                            }

                            recognizer?.reset(stream!!)
                            lastText = ""
                        } else if (currentText != lastText) {
                            withContext(Dispatchers.Main) {
                                val displayText = if (completedText.isEmpty()) {
                                    currentText
                                } else {
                                    "$completedText\n$currentText"
                                }
                                tvResult.text = displayText
                                scrollToBottom()
                            }
                            lastText = currentText
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Recognition error", e)
                withContext(Dispatchers.Main) {
                    updateStatus("识别出错: ${e.message}")
                }
            }
        }
    }

    private fun clearCache() {
        audioRecorder?.clearPcmCache()
        tvResult.text = ""
        Toast.makeText(this, "缓存已清除", Toast.LENGTH_SHORT).show()
        updateStatus("缓存已清除")
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
        if (isRecording) {
            stopRecording()
        }
        audioRecorder = null
        recognizer = null
    }
}
