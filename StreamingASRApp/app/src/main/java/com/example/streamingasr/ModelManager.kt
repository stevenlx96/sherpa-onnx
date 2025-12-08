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
    }

    // 模型文件存储在应用内部存储: /data/data/com.example.streamingasr/files/models/
    private val modelDir: File = File(context.filesDir, MODEL_DIR_NAME)

    init {
        // 确保模型目录存在
        if (!modelDir.exists()) {
            modelDir.mkdirs()
            Log.i(TAG, "Created model directory: ${modelDir.absolutePath}")
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
                val encoder = File(modelDir, "encoder-epoch-99-avg-1.onnx")
                val decoder = File(modelDir, "decoder-epoch-99-avg-1.onnx")
                val joiner = File(modelDir, "joiner-epoch-99-avg-1.onnx")
                val tokens = File(modelDir, "tokens.txt")
                encoder.exists() && decoder.exists() && joiner.exists() && tokens.exists()
            }
            ModelType.PARAFORMER -> {
                val encoder = File(modelDir, "encoder.int8.onnx")
                val decoder = File(modelDir, "decoder.int8.onnx")
                val tokens = File(modelDir, "tokens.txt")
                encoder.exists() && decoder.exists() && tokens.exists()
            }
            ModelType.ZIPFORMER_CTC -> {
                val model = File(modelDir, "model.int8.onnx")
                val tokens = File(modelDir, "tokens.txt")
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
        Log.i(TAG, "Auto-detecting model files in: ${modelDir.absolutePath}")

        val files = modelDir.listFiles() ?: run {
            Log.e(TAG, "Model directory is empty or inaccessible")
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
        numThreads: Int
    ): OnlineRecognizerConfig {
        val modelConfig = when (modelType) {
            ModelType.ZIPFORMER_TRANSDUCER -> {
                OnlineModelConfig(
                    transducer = OnlineTransducerModelConfig(
                        encoder = File(modelDir, "encoder-epoch-99-avg-1.onnx").absolutePath,
                        decoder = File(modelDir, "decoder-epoch-99-avg-1.onnx").absolutePath,
                        joiner = File(modelDir, "joiner-epoch-99-avg-1.onnx").absolutePath
                    ),
                    tokens = File(modelDir, "tokens.txt").absolutePath,
                    numThreads = numThreads,
                    provider = "cpu",
                    debug = false
                )
            }
            ModelType.PARAFORMER -> {
                OnlineModelConfig(
                    paraformer = OnlineParaformerModelConfig(
                        encoder = File(modelDir, "encoder.int8.onnx").absolutePath,
                        decoder = File(modelDir, "decoder.int8.onnx").absolutePath
                    ),
                    tokens = File(modelDir, "tokens.txt").absolutePath,
                    numThreads = numThreads,
                    provider = "cpu",
                    debug = false
                )
            }
            ModelType.ZIPFORMER_CTC -> {
                OnlineModelConfig(
                    zipformer2Ctc = OnlineZipformer2CtcModelConfig(
                        model = File(modelDir, "model.int8.onnx").absolutePath
                    ),
                    tokens = File(modelDir, "tokens.txt").absolutePath,
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
                val encoderFile = File(modelDir, modelFiles.encoder)
                val decoderFile = File(modelDir, modelFiles.decoder)
                val joinerFile = File(modelDir, modelFiles.joiner)
                val tokensFile = File(modelDir, modelFiles.tokens)

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
                val modelFile = File(modelDir, modelFiles.model)
                val tokensFile = File(modelDir, modelFiles.tokens)

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
        val lexiconFile = File(modelDir, "lexicon.txt")
        val replaceFstFile = File(modelDir, "replace.fst")

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
            val lexiconFile = File(modelDir, modelFiles.lexicon)
            val replaceFstFile = File(modelDir, modelFiles.replaceFst)

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
     * 获取模型下载说明
     */
    fun getModelDownloadInstructions(): String {
        return """
            模型文件需要放置在:
            ${modelDir.absolutePath}

            支持的模型类型:

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

            可选：同音字替换功能 (HomophoneReplacer):
               需要的文件:
               - lexicon.txt (词典文件)
               - replace.fst (替换规则FST)

               下载地址:
               https://github.com/k2-fsa/sherpa-onnx/releases/tag/hr-files

               功能说明:
               自动纠正同音字错误，如 "在坐" → "在座", "因该" → "应该"
               这些文件是可选的，如果不存在则不启用同音字替换功能

            使用adb推送模型:
            adb push <模型文件> ${modelDir.absolutePath}/

            或者使用应用的文件管理功能将模型复制到此目录
        """.trimIndent()
    }

    /**
     * 列出模型目录中的所有文件
     */
    fun listModelFiles(): List<String> {
        return modelDir.listFiles()?.map { it.name } ?: emptyList()
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
