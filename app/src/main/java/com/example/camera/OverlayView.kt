package com.example.camera

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log
import android.view.View

class OverlayView constructor(context: Context?, attributeSet: AttributeSet?):
    View(context, attributeSet){
        private var results: List<Pair<Float, Float>> = emptyList()
        private var mirrored: Boolean = false
        private var numJoints = 4
        private var isUsingThunder: Boolean = false

        private val yellowDotPaint = Paint().apply {
            color = android.graphics.Color.YELLOW
            style = Paint.Style.FILL
        }

        private val cyanDotPaint = Paint().apply {
            color = android.graphics.Color.CYAN
            style = Paint.Style.FILL
        }

        private val linePaint = Paint().apply {
            color = android.graphics.Color.GREEN
            style = Paint.Style.STROKE
            strokeWidth = 20f
        }


        fun drawJoints(results: List<Pair<Float, Float>>, mirrored: Boolean, isUsingThunder: Boolean, barbellMode: Boolean) {
            this.results = results
            this.mirrored = mirrored
            this.isUsingThunder = isUsingThunder
            this.numJoints  = if (barbellMode) 3 else 4
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (results.size == numJoints) {
                for (i in 0 until results.size - 1) {
                    val (x1, y1) = results[i]
                    val (x2, y2) = results[i + 1]
                    val startX = width - (x1 * width)
                    val startY = if (mirrored) height - (y1 * height) else y1 * height
                    val endX = width - (x2 * width)
                    val endY = if (mirrored) height - (y2 * height) else y2 * height
                    canvas.drawLine(startX, startY, endX, endY, linePaint)
                }
            }
            for ((x, y) in results) {
                val drawX = width - (x*width)
                val drawY = if (mirrored) height - (y*height) else y*height
                canvas.drawCircle(drawX, drawY, 20f, if (isUsingThunder) cyanDotPaint else yellowDotPaint)
            }
        }
    }