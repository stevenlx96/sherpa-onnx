package com.example.streamingasr

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * 音频录制器，支持PCM格式录制和缓存
 */
class AudioRecorder(
    private val sampleRate: Int = 16000,
    private val cacheDir: File
) {
    companion object {
        private const val TAG = "AudioRecorder"
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var currentPcmFile: File? = null
    private var pcmOutputStream: FileOutputStream? = null

    // 用于保存所有录制的PCM数据
    private val pcmBuffer = mutableListOf<ShortArray>()

    val bufferSize: Int = AudioRecord.getMinBufferSize(
        sampleRate,
        CHANNEL_CONFIG,
        AUDIO_FORMAT
    ) * 2

    /**
     * 开始录制
     * @param savePcm 是否保存PCM文件到缓存
     */
    fun startRecording(savePcm: Boolean = true): Boolean {
        if (isRecording) {
            Log.w(TAG, "Already recording")
            return false
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed")
                return false
            }

            if (savePcm) {
                // 创建PCM缓存文件
                val timestamp = System.currentTimeMillis()
                currentPcmFile = File(cacheDir, "audio_${timestamp}.pcm")
                pcmOutputStream = FileOutputStream(currentPcmFile!!)
                Log.i(TAG, "PCM will be saved to: ${currentPcmFile!!.absolutePath}")
            }

            pcmBuffer.clear()
            audioRecord?.startRecording()
            isRecording = true
            Log.i(TAG, "Recording started")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            return false
        }
    }

    /**
     * 读取音频数据
     * @return FloatArray 音频样本数据 (范围: -1.0 to 1.0)
     */
    suspend fun readAudioData(): FloatArray? = withContext(Dispatchers.IO) {
        if (!isRecording || audioRecord == null) {
            return@withContext null
        }

        val buffer = ShortArray(bufferSize)
        val readSize = audioRecord?.read(buffer, 0, buffer.size) ?: 0

        if (readSize > 0) {
            // 保存到内存缓冲区
            val validBuffer = buffer.copyOf(readSize)
            pcmBuffer.add(validBuffer)

            // 写入PCM文件
            try {
                pcmOutputStream?.let { stream ->
                    val byteBuffer = ByteArray(readSize * 2)
                    for (i in 0 until readSize) {
                        val value = validBuffer[i].toInt()
                        byteBuffer[i * 2] = (value and 0xff).toByte()
                        byteBuffer[i * 2 + 1] = ((value shr 8) and 0xff).toByte()
                    }
                    stream.write(byteBuffer)
                }
            } catch (e: IOException) {
                Log.e(TAG, "Failed to write PCM data", e)
            }

            // 转换为FloatArray (归一化到 -1.0 ~ 1.0)
            FloatArray(readSize) { i ->
                buffer[i] / 32768.0f
            }
        } else {
            null
        }
    }

    /**
     * 停止录制
     */
    fun stopRecording() {
        if (!isRecording) {
            return
        }

        isRecording = false

        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null

            pcmOutputStream?.flush()
            pcmOutputStream?.close()
            pcmOutputStream = null

            currentPcmFile?.let {
                Log.i(TAG, "Recording saved to: ${it.absolutePath} (${it.length()} bytes)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
        }
    }

    /**
     * 获取当前录制的PCM文件
     */
    fun getCurrentPcmFile(): File? = currentPcmFile

    /**
     * 获取所有缓存的PCM数据
     */
    fun getAllPcmData(): List<ShortArray> = pcmBuffer.toList()

    /**
     * 清除PCM缓存
     */
    fun clearPcmCache() {
        pcmBuffer.clear()
    }
}
