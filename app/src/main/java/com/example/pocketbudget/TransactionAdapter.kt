package com.example.pocketbudget

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TransactionAdapter(
    private val transactionList: List<Transaction>,
    private val currencySymbol: String,
    private val onItemClick: (Transaction) -> Unit,
    private val onDeleteClick: (String) -> Unit
) : RecyclerView.Adapter<TransactionAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvMainText: TextView = view.findViewById(R.id.tvNote)
        val tvSubText: TextView = view.findViewById(R.id.tvDate)
        val tvAmount: TextView = view.findViewById(R.id.tvAmount)
        val ivEdit: ImageView = view.findViewById(R.id.ivEditIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_transaction, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val transaction = transactionList[position]

        holder.tvMainText.text = transaction.category

        val sdf = SimpleDateFormat("MMM dd", Locale.getDefault())
        val dateStr = sdf.format(Date(transaction.timestamp))

        if (transaction.description.isNotEmpty()) {
            holder.tvSubText.text = "$dateStr • ${transaction.description}"
        } else {
            holder.tvSubText.text = dateStr
        }


        val formattedAmount = if (currencySymbol == "₫") {
            "${String.format("%.0f", transaction.amount)} $currencySymbol"
        } else {
            "$currencySymbol${String.format("%.2f", transaction.amount)}"
        }

        if (transaction.type == "INCOME") {
            holder.tvAmount.text = "+ $formattedAmount"
            holder.tvAmount.setTextColor(Color.parseColor("#00E676"))
        } else {
            holder.tvAmount.text = "- $formattedAmount"
            holder.tvAmount.setTextColor(Color.parseColor("#FF5252"))
        }

        holder.itemView.setOnClickListener { onItemClick(transaction) }
        holder.ivEdit.setOnClickListener { onItemClick(transaction) }
    }

    override fun getItemCount() = transactionList.size
}