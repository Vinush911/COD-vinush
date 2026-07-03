package com.example.codbenchmarker

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/**
 * This custom view draws the green target boxes and static HUD lines 
 * on top of the camera preview screen.
 */
class OverlayView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    // A list containing the current targets to display on the screen
    private var results: List<Detection> = emptyList()

    // --- Paint settings used for drawing green HUD styles ---

    // Paint for drawing the thin border of target boxes
    private val boxPaint = Paint().apply {
        color = Color.parseColor("#00FF41") // Neon Green
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }

    // Paint for filling the inside of target boxes with light green transparent color
    private val boxFillPaint = Paint().apply {
        color = Color.parseColor("#1500FF41") // Neon Green with 8% opacity
        style = Paint.Style.FILL
    }

    // Paint for drawing the thick targeting corners on each box
    private val cornerPaint = Paint().apply {
        color = Color.parseColor("#00FF41")
        strokeWidth = 8f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND // Makes the lines have rounded ends
    }

    // Paint for drawing the text labels (like "PERSON 80%")
    private val textPaint = Paint().apply {
        color = Color.BLACK
        textSize = 34f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) // Monospace block font
        style = Paint.Style.FILL
    }

    // Paint for drawing the green tag background box behind text labels
    private val tagBackgroundPaint = Paint().apply {
        color = Color.parseColor("#00FF41")
        style = Paint.Style.FILL
    }

    // Paint for drawing static background lines (the outer corner brackets and center crosshair)
    private val hudFramingPaint = Paint().apply {
        color = Color.parseColor("#2500FF41") // Neon Green with 14% opacity
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    // Stores the width and height of the camera frame to help scale boxes correctly
    private var frameWidth = 0
    private var frameHeight = 0

    /**
     * Sets the size of the camera picture to align coordinate systems.
     */
    fun setFrameSize(width: Int, height: Int) {
        frameWidth = width
        frameHeight = height
    }

    /**
     * Updates the targets to draw and requests the system to redraw the view.
     */
    fun setResults(newResults: List<Detection>) {
        results = newResults
        invalidate() // Tells Android to run onDraw() to redraw the screen
    }

    /**
     * Called by Android when it draws this view on the screen.
     */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw the background frame lines and center target crosshair
        drawHudFraming(canvas)

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        // Loop through each target found by the model and draw them
        for (det in results) {
            // Convert normalized coordinates (0.0 to 1.0) of target box to screen pixel coordinates
            val rect = if (frameWidth > 0 && frameHeight > 0) {
                // Calculate how much we need to scale the camera frame to fit the screen
                val scale = maxOf(viewWidth / frameWidth, viewHeight / frameHeight)
                val scaledWidth = frameWidth * scale
                val scaledHeight = frameHeight * scale
                
                // Adjust position offsets to center the camera frame in the view area
                val offsetX = (viewWidth - scaledWidth) / 2f
                val offsetY = (viewHeight - scaledHeight) / 2f

                // Build the final box coordinates
                RectF(
                    det.box.left * scaledWidth + offsetX,
                    det.box.top * scaledHeight + offsetY,
                    det.box.right * scaledWidth + offsetX,
                    det.box.bottom * scaledHeight + offsetY
                )
            } else {
                // Fallback: stretch/squish detection box to fill the entire view area
                RectF(
                    det.box.left * viewWidth,
                    det.box.top * viewHeight,
                    det.box.right * viewWidth,
                    det.box.bottom * viewHeight
                )
            }

            // Draw translucent green background fill inside the box
            canvas.drawRect(rect, boxFillPaint)

            // Draw thin green outline border around the box
            canvas.drawRect(rect, boxPaint)

            // Draw thick corner brackets around the box for targeting appearance
            val cornerLen = min(rect.width() * 0.15f, 40f) // Keep corners proportional but max out at 40 pixels
            
            // Top-Left corner lines
            canvas.drawLine(rect.left, rect.top, rect.left + cornerLen, rect.top, cornerPaint)
            canvas.drawLine(rect.left, rect.top, rect.left, rect.top + cornerLen, cornerPaint)
            
            // Top-Right corner lines
            canvas.drawLine(rect.right, rect.top, rect.right - cornerLen, rect.top, cornerPaint)
            canvas.drawLine(rect.right, rect.top, rect.right, rect.top + cornerLen, cornerPaint)
            
            // Bottom-Left corner lines
            canvas.drawLine(rect.left, rect.bottom, rect.left + cornerLen, rect.bottom, cornerPaint)
            canvas.drawLine(rect.left, rect.bottom, rect.left, rect.bottom - cornerLen, cornerPaint)
            
            // Bottom-Right corner lines
            canvas.drawLine(rect.right, rect.bottom, rect.right - cornerLen, rect.bottom, cornerPaint)
            canvas.drawLine(rect.right, rect.bottom, rect.right, rect.bottom - cornerLen, cornerPaint)

            // Draw label tag card showing target name and percentage
            val labelText = det.formattedLabel
            val textWidth = textPaint.measureText(labelText)
            val textHeight = 34f
            val padding = 8f

            val tagWidth = textWidth + (padding * 2)
            val tagLeft = maxOf(0f, minOf(rect.left, viewWidth - tagWidth)) // Keep tag on screen horizontally

            // Create boundaries of the tag label card
            val tagRect = RectF(
                tagLeft,
                rect.top - textHeight - (padding * 2),
                tagLeft + tagWidth,
                rect.top
            )
            
            // Move tag inside the box if it gets cut off at the top of the screen
            if (tagRect.top < 0) {
                tagRect.offsetTo(tagRect.left, rect.top)
            }
            // Draw the green background tag block
            canvas.drawRect(tagRect, tagBackgroundPaint)
            // Draw the text inside the tag block
            canvas.drawText(labelText, tagRect.left + padding, tagRect.bottom - padding, textPaint)
        }
    }

    /**
     * Draws static target frame brackets at the screen corners and a targeting crosshair in the center.
     */
    private fun drawHudFraming(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val margin = 50f
        val bracketSize = 65f

        // Draw Outer screen framing brackets:
        // Top-Left corner bracket
        canvas.drawLine(margin, margin, margin + bracketSize, margin, hudFramingPaint)
        canvas.drawLine(margin, margin, margin, margin + bracketSize, hudFramingPaint)
        
        // Top-Right corner bracket
        canvas.drawLine(w - margin, margin, w - margin - bracketSize, margin, hudFramingPaint)
        canvas.drawLine(w - margin, margin, w - margin, margin + bracketSize, hudFramingPaint)
        
        // Bottom framing brackets (shifted up to clear HUD controls panel)
        val bottomOffset = h * 0.40f 
        val bMarginY = h - bottomOffset
        
        // Bottom-Left corner bracket
        canvas.drawLine(margin, bMarginY, margin + bracketSize, bMarginY, hudFramingPaint)
        canvas.drawLine(margin, bMarginY, margin, bMarginY - bracketSize, hudFramingPaint)
        
        // Bottom-Right corner bracket
        canvas.drawLine(w - margin, bMarginY, w - margin - bracketSize, bMarginY, hudFramingPaint)
        canvas.drawLine(w - margin, bMarginY, w - margin, bMarginY - bracketSize, hudFramingPaint)

        // Draw targeting crosshair at the center of the screen
        val cx = w / 2f
        val cy = h / 2f - (bottomOffset / 4f)
        val gap = 30f       // Center clear space
        val lineLen = 40f   // Crosshair line length
        
        canvas.drawLine(cx - gap - lineLen, cy, cx - gap, cy, hudFramingPaint) // Left line
        canvas.drawLine(cx + gap, cy, cx + gap + lineLen, cy, hudFramingPaint) // Right line
        canvas.drawLine(cx, cy - gap - lineLen, cx, cy - gap, hudFramingPaint) // Top line
        canvas.drawLine(cx, cy + gap, cx, cy + gap + lineLen, hudFramingPaint) // Bottom line
    }
}