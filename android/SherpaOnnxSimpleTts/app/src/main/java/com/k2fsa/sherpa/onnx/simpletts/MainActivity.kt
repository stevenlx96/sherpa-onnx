package com.k2fsa.sherpa.onnx.simpletts

import android.content.res.AssetManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream

const val TAG = "SimpleTts"

class MainActivity : AppCompatActivity() {
    private lateinit var tts: OfflineTts
    private lateinit var textInput: EditText
    private lateinit var speakButton: Button
    private lateinit var stopButton: Button
    private var isSpeaking: Boolean = false
    private lateinit var track: AudioTrack

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Log.i(TAG, "开始初始化 TTS")
        initTts()
        Log.i(TAG, "TTS 初始化完成")

        Log.i(TAG, "开始初始化 AudioTrack")
        initAudioTrack()
        Log.i(TAG, "AudioTrack 初始化完成")

        textInput = findViewById(R.id.text_input)
        speakButton = findViewById(R.id.speak_button)
        stopButton = findViewById(R.id.stop_button)

        // 设置默认示例文本
        textInput.setText("你好，这是一个简单的中文语音合成示例。")

        speakButton.setOnClickListener { onClickSpeak() }
        stopButton.setOnClickListener { onClickStop() }
    }

    private fun initAudioTrack() {
        val sampleRate = tts.sampleRate()
        val bufLength = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )
        Log.i(TAG, "采样率: $sampleRate, 缓冲区大小: $bufLength")

        val attr = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build()

        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setSampleRate(sampleRate)
            .build()

        track = AudioTrack(
            attr, format, bufLength, AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        track.play()
    }

    // 这个函数由 C++ 代码回调
    private fun callback(samples: FloatArray): Int {
        if (isSpeaking) {
            track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
            return 1
        } else {
            track.stop()
            return 0
        }
    }

    private fun onClickSpeak() {
        val textStr = textInput.text.toString().trim()
        if (textStr.isBlank() || textStr.isEmpty()) {
            Toast.makeText(applicationContext, "请输入要朗读的文字！", Toast.LENGTH_SHORT).show()
            return
        }

        track.pause()
        track.flush()
        track.play()

        speakButton.isEnabled = false
        stopButton.isEnabled = true
        isSpeaking = true

        Thread {
            try {
                val audio = tts.generateWithCallback(
                    text = textStr,
                    sid = 0,      // 说话人 ID，默认为 0
                    speed = 1.0f, // 语速，1.0 为正常速度
                    callback = this::callback
                )

                runOnUiThread {
                    speakButton.isEnabled = true
                    stopButton.isEnabled = false
                    isSpeaking = false
                    track.stop()
                    Toast.makeText(applicationContext, "朗读完成", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "朗读出错: ${e.message}")
                runOnUiThread {
                    speakButton.isEnabled = true
                    stopButton.isEnabled = false
                    isSpeaking = false
                    track.stop()
                    Toast.makeText(applicationContext, "朗读出错: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun onClickStop() {
        isSpeaking = false
        speakButton.isEnabled = true
        stopButton.isEnabled = false
        track.pause()
        track.flush()
        Toast.makeText(applicationContext, "已停止朗读", Toast.LENGTH_SHORT).show()
    }

    private fun initTts() {
        // 模型配置
        // 这里使用 vits-melo-tts-zh_en 模型，支持中英文混合
        //
        // 模型路径: /data/data/com.k2fsa.sherpa.onnx.simpletts/files/models/tts/vits-melo-tts-zh_en/
        //
        // 下载地址: https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-melo-tts-zh_en.tar.bz2
        //
        // 需要的文件:
        //   - model.onnx
        //   - lexicon.txt
        //   - tokens.txt

        val modelDir = "vits-melo-tts-zh_en"
        val modelName = "model.onnx"
        val lexicon = "lexicon.txt"

        // 模型文件路径
        val modelBasePath = File(filesDir, "models/tts")
        val modelPath = File(modelBasePath, modelDir)

        Log.i(TAG, "模型路径: ${modelPath.absolutePath}")

        // 检查模型文件是否存在
        val modelFile = File(modelPath, modelName)
        val lexiconFile = File(modelPath, lexicon)
        val tokensFile = File(modelPath, "tokens.txt")

        if (!modelPath.exists() || !modelFile.exists() || !lexiconFile.exists() || !tokensFile.exists()) {
            val errorMsg = """
                模型文件未找到！

                请将模型放到以下目录：
                ${modelPath.absolutePath}/

                需要的文件：
                - model.onnx
                - lexicon.txt
                - tokens.txt

                下载地址：
                https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-melo-tts-zh_en.tar.bz2

                详细说明请查看 README.md
            """.trimIndent()

            Log.e(TAG, errorMsg)
            Toast.makeText(applicationContext, "模型文件未找到，请查看日志", Toast.LENGTH_LONG).show()
            throw Exception(errorMsg)
        }

        Log.i(TAG, "找到模型文件:")
        Log.i(TAG, "  - model.onnx: ${modelFile.exists()}")
        Log.i(TAG, "  - lexicon.txt: ${lexiconFile.exists()}")
        Log.i(TAG, "  - tokens.txt: ${tokensFile.exists()}")

        // 使用绝对路径配置
        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = modelFile.absolutePath,
                    lexicon = lexiconFile.absolutePath,
                    tokens = tokensFile.absolutePath,
                    dataDir = "",
                ),
                numThreads = 2,
                debug = true,
                provider = "cpu",
            ),
            ruleFsts = "",
            ruleFars = "",
        )

        // 不使用 AssetManager，直接从文件系统加载
        tts = OfflineTts(assetManager = null, config = config)

        val numSpeakers = tts.numSpeakers()
        Log.i(TAG, "TTS 模型说话人数量: $numSpeakers")
    }

    override fun onDestroy() {
        super.onDestroy()
        track.release()
        tts.release()
    }
}
