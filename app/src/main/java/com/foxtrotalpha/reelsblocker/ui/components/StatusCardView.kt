package com.foxtrotalpha.reelsblocker.ui.components

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.CompoundButton
import android.widget.FrameLayout
import androidx.annotation.DrawableRes
import com.foxtrotalpha.reelsblocker.R
import com.foxtrotalpha.reelsblocker.databinding.ViewStatusCardBinding

class StatusCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private val binding = ViewStatusCardBinding.inflate(LayoutInflater.from(context), this, true)

    fun bind(
        title: String,
        body: String,
        enabled: Boolean,
        switchChecked: Boolean,
        switchLabel: String,
        voiceRoastChecked: Boolean = true,
        warning: String? = null,
        actionLabel: String? = null,
        @DrawableRes icon: Int = R.drawable.ic_shield,
        onCheckedChange: ((Boolean) -> Unit)? = null,
        onVoiceRoastCheckedChange: ((Boolean) -> Unit)? = null,
        onAction: (() -> Unit)? = null,
    ) {
        binding.statusIcon.setImageResource(icon)
        binding.statusTitle.text = title
        binding.statusBody.text = body
        binding.statusSwitch.text = switchLabel
        binding.statusSwitch.setOnCheckedChangeListener(null)
        binding.statusSwitch.isChecked = switchChecked
        binding.statusSwitch.setOnCheckedChangeListener { _: CompoundButton, isChecked: Boolean ->
            onCheckedChange?.invoke(isChecked)
        }

        binding.voiceRoastSwitch.setOnCheckedChangeListener(null)
        binding.voiceRoastSwitch.isChecked = voiceRoastChecked
        binding.voiceRoastSwitch.setOnCheckedChangeListener { _: CompoundButton, isChecked: Boolean ->
            onVoiceRoastCheckedChange?.invoke(isChecked)
        }

        binding.statusPill.text = if (enabled) {
            context.getString(R.string.status_pill_on)
        } else {
            context.getString(R.string.status_pill_off)
        }
        binding.statusPill.setBackgroundResource(
            if (enabled) R.drawable.bg_pill_status_on else R.drawable.bg_pill_status_off,
        )
        binding.statusPill.setTextColor(
            context.getColor(if (enabled) R.color.zen_sage else R.color.zen_text_tertiary),
        )

        if (warning.isNullOrBlank()) {
            binding.statusWarning.visibility = View.GONE
        } else {
            binding.statusWarning.visibility = View.VISIBLE
            binding.statusWarning.text = warning
        }

        if (actionLabel.isNullOrBlank() || onAction == null) {
            binding.statusAction.visibility = View.GONE
        } else {
            binding.statusAction.visibility = View.VISIBLE
            binding.statusAction.text = actionLabel
            binding.statusAction.setOnClickListener { onAction() }
        }
    }
}
