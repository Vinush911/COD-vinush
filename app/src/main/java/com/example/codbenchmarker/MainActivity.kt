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

class MainActivity : AppCompatActivity() {

    private var detector: CamouflageDetector? = null
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

    private var frameCount = 0
    private var lastFpsTimestamp = System.currentTimeMillis()
    private var currentFps = 0
    private var currentThreads = 4

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize OpenCV natively
        if (!OpenCVLoader.initDebug()) {
            Log.e("COD_DEBUG", "OpenCV initialization failed!")
        } else {
            Log.d("COD_DEBUG", "OpenCV initialized successfully.")
        }

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Bind HUD Views
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

        // Blinking red dot animation for tactical HUD feed
        val pulseAnimation = AlphaAnimation(1.0f, 0.1f).apply {
            duration = 600
            repeatMode = Animation.REVERSE
            repeatCount = Animation.INFINITE
        }
        liveIndicatorDot.startAnimation(pulseAnimation)

        detector = CamouflageDetector(this, "yolov8n_float16.tflite")

        // Wire Confidence Seekbar
        confidenceSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val threshold = progress / 100f
                confPercentText.text = "$progress%"
                detector?.setConfidenceThreshold(threshold)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Wire Thread adjustments
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

        if (allPermissionsGranted()) startCamera()
        else requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 101)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(findViewById<PreviewView>(R.id.viewFinder).surfaceProvider)
            }

            val analyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build().also {
                    it.setAnalyzer(Executors.newSingleThreadExecutor()) { proxy -> processFrame(proxy) }
                }

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analyzer)
        }, ContextCompat.getMainExecutor(this))
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun processFrame(imageProxy: ImageProxy) {
        val start = System.nanoTime()
        try {
            val boxes = detector?.runInference(imageProxy) ?: emptyList()
            val latency = (System.nanoTime() - start) / 1_000_000.0

            val rotation = imageProxy.imageInfo.rotationDegrees
            val frameWidth = if (rotation == 90 || rotation == 270) imageProxy.height else imageProxy.width
            val frameHeight = if (rotation == 90 || rotation == 270) imageProxy.width else imageProxy.height

            frameCount++
            val currentTime = System.currentTimeMillis()
            var updateMetricsUI = false

            if (currentTime - lastFpsTimestamp >= 1000) {
                currentFps = frameCount
                frameCount = 0
                lastFpsTimestamp = currentTime
                updateMetricsUI = true
            }

            runOnUiThread {
                if (updateMetricsUI) {
                    val runtime = Runtime.getRuntime()
                    val usedMemMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)

                    latencyView.text = String.format("%.1f ms", latency)
                    fpsView.text = "$currentFps FPS"
                    ramView.text = "$usedMemMb MB"
                }
                targetsView.text = String.format("%02d", boxes.size)
                overlay.setFrameSize(frameWidth, frameHeight)
                overlay.setResults(boxes)
                overlay.invalidate()
            }
        } catch (e: Exception) {
            Log.e("COD_DEBUG", "Inference Error: ${e.message}")
        } finally {
            imageProxy.close()
        }
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == 0

    override fun onRequestPermissionsResult(rc: Int, p: Array<String>, g: IntArray) {
        super.onRequestPermissionsResult(rc, p, g)
        if (rc == 101 && g.isNotEmpty() && g[0] == 0) startCamera()
    }

    override fun onDestroy() {
        super.onDestroy()
        detector?.close()
    }
}