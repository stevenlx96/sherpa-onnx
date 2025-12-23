package com.example.demo.tts

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.demo.tts.databinding.ActivityMainBinding
import com.k2fsa.sherpa.onnx.tts.TtsManager

private const val TAG = "SherpaTtsDemo"

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var ttsManager: TtsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 设置默认文本
        binding.textInput.setText("你好，这是使用AAR的TTS示例应用。")

        // 初始化 TTS
        initializeTts()

        // 设置按钮点击事件
        binding.btnSpeak.setOnClickListener {
            speakText()
        }

        binding.btnStop.setOnClickListener {
            stopSpeaking()
        }
    }

    private fun initializeTts() {
        try {
            Log.i(TAG, "开始初始化 TTS")
            ttsManager = TtsManager(this)

            // 使用 Matcha 模型
            ttsManager.initialize("matcha-icefall-zh-baker", "matcha")

            val numSpeakers = ttsManager.getNumSpeakers()
            val sampleRate = ttsManager.getSampleRate()

            Log.i(TAG, "TTS 初始化成功 - 说话人数: $numSpeakers, 采样率: $sampleRate")
            Toast.makeText(this, "TTS 初始化成功", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Log.e(TAG, "TTS 初始化失败", e)
            val errorMsg = """
                TTS 初始化失败！

                错误信息: ${e.message}

                请确保模型文件已放置在:
                /data/data/com.example.demo.tts/files/models/tts/matcha-icefall-zh-baker/

                需要的文件:
                - model-steps-3.onnx
                - lexicon.txt
                - tokens.txt
                - dict/ (目录)
            """.trimIndent()

            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
        }
    }

    private fun speakText() {
        try {
            val text = binding.textInput.text.toString()
            if (text.isEmpty()) {
                Toast.makeText(this, "请输入要朗读的文字", Toast.LENGTH_SHORT).show()
                return
            }

            Log.i(TAG, "开始朗读: $text")
            Toast.makeText(this, "开始朗读...", Toast.LENGTH_SHORT).show()

            // 使用 TtsManager 朗读
            ttsManager.speak(
                text = text,
                speed = 0.8f,  // 0.8 倍速
                sid = 0        // 说话人 ID
            )

            Toast.makeText(this, "朗读完成", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Log.e(TAG, "朗读失败", e)
            Toast.makeText(this, "朗读失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopSpeaking() {
        try {
            ttsManager.stop()
            Toast.makeText(this, "已停止朗读", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "停止失败", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            ttsManager.release()
            Log.i(TAG, "TTS 资源已释放")
        } catch (e: Exception) {
            Log.e(TAG, "释放资源失败", e)
        }
    }
}
