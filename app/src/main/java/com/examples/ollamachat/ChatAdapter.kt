package com.examples.ollamachat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ChatAdapter(private val items: MutableList<Message>) :
    RecyclerView.Adapter<ChatAdapter.VH>() {

    companion object {
        private const val TYPE_USER = 1
        private const val TYPE_ASSISTANT = 2
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tv: TextView = view.findViewById(R.id.tvMessage)
    }

    override fun getItemViewType(position: Int): Int =
        if (items[position].role == "user") TYPE_USER else TYPE_ASSISTANT

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val layout = if (viewType == TYPE_USER) R.layout.item_user_message
                     else R.layout.item_assistant_message
        return VH(LayoutInflater.from(parent.context).inflate(layout, parent, false))
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.tv.text = items[position].content
    }

    override fun getItemCount(): Int = items.size
}