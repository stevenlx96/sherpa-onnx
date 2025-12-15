package com.example.streamingasr

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.*
import java.io.File

/**
 * 自定义模型文件配置
 * 允许用户指定自己的模型文件名
 */
data class ModelFiles(
    // Transducer 模型文件
    val encoder: String? = null,
    val decoder: String? = null,
    val joiner: String? = null,

    // Paraformer/其他模型文件
    val model: String? = null,

    // 通用文件
    val tokens: String = "tokens.txt",

    // HomophoneReplacer 文件（可选）
    val lexicon: String? = null,
    val replaceFst: String? = null
)

/**
 * 模型管理类
 * 负责从 /data/data/com.example.streamingasr/files/models 加载模型
 *
 * 支持两种使用方式：
 * 1. 使用预定义的模型类型（自动匹配文件名）
 * 2. 使用自定义的模型文件名
 */
class ModelManager(private val context: Context) {

    companion object {
        private const val TAG = "ModelManager"
        private const val MODEL_DIR_NAME = "models"
        private const val ASR_SUBDIR = "asr"
        private const val KWS_SUBDIR = "kws"
        private const val VAD_SUBDIR = "vad"
    }

    // 模型文件存储在应用内部存储: /data/data/com.example.streamingasr/files/models/
    private val modelDir: File = File(context.filesDir, MODEL_DIR_NAME)
    private val asrDir: File = File(modelDir, ASR_SUBDIR)
    private val kwsDir: File = File(modelDir, KWS_SUBDIR)
    private val vadDir: File = File(modelDir, VAD_SUBDIR)

    init {
        // 确保模型目录存在
        if (!modelDir.exists()) {
            modelDir.mkdirs()
            Log.i(TAG, "Created model directory: ${modelDir.absolutePath}")
        }
        // 创建子目录
        if (!asrDir.exists()) {
            asrDir.mkdirs()
            Log.i(TAG, "Created ASR directory: ${asrDir.absolutePath}")
        }
        if (!kwsDir.exists()) {
            kwsDir.mkdirs()
            Log.i(TAG, "Created KWS directory: ${kwsDir.absolutePath}")
        }
        if (!vadDir.exists()) {
            vadDir.mkdirs()
            Log.i(TAG, "Created VAD directory: ${vadDir.absolutePath}")
        }
    }

    /**
     * 获取模型目录路径
     */
    fun getModelDir(): File = modelDir

    /**
     * 检查模型是否存在
     */
    private fun checkModelExists(modelType: ModelType): Boolean {
        return when (modelType) {
            ModelType.ZIPFORMER_TRANSDUCER -> {
                val encoder = File(asrDir, "encoder-epoch-99-avg-1.onnx")
                val decoder = File(asrDir, "decoder-epoch-99-avg-1.onnx")
                val joiner = File(asrDir, "joiner-epoch-99-avg-1.onnx")
                val tokens = File(asrDir, "tokens.txt")
                encoder.exists() && decoder.exists() && joiner.exists() && tokens.exists()
            }
            ModelType.PARAFORMER -> {
                val encoder = File(asrDir, "encoder.int8.onnx")
                val decoder = File(asrDir, "decoder.int8.onnx")
                val tokens = File(asrDir, "tokens.txt")
                encoder.exists() && decoder.exists() && tokens.exists()
            }
            ModelType.ZIPFORMER_CTC -> {
                val model = File(asrDir, "model.int8.onnx")
                val tokens = File(asrDir, "tokens.txt")
                model.exists() && tokens.exists()
            }
        }
    }

