package com.foxtrotalpha.reelsblocker.ui.chart

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.provider.Settings
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import com.foxtrotalpha.reelsblocker.R

class BarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.zen_divider)
        strokeWidth = 1f * resources.displayMetrics.density
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

    private var points: List<ChartPoint> = emptyList()
    private var showAverage = true
    private var sparkline = false
    private var progress = 1f
    private var animator: ValueAnimator? = null
    private val barRect = RectF()

    var barColor: Int = ContextCompat.getColor(context, R.color.zen_sage)
        set(value) {
            field = value
            barPaint.color = value
            invalidate()
        }

    var highlightColor: Int = ContextCompat.getColor(context, R.color.zen_gold)
        set(value) {
            field = value
            highlightPaint.color = value
            invalidate()
        }

    init {
        barPaint.color = barColor
        highlightPaint.color = highlightColor
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
            duration = (450 * scale).toLong()
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun setShowAverage(show: Boolean) {
        showAverage = show
        invalidate()
    }

    fun setSparkline(enabled: Boolean) {
        sparkline = enabled
        if (enabled) {
            showAverage = false
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (points.isEmpty()) return

        val density = resources.displayMetrics.density
        val labelHeight = if (sparkline) 0f else 20f * density
        val chartBottom = height - labelHeight
        val chartTop = if (sparkline) 2f * density else 8f * density
        val chartHeight = (chartBottom - chartTop).coerceAtLeast(1f)
        val maxValue = points.maxOf { it.value }.coerceAtLeast(1f)
        val average = points.map { it.value }.average().toFloat()
        val slotWidth = width / points.size.toFloat()
        val barWidth = slotWidth * if (sparkline) 0.58f else 0.46f
        val radius = (if (sparkline) 4f else 6f) * density

        if (!sparkline) {
            canvas.drawLine(0f, chartBottom, width.toFloat(), chartBottom, axisPaint)
        }

        if (showAverage && average > 0f) {
            val y = chartBottom - (average / maxValue) * chartHeight * progress
            canvas.drawLine(0f, y, width.toFloat(), y, averagePaint)
        }

        points.forEachIndexed { index, point ->
            val centerX = slotWidth * index + slotWidth / 2f
            val barHeight = if (point.value <= 0f) {
                3f * density
            } else {
                (point.value / maxValue) * chartHeight * progress
            }
            barRect.set(
                centerX - barWidth / 2f,
                chartBottom - barHeight,
                centerX + barWidth / 2f,
                chartBottom,
            )
            val paint = if (point.highlight) highlightPaint else barPaint
            canvas.drawRoundRect(barRect, radius, radius, paint)
            if (!sparkline) {
                canvas.drawText(point.label, centerX, height - 4f * density, labelPaint)
            }
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }
}
