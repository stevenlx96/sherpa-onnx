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
        // 你需要下载模型并放在 assets 目录下
        // 下载地址: https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-melo-tts-zh_en.tar.bz2
        //
        // 解压后，将以下文件放到 app/src/main/assets/vits-melo-tts-zh_en/ 目录:
        //   - model.onnx
        //   - lexicon.txt
        //   - tokens.txt
        //   - date.fst
        //   - number.fst
        //   - phone.fst

        val modelDir = "vits-melo-tts-zh_en"
        val modelName = "model.onnx"
        val lexicon = "lexicon.txt"

        // 检查模型文件是否存在
        try {
            val modelFiles = application.assets.list(modelDir)
            if (modelFiles.isNullOrEmpty()) {
                throw Exception("模型目录 $modelDir 不存在或为空！请下载模型并放到 assets 目录。")
            }
            Log.i(TAG, "找到模型文件: ${modelFiles.joinToString(", ")}")
        } catch (e: Exception) {
            Toast.makeText(
                applicationContext,
                "错误: ${e.message}\n\n请参考 README.md 下载并配置模型文件",
                Toast.LENGTH_LONG
            ).show()
            throw e
        }

        val config = getOfflineTtsConfig(
            modelDir = modelDir,
            modelName = modelName,
            acousticModelName = "",
            vocoder = "",
            voices = "",
            lexicon = lexicon,
            dataDir = "",
            dictDir = "",
            ruleFsts = "",  // 可以添加: "$modelDir/phone.fst,$modelDir/date.fst,$modelDir/number.fst"
            ruleFars = "",
            isKitten = false
        )

        tts = OfflineTts(assetManager = application.assets, config = config)

        val numSpeakers = tts.numSpeakers()
        Log.i(TAG, "TTS 模型说话人数量: $numSpeakers")
    }

    override fun onDestroy() {
        super.onDestroy()
        track.release()
        tts.release()
    }
}
