package com.example.codbenchmarker

import android.content.Context
import android.graphics.*
import androidx.camera.core.ImageProxy
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min

data class Detection(
    val box: RectF,
    val score: Float,
    val label: String
)

class CamouflageDetector(
    context: Context,
    modelName: String
) {

    private var interpreter: Interpreter
    private val inputSize = 640

    init {
        val modelBuffer = loadModel(context, modelName)
        interpreter = Interpreter(modelBuffer)
    }

    // ✅ FIXED MODEL LOADER
    private fun loadModel(context: Context, modelName: String): ByteBuffer {
        val fileDescriptor = context.assets.openFd(modelName)
        val inputStream = fileDescriptor.createInputStream()
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength

        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            startOffset,
            declaredLength
        )
    }

    fun runInference(image: ImageProxy): List<Detection> {
        val input = preprocess(image)

        val output = Array(1) { Array(84) { FloatArray(8400) } }
        interpreter.run(input, output)

        return postProcess(output[0])
    }

    // -------------------------------
    // PREPROCESS (RGB + NORMALIZATION)
    // -------------------------------
    private fun preprocess(image: ImageProxy): ByteBuffer {
        val bitmap = imageProxyToBitmap(image)
        val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)

        val buffer = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4)
        buffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(inputSize * inputSize)
        resized.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        for (pixel in pixels) {
            val r = ((pixel shr 16) and 0xFF) / 255f
            val g = ((pixel shr 8) and 0xFF) / 255f
            val b = (pixel and 0xFF) / 255f

            buffer.putFloat(r)
            buffer.putFloat(g)
            buffer.putFloat(b)
        }

        return buffer
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val buffer = image.planes[0].buffer
        val bitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(buffer)

        val matrix = Matrix().apply {
            postRotate(image.imageInfo.rotationDegrees.toFloat())
        }

        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    // -------------------------------
    // POSTPROCESS (YOLO FIXED)
    // -------------------------------
    private fun postProcess(output: Array<FloatArray>): List<Detection> {
        val detections = mutableListOf<Detection>()
        val numBoxes = output[0].size

        for (i in 0 until numBoxes) {

            val cx = output[0][i]
            val cy = output[1][i]
            val w = output[2][i]
            val h = output[3][i]

            val obj = output[4][i]
            val cls = output[5][i] // person only

            val confidence = obj * cls

            if (confidence > 0.01f) {

                val left = cx - w / 2
                val top = cy - h / 2
                val right = cx + w / 2
                val bottom = cy + h / 2

                detections.add(
                    Detection(
                        RectF(left, top, right, bottom),
                        confidence,
                        "PERSON"
                    )
                )
            }
        }

        return nms(detections)
    }

    // -------------------------------
    // NMS
    // -------------------------------
    private fun nms(detections: List<Detection>, iouThreshold: Float = 0.5f): List<Detection> {
        val result = mutableListOf<Detection>()
        val sorted = detections.sortedByDescending { it.score }.toMutableList()

        while (sorted.isNotEmpty()) {
            val best = sorted.removeAt(0)
            result.add(best)

            sorted.removeAll {
                iou(best.box, it.box) > iouThreshold
            }
        }

        return result.take(5)
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
        interpreter.close()
    }
}