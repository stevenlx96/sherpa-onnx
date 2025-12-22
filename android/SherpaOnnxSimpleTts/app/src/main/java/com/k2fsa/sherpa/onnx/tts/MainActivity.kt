package com.k2fsa.sherpa.onnx.tts

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
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File

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

        textInput = findViewById(R.id.text_input)
        speakButton = findViewById(R.id.speak_button)
        stopButton = findViewById(R.id.stop_button)

        // 设置默认文本
        textInput.setText("你好，这是一个简单的中文语音合成示例。")

        speakButton.setOnClickListener { onClickSpeak() }
        stopButton.setOnClickListener { onClickStop() }

        // 初始化 TTS
        try {
            Log.i(TAG, "开始初始化 TTS")
            initTts()
            Log.i(TAG, "TTS 初始化完成")

            Log.i(TAG, "开始初始化 AudioTrack")
            initAudioTrack()
            Log.i(TAG, "AudioTrack 初始化完成")
        } catch (e: Exception) {
            Log.e(TAG, "初始化失败", e)
            Toast.makeText(this, "初始化失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun initAudioTrack() {
        val sampleRate = tts.sampleRate()
        val bufLength = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )

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

    // C++ 回调函数
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
        if (textStr.isBlank()) {
            Toast.makeText(this, "请输入要朗读的文字！", Toast.LENGTH_SHORT).show()
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
                tts.generateWithCallback(
                    text = textStr,
                    sid = 0,
                    speed = 1.0f,
                    callback = this::callback
                )

                runOnUiThread {
                    speakButton.isEnabled = true
                    stopButton.isEnabled = false
                    isSpeaking = false
                    track.stop()
                    Toast.makeText(this, "朗读完成", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "朗读出错", e)
                runOnUiThread {
                    speakButton.isEnabled = true
                    stopButton.isEnabled = false
                    isSpeaking = false
                    track.stop()
                    Toast.makeText(this, "朗读出错: ${e.message}", Toast.LENGTH_LONG).show()
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
    }

    private fun initTts() {
        // 模型路径: /data/data/com.k2fsa.sherpa.onnx.tts/files/models/tts/vits-melo-tts-zh_en/
        val modelDir = "vits-melo-tts-zh_en"
        val modelBasePath = File(filesDir, "models/tts")
        val modelPath = File(modelBasePath, modelDir)

        Log.i(TAG, "模型路径: ${modelPath.absolutePath}")

        // 检查模型文件
        val modelFile = File(modelPath, "model.onnx")
        val lexiconFile = File(modelPath, "lexicon.txt")
        val tokensFile = File(modelPath, "tokens.txt")

        if (!modelFile.exists() || !lexiconFile.exists() || !tokensFile.exists()) {
            val errorMsg = """
                模型文件未找到！

                请将模型放到: ${modelPath.absolutePath}/

                需要的文件:
                - model.onnx
                - lexicon.txt
                - tokens.txt

                下载地址:
                https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-melo-tts-zh_en.tar.bz2
            """.trimIndent()

            throw Exception(errorMsg)
        }

        // 配置 TTS
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

        tts = OfflineTts(assetManager = null, config = config)

        val numSpeakers = tts.numSpeakers()
        Log.i(TAG, "TTS 说话人数量: $numSpeakers")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::track.isInitialized) {
            track.release()
        }
        if (::tts.isInitialized) {
            tts.release()
        }
    }
}
