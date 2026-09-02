package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_selection

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.os.Bundle
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.ListItemKeyboardBinding
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.KeyboardType

class KeyboardSelectionAdapter(
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit,
    private val onDeleteClick: (Int) -> Unit,
    private val onKeyboardClick: (position: Int, keyboardType: KeyboardType) -> Unit,
    private val onAccessibilityAction: (position: Int, action: AccessibilityAction) -> Unit
) : ListAdapter<KeyboardType, KeyboardSelectionAdapter.KeyboardViewHolder>(DiffCallback()) {

    private var isEditing: Boolean = false

    @SuppressLint("NotifyDataSetChanged")
    fun setEditMode(isEditing: Boolean) {
        if (this.isEditing != isEditing) {
            this.isEditing = isEditing
            // This is a simple way to refresh all views to show/hide icons
            notifyDataSetChanged()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): KeyboardViewHolder {
        val binding =
            ListItemKeyboardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return KeyboardViewHolder(binding)
    }

    override fun onBindViewHolder(holder: KeyboardViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class KeyboardViewHolder(private val binding: ListItemKeyboardBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            binding.deleteIcon.setOnClickListener {
                if (absoluteAdapterPosition != RecyclerView.NO_POSITION) {
                    onDeleteClick(absoluteAdapterPosition)
                }
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        fun bind(keyboardType: KeyboardType) {
            binding.keyboardName.text = getKeyboardDisplayName(keyboardType)
            // Click to select keyboard
            binding.root.setOnClickListener {
                val currentPosition = bindingAdapterPosition
                if (currentPosition != RecyclerView.NO_POSITION) {
                    onKeyboardClick(currentPosition, keyboardType)
                }
            }

            binding.dragHandle.visibility = if (isEditing) View.VISIBLE else View.GONE
            binding.deleteIcon.visibility = if (isEditing) View.VISIBLE else View.GONE

            // Set up drag handle if editing
            if (isEditing) {
                binding.dragHandle.setOnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                        onStartDrag(this)
                    }
                    false
                }
            } else {
                binding.dragHandle.setOnTouchListener(null)
            }

            // Accessibility actions for TalkBack
            if (isEditing) {
                ViewCompat.setAccessibilityDelegate(itemView, object : AccessibilityDelegateCompat() {
                    override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                        super.onInitializeAccessibilityNodeInfo(host, info)
                        info.addAction(
                            AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                                R.id.accessibility_action_move_up,
                                "上に移動"
                            )
                        )
                        info.addAction(
                            AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                                R.id.accessibility_action_move_down,
                                "下に移動"
                            )
                        )
                        info.addAction(
                            AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                                R.id.accessibility_action_move_top,
                                "先頭に移動"
                            )
                        )
                        info.addAction(
                            AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                                R.id.accessibility_action_move_bottom,
                                "最後に移動"
                            )
                        )
                    }

                    override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean {
                        val currentPosition = bindingAdapterPosition
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
            } else {
                ViewCompat.setAccessibilityDelegate(itemView, null)
            }
        }
    }

    enum class AccessibilityAction {
        MOVE_UP,
        MOVE_DOWN,
        MOVE_TOP,
        MOVE_BOTTOM
    }

    private class DiffCallback : DiffUtil.ItemCallback<KeyboardType>() {
        override fun areItemsTheSame(oldItem: KeyboardType, newItem: KeyboardType): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: KeyboardType, newItem: KeyboardType): Boolean {
            return oldItem == newItem
        }
    }

    private fun getKeyboardDisplayName(keyboardType: KeyboardType): String {
        return when (keyboardType) {
            KeyboardType.TENKEY -> "テンキー"
            KeyboardType.QWERTY -> "英語(QWERTY)"
            KeyboardType.ROMAJI -> "日本語 - ローマ字"

            KeyboardType.CUSTOM -> "カスタム - ユーザー定義"
            KeyboardType.AYAME_TENKEY -> "アヤメテンキー"
            KeyboardType.AYAME_QWERTY -> "アヤメ英語(QWERTY)"
            KeyboardType.AYAME_ROMAJI -> "アヤメ日本語 - ローマ字"
            KeyboardType.NUMERIC -> "数字専用キーボード"
            KeyboardType.AYAME_NUMERIC -> "アヤメ数字専用キーボード"
            KeyboardType.TABLET_KANA -> "タブレット用かなレイアウト"
            KeyboardType.AYAME_TABLET_KANA -> "アヤメタブレット用かなレイアウト"
            KeyboardType.BRAILLE -> "点字キーボード"
        }
    }
}
