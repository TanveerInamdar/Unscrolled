package com.foxtrotalpha.reelsblocker.coach

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.foxtrotalpha.reelsblocker.R
import io.noties.markwon.Markwon

internal class CoachMessageAdapter(
    context: Context,
) : RecyclerView.Adapter<CoachMessageAdapter.BubbleHolder>() {

    private val items = mutableListOf<CoachChatMessage>()
    private val markwon = Markwon.create(context.applicationContext)

    fun submit(messages: List<CoachChatMessage>) {
        items.clear()
        items.addAll(messages)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return if (items[position].role == CoachChatMessage.ROLE_USER) {
            VIEW_USER
        } else {
            VIEW_ASSISTANT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BubbleHolder {
        val layout = if (viewType == VIEW_USER) {
            R.layout.item_coach_user
        } else {
            R.layout.item_coach_assistant
        }
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return BubbleHolder(view as ViewGroup)
    }

    override fun onBindViewHolder(holder: BubbleHolder, position: Int) {
        val message = items[position]
        if (message.role == CoachChatMessage.ROLE_ASSISTANT) {
            markwon.setMarkdown(holder.text, message.text)
        } else {
            holder.text.text = message.text
        }
    }

    override fun getItemCount(): Int = items.size

    class BubbleHolder(root: ViewGroup) : RecyclerView.ViewHolder(root) {
        val text: TextView = root.findViewById(R.id.bubbleText)
    }

    companion object {
        private const val VIEW_USER = 1
        private const val VIEW_ASSISTANT = 2
    }
}