    /**
     * 创建在线识别器（使用预定义的模型类型）
     * @param modelType 模型类型
     * @param numThreads 线程数 (默认为CPU核心数)
     * @return OnlineRecognizer 或 null (如果模型不存在)
     */
    fun createOnlineRecognizer(
        modelType: ModelType = ModelType.ZIPFORMER_TRANSDUCER,
        numThreads: Int = Runtime.getRuntime().availableProcessors()
    ): OnlineRecognizer? {

        if (!checkModelExists(modelType)) {
            Log.e(TAG, "Model files not found in ${modelDir.absolutePath}")
            Log.e(TAG, "Please copy model files to this directory first")
            return null
        }

        Log.i(TAG, "Loading model from: ${modelDir.absolutePath}")
        Log.i(TAG, "Using $numThreads threads")

        val config = createRecognizerConfig(modelType, numThreads)

        return try {
            // assetManager设为null，从文件系统加载
            val recognizer = OnlineRecognizer(
                assetManager = null,
                config = config
            )
            Log.i(TAG, "OnlineRecognizer created successfully")
            recognizer
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create OnlineRecognizer", e)
            null
        }
    }


    /**
     * 创建识别器配置
     */
    private fun createRecognizerConfig(
        modelType: ModelType,
        numThreads: Int
    ): OnlineRecognizerConfig {
        val modelConfig = when (modelType) {
            ModelType.ZIPFORMER_TRANSDUCER -> {
                // 检查是否有 bpe.vocab 文件（双语模型需要）
                val bpeVocabFile = File(asrDir, "bpe.vocab")
                val bpeVocabPath = if (bpeVocabFile.exists()) {
                    bpeVocabFile.absolutePath
                } else {
                    ""
                }

                // 双语模型使用 cjkchar+bpe，纯中文使用 cjkchar
                val modelingUnit = if (bpeVocabPath.isNotEmpty()) "cjkchar+bpe" else "cjkchar"

                Log.i(TAG, "Modeling unit: $modelingUnit")
                if (bpeVocabPath.isNotEmpty()) {
                    Log.i(TAG, "BPE vocab: $bpeVocabPath")
                }

                OnlineModelConfig(
                    transducer = OnlineTransducerModelConfig(
                        encoder = File(asrDir, "encoder-epoch-99-avg-1.onnx").absolutePath,
                        decoder = File(asrDir, "decoder-epoch-99-avg-1.onnx").absolutePath,
                        joiner = File(asrDir, "joiner-epoch-99-avg-1.onnx").absolutePath
                    ),
                    tokens = File(asrDir, "tokens.txt").absolutePath,
                    numThreads = numThreads,
                    provider = "cpu",
                    debug = false,
                    modelingUnit = modelingUnit,  // 双语模型: cjkchar+bpe, 纯中文: cjkchar
                    bpeVocab = bpeVocabPath       // BPE词表路径（双语模型需要）
                )
            }
            ModelType.PARAFORMER -> {
                OnlineModelConfig(
                    paraformer = OnlineParaformerModelConfig(
                        encoder = File(asrDir, "encoder.int8.onnx").absolutePath,
                        decoder = File(asrDir, "decoder.int8.onnx").absolutePath
                    ),
                    tokens = File(asrDir, "tokens.txt").absolutePath,
                    numThreads = numThreads,
                    provider = "cpu",
                    debug = false
                )
            }
            ModelType.ZIPFORMER_CTC -> {
                OnlineModelConfig(
                    zipformer2Ctc = OnlineZipformer2CtcModelConfig(
                        model = File(asrDir, "model.int8.onnx").absolutePath
                    ),
                    tokens = File(asrDir, "tokens.txt").absolutePath,
                    numThreads = numThreads,
                    provider = "cpu",
                    debug = false
                )
            }
        }

        // 配置同音字替换器（如果文件存在）
        val hrConfig = createHomophoneReplacerConfig()

        return OnlineRecognizerConfig(
            featConfig = FeatureConfig(
                sampleRate = 16000,
                featureDim = 80
            ),
            modelConfig = modelConfig,
            hr = hrConfig,
            enableEndpoint = true,
            decodingMethod = "greedy_search",
            maxActivePaths = 4
        )
    }


