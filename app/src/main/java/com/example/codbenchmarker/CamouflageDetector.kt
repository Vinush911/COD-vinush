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

/**
 * Data structure that holds information about a detected object on screen.
 * 
 * @property box The location of the target box scaled between 0.0 and 1.0.
 * @property score The confidence score of the target (0.0 to 1.0).
 * @property label The name of the target class (like "PERSON").
 * @property formattedLabel Label text and percentage shown on the screen.
 */
data class Detection(
    val box: RectF,
    val score: Float,
    val label: String,
    val formattedLabel: String = String.format(java.util.Locale.US, "%s %.0f%%", label, score * 100)
)

/**
 * This class handles loading and running the TensorFlow Lite AI model
 * to detect targets in the camera view.
 */
class CamouflageDetector(
    context: Context,
    modelName: String
) {

    // The TensorFlow Lite engine used to run the model calculations
    private var interpreter: Interpreter
    
    // The image width and height the model expects (640x640 pixels)
    private val inputSize = 640
    
    // Buffer containing the loaded model file
    private val modelBuffer: ByteBuffer = loadModel(context, modelName)

    // Ignore targets with a confidence score lower than this threshold
    private var confidenceThreshold = 0.55f

    // Pre-allocated memory space to hold input pixel values (prevents app stutter)
    private val inputBuffer = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4).apply {
        order(ByteOrder.nativeOrder())
    }
    
    // Pre-allocated array to copy pixel values from OpenCV
    private val pixelBuffer = ByteArray(inputSize * inputSize * 3)

    init {
        // Set the model engine to use 4 threads to make processing faster
        val options = Interpreter.Options().apply {
            numThreads = 4
        }
        interpreter = Interpreter(modelBuffer, options)

        // Read and print details about model input and output structures for debugging
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

    /**
     * Loads the model file directly from the app's assets folder.
     */
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

    /**
     * Changes the number of CPU threads the model is allowed to use.
     */
    fun setThreadCount(threads: Int) {
        synchronized(this) {
            interpreter.close() // Release old engine memory
            val options = Interpreter.Options().apply {
                numThreads = threads
            }
            interpreter = Interpreter(modelBuffer, options) // Re-create with new thread size
        }
    }

    /**
     * Updates the minimum confidence threshold score required to show a target box.
     */
    fun setConfidenceThreshold(threshold: Float) {
        confidenceThreshold = threshold
    }

    /**
     * Takes a camera frame, runs it through the model, and returns target boxes.
     */
    fun runInference(image: ImageProxy): List<Detection> {
        return try {
            // 1. Resize, rotate, and write the camera frame into our input buffer memory
            preprocess(image)
            
            val outputTensor = interpreter.getOutputTensor(0)

            // Validate that the model outputs Float32 values (decimal numbers)
            if (outputTensor.dataType() != DataType.FLOAT32) {
                Log.e("MLPerf_Terminal", "CRITICAL ERROR: Model is ${outputTensor.dataType()}! Code expects FLOAT32. Re-export your YOLO model.")
                return emptyList()
            }

            // 2. Allocate space to store the model outputs
            val shape = outputTensor.shape()
            val output = Array(1) { Array(shape[1]) { FloatArray(shape[2]) } }

            // 3. Reset the input memory buffer pointer to the start index
            inputBuffer.rewind()
            
            // 4. Run the model prediction (synchronized to prevent thread collisions)
            synchronized(this) {
                interpreter.run(inputBuffer, output)
            }

            // 5. Decode the outputs into boxes and filter out overlapping matches
            postProcess(output[0], shape[1], shape[2])
        } catch (e: Exception) {
            Log.e("MLPerf_Terminal", "Hardware or Inference Error CAUGHT: ${e.message}")
            emptyList()
        }
    }

    /**
     * Uses OpenCV to rotate, resize, and write camera image pixels to the model buffer.
     */
    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun preprocess(image: ImageProxy) {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride

        // Wrap the raw camera frame buffer into an OpenCV Mat object to avoid copying memory
        val mat = Mat(image.height, image.width, CvType.CV_8UC4, buffer, rowStride.toLong())

        // Rotate the image depending on camera orientation
        val rotatedMat = Mat()
        when (image.imageInfo.rotationDegrees) {
            90 -> Core.rotate(mat, rotatedMat, Core.ROTATE_90_CLOCKWISE)
            180 -> Core.rotate(mat, rotatedMat, Core.ROTATE_180)
            270 -> Core.rotate(mat, rotatedMat, Core.ROTATE_90_COUNTERCLOCKWISE)
            else -> mat.copyTo(rotatedMat)
        }

        // Convert the color format from RGBA to standard RGB
        val rgbMat = Mat()
        Imgproc.cvtColor(rotatedMat, rgbMat, Imgproc.COLOR_RGBA2RGB)

        // Resize the image to 640x640 pixels
        val resizedMat = Mat()
        Imgproc.resize(rgbMat, resizedMat, Size(inputSize.toDouble(), inputSize.toDouble()))

        // Copy raw pixel bytes to our Java byte array
        resizedMat.get(0, 0, pixelBuffer)

        // Convert pixel bytes (0 to 255) to float decimals (0.0 to 1.0) and write to inputBuffer
        inputBuffer.rewind()
        for (i in 0 until (inputSize * inputSize * 3)) {
            val byteVal = pixelBuffer[i].toInt() and 0xFF
            inputBuffer.putFloat(byteVal / 255f)
        }

        // Release OpenCV native memory immediately to prevent memory leaks
        mat.release()
        rotatedMat.release()
        rgbMat.release()
        resizedMat.release()
    }

    /**
     * Decodes model output into bounding boxes and filters out duplicates.
     */
    private fun postProcess(output: Array<FloatArray>, dim1: Int, dim2: Int): List<Detection> {
        val detections = mutableListOf<Detection>()
        
        // Check if output dimensions are transposed (swapped)
        val isTransposed = dim1 > dim2
        val numBoxes = if (isTransposed) dim1 else dim2

        // Loop through all candidate prediction boxes (e.g. 8400 boxes)
        for (i in 0 until numBoxes) {
            // Get the confidence score for the current box
            val score = if (isTransposed) output[i][4] else output[4][i]

            // If score is above confidence threshold, parse box coordinates
            if (score > confidenceThreshold) {
                // Get raw center coordinates (cx, cy) and size (w, h)
                var cx = if (isTransposed) output[i][0] else output[0][i]
                var cy = if (isTransposed) output[i][1] else output[1][i]
                var w  = if (isTransposed) output[i][2] else output[2][i]
                var h  = if (isTransposed) output[i][3] else output[3][i]

                // If coordinates are absolute coordinates (0 to 640), scale them down to range (0.0 to 1.0)
                if (cx > 1f || cy > 1f || w > 1f || h > 1f) {
                    cx /= 640f
                    cy /= 640f
                    w /= 640f
                    h /= 640f
                }

                // Convert center coordinates (CX, CY, W, H) to box boundaries (Left, Top, Right, Bottom)
                val left = cx - w / 2f
                val top = cy - h / 2f
                val right = cx + w / 2f
                val bottom = cy + h / 2f

                // Clamp values between 0.0 and 1.0 so box fits on the screen
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

        // Run Non-Maximum Suppression to remove overlapping duplicate boxes
        return nms(detections)
    }

    /**
     * Keeps only the highest scoring box when multiple boxes overlap on the same target.
     */
    private fun nms(detections: List<Detection>, iouThreshold: Float = 0.5f): List<Detection> {
        val result = mutableListOf<Detection>()
        
        // Sort all detections by their confidence score, highest first
        val sorted = detections.sortedByDescending { it.score }
        val suppressed = BooleanArray(sorted.size)

        for (i in sorted.indices) {
            if (suppressed[i]) continue

            val best = sorted[i]
            result.add(best) // Keep the highest score box

            // Draw a maximum of 10 boxes on screen at one time
            if (result.size >= 10) break

            // Suppress other boxes that overlap heavily with our selected box
            for (j in (i + 1) until sorted.size) {
                if (!suppressed[j] && iou(best.box, sorted[j].box) > iouThreshold) {
                    suppressed[j] = true
                }
            }
        }

        return result
    }

    /**
     * Calculates the overlap percentage (Intersection-over-Union) between two boxes.
     */
    private fun iou(a: RectF, b: RectF): Float {
        // Calculate areas of both boxes
        val areaA = (a.right - a.left) * (a.bottom - a.top)
        val areaB = (b.right - b.left) * (b.bottom - b.top)

        // Find boundary edges of the overlapping box
        val left = max(a.left, b.left)
        val top = max(a.top, b.top)
        val right = min(a.right, b.right)
        val bottom = min(a.bottom, b.bottom)

        // Calculate overlapping area size
        val interArea = max(0f, right - left) * max(0f, bottom - top)
        
        // Intersection divided by total union area
        return interArea / (areaA + areaB - interArea)
    }

    /**
     * Closes the engine to release native system memory.
     */
    fun close() {
        synchronized(this) {
            interpreter.close()
        }
    }
}