package com.example.demo.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.k2fsa.sherpa.onnx.*
import java.io.File

private const val TAG = "SherpaTtsDemo"

class MainActivity : AppCompatActivity() {
    private lateinit var tts: OfflineTts
    private lateinit var textInput: EditText
    private lateinit var speakButton: Button
    private lateinit var stopButton: Button

    // 滑动条
    private lateinit var seekSpeed: SeekBar
    private lateinit var seekNoise: SeekBar
    private lateinit var seekLength: SeekBar

    private var isSpeaking: Boolean = false
    private lateinit var track: AudioTrack
    private var modelPathString: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. 初始化 UI 控件
        textInput = findViewById(R.id.text_input)
        speakButton = findViewById(R.id.btn_speak)
        stopButton = findViewById(R.id.btn_stop)
        seekSpeed = findViewById(R.id.seek_speed)
        seekNoise = findViewById(R.id.seek_noise)
        seekLength = findViewById(R.id.seek_length)

        textInput.setText("你好，这是一个可以调节参数的语音合成示例。")

        // 2. 核心：必须先确定路径，后面重新初始化才不会报错
        val modelDir = "matcha-icefall-zh-baker"
        modelPathString = File(filesDir, "models/tts/$modelDir").absolutePath

        speakButton.setOnClickListener { onClickSpeak() }
        stopButton.setOnClickListener { onClickStop() }

        // 3. 初次加载
        try {
            updateConfigAndRestartTts() // 直接用这个函数完成初次初始化
            initAudioTrack()
            Toast.makeText(this, "TTS 初始化成功", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "初始化失败", e)
            val errorMsg = """
                TTS 初始化失败！

                错误信息: ${e.message}

                请确保模型文件已放置在:
                $modelPathString/

                需要的文件:
                - model.onnx
                - vocos-22khz-univ.onnx
                - lexicon.txt
                - tokens.txt
                - dict/ (目录)
                - phone.fst, date.fst, number.fst (可选)
            """.trimIndent()

            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
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

        track = AudioTrack(attr, format, bufLength, AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE)
        track.setVolume(AudioTrack.getMaxVolume())
    }

    // 更新配置并重启 TTS（因为 Matcha 的部分参数是在初始化时决定的）
    private fun updateConfigAndRestartTts() {
        val noise = seekNoise.progress / 100f   // 默认 80 -> 0.8f
        val length = seekLength.progress / 100f // 默认 105 -> 1.05f

        val ruleFsts = listOf(
            "$modelPathString/phone.fst",
            "$modelPathString/date.fst",
            "$modelPathString/number.fst"
        ).filter { File(it).exists() }.joinToString(",")

        // 如果旧的已经存在，先释放内存
        if (::tts.isInitialized) {
            tts.release()
        }

        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                matcha = OfflineTtsMatchaModelConfig(
                    acousticModel = "$modelPathString/model.onnx",
                    vocoder = "$modelPathString/vocos-22khz-univ.onnx",
                    lexicon = "$modelPathString/lexicon.txt",
                    tokens = "$modelPathString/tokens.txt",
                    dataDir = modelPathString,
                    noiseScale = noise,
                    lengthScale = length
                ),
                numThreads = 4, // 增加到 4 线程，更快更真实
                debug = true,
                provider = "cpu"
            ),
            ruleFsts = ruleFsts,
            silenceScale = 0.6f
        )
        tts = OfflineTts(assetManager = null, config = config)
        Log.i(TAG, "TTS 配置已更新 - Noise: $noise, Length: $length")
    }

    private fun callback(samples: FloatArray): Int {
        if (isSpeaking) {
            track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
            return 1
        }
        return 0
    }

    private fun onClickSpeak() {
        val textStr = textInput.text.toString().trim()
        if (textStr.isBlank()) {
            Toast.makeText(this, "请输入要朗读的文字", Toast.LENGTH_SHORT).show()
            return
        }

        // 说话前，根据当前滑动条重新加载配置
        try {
            updateConfigAndRestartTts()
        } catch (e: Exception) {
            Log.e(TAG, "更新配置失败", e)
            Toast.makeText(this, "更新配置失败: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }

        val currentSpeed = seekSpeed.progress / 100f // 语速是在生成时动态传入的

        isSpeaking = true
        speakButton.isEnabled = false
        stopButton.isEnabled = true

        track.pause()
        track.flush()
        track.play()

        Thread {
            try {
                Log.i(TAG, "开始朗读: $textStr, 语速: $currentSpeed")
                // 修正后的生成调用
                tts.generateWithCallback(
                    text = textStr,
                    sid = 0,
                    speed = currentSpeed,
                    callback = this::callback
                )

                runOnUiThread {
                    onClickStop()
                    Toast.makeText(this, "朗读完成", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "朗读出错", e)
                runOnUiThread {
                    onClickStop()
                    Toast.makeText(this, "朗读失败: ${e.message}", Toast.LENGTH_LONG).show()
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

    override fun onDestroy() {
        super.onDestroy()
        if (::track.isInitialized) track.release()
        if (::tts.isInitialized) tts.release()
    }
}