    /**
     * 创建同音字替换器配置（使用默认文件名）
     * 如果lexicon.txt和replace.fst都存在，则启用同音字替换功能
     */
    private fun createHomophoneReplacerConfig(): HomophoneReplacerConfig {
        val lexiconFile = File(asrDir, "lexicon.txt")
        val replaceFstFile = File(asrDir, "replace.fst")

        return if (lexiconFile.exists() && replaceFstFile.exists()) {
            Log.i(TAG, "HomophoneReplacer enabled: lexicon=${lexiconFile.absolutePath}, fst=${replaceFstFile.absolutePath}")
            HomophoneReplacerConfig(
                lexicon = lexiconFile.absolutePath,
                ruleFsts = replaceFstFile.absolutePath
            )
        } else {
            if (!lexiconFile.exists()) {
                Log.d(TAG, "HomophoneReplacer disabled: lexicon.txt not found")
            }
            if (!replaceFstFile.exists()) {
                Log.d(TAG, "HomophoneReplacer disabled: replace.fst not found")
            }
            HomophoneReplacerConfig()
        }
    }


    /**
     * 获取模型下载说明
     */
    fun getModelDownloadInstructions(): String {
        return """
            ❌ 模型文件未找到

            模型目录: ${modelDir.absolutePath}/

            📥 下载模型文件并推送到设备：

            必需文件 (ASR):
            - encoder-epoch-99-avg-1.onnx
            - decoder-epoch-99-avg-1.onnx
            - joiner-epoch-99-avg-1.onnx
            - tokens.txt
            放置位置: ${asrDir.absolutePath}/

            可选文件 (KWS): keywords.txt + 模型文件
            放置位置: ${kwsDir.absolutePath}/

            可选文件 (VAD): silero_vad.onnx
            放置位置: ${vadDir.absolutePath}/

            可选文件 (同音字): lexicon.txt + replace.fst
            放置位置: ${asrDir.absolutePath}/

            详细说明请查看 README.md
        """.trimIndent()
    }


    /**
     * 创建 Silero VAD
     * @param threshold 语音检测阈值 (0-1, 默认 0.5)
     * @param minSilenceDuration 最短静音时长，用于断句 (秒, 默认 0.3)
     * @param minSpeechDuration 最短语音时长，过滤杂音 (秒, 默认 0.25)
     * @param maxSpeechDuration 最大语音时长，强制断句 (秒, 默认 10.0)
     * @return Vad 或 null (如果模型不存在)
     */
    fun createVad(
        threshold: Float = 0.5F,
        minSilenceDuration: Float = 0.3F,
        minSpeechDuration: Float = 0.25F,
        maxSpeechDuration: Float = 10.0F
    ): Vad? {
        val vadModelFile = File(vadDir, "silero_vad.onnx")

        if (!vadModelFile.exists()) {
            Log.e(TAG, "VAD model not found: ${vadModelFile.absolutePath}")
            return null
        }

        Log.i(TAG, "Loading VAD model from: ${vadModelFile.absolutePath}")
        Log.i(TAG, "VAD config: threshold=$threshold, minSilence=$minSilenceDuration, maxSpeech=$maxSpeechDuration")

        val config = VadModelConfig(
            sileroVadModelConfig = SileroVadModelConfig(
                model = vadModelFile.absolutePath,
                threshold = threshold,
                minSilenceDuration = minSilenceDuration,
                minSpeechDuration = minSpeechDuration,
                maxSpeechDuration = maxSpeechDuration,
                windowSize = 512
            ),
            sampleRate = 16000,
            numThreads = 1,
            provider = "cpu",
            debug = false
        )

        return try {
            val vad = Vad(assetManager = null, config = config)
            Log.i(TAG, "VAD created successfully")
            vad
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create VAD", e)
            null
        }
    }


