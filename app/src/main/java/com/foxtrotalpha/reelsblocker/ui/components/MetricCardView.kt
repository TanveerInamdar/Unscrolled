package com.foxtrotalpha.reelsblocker.ui.components

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.foxtrotalpha.reelsblocker.R
import com.foxtrotalpha.reelsblocker.databinding.ViewMetricCardBinding

class MetricCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private val binding = ViewMetricCardBinding.inflate(LayoutInflater.from(context), this, true)

    fun bind(
        @DrawableRes icon: Int,
        value: String,
        label: String,
        comparison: String? = null,
        available: Boolean = true,
    ) {
        binding.metricIcon.setImageResource(icon)
        binding.metricLabel.text = label
        if (available) {
            binding.metricValue.text = value
            binding.metricValue.setTextColor(ContextCompat.getColor(context, R.color.zen_text_primary))
        } else {
            binding.metricValue.text = context.getString(R.string.metric_unavailable)
            binding.metricValue.setTextColor(ContextCompat.getColor(context, R.color.zen_text_tertiary))
        }
        if (available && !comparison.isNullOrBlank()) {
            binding.metricComparison.visibility = View.VISIBLE
            binding.metricComparison.text = comparison
        } else {
            binding.metricComparison.visibility = View.GONE
        }
    }
}
