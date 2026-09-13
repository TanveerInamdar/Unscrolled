package com.foxtrotalpha.reelsblocker.ui.components

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.DrawableRes
import com.foxtrotalpha.reelsblocker.databinding.ViewEmptyStateBinding

class EmptyStateView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private val binding = ViewEmptyStateBinding.inflate(LayoutInflater.from(context), this, true)

    fun bind(
        message: String,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null,
        @DrawableRes icon: Int? = null,
    ) {
        binding.emptyMessage.text = message
        if (icon != null) {
            binding.emptyIcon.visibility = View.VISIBLE
            binding.emptyIcon.setImageResource(icon)
        } else {
            binding.emptyIcon.visibility = View.GONE
        }
        if (actionLabel.isNullOrBlank() || onAction == null) {
            binding.emptyAction.visibility = View.GONE
        } else {
            binding.emptyAction.visibility = View.VISIBLE
            binding.emptyAction.text = actionLabel
            binding.emptyAction.setOnClickListener { onAction() }
        }
    }
}
