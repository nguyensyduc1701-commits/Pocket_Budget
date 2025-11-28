package com.example.pocketbudget

import android.content.Context
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashSet

class NotificationActivity : AppCompatActivity() {

    private lateinit var adapter: NotificationAdapter
    private val notificationList = ArrayList<NotificationItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification)

        val btnBack = findViewById<ImageView>(R.id.btnBack)
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        btnBack.setOnClickListener { finish() }

        // Initialize Adapter with Delete Logic
        adapter = NotificationAdapter(notificationList) { id, position ->
            deleteNotification(id, position)
        }
        recyclerView.adapter = adapter

        loadNotifications()
    }

    private fun loadNotifications() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val prefs = getSharedPreferences("DeletedNotifs", Context.MODE_PRIVATE)
        val deletedSet = prefs.getStringSet("ids", HashSet()) ?: HashSet()

        FirebaseFirestore.getInstance().collection("users").document(userId)
            .collection("transactions")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50)
            .get()
            .addOnSuccessListener { documents ->
                notificationList.clear()
                var totalExpense = 0.0

                //  Convert Transactions to Notifications AND Calculate Total Expense
                for (doc in documents) {
                    val id = doc.id
                    val amount = doc.getDouble("amount") ?: 0.0
                    val type = doc.getString("type") ?: "EXPENSE"
                    val category = doc.getString("category") ?: "Unknown"
                    val timestamp = doc.getLong("timestamp") ?: 0L

                    // Calculate Total for Budget Logic
                    if (type == "EXPENSE") {
                        totalExpense += amount
                    }

                    // Skip adding to list if user deleted this specific transaction notification
                    if (deletedSet.contains(id)) continue

                    val dateStr = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(timestamp))

                    if (type == "EXPENSE") {
                        notificationList.add(NotificationItem(
                            id,
                            "Expense Alert",
                            "You spent $${amount.toInt()} on $category.",
                            dateStr,
                            "INFO"
                        ))
                    } else {
                        notificationList.add(NotificationItem(
                            id,
                            "Income Received",
                            "You received $${amount.toInt()} from $category.",
                            dateStr,
                            "SUCCESS"
                        ))
                    }
                }

                //  Add Budget Status (Top Priority) - ALWAYS ADD THIS
                addBudgetNotification(totalExpense)

                adapter.notifyDataSetChanged()
            }
    }

    private fun addBudgetNotification(currentExpense: Double) {
        val prefs = getSharedPreferences("BudgetPrefs", Context.MODE_PRIVATE)
        val limit = prefs.getFloat("limit", 1000f)

        val item: NotificationItem

        if (currentExpense > limit) {
            item = NotificationItem(
                "budget_status", // Fixed ID
                "Budget Exceeded!",
                "You have spent $${currentExpense.toInt()} which is over your $${limit.toInt()} limit.",
                "Urgent",
                "ALERT"
            )
        } else if (currentExpense > (limit * 0.8)) {
            item = NotificationItem(
                "budget_status",
                "Budget Warning",
                "You've used over 80% of your monthly budget.",
                "Today",
                "INFO"
            )
        } else {
            item = NotificationItem(
                "budget_status",
                "On Track",
                "You are within your budget (${((currentExpense/limit)*100).toInt()}% used).",
                "Today",
                "SUCCESS"
            )
        }


        notificationList.add(0, item)
    }

    private fun deleteNotification(id: String, position: Int) {
        //  Guard Clause to prevent deleting permanent notification ---
        if (id == "budget_status") {
            // Do not delete permanent notification
            Toast.makeText(this, "The budget status notification cannot be dismissed.", Toast.LENGTH_SHORT).show()
            return
        }

        // 1. Remove from UI
        if (position < notificationList.size) {
            notificationList.removeAt(position)
            adapter.notifyItemRemoved(position)
            // Fix range to prevent crashes
            adapter.notifyItemRangeChanged(position, notificationList.size)
        }

        // 2. Save "Deleted" status to Phone Memory for transaction notifications
        val prefs = getSharedPreferences("DeletedNotifs", Context.MODE_PRIVATE)
        val set = prefs.getStringSet("ids", HashSet())?.toMutableSet() ?: HashSet()
        set.add(id)
        prefs.edit().putStringSet("ids", set).apply()

        Toast.makeText(this, "Transaction notification dismissed", Toast.LENGTH_SHORT).show()
    }
}