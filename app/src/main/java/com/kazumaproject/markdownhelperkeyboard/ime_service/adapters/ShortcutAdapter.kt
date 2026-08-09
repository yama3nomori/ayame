package com.kazumaproject.markdownhelperkeyboard.ime_service.adapters

import android.content.Context
import android.graphics.PorterDuff
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.short_cut.ShortcutType
import timber.log.Timber

class ShortcutAdapter : ListAdapter<ShortcutType, ShortcutAdapter.ViewHolder>(DiffCallback) {

    var isAyameMode: Boolean = false
        set(value) {
            Timber.d("ShortcutAdapter isAyameMode set to: $value")
            field = value
            lastClickedPosition = -1
            lastClickedTime = 0L
        }
    private var lastClickedPosition: Int = -1
    private var lastClickedTime: Long = 0L

    /**
     * A listener that gets called when an item is clicked.
     * The listener receives the resource ID of the clicked item.
     */
    var onItemClicked: ((ShortcutType) -> Unit)? = null

    /**
     * A listener that gets called when an item is long-clicked.
     */
    var onItemLongClicked: ((ShortcutType) -> Unit)? = null

    // ★追加: アイコンの色を保持する変数 (nullの場合はデフォルトの色)
    private var iconColor: Int? = null

    /**
     * ViewHolder now captures clicks and calls the adapter's listener.
     * It's an 'inner class' to access the adapter's onItemClicked property.
     */
    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.item_image)

        init {
            itemView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val accessibilityManager = itemView.context.getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
                    Timber.d("ShortcutAdapter click: isAyameMode=$isAyameMode, position=$position, lastClickedPosition=$lastClickedPosition, isTouchExplorationEnabled=${accessibilityManager.isTouchExplorationEnabled}")
                    if (isAyameMode) {
                        if (accessibilityManager.isEnabled && accessibilityManager.isTouchExplorationEnabled) {
                            Timber.d("ShortcutAdapter click: TalkBack bypass immediate invoke")
                            onItemClicked?.invoke(getItem(position))
                        } else {
                            val currentTime = SystemClock.uptimeMillis()
                            Timber.d("ShortcutAdapter click: diff=${currentTime - lastClickedTime}")
                            if (position == lastClickedPosition && currentTime - lastClickedTime < 500) {
                                Timber.d("ShortcutAdapter click: double-tap matched! invoke")
                                onItemClicked?.invoke(getItem(position))
                                lastClickedPosition = -1
                                lastClickedTime = 0L
                            } else {
                                Timber.d("ShortcutAdapter click: single-tap recorded")
                                lastClickedPosition = position
                                lastClickedTime = currentTime
                            }
                        }
                    } else {
                        onItemClicked?.invoke(getItem(position))
                    }
                }
            }

            itemView.setOnLongClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemLongClicked?.invoke(getItem(position))
                    true
                } else {
                    false
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_shortcut, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.imageView.setImageResource(item.iconResId) // Enumからアイコン取得
        holder.imageView.contentDescription = item.description
        holder.imageView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        Timber.d("ShortcutAdapter: Binding item ${item.name}, description: ${item.description}, contentDescription set to: ${holder.imageView.contentDescription}")

        // 以前のアクセシビリティアクションをクリアして、リサイクル時の影響を防ぐ
        androidx.core.view.ViewCompat.setAccessibilityDelegate(holder.itemView, null)

        if (item == ShortcutType.PASTE) {
            androidx.core.view.ViewCompat.addAccessibilityAction(holder.itemView, "クリップボード履歴") { _, _ ->
                onItemLongClicked?.invoke(item)
                true
            }
        }

        // ★追加: 色が設定されていれば適用し、なければ解除する
        iconColor?.let { color ->
            holder.imageView.setColorFilter(color, PorterDuff.Mode.SRC_IN)
        } ?: run {
            holder.imageView.clearColorFilter()
        }
    }

    // ★追加: 外部から色を設定するメソッド
    fun setIconColor(color: Int) {
        if (iconColor == color) return
        iconColor = color
        notifyItemRangeChanged(0, itemCount)
    }

    private object DiffCallback : DiffUtil.ItemCallback<ShortcutType>() {
        override fun areItemsTheSame(oldItem: ShortcutType, newItem: ShortcutType): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: ShortcutType, newItem: ShortcutType): Boolean =
            oldItem == newItem
    }
}
