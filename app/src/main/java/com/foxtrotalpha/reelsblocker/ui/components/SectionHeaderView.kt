package com.foxtrotalpha.reelsblocker.ui.components

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import com.foxtrotalpha.reelsblocker.databinding.ViewSectionHeaderBinding

class SectionHeaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private val binding = ViewSectionHeaderBinding.inflate(LayoutInflater.from(context), this, true)

    fun bind(title: String, subtitle: String? = null) {
        binding.sectionTitle.text = title
        if (subtitle.isNullOrBlank()) {
            binding.sectionSubtitle.visibility = View.GONE
        } else {
            binding.sectionSubtitle.visibility = View.VISIBLE
            binding.sectionSubtitle.text = subtitle
        }
    }
}
