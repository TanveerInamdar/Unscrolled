package com.foxtrotalpha.reelsblocker.ui.chart

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.provider.Settings
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import com.foxtrotalpha.reelsblocker.R

class LineChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * resources.displayMetrics.density
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.zen_divider)
        strokeWidth = resources.displayMetrics.density
    }
    private val averagePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.zen_text_tertiary)
        strokeWidth = 1.5f * resources.displayMetrics.density
        pathEffect = DashPathEffect(floatArrayOf(8f, 8f), 0f)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.zen_text_tertiary)
        textSize = 11f * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.CENTER
    }
    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var points: List<ChartPoint> = emptyList()
    private var sparkline = false
    private var progress = 1f
    private var animator: ValueAnimator? = null
    private val linePath = Path()
    private val fillPath = Path()

    var lineColor: Int = ContextCompat.getColor(context, R.color.zen_gold)
        set(value) {
            field = value
            linePaint.color = value
            pointPaint.color = value
            invalidate()
        }

    var fillColor: Int = ContextCompat.getColor(context, R.color.zen_gold_fill)
        set(value) {
            field = value
            fillPaint.color = value
            invalidate()
        }

    init {
        linePaint.color = lineColor
        fillPaint.color = fillColor
        pointPaint.color = lineColor
    }

    fun setSparkline(enabled: Boolean) {
        sparkline = enabled
        linePaint.strokeWidth = (if (enabled) 2.25f else 3f) * resources.displayMetrics.density
        invalidate()
    }

    fun setPoints(newPoints: List<ChartPoint>, animate: Boolean = true) {
        points = newPoints
        val scale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
        if (!animate || scale <= 0f) {
            progress = 1f
            invalidate()
            return
        }
        animator?.cancel()
        progress = 0f
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = (500 * scale).toLong()
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (points.isEmpty()) return

        val density = resources.displayMetrics.density
        val labelHeight = if (sparkline) 0f else 20f * density
        val chartBottom = height - labelHeight
        val chartTop = if (sparkline) 4f * density else 12f * density
        val chartHeight = (chartBottom - chartTop).coerceAtLeast(1f)
        val maxValue = points.maxOf { it.value }.coerceAtLeast(1f)
        val average = points.map { it.value }.average().toFloat()
        val slotWidth = width / points.size.toFloat()

        if (!sparkline) {
            canvas.drawLine(0f, chartBottom, width.toFloat(), chartBottom, axisPaint)
            if (average > 0f) {
                val y = chartBottom - (average / maxValue) * chartHeight * progress
                canvas.drawLine(0f, y, width.toFloat(), y, averagePaint)
            }
        }

        linePath.reset()
        fillPath.reset()
        points.forEachIndexed { index, point ->
            val x = slotWidth * index + slotWidth / 2f
            val y = chartBottom - (point.value / maxValue) * chartHeight * progress
            if (index == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, chartBottom)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        val lastX = slotWidth * (points.lastIndex) + slotWidth / 2f
        fillPath.lineTo(lastX, chartBottom)
        fillPath.close()

        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(linePath, linePaint)

        points.forEachIndexed { index, point ->
            val x = slotWidth * index + slotWidth / 2f
            val y = chartBottom - (point.value / maxValue) * chartHeight * progress
            if (!sparkline) {
                canvas.drawCircle(x, y, 3.5f * density, pointPaint)
                canvas.drawText(point.label, x, height - 4f * density, labelPaint)
            }
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }
}