    /**
     * 创建唤醒词识别器 (Keyword Spotter)
     * 使用专用的 KWS 模型（小模型，专为唤醒词设计）
     *
     * @param keywordsFile 关键词文件名 (默认 keywords.txt)
     * @param threshold 唤醒阈值 (默认 0.5)
     * @param score 关键词分数 (默认 1.0)
     * @param maxActivePaths 最大激活路径数 (默认 4)
     * @param numThreads 线程数
     * @return KeywordSpotter 或 null (如果模型不存在)
     */
    fun createKeywordSpotter(
        keywordsFile: String = "keywords.txt",
        threshold: Float = 0.5F,
        score: Float = 1.0F,
        maxActivePaths: Int = 4,
        numThreads: Int = 1
    ): KeywordSpotter? {
        // KWS 专用模型文件（wenetspeech 中文唤醒词模型）
        val kwsEncoderFile = File(kwsDir, "encoder-epoch-12-avg-2-chunk-16-left-64.onnx")
        val kwsDecoderFile = File(kwsDir, "decoder-epoch-12-avg-2-chunk-16-left-64.onnx")
        val kwsJoinerFile = File(kwsDir, "joiner-epoch-12-avg-2-chunk-16-left-64.onnx")
        val kwsTokensFile = File(kwsDir, "tokens.txt")
        val kwFile = File(kwsDir, keywordsFile)

        if (!kwsEncoderFile.exists() || !kwsDecoderFile.exists() || !kwsJoinerFile.exists()) {
            Log.e(TAG, "KWS model files not found in ${kwsDir.absolutePath}")
            Log.e(TAG, "Expected files:")
            Log.e(TAG, "  - encoder-epoch-12-avg-2-chunk-16-left-64.onnx")
            Log.e(TAG, "  - decoder-epoch-12-avg-2-chunk-16-left-64.onnx")
            Log.e(TAG, "  - joiner-epoch-12-avg-2-chunk-16-left-64.onnx")
            return null
        }

        if (!kwsTokensFile.exists()) {
            Log.e(TAG, "KWS tokens file not found: ${kwsTokensFile.absolutePath}")
            return null
        }

        if (!kwFile.exists()) {
            Log.e(TAG, "Keywords file not found: ${kwFile.absolutePath}")
            return null
        }

        Log.i(TAG, "Loading KWS model from: ${kwsDir.absolutePath}")
        Log.i(TAG, "KWS Encoder: ${kwsEncoderFile.name}")
        Log.i(TAG, "Keywords file: ${kwFile.absolutePath}")
        Log.i(TAG, "KWS config: threshold=$threshold, score=$score")

        val modelConfig = OnlineModelConfig(
            transducer = OnlineTransducerModelConfig(
                encoder = kwsEncoderFile.absolutePath,
                decoder = kwsDecoderFile.absolutePath,
                joiner = kwsJoinerFile.absolutePath
            ),
            tokens = kwsTokensFile.absolutePath,
            numThreads = numThreads,
            provider = "cpu",
            debug = false,
            modelType = "zipformer2"
        )

        val kwsConfig = KeywordSpotterConfig(
            featConfig = FeatureConfig(
                sampleRate = 16000,
                featureDim = 80
            ),
            modelConfig = modelConfig,
            maxActivePaths = maxActivePaths,
            keywordsFile = kwFile.absolutePath,
            keywordsScore = score,
            keywordsThreshold = threshold,
            numTrailingBlanks = 1
        )

        return try {
            val kws = KeywordSpotter(assetManager = null, config = kwsConfig)
            Log.i(TAG, "KeywordSpotter created successfully with dedicated KWS model")
            kws
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create KeywordSpotter", e)
            null
        }
    }

    /**
     * 检查 KWS 专用模型和关键词文件是否存在
     */
    fun checkKwsExists(keywordsFile: String = "keywords.txt"): Boolean {
        val kwsEncoderFile = File(kwsDir, "encoder-epoch-12-avg-2-chunk-16-left-64.onnx")
        val kwsDecoderFile = File(kwsDir, "decoder-epoch-12-avg-2-chunk-16-left-64.onnx")
        val kwsJoinerFile = File(kwsDir, "joiner-epoch-12-avg-2-chunk-16-left-64.onnx")
        val kwsTokensFile = File(kwsDir, "tokens.txt")
        val kwFile = File(kwsDir, keywordsFile)

        return kwsEncoderFile.exists() && kwsDecoderFile.exists() &&
               kwsJoinerFile.exists() && kwsTokensFile.exists() && kwFile.exists()
    }

    /**
     * 支持的模型类型
     */
    enum class ModelType {
        ZIPFORMER_TRANSDUCER,  // Transducer模型 (推荐)
        PARAFORMER,             // Paraformer模型
        ZIPFORMER_CTC          // CTC模型
    }
}
