package com.foxtrotalpha.reelsblocker.ui.chart

data class ChartPoint(
    val label: String,
    val value: Float,
    val highlight: Boolean = false,
)

fun interface ChartValueFormatter {
    fun format(value: Float): String
}
