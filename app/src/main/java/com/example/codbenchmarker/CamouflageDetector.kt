package com.example.codbenchmarker

import android.content.Context
import android.graphics.*
import android.util.Log
import androidx.camera.core.ImageProxy
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.DataType
import org.opencv.core.Mat
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min
import kotlin.math.exp

data class Detection(
    val box: RectF,
    val score: Float,
    val label: String,
    val formattedLabel: String = String.format(java.util.Locale.US, "%s %.0f%%", label, score * 100)
)

class CamouflageDetector(
    context: Context,
    modelName: String
) {

    private var interpreter: Interpreter
    private val inputSize = 640
    private val modelBuffer: ByteBuffer = loadModel(context, modelName)

    private var confidenceThreshold = 0.55f

    // BERSERKER: Pre-allocate Memory to eliminate GC stutters and drop Latency
    private val inputBuffer = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4).apply {
        order(ByteOrder.nativeOrder())
    }
    private val pixelBuffer = ByteArray(inputSize * inputSize * 3)

    init {
        // BERSERKER: Force TFLite to use 4 threads to match your UI readout
        val options = Interpreter.Options().apply {
            numThreads = 4
        }
        interpreter = Interpreter(modelBuffer, options)

        // Log model info for debug and compatibility verification of INT8/FP16 models
        try {
            val inputTensor = interpreter.getInputTensor(0)
            val outputTensor = interpreter.getOutputTensor(0)
            Log.i("MLPerf_Terminal", "Model Loaded: $modelName")
            Log.i("MLPerf_Terminal", "Input Tensor: DataType=${inputTensor.dataType()}, Shape=${inputTensor.shape().contentToString()}")
            Log.i("MLPerf_Terminal", "Output Tensor: DataType=${outputTensor.dataType()}, Shape=${outputTensor.shape().contentToString()}")
        } catch (e: Exception) {
            Log.e("MLPerf_Terminal", "Error reading tensor details: ${e.message}")
        }
    }

    private fun loadModel(context: Context, modelName: String): ByteBuffer {
        val fileDescriptor = context.assets.openFd(modelName)
        val inputStream = fileDescriptor.createInputStream()
        val fileChannel = inputStream.channel
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            fileDescriptor.startOffset,
            fileDescriptor.declaredLength
        )
    }

    fun setThreadCount(threads: Int) {
        synchronized(this) {
            interpreter.close()
            val options = Interpreter.Options().apply {
                numThreads = threads
            }
            interpreter = Interpreter(modelBuffer, options)
        }
    }

    fun setConfidenceThreshold(threshold: Float) {
        confidenceThreshold = threshold
    }

    fun runInference(image: ImageProxy): List<Detection> {
        return try {
            preprocess(image) // Fills the pre-allocated inputBuffer
            val outputTensor = interpreter.getOutputTensor(0)

            if (outputTensor.dataType() != DataType.FLOAT32) {
                Log.e("MLPerf_Terminal", "CRITICAL ERROR: Model is ${outputTensor.dataType()}! Code expects FLOAT32. Re-export your YOLO model.")
                return emptyList()
            }

            val shape = outputTensor.shape()
            val output = Array(1) { Array(shape[1]) { FloatArray(shape[2]) } }

            // ARCHITECT FIX: Explicitly rewind the buffer pointer to the beginning before TFLite reads it.
            inputBuffer.rewind()
            synchronized(this) {
                interpreter.run(inputBuffer, output)
            }

            postProcess(output[0], shape[1], shape[2])
        } catch (e: Exception) {
            // BERSERKER SHIELD: Catch any hardware buffer/padding issues gracefully
            Log.e("MLPerf_Terminal", "Hardware or Inference Error CAUGHT: ${e.message}")
            emptyList()
        }
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun preprocess(image: ImageProxy) {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride

        // Create OpenCV Mat directly from RGBA ImageProxy buffer (no Java Bitmap allocations)
        val mat = Mat(image.height, image.width, CvType.CV_8UC4, buffer, rowStride.toLong())

        // Rotate natively
        val rotatedMat = Mat()
        when (image.imageInfo.rotationDegrees) {
            90 -> Core.rotate(mat, rotatedMat, Core.ROTATE_90_CLOCKWISE)
            180 -> Core.rotate(mat, rotatedMat, Core.ROTATE_180)
            270 -> Core.rotate(mat, rotatedMat, Core.ROTATE_90_COUNTERCLOCKWISE)
            else -> mat.copyTo(rotatedMat)
        }

        // Convert RGBA -> RGB
        val rgbMat = Mat()
        Imgproc.cvtColor(rotatedMat, rgbMat, Imgproc.COLOR_RGBA2RGB)

        // Resize natively to 640x640
        val resizedMat = Mat()
        Imgproc.resize(rgbMat, resizedMat, Size(inputSize.toDouble(), inputSize.toDouble()))

        // Copy raw pixel bytes to pre-allocated ByteArray in a single JNI call
        resizedMat.get(0, 0, pixelBuffer)

        // Normalize and copy to inputBuffer
        inputBuffer.rewind()
        for (i in 0 until (inputSize * inputSize * 3)) {
            val byteVal = pixelBuffer[i].toInt() and 0xFF
            inputBuffer.putFloat(byteVal / 255f)
        }

        // Clean up native mats immediately
        mat.release()
        rotatedMat.release()
        rgbMat.release()
        resizedMat.release()
    }

    private fun postProcess(output: Array<FloatArray>, dim1: Int, dim2: Int): List<Detection> {
        val detections = mutableListOf<Detection>()
        val isTransposed = dim1 > dim2
        val numBoxes = if (isTransposed) dim1 else dim2

        for (i in 0 until numBoxes) {
            val score = if (isTransposed) output[i][4] else output[4][i]

            if (score > confidenceThreshold) {
                var cx = if (isTransposed) output[i][0] else output[0][i]
                var cy = if (isTransposed) output[i][1] else output[1][i]
                var w  = if (isTransposed) output[i][2] else output[2][i]
                var h  = if (isTransposed) output[i][3] else output[3][i]

                // Automatically detect and normalize raw pixel coordinates (some INT8 exports use 0..640 instead of 0..1)
                if (cx > 1f || cy > 1f || w > 1f || h > 1f) {
                    cx /= 640f
                    cy /= 640f
                    w /= 640f
                    h /= 640f
                }

                val left = cx - w / 2f
                val top = cy - h / 2f
                val right = cx + w / 2f
                val bottom = cy + h / 2f

                detections.add(
                    Detection(
                        RectF(
                            max(0f, min(1f, left)),
                            max(0f, min(1f, top)),
                            min(1f, max(0f, right)),
                            min(1f, max(0f, bottom))
                        ),
                        score,
                        "PERSON"
                    )
                )
            }
        }

        return nms(detections)
    }

    private fun nms(detections: List<Detection>, iouThreshold: Float = 0.5f): List<Detection> {
        val result = mutableListOf<Detection>()
        val sorted = detections.sortedByDescending { it.score }
        val suppressed = BooleanArray(sorted.size)

        for (i in sorted.indices) {
            if (suppressed[i]) continue

            val best = sorted[i]
            result.add(best)

            if (result.size >= 10) break

            for (j in (i + 1) until sorted.size) {
                if (!suppressed[j] && iou(best.box, sorted[j].box) > iouThreshold) {
                    suppressed[j] = true
                }
            }
        }

        return result
    }

    private fun iou(a: RectF, b: RectF): Float {
        val areaA = (a.right - a.left) * (a.bottom - a.top)
        val areaB = (b.right - b.left) * (b.bottom - b.top)

        val left = max(a.left, b.left)
        val top = max(a.top, b.top)
        val right = min(a.right, b.right)
        val bottom = min(a.bottom, b.bottom)

        val interArea = max(0f, right - left) * max(0f, bottom - top)
        return interArea / (areaA + areaB - interArea)
    }

    fun close() {
        synchronized(this) {
            interpreter.close()
        }
    }
}