package com.foxtrotalpha.reelsblocker.ui.components

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.foxtrotalpha.reelsblocker.databinding.ViewChartCardBinding

class ChartCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private val binding = ViewChartCardBinding.inflate(LayoutInflater.from(context), this, true)

    fun setTitle(title: String) {
        binding.chartTitle.text = title
    }

    fun setHeadline(value: String?) {
        if (value.isNullOrBlank()) {
            binding.chartHeadline.visibility = View.GONE
        } else {
            binding.chartHeadline.visibility = View.VISIBLE
            binding.chartHeadline.text = value
        }
    }

    fun setSubtitle(value: String?) {
        if (value.isNullOrBlank()) {
            binding.chartSubtitle.visibility = View.GONE
        } else {
            binding.chartSubtitle.visibility = View.VISIBLE
            binding.chartSubtitle.text = value
        }
    }

    fun setChart(view: View) {
        binding.chartSlot.removeAllViews()
        (view.parent as? ViewGroup)?.removeView(view)
        binding.chartSlot.addView(
            view,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
    }

    fun extraSlot(): ViewGroup = binding.chartExtraSlot

    fun showContent() {
        binding.chartHeadline.visibility = View.VISIBLE
        binding.chartSlot.visibility = View.VISIBLE
        binding.chartEmptyState.visibility = View.GONE
    }

    fun showEmpty(message: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
        binding.chartHeadline.visibility = View.GONE
        binding.chartSubtitle.visibility = View.GONE
        binding.chartSlot.visibility = View.GONE
        binding.chartExtraSlot.visibility = View.GONE
        binding.chartEmptyState.visibility = View.VISIBLE
        binding.chartEmptyState.bind(message = message, actionLabel = actionLabel, onAction = onAction)
    }

    fun emptyState(): EmptyStateView = binding.chartEmptyState
}
