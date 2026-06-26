package com.example.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

data class TfliteResult(
    val label: String,
    val confidence: Float
)

class TfliteFoodClassifier(private val context: Context) {

    private var interpreter: Interpreter? = null
    private val labels = mutableListOf<String>()
    private var isInitialized = false

    // Cache parameters to prevent GC thrashing and redundant calculations
    private var imageHeight = 224
    private var imageWidth = 224
    private var numChannels = 3
    private var isFloatInput = true
    private var isInt8Input = false
    private var isUInt8Input = false
    private var numClasses = 0
    private var isFloatOutput = true
    private var isInt8Output = false
    private var isUInt8Output = false

    // Quantization Parameters for input
    private var inputScale = 1.0f
    private var inputZeroPoint = 0

    // Pre-allocated reusable buffers
    private var inputBuffer: ByteBuffer? = null
    private var pixelsArray: IntArray? = null
    private var outputProbabilitiesFloat: Array<FloatArray>? = null
    private var outputProbabilitiesByte: Array<ByteArray>? = null

    init {
        try {
            loadLabels()
            loadModel()
            isInitialized = interpreter != null || labels.isNotEmpty()
        } catch (e: Throwable) {
            Log.e("TfliteClassifier", "Erro ao inicializar o classificador TFLite: ${e.message}", e)
        }
    }

