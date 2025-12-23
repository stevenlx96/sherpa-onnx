package com.k2fsa.sherpa.onnx.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import com.k2fsa.sherpa.onnx.*
import java.io.File

/**
 * TTS 管理器，提供简洁的 API 用于文本转语音
 */
class TtsManager(private val context: Context) {
    private var tts: OfflineTts? = null
    private var audioTrack: AudioTrack? = null
    private var isSpeaking: Boolean = false

    companion object {
        private const val TAG = "TtsManager"
        private const val SAMPLE_RATE = 22050
    }

    /**
     * 初始化 TTS
     * @param modelDir 模型目录名称（相对于 /data/data/包名/files/models/tts/）
     * @param modelType 模型类型：vits 或 matcha
     */
    fun initialize(modelDir: String, modelType: String = "matcha") {
        val modelBasePath = File(context.filesDir, "models/tts")
        val modelPath = File(modelBasePath, modelDir)

        Log.i(TAG, "模型路径: ${modelPath.absolutePath}")

        when (modelType.lowercase()) {
            "vits" -> initVitsModel(modelPath)
            "matcha" -> initMatchaModel(modelPath)
            else -> throw IllegalArgumentException("不支持的模型类型: $modelType")
        }

        initAudioTrack()
        Log.i(TAG, "TTS 初始化完成")
    }

    private fun initVitsModel(modelPath: File) {
        val acousticModelFile = File(modelPath, "model.onnx")
        val lexiconFile = File(modelPath, "lexicon.txt")
        val tokensFile = File(modelPath, "tokens.txt")

        checkModelFiles(acousticModelFile, lexiconFile, tokensFile)

        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = acousticModelFile.absolutePath,
                    lexicon = lexiconFile.absolutePath,
                    tokens = tokensFile.absolutePath,
                    dataDir = "",
                    noiseScale = 0.667f,
                    noiseScaleW = 0.8f,
                    lengthScale = 1.0f,
                ),
                numThreads = 2,
                debug = true,
                provider = "cpu",
            ),
            ruleFsts = "",
            ruleFars = "",
        )

        tts = OfflineTts(assetManager = null, config = config)
        Log.i(TAG, "VITS 模型加载完成，说话人数量: ${tts?.numSpeakers()}")
    }

    private fun initMatchaModel(modelPath: File) {
        val acousticModelFile = File(modelPath, "model-steps-3.onnx")
        val lexiconFile = File(modelPath, "lexicon.txt")
        val tokensFile = File(modelPath, "tokens.txt")

        checkModelFiles(acousticModelFile, lexiconFile, tokensFile)

        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                matcha = OfflineTtsMatchaModelConfig(
                    acousticModel = acousticModelFile.absolutePath,
                    vocoder = "",
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
        Log.i(TAG, "Matcha 模型加载完成，说话人数量: ${tts?.numSpeakers()}")
    }

    private fun checkModelFiles(vararg files: File) {
        val missingFiles = files.filter { !it.exists() }
        if (missingFiles.isNotEmpty()) {
            throw IllegalStateException("模型文件缺失: ${missingFiles.joinToString { it.absolutePath }}")
        }
    }

    private fun initAudioTrack() {
        val attr = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()

        val bufLength = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )

        audioTrack = AudioTrack(
            attr, format, bufLength, AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )

        // 设置音量为最大
        audioTrack?.setVolume(AudioTrack.getMaxVolume())
        audioTrack?.play()
    }

    /**
     * 朗读文本
     * @param text 要朗读的文本
     * @param speed 语速（0.5-2.0，默认 0.8）
     * @param sid 说话人ID（默认 0）
     * @param callback 音频生成回调（可选），返回 0 继续，返回 1 停止
     */
    fun speak(
        text: String,
        speed: Float = 0.8f,
        sid: Int = 0,
        callback: ((FloatArray) -> Int)? = null
    ) {
        if (tts == null) {
            throw IllegalStateException("TTS 未初始化，请先调用 initialize()")
        }

        if (isSpeaking) {
            Log.w(TAG, "正在朗读中，请先停止")
            return
        }

        isSpeaking = true
        Log.i(TAG, "开始朗读: $text")

        tts?.generateWithCallback(
            text = text,
            sid = sid,
            speed = speed,
            callback = callback ?: ::defaultCallback
        )

        isSpeaking = false
        Log.i(TAG, "朗读完成")
    }

    /**
     * 停止朗读
     */
    fun stop() {
        if (isSpeaking) {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.play()
            isSpeaking = false
            Log.i(TAG, "朗读已停止")
        }
    }

    /**
     * 释放资源
     */
    fun release() {
        stop()
        audioTrack?.release()
        tts?.free()
        audioTrack = null
        tts = null
        Log.i(TAG, "TTS 资源已释放")
    }

    /**
     * 获取说话人数量
     */
    fun getNumSpeakers(): Int {
        return tts?.numSpeakers() ?: 0
    }

    /**
     * 获取采样率
     */
    fun getSampleRate(): Int {
        return tts?.sampleRate() ?: SAMPLE_RATE
    }

    private fun defaultCallback(samples: FloatArray): Int {
        audioTrack?.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
        return 0
    }
}
