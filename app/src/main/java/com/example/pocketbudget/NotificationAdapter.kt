package com.example.pocketbudget

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView


data class NotificationItem(
    val id: String,
    val title: String,
    val message: String,
    val time: String,
    val type: String
)

class NotificationAdapter(
    private val list: MutableList<NotificationItem>,
    private val onDeleteClick: (String, Int) -> Unit // Callback function
) : RecyclerView.Adapter<NotificationAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tvTitle)
        val message: TextView = view.findViewById(R.id.tvMessage)
        val time: TextView = view.findViewById(R.id.tvTime)
        val icon: ImageView = view.findViewById(R.id.ivIcon)
        val btnDelete: ImageView = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]
        holder.title.text = item.title
        holder.message.text = item.message
        holder.time.text = item.time

        // Icon Colors
        when (item.type) {
            "ALERT" -> holder.icon.setColorFilter(android.graphics.Color.parseColor("#FF5252"))
            "INFO" -> holder.icon.setColorFilter(android.graphics.Color.parseColor("#00E5FF"))
            "SUCCESS" -> holder.icon.setColorFilter(android.graphics.Color.parseColor("#00E676"))
        }

        //  Check if the item is the permanent Budget Status
        if (item.id == "budget_status") {
            holder.btnDelete.visibility = View.GONE
            holder.itemView.isClickable = false
        } else {
            holder.btnDelete.visibility = View.VISIBLE
            // Delete Click for non-budget items
            holder.btnDelete.setOnClickListener {
                onDeleteClick(item.id, position)
            }
        }
    }

    override fun getItemCount() = list.size
}