package com.puja.ai.ui

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.puja.ai.R
import com.puja.ai.data.ChatMessage

class ChatAdapter : RecyclerView.Adapter<ChatAdapter.VH>() {
    private var items: List<ChatMessage> = emptyList()

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val bubble: LinearLayout = v.findViewById(R.id.bubble)
    }

    fun submit(newList: List<ChatMessage>) {
        val old = items
        items = newList
        when {
            old.isNotEmpty() && newList.size == old.size && newList.last().id == old.last().id ->
                notifyItemChanged(newList.size - 1)
            old.isNotEmpty() && newList.size == old.size + 1 && newList[old.size - 1].id == old.last().id ->
                notifyItemInserted(old.size)
            else -> notifyDataSetChanged()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_message, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val m = items[position]
        val ctx = h.itemView.context
        val isUser = m.role == ChatMessage.USER
        val segs = segments(m.text)
        val hasCode = segs.any { it.isCode }

        val lp = h.bubble.layoutParams as LinearLayout.LayoutParams
        lp.gravity = if (isUser) Gravity.END else Gravity.START
        lp.width = if (hasCode) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT
        lp.marginStart = if (isUser) ctx.dp(48) else 0
        lp.marginEnd = if (isUser) 0 else ctx.dp(24)
        h.bubble.layoutParams = lp
        h.bubble.setBackgroundResource(if (isUser) R.drawable.bg_bubble_user else R.drawable.bg_bubble_ai)
        h.bubble.removeAllViews()

        val textColor = when {
            isUser -> ContextCompat.getColor(ctx, R.color.white)
            m.error -> ContextCompat.getColor(ctx, R.color.puja_error)
            else -> ContextCompat.getColor(ctx, R.color.puja_text)
        }

        if (m.text.isBlank()) {
            h.bubble.addView(textView(h, "…", textColor))
            return
        }
        for (s in segs) {
            if (s.isCode) {
                val v = LayoutInflater.from(ctx).inflate(R.layout.view_code_block, h.bubble, false)
                v.findViewById<TextView>(R.id.codeLang).text = s.lang.ifEmpty { "code" }
                v.findViewById<TextView>(R.id.codeText).text = s.body
                v.findViewById<View>(R.id.codeCopy).setOnClickListener { ctx.copyToClipboard("code", s.body) }
                h.bubble.addView(v)
            } else if (s.body.isNotBlank()) {
                h.bubble.addView(textView(h, formatInline(s.body), textColor))
            }
        }
    }

    private fun textView(h: VH, text: CharSequence, color: Int) = TextView(h.itemView.context).apply {
        this.text = text
        setTextColor(color)
        textSize = 16f
        setLineSpacing(0f, 1.1f)
        setTextIsSelectable(true)
    }

    private data class Seg(val isCode: Boolean, val lang: String, val body: String)

    private fun segments(text: String): List<Seg> {
        val out = mutableListOf<Seg>()
        var last = 0
        for (m in FENCE.findAll(text)) {
            if (m.range.first > last) out.add(Seg(false, "", text.substring(last, m.range.first).trim('\n')))
            out.add(Seg(true, m.groupValues[1], m.groupValues[2].trimEnd()))
            last = m.range.last + 1
        }
        if (last < text.length) out.add(Seg(false, "", text.substring(last).trim('\n')))
        return out
    }

    private fun formatInline(s: String): CharSequence {
        val sb = SpannableStringBuilder()
        var last = 0
        for (m in INLINE.findAll(s)) {
            sb.append(s, last, m.range.first)
            val start = sb.length
            if (m.groups[1] != null) {
                sb.append(m.groupValues[1])
                sb.setSpan(StyleSpan(Typeface.BOLD), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else {
                sb.append(m.groupValues[2])
                sb.setSpan(TypefaceSpan("monospace"), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            last = m.range.last + 1
        }
        sb.append(s, last, s.length)
        return sb
    }

    companion object {
        private val FENCE = Regex("```([A-Za-z0-9+#_-]*)[ \\t]*\\n?([\\s\\S]*?)(?:```|\\z)")
        private val INLINE = Regex("\\*\\*(.+?)\\*\\*|`([^`\\n]+)`")
    }
}