    private fun loadLabels() {
        try {
            context.assets.open("food_labels.txt").use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var line: String? = reader.readLine()
                    while (line != null) {
                        if (line.trim().isNotEmpty()) {
                            labels.add(line.trim())
                        }
                        line = reader.readLine()
                    }
                }
            }
            Log.d("TfliteClassifier", "Rótulos carregados com sucesso: ${labels.size} itens")
        } catch (e: Exception) {
            Log.e("TfliteClassifier", "Erro ao carregar food_labels.txt", e)
        }
    }

    private fun loadModel() {
        try {
            val fileDescriptor = context.assets.openFd("model.tflite")
            
            // Verifica se é o modelo dummy/placeholder
            if (fileDescriptor.declaredLength < 1024) {
                Log.w("TfliteClassifier", "Modelo TFLite placeholder detectado. Utilizando fallback (Gemini API ou mock).")
                interpreter = null
                return
            }

            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength
            val mappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
            
            val options = Interpreter.Options()
            options.setNumThreads(4)
            interpreter = Interpreter(mappedByteBuffer, options)
            Log.d("TfliteClassifier", "Modelo TFLite model.tflite carregado com sucesso nativamente!")
            
            setupCachedBuffers()
        } catch (e: Exception) {
            Log.e("TfliteClassifier", "Erro ao carregar modelo TFLite. Utilizando fallback resiliente: ${e.message}")
            interpreter = null
        }
    }

    private fun setupCachedBuffers() {
        val currentInterpreter = interpreter ?: return
        try {
            // 1. Inspect Input Tensor
            val inputTensor = currentInterpreter.getInputTensor(0)
            val inputShape = inputTensor.shape()
            imageHeight = if (inputShape.size >= 2) inputShape[1] else 224
            imageWidth = if (inputShape.size >= 3) inputShape[2] else 224
            numChannels = if (inputShape.size >= 4) inputShape[3] else 3
            
            val inputDataType = inputTensor.dataType()
            isFloatInput = inputDataType == org.tensorflow.lite.DataType.FLOAT32
            isInt8Input = inputDataType == org.tensorflow.lite.DataType.INT8
            isUInt8Input = inputDataType == org.tensorflow.lite.DataType.UINT8

            // Extract Input Quantization Parameters
            val inParams = inputTensor.quantizationParams()
            if (inParams != null) {
                inputScale = inParams.scale
                inputZeroPoint = inParams.zeroPoint
            }

            // 2. Inspect Output Tensor
            val outputTensor = currentInterpreter.getOutputTensor(0)
            val outputShape = outputTensor.shape()
            numClasses = if (outputShape.size >= 2) outputShape[1] else labels.size
            
            val outputDataType = outputTensor.dataType()
            isFloatOutput = outputDataType == org.tensorflow.lite.DataType.FLOAT32
            isInt8Output = outputDataType == org.tensorflow.lite.DataType.INT8
            isUInt8Output = outputDataType == org.tensorflow.lite.DataType.UINT8

            // 3. Pre-allocate Buffers for single-allocation reuse
            val bytesPerChannel = if (isFloatInput) 4 else 1
            inputBuffer = ByteBuffer.allocateDirect(1 * imageWidth * imageHeight * numChannels * bytesPerChannel).apply {
                order(ByteOrder.nativeOrder())
            }
            pixelsArray = IntArray(imageWidth * imageHeight)

            if (isFloatOutput) {
                outputProbabilitiesFloat = Array(1) { FloatArray(numClasses) }
            } else {
                outputProbabilitiesByte = Array(1) { ByteArray(numClasses) }
            }

            Log.d("TfliteClassifier", "Buffers de alta performance configurados. Resolução: ${imageWidth}x${imageHeight}x${numChannels}, FloatInput=$isFloatInput, QuantInputScale=$inputScale, Classes=$numClasses")
        } catch (e: Exception) {
            Log.e("TfliteClassifier", "Falha ao pré-alocar buffers TFLite: ${e.message}", e)
        }
    }

    suspend fun classify(bitmap: Bitmap): TfliteResult = withContext(Dispatchers.Default) {
        if (labels.isEmpty()) {
            return@withContext TfliteResult(label = "Alimento Desconhecido", confidence = 0.0f)
        }

        val currentInterpreter = interpreter
        if (currentInterpreter == null) {
            Log.w("TfliteClassifier", "Interpreter TFLite indisponível. Usando mock de fallback.")
            return@withContext TfliteResult(label = labels.firstOrNull() ?: "Arroz branco", confidence = 0.5f)
        }

        // Thread-safe synchronization on cached reusable buffers to prevent GC thrashing and concurrency issues
        synchronized(this@TfliteFoodClassifier) {
            val localInputBuffer = inputBuffer
            val localPixels = pixelsArray
            
            if (localInputBuffer == null || localPixels == null) {
                Log.w("TfliteClassifier", "Buffers não inicializados corretamente. Executando fallback semântico.")
                return@withContext TfliteResult(label = "Alimento Desconhecido", confidence = 0.0f)
            }

            try {
                // Resize bitmap based on model dimensions
                val resizedBitmap = Bitmap.createScaledBitmap(bitmap, imageWidth, imageHeight, true)
                localInputBuffer.clear()
                
                // Read pixels into the pre-allocated array
                resizedBitmap.getPixels(localPixels, 0, imageWidth, 0, 0, imageWidth, imageHeight)

                if (isFloatInput) {
                    // Adaptative Normalization for Float32 inputs (e.g. MobileNetV2 expects [-1.0, 1.0])
                    for (pixelValue in localPixels) {
                        val r = ((pixelValue shr 16) and 0xFF)
                        val g = ((pixelValue shr 8) and 0xFF)
                        val b = (pixelValue and 0xFF)
                        
                        // MobileNet [-1, 1] normalization style
                        localInputBuffer.putFloat((r - 127.5f) / 127.5f)
                        localInputBuffer.putFloat((g - 127.5f) / 127.5f)
                        localInputBuffer.putFloat((b - 127.5f) / 127.5f)
                    }
                } else {
                    // Quantized INT8 or UINT8 input using model's own scale and zeroPoint
                    for (pixelValue in localPixels) {
                        val r = ((pixelValue shr 16) and 0xFF)
                        val g = ((pixelValue shr 8) and 0xFF)
                        val b = (pixelValue and 0xFF)

                        if (isInt8Input) {
                            // Convert [0, 255] to quantized range using scale & zero-point if valid
                            val normR = r / 255.0f
                            val normG = g / 255.0f
                            val normB = b / 255.0f
                            
                            val qR = if (inputScale != 0f) ((normR / inputScale) + inputZeroPoint).toInt().coerceIn(-128, 127).toByte() else (r - 128).toByte()
                            val qG = if (inputScale != 0f) ((normG / inputScale) + inputZeroPoint).toInt().coerceIn(-128, 127).toByte() else (g - 128).toByte()
                            val qB = if (inputScale != 0f) ((normB / inputScale) + inputZeroPoint).toInt().coerceIn(-128, 127).toByte() else (b - 128).toByte()
                            
                            localInputBuffer.put(qR)
                            localInputBuffer.put(qG)
                            localInputBuffer.put(qB)
                        } else {
                            // UINT8 range [0, 255]
                            val normR = r / 255.0f
                            val normG = g / 255.0f
                            val normB = b / 255.0f

                            val qR = if (inputScale != 0f) ((normR / inputScale) + inputZeroPoint).toInt().coerceIn(0, 255).toByte() else r.toByte()
                            val qG = if (inputScale != 0f) ((normG / inputScale) + inputZeroPoint).toInt().coerceIn(0, 255).toByte() else g.toByte()
                            val qB = if (inputScale != 0f) ((normB / inputScale) + inputZeroPoint).toInt().coerceIn(0, 255).toByte() else b.toByte()

                            localInputBuffer.put(qR)
                            localInputBuffer.put(qG)
                            localInputBuffer.put(qB)
                        }
                    }
                }

                // Prepare reusable output array
                val outputProbabilities: Any = if (isFloatOutput) {
                    outputProbabilitiesFloat?.also { it[0].fill(0f) } ?: Array(1) { FloatArray(numClasses) }
                } else {
                    outputProbabilitiesByte?.also { it[0].fill(0) } ?: Array(1) { ByteArray(numClasses) }
                }

                currentInterpreter.run(localInputBuffer, outputProbabilities)

                var maxConfidence = -1.0f
                var maxIndex = -1

                if (isFloatOutput) {
                    val floatOutput = outputProbabilities as Array<FloatArray>
                    for (i in 0 until minOf(labels.size, numClasses)) {
                        val conf = floatOutput[0][i]
                        if (conf > maxConfidence) {
                            maxConfidence = conf
                            maxIndex = i
                        }
                    }
                } else {
                    val byteOutput = outputProbabilities as Array<ByteArray>
                    for (i in 0 until minOf(labels.size, numClasses)) {
                        val rawValue = byteOutput[0][i].toInt()
                        val conf = if (isInt8Output) {
                            (rawValue + 128) / 255.0f
                        } else {
                            (rawValue and 0xFF) / 255.0f
                        }
                        if (conf > maxConfidence) {
                            maxConfidence = conf
                            maxIndex = i
                        }
                    }
                }

                if (maxIndex != -1) {
                    val predictedLabel = labels[maxIndex]
                    Log.d("TfliteClassifier", "Inferência local otimizada e precisa: $predictedLabel ($maxConfidence)")
                    TfliteResult(label = predictedLabel, confidence = maxConfidence)
                } else {
                    TfliteResult(label = "Alimento Desconhecido", confidence = 0.0f)
                }
            } catch (e: Exception) {
                Log.e("TfliteClassifier", "Erro na execução da inferência TFLite local: ${e.message}", e)
                TfliteResult(label = "Alimento Desconhecido", confidence = 0.0f)
            }
        }
    }
}
