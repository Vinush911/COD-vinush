package com.example.codbenchmarker

import android.os.Bundle
import android.widget.TextView
import android.widget.SeekBar
import android.widget.Button
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import android.util.Log
import org.opencv.android.OpenCVLoader

/**
 * This is the main screen of the application. It handles camera permissions, 
 * sets up the camera feed, and displays performance metrics (FPS, Latency, RAM) on the screen.
 */
class MainActivity : AppCompatActivity() {

    // Helper object to run the target detector model
    private var detector: CamouflageDetector? = null
    
    // UI elements to show camera preview, boxes, and telemetry numbers
    private lateinit var overlay: OverlayView
    private lateinit var fpsView: TextView
    private lateinit var latencyView: TextView
    private lateinit var ramView: TextView
    private lateinit var targetsView: TextView

    private lateinit var confidenceSeekBar: SeekBar
    private lateinit var confPercentText: TextView
    private lateinit var threadsCountText: TextView
    private lateinit var btnDecThreads: Button
    private lateinit var btnIncThreads: Button
    private lateinit var liveIndicatorDot: View

    // Variables used to count FPS and keep track of CPU threads
    private var frameCount = 0
    private var lastFpsTimestamp = System.currentTimeMillis()
    private var currentFps = 0
    private var currentThreads = 4 // Start with 4 CPU threads

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Initialize OpenCV native library for fast image rotation and resizing
        if (!OpenCVLoader.initDebug()) {
            Log.e("COD_DEBUG", "OpenCV initialization failed!")
        } else {
            Log.d("COD_DEBUG", "OpenCV initialized successfully.")
        }

        // 2. Set the screen to draw edge-to-edge and load layout XML file
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // 3. Connect view variables to the actual layout UI components
        overlay = findViewById(R.id.boundingBoxOverlay)
        fpsView = findViewById(R.id.fpsVal)
        latencyView = findViewById(R.id.latencyVal)
        ramView = findViewById(R.id.ramVal)
        targetsView = findViewById(R.id.targetsCountVal)

        confidenceSeekBar = findViewById(R.id.confidenceSeekBar)
        confPercentText = findViewById(R.id.confPercentText)
        threadsCountText = findViewById(R.id.threadsCountText)
        btnDecThreads = findViewById(R.id.btnDecThreads)
        btnIncThreads = findViewById(R.id.btnIncThreads)
        liveIndicatorDot = findViewById(R.id.liveIndicatorDot)

        // 4. Start a blinking animation on the red live feed indicator dot
        val pulseAnimation = AlphaAnimation(1.0f, 0.1f).apply {
            duration = 600
            repeatMode = Animation.REVERSE
            repeatCount = Animation.INFINITE
        }
        liveIndicatorDot.startAnimation(pulseAnimation)

        // 5. Initialize the detector with our YOLO model file
        detector = CamouflageDetector(this, "yolov8n_float16.tflite")

        // 6. Connect the confidence slider bar to filter weak detections dynamically
        confidenceSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val threshold = progress / 100f
                confPercentText.text = "$progress%"
                detector?.setConfidenceThreshold(threshold)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 7. Configure thread control buttons to increase/decrease CPU usage
        btnDecThreads.setOnClickListener {
            if (currentThreads > 1) {
                currentThreads--
                threadsCountText.text = currentThreads.toString()
                detector?.setThreadCount(currentThreads)
            }
        }
        btnIncThreads.setOnClickListener {
            if (currentThreads < 8) {
                currentThreads++
                threadsCountText.text = currentThreads.toString()
                detector?.setThreadCount(currentThreads)
            }
        }

        // 8. Request camera permission, or start camera if already granted
        if (allPermissionsGranted()) startCamera()
        else requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 101)
    }

    /**
     * Initializes and starts the camera stream using Android CameraX.
     */
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            
            // Set up the viewfinder screen preview
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(findViewById<PreviewView>(R.id.viewFinder).surfaceProvider)
            }

            // Set up the image analyzer. It drops old frames so we only process the latest camera frame.
            val analyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build().also {
                    // Run the frame processing on a separate background thread so the screen stays responsive
                    it.setAnalyzer(Executors.newSingleThreadExecutor()) { proxy -> processFrame(proxy) }
                }

            // Link camera preview and analysis to the screen's active lifecycle
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analyzer)
        }, ContextCompat.getMainExecutor(this))
    }

    /**
     * Processes each live camera frame, runs model detection, and updates performance stats.
     */
    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun processFrame(imageProxy: ImageProxy) {
        val start = System.nanoTime()
        try {
            // Run target detector model on the current frame
            val boxes = detector?.runInference(imageProxy) ?: emptyList()
            // Calculate how long (latency) the model took in milliseconds
            val latency = (System.nanoTime() - start) / 1_000_000.0

            // Get rotation from camera sensor to orient target boxes correctly
            val rotation = imageProxy.imageInfo.rotationDegrees
            val frameWidth = if (rotation == 90 || rotation == 270) imageProxy.height else imageProxy.width
            val frameHeight = if (rotation == 90 || rotation == 270) imageProxy.width else imageProxy.height

            // Calculate processed frames count in the last second to get live FPS
            frameCount++
            val currentTime = System.currentTimeMillis()
            var updateMetricsUI = false

            if (currentTime - lastFpsTimestamp >= 1000) {
                currentFps = frameCount
                frameCount = 0
                lastFpsTimestamp = currentTime
                updateMetricsUI = true
            }

            // Run layout view updates on the main UI thread
            runOnUiThread {
                if (updateMetricsUI) {
                    // Read current app memory usage in Megabytes (MB)
                    val runtime = Runtime.getRuntime()
                    val usedMemMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)

                    // Show stats on screen labels
                    latencyView.text = String.format("%.1f ms", latency)
                    fpsView.text = "$currentFps FPS"
                    ramView.text = "$usedMemMb MB"
                }
                targetsView.text = String.format("%02d", boxes.size)
                
                // Draw boxes and target labels on top of the preview screen
                overlay.setFrameSize(frameWidth, frameHeight)
                overlay.setResults(boxes)
                overlay.invalidate()
            }
        } catch (e: Exception) {
            Log.e("COD_DEBUG", "Inference Error: ${e.message}")
        } finally {
            // CRITICAL: Always close the image frame to let CameraX send the next frame
            imageProxy.close()
        }
    }

    // Helper to check if camera permission has been granted
    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == 0

    // Handles result when the user grants or denies camera permission
    override fun onRequestPermissionsResult(rc: Int, p: Array<String>, g: IntArray) {
        super.onRequestPermissionsResult(rc, p, g)
        if (rc == 101 && g.isNotEmpty() && g[0] == 0) startCamera()
    }

    // Runs when the app screen is closed. We close the detector to release memory.
    override fun onDestroy() {
        super.onDestroy()
        detector?.close()
    }
}