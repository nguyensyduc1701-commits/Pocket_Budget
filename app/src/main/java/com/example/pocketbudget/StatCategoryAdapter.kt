package com.example.pocketbudget

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class CategoryStat(val name: String, val totalAmount: Double, val type: String)

class StatCategoryAdapter(
    private val categoryList: List<CategoryStat>,
    private val currencySymbol: String // NEW
) : RecyclerView.Adapter<StatCategoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tvCatName)
        val amount: TextView = view.findViewById(R.id.tvCatAmount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_stat_category, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val stat = categoryList[position]
        holder.name.text = stat.name

        val formattedAmount = if (currencySymbol == "₫") {
            "${String.format("%.0f", stat.totalAmount)} $currencySymbol"
        } else {
            "$currencySymbol${String.format("%.0f", stat.totalAmount)}"
        }

        if (stat.type == "INCOME") {
            holder.amount.text = "+ $formattedAmount"
            holder.amount.setTextColor(Color.parseColor("#00E676")) // Green
        } else {
            holder.amount.text = "- $formattedAmount"
            holder.amount.setTextColor(Color.parseColor("#FF5252")) // Red
        }
    }

    override fun getItemCount() = categoryList.size
}