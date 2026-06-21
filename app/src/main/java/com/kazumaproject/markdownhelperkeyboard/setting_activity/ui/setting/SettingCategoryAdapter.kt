package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.markdownhelperkeyboard.R

/**
 * 設定カテゴリ一覧を表示する RecyclerView Adapter
 */
data class SettingCategory(
    val iconRes: Int,
    val titleRes: Int,
    val summaryRes: Int,
    val actionId: Int,
)

class SettingCategoryAdapter(
    private val categories: List<SettingCategory>,
    private val onItemClick: (SettingCategory) -> Unit,
) : RecyclerView.Adapter<SettingCategoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.setting_category_icon)
        val title: TextView = view.findViewById(R.id.setting_category_title)
        val summary: TextView = view.findViewById(R.id.setting_category_summary)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_setting_category, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val category = categories[position]
        holder.icon.setImageResource(category.iconRes)
        holder.title.setText(category.titleRes)
        holder.summary.setText(category.summaryRes)
        holder.itemView.setOnClickListener {
            onItemClick(category)
        }
    }

    override fun getItemCount(): Int = categories.size
}
