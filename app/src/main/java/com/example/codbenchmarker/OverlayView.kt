package com.example.codbenchmarker

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class OverlayView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    private var results: List<Detection> = emptyList()

    // Neon Green HUD style
    private val boxPaint = Paint().apply {
        color = Color.parseColor("#00FF41")
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }

    private val boxFillPaint = Paint().apply {
        color = Color.parseColor("#1500FF41") // 8% opacity neon green fill
        style = Paint.Style.FILL
    }

    private val cornerPaint = Paint().apply {
        color = Color.parseColor("#00FF41")
        strokeWidth = 8f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val textPaint = Paint().apply {
        color = Color.BLACK
        textSize = 34f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        style = Paint.Style.FILL
    }

    private val tagBackgroundPaint = Paint().apply {
        color = Color.parseColor("#00FF41")
        style = Paint.Style.FILL
    }

    // Faint HUD framing lines
    private val hudFramingPaint = Paint().apply {
        color = Color.parseColor("#2500FF41") // Faint neon green
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    private var frameWidth = 0
    private var frameHeight = 0

    fun setFrameSize(width: Int, height: Int) {
        frameWidth = width
        frameHeight = height
    }

    fun setResults(newResults: List<Detection>) {
        results = newResults
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw screen framing HUD corners and crosshair
        drawHudFraming(canvas)

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        for (det in results) {
            val rect = if (frameWidth > 0 && frameHeight > 0) {
                // Correct mapping with Aspect Ratio and Scaling offsets (FILL_CENTER mapping)
                val scale = maxOf(viewWidth / frameWidth, viewHeight / frameHeight)
                val scaledWidth = frameWidth * scale
                val scaledHeight = frameHeight * scale
                val offsetX = (viewWidth - scaledWidth) / 2f
                val offsetY = (viewHeight - scaledHeight) / 2f

                RectF(
                    det.box.left * scaledWidth + offsetX,
                    det.box.top * scaledHeight + offsetY,
                    det.box.right * scaledWidth + offsetX,
                    det.box.bottom * scaledHeight + offsetY
                )
            } else {
                RectF(
                    det.box.left * viewWidth,
                    det.box.top * viewHeight,
                    det.box.right * viewWidth,
                    det.box.bottom * viewHeight
                )
            }

            // 1. Draw box background fill
            canvas.drawRect(rect, boxFillPaint)

            // 2. Draw standard bounding box thin border
            canvas.drawRect(rect, boxPaint)

            // 3. Draw heavy corner brackets for targeting effect
            val cornerLen = min(rect.width() * 0.15f, 40f)
            // Top-Left corner
            canvas.drawLine(rect.left, rect.top, rect.left + cornerLen, rect.top, cornerPaint)
            canvas.drawLine(rect.left, rect.top, rect.left, rect.top + cornerLen, cornerPaint)
            // Top-Right corner
            canvas.drawLine(rect.right, rect.top, rect.right - cornerLen, rect.top, cornerPaint)
            canvas.drawLine(rect.right, rect.top, rect.right, rect.top + cornerLen, cornerPaint)
            // Bottom-Left corner
            canvas.drawLine(rect.left, rect.bottom, rect.left + cornerLen, rect.bottom, cornerPaint)
            canvas.drawLine(rect.left, rect.bottom, rect.left, rect.bottom - cornerLen, cornerPaint)
            // Bottom-Right corner
            canvas.drawLine(rect.right, rect.bottom, rect.right - cornerLen, rect.bottom, cornerPaint)
            canvas.drawLine(rect.right, rect.bottom, rect.right, rect.bottom - cornerLen, cornerPaint)

            // 4. Draw label tag at the top
            val labelText = String.format("%s %.0f%%", det.label, det.score * 100)
            val textWidth = textPaint.measureText(labelText)
            val textHeight = 34f
            val padding = 8f

            val tagRect = RectF(
                rect.left,
                rect.top - textHeight - (padding * 2),
                rect.left + textWidth + (padding * 2),
                rect.top
            )
            
            // Adjust tag if it goes above the screen
            if (tagRect.top < 0) {
                tagRect.offsetTo(tagRect.left, rect.top)
                canvas.drawRect(tagRect, tagBackgroundPaint)
                canvas.drawText(labelText, tagRect.left + padding, tagRect.bottom - padding, textPaint)
            } else {
                canvas.drawRect(tagRect, tagBackgroundPaint)
                canvas.drawText(labelText, tagRect.left + padding, tagRect.bottom - padding, textPaint)
            }
        }
    }

    private fun drawHudFraming(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val margin = 50f
        val bracketSize = 65f

        // Draw outer framing brackets
        // Top-Left
        canvas.drawLine(margin, margin, margin + bracketSize, margin, hudFramingPaint)
        canvas.drawLine(margin, margin, margin, margin + bracketSize, hudFramingPaint)
        // Top-Right
        canvas.drawLine(w - margin, margin, w - margin - bracketSize, margin, hudFramingPaint)
        canvas.drawLine(w - margin, margin, w - margin, margin + bracketSize, hudFramingPaint)
        
        // Bottom framing brackets (offset vertically to avoid panel)
        val bottomOffset = h * 0.40f 
        val bMarginY = h - bottomOffset
        // Bottom-Left
        canvas.drawLine(margin, bMarginY, margin + bracketSize, bMarginY, hudFramingPaint)
        canvas.drawLine(margin, bMarginY, margin, bMarginY - bracketSize, hudFramingPaint)
        // Bottom-Right
        canvas.drawLine(w - margin, bMarginY, w - margin - bracketSize, bMarginY, hudFramingPaint)
        canvas.drawLine(w - margin, bMarginY, w - margin, bMarginY - bracketSize, hudFramingPaint)

        // Draw crosshairs at center targeting area
        val cx = w / 2f
        val cy = h / 2f - (bottomOffset / 4f)
        val gap = 30f
        val lineLen = 40f
        
        canvas.drawLine(cx - gap - lineLen, cy, cx - gap, cy, hudFramingPaint)
        canvas.drawLine(cx + gap, cy, cx + gap + lineLen, cy, hudFramingPaint)
        canvas.drawLine(cx, cy - gap - lineLen, cx, cy - gap, hudFramingPaint)
        canvas.drawLine(cx, cy + gap, cx, cy + gap + lineLen, hudFramingPaint)
    }
}