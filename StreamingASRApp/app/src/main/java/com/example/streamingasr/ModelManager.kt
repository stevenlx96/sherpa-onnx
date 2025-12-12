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
    fun checkModelExists(modelType: ModelType): Boolean {
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
     * @param hotwordsFile 热词文件名 (可选，放在 asr/ 目录)
     * @param hotwordsScore 热词权重 (默认 1.5，越高越优先)
     * @return OnlineRecognizer 或 null (如果模型不存在)
     */
    fun createOnlineRecognizer(
        modelType: ModelType = ModelType.ZIPFORMER_TRANSDUCER,
        numThreads: Int = Runtime.getRuntime().availableProcessors(),
        hotwordsFile: String = "",
        hotwordsScore: Float = 1.5f
    ): OnlineRecognizer? {

        if (!checkModelExists(modelType)) {
            Log.e(TAG, "Model files not found in ${modelDir.absolutePath}")
            Log.e(TAG, "Please copy model files to this directory first")
            return null
        }

        Log.i(TAG, "Loading model from: ${modelDir.absolutePath}")
        Log.i(TAG, "Using $numThreads threads")

        val config = createRecognizerConfig(modelType, numThreads, hotwordsFile, hotwordsScore)

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
     * 创建在线识别器（使用自定义模型文件名）
     * @param modelFiles 自定义的模型文件配置
     * @param numThreads 线程数 (默认为CPU核心数)
     * @return OnlineRecognizer 或 null (如果模型不存在)
     */
    fun createOnlineRecognizer(
        modelFiles: ModelFiles,
        numThreads: Int = Runtime.getRuntime().availableProcessors()
    ): OnlineRecognizer? {

        Log.i(TAG, "Loading custom model from: ${modelDir.absolutePath}")
        Log.i(TAG, "Using $numThreads threads")

        val config = try {
            createRecognizerConfig(modelFiles, numThreads)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create recognizer config: ${e.message}")
            return null
        }

        return try {
            val recognizer = OnlineRecognizer(
                assetManager = null,
                config = config
            )
            Log.i(TAG, "OnlineRecognizer created successfully with custom model files")
            recognizer
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create OnlineRecognizer", e)
            null
        }
    }

    /**
     * 自动查找并创建在线识别器
     * 自动扫描模型目录，匹配文件名模式
     * @param numThreads 线程数 (默认为CPU核心数)
     * @return OnlineRecognizer 或 null (如果未找到匹配的模型)
     */
    fun createOnlineRecognizerAuto(
        numThreads: Int = Runtime.getRuntime().availableProcessors()
    ): OnlineRecognizer? {
        Log.i(TAG, "Auto-detecting model files in: ${asrDir.absolutePath}")

        val files = asrDir.listFiles() ?: run {
            Log.e(TAG, "ASR directory is empty or inaccessible")
            return null
        }

        val fileNames = files.map { it.name }
        Log.i(TAG, "Found files: ${fileNames.joinToString(", ")}")

        // 尝试查找 Transducer 模型
        val encoder = fileNames.find { it.contains("encoder") && it.endsWith(".onnx") }
        val decoder = fileNames.find { it.contains("decoder") && it.endsWith(".onnx") }
        val joiner = fileNames.find { it.contains("joiner") && it.endsWith(".onnx") }

        if (encoder != null && decoder != null && joiner != null) {
            Log.i(TAG, "Detected Transducer model: encoder=$encoder, decoder=$decoder, joiner=$joiner")
            val modelFiles = ModelFiles(
                encoder = encoder,
                decoder = decoder,
                joiner = joiner,
                tokens = "tokens.txt"
            )
            return createOnlineRecognizer(modelFiles, numThreads)
        }

        // 尝试查找 Paraformer 或 CTC 模型
        val model = fileNames.find {
            it.endsWith(".onnx") && !it.contains("encoder") && !it.contains("decoder") && !it.contains("joiner")
        }

        if (model != null) {
            Log.i(TAG, "Detected Paraformer/CTC model: $model")
            val modelFiles = ModelFiles(
                model = model,
                tokens = "tokens.txt"
            )
            return createOnlineRecognizer(modelFiles, numThreads)
        }

        Log.e(TAG, "No compatible model files found")
        return null
    }

    /**
     * 创建识别器配置
     */
    private fun createRecognizerConfig(
        modelType: ModelType,
        numThreads: Int,
        hotwordsFile: String = "",
        hotwordsScore: Float = 1.5f
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

        // 配置语言模型（LM）用于 rescoring（如果文件存在）
        val lmConfig = createLMConfig()

        // 处理热词文件路径
        val hotwordsPath = if (hotwordsFile.isNotEmpty()) {
            val file = File(asrDir, hotwordsFile)
            if (file.exists()) {
                // 读取并打印热词内容，用于调试
                try {
                    // 读取文件内容并自动去除 UTF-8 BOM
                    var fileContent = file.readText()

                    // 检测并移除 UTF-8 BOM (EF BB BF = \uFEFF)
                    if (fileContent.startsWith("\uFEFF")) {
                        Log.i(TAG, "检测到 UTF-8 BOM，正在自动移除...")
                        fileContent = fileContent.substring(1)

                        // 写回文件（无BOM版本）
                        try {
                            file.writeText(fileContent)
                            Log.i(TAG, "已自动清理热词文件中的 BOM")
                        } catch (e: Exception) {
                            Log.w(TAG, "无法写入清理后的文件，但会继续使用清理后的内容", e)
                        }
                    }

                    val hotwordsContent = fileContent.lines().filter { it.isNotBlank() }
                    Log.i(TAG, "=== 热词文件加载成功 ===")
                    Log.i(TAG, "文件路径: ${file.absolutePath}")
                    Log.i(TAG, "文件大小: ${file.length()} bytes")
                    Log.i(TAG, "热词数量: ${hotwordsContent.size}")
                    Log.i(TAG, "热词权重: $hotwordsScore")
                    Log.i(TAG, "热词内容:")
                    hotwordsContent.forEachIndexed { index, line ->
                        val bytes = line.toByteArray()
                        val hex = bytes.joinToString(" ") { "%02X".format(it) }
                        Log.i(TAG, "  [$index] '$line' (hex: $hex)")
                    }
                    Log.i(TAG, "======================")
                } catch (e: Exception) {
                    Log.e(TAG, "读取热词文件内容失败", e)
                }
                file.absolutePath
            } else {
                Log.w(TAG, "Hotwords file not found: ${file.absolutePath}, will be ignored")
                ""
            }
        } else {
            ""
        }

        // 当使用热词时，必须使用 modified_beam_search
        val decodingMethod = if (hotwordsPath.isNotEmpty()) {
            "modified_beam_search"
        } else {
            "greedy_search"
        }

        // 当使用热词时，增加 beam size 以提高热词命中率
        val maxActivePaths = if (hotwordsPath.isNotEmpty()) 20 else 4

        return OnlineRecognizerConfig(
            featConfig = FeatureConfig(
                sampleRate = 16000,
                featureDim = 80
            ),
            modelConfig = modelConfig,
            lmConfig = lmConfig,  // 添加语言模型配置
            hr = hrConfig,
            enableEndpoint = true,
            decodingMethod = decodingMethod,
            maxActivePaths = maxActivePaths,
            hotwordsFile = hotwordsPath,
            hotwordsScore = hotwordsScore
        )
    }

    /**
     * 创建识别器配置（使用自定义模型文件）
     */
    private fun createRecognizerConfig(
        modelFiles: ModelFiles,
        numThreads: Int
    ): OnlineRecognizerConfig {
        // 确定模型类型并构建配置
        val modelConfig = when {
            // Transducer 模型（encoder + decoder + joiner）
            modelFiles.encoder != null && modelFiles.decoder != null && modelFiles.joiner != null -> {
                val encoderFile = File(asrDir, modelFiles.encoder)
                val decoderFile = File(asrDir, modelFiles.decoder)
                val joinerFile = File(asrDir, modelFiles.joiner)
                val tokensFile = File(asrDir, modelFiles.tokens)

                require(encoderFile.exists()) { "Encoder file not found: ${encoderFile.absolutePath}" }
                require(decoderFile.exists()) { "Decoder file not found: ${decoderFile.absolutePath}" }
                require(joinerFile.exists()) { "Joiner file not found: ${joinerFile.absolutePath}" }
                require(tokensFile.exists()) { "Tokens file not found: ${tokensFile.absolutePath}" }

                Log.i(TAG, "Using Transducer model:")
                Log.i(TAG, "  encoder: ${modelFiles.encoder}")
                Log.i(TAG, "  decoder: ${modelFiles.decoder}")
                Log.i(TAG, "  joiner: ${modelFiles.joiner}")

                OnlineModelConfig(
                    transducer = OnlineTransducerModelConfig(
                        encoder = encoderFile.absolutePath,
                        decoder = decoderFile.absolutePath,
                        joiner = joinerFile.absolutePath
                    ),
                    tokens = tokensFile.absolutePath,
                    numThreads = numThreads,
                    provider = "cpu",
                    debug = false
                )
            }

            // Paraformer/CTC 单文件模型
            modelFiles.model != null -> {
                val modelFile = File(asrDir, modelFiles.model)
                val tokensFile = File(asrDir, modelFiles.tokens)

                require(modelFile.exists()) { "Model file not found: ${modelFile.absolutePath}" }
                require(tokensFile.exists()) { "Tokens file not found: ${tokensFile.absolutePath}" }

                Log.i(TAG, "Using single model file: ${modelFiles.model}")

                // 根据文件名判断是 Paraformer 还是 CTC
                if (modelFiles.model.contains("paraformer", ignoreCase = true)) {
                    OnlineModelConfig(
                        paraformer = OnlineParaformerModelConfig(
                            encoder = modelFile.absolutePath,
                            decoder = modelFile.absolutePath
                        ),
                        tokens = tokensFile.absolutePath,
                        numThreads = numThreads,
                        provider = "cpu",
                        debug = false
                    )
                } else {
                    OnlineModelConfig(
                        zipformer2Ctc = OnlineZipformer2CtcModelConfig(
                            model = modelFile.absolutePath
                        ),
                        tokens = tokensFile.absolutePath,
                        numThreads = numThreads,
                        provider = "cpu",
                        debug = false
                    )
                }
            }

            else -> {
                throw IllegalArgumentException("Invalid model configuration: must provide either (encoder, decoder, joiner) or (model)")
            }
        }

        // 配置同音字替换器
        val hrConfig = createHomophoneReplacerConfig(modelFiles)

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
     * 创建同音字替换器配置（使用自定义文件名）
     */
    private fun createHomophoneReplacerConfig(modelFiles: ModelFiles): HomophoneReplacerConfig {
        // 如果ModelFiles中指定了自定义的lexicon和replaceFst
        if (modelFiles.lexicon != null && modelFiles.replaceFst != null) {
            val lexiconFile = File(asrDir, modelFiles.lexicon)
            val replaceFstFile = File(asrDir, modelFiles.replaceFst)

            return if (lexiconFile.exists() && replaceFstFile.exists()) {
                Log.i(TAG, "HomophoneReplacer enabled (custom): lexicon=${lexiconFile.absolutePath}, fst=${replaceFstFile.absolutePath}")
                HomophoneReplacerConfig(
                    lexicon = lexiconFile.absolutePath,
                    ruleFsts = replaceFstFile.absolutePath
                )
            } else {
                Log.w(TAG, "Custom HomophoneReplacer files specified but not found")
                HomophoneReplacerConfig()
            }
        }

        // 否则使用默认配置
        return createHomophoneReplacerConfig()
    }

    /**
     * 创建语言模型配置（用于 rescoring）
     * 如果 LM 文件存在，则启用 rescoring 功能以提高识别准确率
     */
    private fun createLMConfig(): OnlineLMConfig {
        val lmFile = File(asrDir, "with-state-epoch-99-avg-1.int8.onnx")

        return if (lmFile.exists()) {
            Log.i(TAG, "Language Model enabled: ${lmFile.absolutePath}")
            Log.i(TAG, "Rescoring will improve accuracy for ambiguous words")
            OnlineLMConfig(
                model = lmFile.absolutePath,
                scale = 0.5f  // LM 权重（0.5 是推荐值）
            )
        } else {
            Log.d(TAG, "Language Model disabled: with-state-epoch-99-avg-1.int8.onnx not found")
            OnlineLMConfig()  // 空配置，不启用 LM
        }
    }

    /**
     * 获取模型下载说明
     */
    fun getModelDownloadInstructions(): String {
        return """
            模型文件需要按以下目录结构放置:
            ${modelDir.absolutePath}/
            ├── asr/        - ASR 语音识别模型
            ├── kws/        - KWS 唤醒词检测模型
            └── vad/        - VAD 语音活动检测模型

            ========================================
            必需文件1: ASR 模型 (语音识别)
            ========================================

            放置位置: ${asrDir.absolutePath}/

            1. Zipformer Transducer (推荐 - 中英文双语):
               需要的文件:
               - encoder-epoch-99-avg-1.onnx
               - decoder-epoch-99-avg-1.onnx
               - joiner-epoch-99-avg-1.onnx
               - tokens.txt

               下载地址:
               https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20.tar.bz2

            2. Paraformer (中文+英文):
               需要的文件:
               - encoder.int8.onnx
               - decoder.int8.onnx
               - tokens.txt

               下载地址:
               https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-paraformer-bilingual-zh-en.tar.bz2

            3. Zipformer CTC:
               需要的文件:
               - model.int8.onnx
               - tokens.txt

            ========================================
            可选: KWS 模型 (唤醒词检测)
            ========================================

            放置位置: ${kwsDir.absolutePath}/

            需要的文件:
            - encoder-epoch-12-avg-2-chunk-16-left-64.onnx
            - decoder-epoch-12-avg-2-chunk-16-left-64.onnx
            - joiner-epoch-12-avg-2-chunk-16-left-64.onnx
            - tokens.txt
            - keywords.txt (唤醒词列表)

            下载地址:
            https://github.com/k2-fsa/sherpa-onnx/releases/download/kws-models/sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01.tar.bz2

            功能说明:
            - 提供"你好小智"等唤醒词检测功能
            - 模型小 (3.3MB)，功耗低
            - 如果不存在 keywords.txt，应用将以直接识别模式启动

            ========================================
            推荐: Silero VAD (优化断句)
            ========================================

            放置位置: ${vadDir.absolutePath}/

            文件名: silero_vad.onnx

            下载地址:
            https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx

            功能说明:
            - 智能语音活动检测，比内置endpoint更准确
            - 0.3秒静音即可断句（比内置快5倍）
            - 抗噪音能力强，避免误断句
            - 如果不存在，将使用内置的endpoint断句

            ========================================
            可选: 同音字替换功能 (HomophoneReplacer)
            ========================================

            放置位置: ${asrDir.absolutePath}/

            需要的文件:
            - lexicon.txt (词典文件)
            - replace.fst (替换规则FST)

            下载地址:
            https://github.com/k2-fsa/sherpa-onnx/releases/tag/hr-files

            功能说明:
            自动纠正同音字错误，如 "在坐" → "在座", "因该" → "应该"
            这些文件是可选的，如果不存在则不启用同音字替换功能

            ========================================
            使用adb推送模型示例:
            ========================================

            # 推送 ASR 模型
            adb push encoder-epoch-99-avg-1.onnx ${asrDir.absolutePath}/
            adb push decoder-epoch-99-avg-1.onnx ${asrDir.absolutePath}/
            adb push joiner-epoch-99-avg-1.onnx ${asrDir.absolutePath}/
            adb push tokens.txt ${asrDir.absolutePath}/

            # 推送 KWS 模型 (可选)
            adb push encoder-epoch-12-avg-2-chunk-16-left-64.onnx ${kwsDir.absolutePath}/
            adb push decoder-epoch-12-avg-2-chunk-16-left-64.onnx ${kwsDir.absolutePath}/
            adb push joiner-epoch-12-avg-2-chunk-16-left-64.onnx ${kwsDir.absolutePath}/
            adb push tokens.txt ${kwsDir.absolutePath}/
            adb push keywords.txt ${kwsDir.absolutePath}/

            # 推送 VAD 模型 (推荐)
            adb push silero_vad.onnx ${vadDir.absolutePath}/

            或者使用应用的文件管理功能将模型复制到对应目录
        """.trimIndent()
    }

    /**
     * 列出模型目录中的所有文件
     */
    fun listModelFiles(): List<String> {
        return modelDir.listFiles()?.map { it.name } ?: emptyList()
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
     * 检查 VAD 模型是否存在
     */
    fun checkVadExists(): Boolean {
        val vadModelFile = File(vadDir, "silero_vad.onnx")
        return vadModelFile.exists()
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
     * 检查热词文件是否存在
     * @param hotwordsFile 热词文件名（在 asr/ 目录下）
     */
    fun checkHotwordsExists(hotwordsFile: String = "hotwords.txt"): Boolean {
        val file = File(asrDir, hotwordsFile)
        return file.exists()
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
