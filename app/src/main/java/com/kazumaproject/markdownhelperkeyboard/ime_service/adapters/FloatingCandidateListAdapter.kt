package com.kazumaproject.markdownhelperkeyboard.ime_service.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.core.data.floating_candidate.CandidateItem
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.repository.TamachiRepository
import timber.log.Timber

private const val VIEW_TYPE_SUGGESTION = 1
private const val VIEW_TYPE_PAGER = 2

class FloatingCandidateListAdapter(
    private val pageSize: Int,
) : ListAdapter<CandidateItem, RecyclerView.ViewHolder>(DiffCallback()) {

    // --- Public Callbacks ---
    var onSuggestionClicked: ((suggestion: CandidateItem) -> Unit)? = null
    var onPagerClicked: (() -> Unit)? = null
    var isDTalkerTTSActive: Boolean = false
    var tamachiRepository: TamachiRepository? = null

    // --- Highlight State ---
    private var highlightedPosition: Int = RecyclerView.NO_POSITION

    // --- Public methods to control highlight ---
    fun updateHighlightPosition(newPosition: Int) {
        val previousPosition = highlightedPosition
        highlightedPosition = newPosition

        Timber.d("updateHighlightPosition: $newPosition")

        if (previousPosition != RecyclerView.NO_POSITION) {
            notifyItemChanged(previousPosition)
        }
        if (newPosition != RecyclerView.NO_POSITION) {
            notifyItemChanged(newPosition)
        }
    }

    fun getHighlightedItem(): CandidateItem? {
        return if (highlightedPosition in 0 until itemCount) {
            getItem(highlightedPosition)
        } else {
            null
        }
    }

    // --- Suggestion ViewHolder ---
    inner class SuggestionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val textView: TextView = view.findViewById(R.id.text_view_item)

        init {
            itemView.setOnClickListener {
                if (absoluteAdapterPosition != RecyclerView.NO_POSITION) {
                    onSuggestionClicked?.invoke(getItem(absoluteAdapterPosition))
                }
            }
        }

        fun bind(item: CandidateItem, position: Int) {
            textView.text = item.word
            val positionText = " ${position + 1}の$itemCount"
            // TalkBackには指定されたエンジンに応じた情報を渡す
            itemView.contentDescription = if (isDTalkerTTSActive) {
                getSyosaiYomiSSML(item.word, positionText)
            } else {
                val baseReading = tamachiRepository?.getDetailedReading(item.word) ?: item.word
                "$baseReading $positionText"
            }
        }
    }

    // --- Pager ViewHolder ---
    inner class PagerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val textView: TextView = view.findViewById(R.id.text_view_item)

        init {
            itemView.setOnClickListener { onPagerClicked?.invoke() }
        }

        fun bind(text: String) {
            textView.text = text
        }
    }

    // --- Adapter Overrides ---
    override fun getItemViewType(position: Int): Int {
        return if (position == pageSize) VIEW_TYPE_PAGER else VIEW_TYPE_SUGGESTION
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_SUGGESTION -> SuggestionViewHolder(
                inflater.inflate(
                    R.layout.floating_candidate_list_item_string,
                    parent,
                    false
                )
            )

            VIEW_TYPE_PAGER -> PagerViewHolder(
                inflater.inflate(
                    R.layout.floating_candidate_list_item_pager,
                    parent,
                    false
                )
            )

            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        // Set activation state for background drawable
        holder.itemView.isActivated = (position == highlightedPosition)

        val currentItem = getItem(position)
        when (holder) {
            is SuggestionViewHolder -> holder.bind(currentItem, position)
            is PagerViewHolder -> holder.bind(currentItem.word)
        }
    }

    // --- DiffUtil Callback ---
    private class DiffCallback : DiffUtil.ItemCallback<CandidateItem>() {
        override fun areItemsTheSame(oldItem: CandidateItem, newItem: CandidateItem): Boolean =
            oldItem.word == newItem.word

        override fun areContentsTheSame(oldItem: CandidateItem, newItem: CandidateItem): Boolean =
            oldItem == newItem
    }

    /**
     * ハイライトされているアイテムを選択し、対応するクリックイベントをトリガーします。
     * アイテムが通常の候補（Suggestion）の場合にのみ onSuggestionClicked を呼び出します。
     */
    fun selectHighlightedItem() {
        // highlightedPosition が有効な範囲にあるか確認
        if (highlightedPosition == RecyclerView.NO_POSITION || highlightedPosition >= itemCount) {
            Timber.w("No item selected or invalid position: $highlightedPosition")
            return
        }

        // ハイライトされているアイテムがページャー（VIEW_TYPE_PAGER）でないことを確認
        if (getItemViewType(highlightedPosition) == VIEW_TYPE_SUGGESTION) {
            getHighlightedItem()?.let { item ->
                Timber.d("Programmatically selecting item: ${item.word}")
                onSuggestionClicked?.invoke(item)
            }
        } else {
            // 必要であればページャーが選択された際の処理もここに書ける
            Timber.d("Highlighted item is a pager. Not triggering onSuggestionClicked.")
            // onPagerClicked?.invoke() などを呼び出すことも可能
        }
    }

    private fun getSyosaiYomiSSML(word: String, positionText: String): String {
        val escapedWord = word.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\"?>")
        sb.append("<speak version=\"1.0\" xmlns=\"http://www.w3.org/2001/10/synthesis\" ")
        sb.append("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" ")
        sb.append("xsi:schemaLocation=\"http://www.w3.org/2001/10/synthesis ")
        sb.append("http://www.w3.org/TR/speech-synthesis/synthesis.xsd\" ")
        sb.append("xml:lang=\"ja\">")
        sb.append("<say-as interpret-as=\"characters\" format=\"glyphs\">")
        sb.append(escapedWord)
        sb.append("</say-as>")
        sb.append(positionText)
        sb.append("</speak>")
        return sb.toString()
    }
}
