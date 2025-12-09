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
    private var shouldCacheInMemory = true  // 是否在内存中缓存

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
     * @param cacheInMemory 是否在内存中缓存音频数据
     */
    fun startRecording(savePcm: Boolean = true, cacheInMemory: Boolean = true): Boolean {
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

            // 保存缓存设置
            shouldCacheInMemory = cacheInMemory

            if (savePcm) {
                // 先清理旧缓存（如果超过限制）
                cleanOldCacheFiles(maxCacheSizeBytes = 500 * 1024 * 1024, keepRecentCount = 1)

                // 创建PCM缓存文件
                val timestamp = System.currentTimeMillis()
                currentPcmFile = File(cacheDir, "audio_${timestamp}.pcm")
                pcmOutputStream = FileOutputStream(currentPcmFile!!)
                Log.i(TAG, "PCM will be saved to: ${currentPcmFile!!.absolutePath}")
            }

            pcmBuffer.clear()
            audioRecord?.startRecording()
            isRecording = true
            Log.i(TAG, "Recording started (savePcm=$savePcm, cacheInMemory=$cacheInMemory)")
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
            val validBuffer = buffer.copyOf(readSize)

            // 只有在需要时才保存到内存缓冲区
            if (shouldCacheInMemory) {
                pcmBuffer.add(validBuffer)
            }

            // 写入PCM文件（如果需要）
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

    /**
     * 清理旧的缓存文件，保留最新的
     * @param maxCacheSizeBytes 最大缓存大小（字节），默认 500MB
     * @param keepRecentCount 保留最近的文件数量，默认 1 个
     */
    fun cleanOldCacheFiles(
        maxCacheSizeBytes: Long = 500 * 1024 * 1024,  // 500 MB
        keepRecentCount: Int = 1
    ) {
        try {
            // 获取所有 PCM 缓存文件
            val cacheFiles = cacheDir.listFiles()?.filter { it.extension == "pcm" } ?: return

            if (cacheFiles.isEmpty()) {
                Log.d(TAG, "No cache files found")
                return
            }

            // 按修改时间排序，最新的在前
            val sortedFiles = cacheFiles.sortedByDescending { it.lastModified() }

            // 计算总大小
            val totalSize = sortedFiles.sumOf { it.length() }

            Log.i(TAG, "Cache directory: ${cacheDir.absolutePath}")
            Log.i(TAG, "Total cache size: ${totalSize / 1024 / 1024} MB (${sortedFiles.size} files)")

            if (totalSize > maxCacheSizeBytes) {
                Log.i(TAG, "Cache size exceeds limit (${maxCacheSizeBytes / 1024 / 1024} MB), cleaning old files...")

                // 保留最新的 keepRecentCount 个，删除其他所有
                val filesToDelete = sortedFiles.drop(keepRecentCount)
                var deletedSize = 0L
                var deletedCount = 0

                filesToDelete.forEach { file ->
                    val fileSize = file.length()
                    if (file.delete()) {
                        deletedSize += fileSize
                        deletedCount++
                        Log.d(TAG, "Deleted old cache file: ${file.name} (${fileSize / 1024 / 1024} MB)")
                    } else {
                        Log.w(TAG, "Failed to delete cache file: ${file.name}")
                    }
                }

                Log.i(TAG, "Cleaned $deletedCount files, freed ${deletedSize / 1024 / 1024} MB")
                Log.i(TAG, "Remaining: ${sortedFiles.take(keepRecentCount).size} files, ${(totalSize - deletedSize) / 1024 / 1024} MB")
            } else {
                Log.d(TAG, "Cache size within limit, no cleanup needed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning cache files", e)
        }
    }

    /**
     * 获取缓存目录的总大小（字节）
     */
    fun getCacheSize(): Long {
        return try {
            cacheDir.listFiles()?.filter { it.extension == "pcm" }?.sumOf { it.length() } ?: 0L
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating cache size", e)
            0L
        }
    }

    /**
     * 删除所有缓存文件
     */
    fun deleteAllCacheFiles() {
        try {
            val cacheFiles = cacheDir.listFiles()?.filter { it.extension == "pcm" } ?: return
            var deletedCount = 0
            var deletedSize = 0L

            cacheFiles.forEach { file ->
                val fileSize = file.length()
                if (file.delete()) {
                    deletedCount++
                    deletedSize += fileSize
                    Log.d(TAG, "Deleted cache file: ${file.name}")
                }
            }

            Log.i(TAG, "Deleted all cache files: $deletedCount files, ${deletedSize / 1024 / 1024} MB")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting cache files", e)
        }
    }
}
