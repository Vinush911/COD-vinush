package com.example.codbenchmarker

import android.util.Log
import android.graphics.Bitmap

/**
 * This object is used to test the model's speed and memory usage
 * by running it on a list of images.
 */
object ModelBenchmarker {
    // This label identifies our messages in the Android log window (Logcat)
    private const val TAG = "MLPerf_Terminal"

    /**
     * Runs the speed test on a list of images.
     * 
     * @param images The list of pictures to test.
     * @param inferenceBlock The code that actually runs the model on a picture.
     */
    fun runBenchmark(images: List<Bitmap>, inferenceBlock: (Bitmap) -> Unit) {
        // Print the top header of our results table
        Log.i(TAG, String.format("%-12s | %-15s | %-10s | %-15s", "Dataset", "Latency (ms)", "FPS", "Memory Δ (MB)"))
        Log.i(TAG, "-".repeat(60))

        // Go through each image one by one
        images.forEachIndexed { index, bitmap ->
            // Record how much memory the app is using before starting
            val startMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
            // Record the exact start time in nanoseconds
            val startTime = System.nanoTime()

            // Run the model on the current image
            inferenceBlock(bitmap)

            // Record the exact end time in nanoseconds
            val endTime = System.nanoTime()
            // Record how much memory the app is using after running the model
            val endMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()

            // Calculate latency (how long it took) in milliseconds
            val latencyMs = (endTime - startTime) / 1_000_000.0
            
            // Calculate how many frames could be processed per second (FPS)
            val fps = if (latencyMs > 0) 1000.0 / latencyMs else 0.0
            
            // Calculate the change in memory usage in Megabytes (MB)
            val memoryUsedMb = Math.max(0.0, (endMemory - startMemory) / (1024.0 * 1024.0))

            // Print the results for this image to the log window
            Log.i(TAG, String.format("Image %-6d | %-15.2f | %-10.2f | %-15.2f", index + 1, latencyMs, fps, memoryUsedMb))
        }
    }
}