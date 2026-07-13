package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.candidate_tab_order.adapter

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
import com.kazumaproject.markdownhelperkeyboard.databinding.ListItemCandidateTabBinding
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.CandidateTab

class CandidateTabOrderAdapter(
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit,
    private val onDeleteClick: (Int) -> Unit,
    private val onItemClick: (position: Int, candidateTab: CandidateTab) -> Unit,
    private val onAccessibilityAction: (position: Int, action: AccessibilityAction) -> Unit
) : ListAdapter<CandidateTab, CandidateTabOrderAdapter.CandidateTabViewHolder>(DiffCallback()) {

    enum class AccessibilityAction {
        MOVE_UP,
        MOVE_DOWN,
        MOVE_TOP,
        MOVE_BOTTOM,
        DELETE
    }

    private var isEditing: Boolean = false

    @SuppressLint("NotifyDataSetChanged")
    fun setEditMode(isEditing: Boolean) {
        if (this.isEditing != isEditing) {
            this.isEditing = isEditing
            notifyDataSetChanged()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CandidateTabViewHolder {
        val binding =
            ListItemCandidateTabBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CandidateTabViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CandidateTabViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class CandidateTabViewHolder(private val binding: ListItemCandidateTabBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            binding.deleteIcon.setOnClickListener {
                if (absoluteAdapterPosition != RecyclerView.NO_POSITION) {
                    onDeleteClick(absoluteAdapterPosition)
                }
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        fun bind(candidateTab: CandidateTab) {
            binding.tabName.text = getCandidateTabDisplayName(candidateTab)

            binding.dragHandle.visibility = if (isEditing) View.VISIBLE else View.GONE
            binding.deleteIcon.visibility = if (isEditing) View.VISIBLE else View.GONE

            binding.root.setOnClickListener {
                val currentPosition = bindingAdapterPosition
                if (currentPosition != RecyclerView.NO_POSITION) {
                    onItemClick(currentPosition, candidateTab)
                }
            }

            if (isEditing) {
                binding.dragHandle.setOnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                        onStartDrag(this)
                    }
                    false
                }

                ViewCompat.setAccessibilityDelegate(itemView, object : AccessibilityDelegateCompat() {
                    override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                        super.onInitializeAccessibilityNodeInfo(host, info)
                        val currentPosition = bindingAdapterPosition
                        if (currentPosition == RecyclerView.NO_POSITION) return

                        if (currentPosition > 0) {
                            info.addAction(
                                AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                                    R.id.accessibility_action_move_up,
                                    "上に移動"
                                )
                            )
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
                                    R.id.accessibility_action_move_down,
                                    "下に移動"
                                )
                            )
                            info.addAction(
                                AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                                    R.id.accessibility_action_move_bottom,
                                    "最後に移動"
                                )
                            )
                        }
                        if (itemCount > 1) {
                            info.addAction(
                                AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                                    R.id.accessibility_action_delete,
                                    "削除"
                                )
                            )
                        }
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
                            R.id.accessibility_action_delete -> {
                                if (itemCount > 1) {
                                    onAccessibilityAction(currentPosition, AccessibilityAction.DELETE)
                                    host.announceForAccessibility("削除しました")
                                }
                                true
                            }
                            else -> super.performAccessibilityAction(host, action, args)
                        }
                    }
                })
            } else {
                binding.dragHandle.setOnTouchListener(null)
                ViewCompat.setAccessibilityDelegate(itemView, null)
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<CandidateTab>() {
        override fun areItemsTheSame(oldItem: CandidateTab, newItem: CandidateTab): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: CandidateTab, newItem: CandidateTab): Boolean {
            return oldItem == newItem
        }
    }

    // このヘルパー関数はFragmentにもありますが、Adapter内でも必要です
    private fun getCandidateTabDisplayName(candidateTab: CandidateTab): String {
        return when (candidateTab) {
            CandidateTab.PREDICTION -> "予測変換"
            CandidateTab.CONVERSION -> "通常変換"
            CandidateTab.EISUKANA -> "英数・かな"
        }
    }
}
