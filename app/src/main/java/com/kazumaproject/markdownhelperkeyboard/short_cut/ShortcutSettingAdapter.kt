package com.kazumaproject.markdownhelperkeyboard.short_cut

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.ItemShortcutSettingBinding
import com.kazumaproject.markdownhelperkeyboard.short_cut.data.EditableShortcut

class ShortcutSettingAdapter(
    private val onToggle: (position: Int, item: EditableShortcut, isChecked: Boolean) -> Unit,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit,
    private val onItemClick: (position: Int, item: EditableShortcut) -> Unit,
    private val onAccessibilityAction: (position: Int, action: AccessibilityAction) -> Unit
) : ListAdapter<EditableShortcut, ShortcutSettingAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(val binding: ItemShortcutSettingBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemShortcutSettingBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        with(holder.binding) {
            textTitle.text = item.type.description
            iconImage.setImageResource(item.type.iconResId)

            // リスナーを一度外してから状態をセットしてループを防ぐ
            switchEnable.setOnCheckedChangeListener(null)
            switchEnable.isChecked = item.isEnabled
            switchEnable.setOnCheckedChangeListener { _, isChecked ->
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onToggle(pos, item, isChecked)
                }
            }

            // ハンドルのタッチイベントでドラッグ開始
            dragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    onStartDrag(holder)
                }
                false
            }

            root.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onItemClick(pos, item)
                }
            }

            // Set content description for the row to read the title first
            val dragDescription = root.context.getString(com.kazumaproject.core.R.string.drag_handle_description)
            root.contentDescription = "${item.type.description}, $dragDescription"
        }

        // Accessibility actions for TalkBack
        ViewCompat.setAccessibilityDelegate(holder.itemView, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                val currentPosition = holder.bindingAdapterPosition
                if (currentPosition == RecyclerView.NO_POSITION) return

                if (currentPosition > 0) {
                    info.addAction(
                        AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                            R.id.accessibility_action_move_up,
                            "上に移動"
                        )
                    )
                }
                if (currentPosition < itemCount - 1) {
                    info.addAction(
                        AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                            R.id.accessibility_action_move_down,
                            "下に移動"
                        )
                    )
                }
                if (currentPosition > 0) {
                    info.addAction(
                        AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                            R.id.accessibility_action_move_top,
                            "先頭に移動"
                        )
                    )
                }
                if (currentPosition < itemCount - 1) {
                    info.addAction(
                        AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                            R.id.accessibility_action_move_bottom,
                            "最後に移動"
                        )
                    )
                }
            }

            override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean {
                val currentPosition = holder.bindingAdapterPosition
                if (currentPosition == RecyclerView.NO_POSITION) return false

                return when (action) {
                    R.id.accessibility_action_move_up -> {
                        if (currentPosition > 0) {
                            onAccessibilityAction(currentPosition, AccessibilityAction.MOVE_UP)
                            host.announceForAccessibility("上に移動しました")
                        }
                        true
                    }
                    R.id.accessibility_action_move_down -> {
                        if (currentPosition < itemCount - 1) {
                            onAccessibilityAction(currentPosition, AccessibilityAction.MOVE_DOWN)
                            host.announceForAccessibility("下に移動しました")
                        }
                        true
                    }
                    R.id.accessibility_action_move_top -> {
                        if (currentPosition > 0) {
                            onAccessibilityAction(currentPosition, AccessibilityAction.MOVE_TOP)
                            host.announceForAccessibility("先頭に移動しました")
                        }
                        true
                    }
                    R.id.accessibility_action_move_bottom -> {
                        if (currentPosition < itemCount - 1) {
                            onAccessibilityAction(currentPosition, AccessibilityAction.MOVE_BOTTOM)
                            host.announceForAccessibility("最後に移動しました")
                        }
                        true
                    }
                    else -> super.performAccessibilityAction(host, action, args)
                }
            }
        })
    }

    enum class AccessibilityAction {
        MOVE_UP,
        MOVE_DOWN,
        MOVE_TOP,
        MOVE_BOTTOM
    }

    private object DiffCallback : DiffUtil.ItemCallback<EditableShortcut>() {
        override fun areItemsTheSame(
            oldItem: EditableShortcut,
            newItem: EditableShortcut
        ): Boolean {
            return oldItem.type.id == newItem.type.id
        }

        override fun areContentsTheSame(
            oldItem: EditableShortcut,
            newItem: EditableShortcut
        ): Boolean {
            return oldItem == newItem
        }
    }
}
