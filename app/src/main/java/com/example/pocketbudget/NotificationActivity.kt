package com.example.pocketbudget

import android.content.Context
import android.os.Bundle
import android.view.View
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
    private var currencySymbol: String = "$"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification)

        val btnBack = findViewById<ImageView>(R.id.btnBack)
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        btnBack.setOnClickListener { finish() }


        val prefs = getSharedPreferences("BudgetPrefs", Context.MODE_PRIVATE)
        currencySymbol = prefs.getString("currency", "$") ?: "$"

        // Initialize Adapter with Delete Logic
        adapter = NotificationAdapter(notificationList) { id, position ->
            deleteNotification(id, position)
        }
        recyclerView.adapter = adapter

        loadNotifications()
    }


    private fun formatMoney(amount: Double): String {
        return if (currencySymbol == "₫") {
            "${String.format("%.0f", amount)} $currencySymbol"
        } else {
            "$currencySymbol${String.format("%.0f", amount)}"
        }
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
                var currentMonthExpense = 0.0


                val calendar = Calendar.getInstance()
                val currentMonth = calendar.get(Calendar.MONTH)
                val currentYear = calendar.get(Calendar.YEAR)

                for (doc in documents) {
                    val id = doc.id
                    val amount = doc.getDouble("amount") ?: 0.0
                    val type = doc.getString("type") ?: "EXPENSE"
                    val category = doc.getString("category") ?: "Unknown"
                    val timestamp = doc.getLong("timestamp") ?: 0L


                    if (type == "EXPENSE") {
                        val transCalendar = Calendar.getInstance()
                        transCalendar.timeInMillis = timestamp

                        if (transCalendar.get(Calendar.MONTH) == currentMonth &&
                            transCalendar.get(Calendar.YEAR) == currentYear) {
                            currentMonthExpense += amount
                        }
                    }

                    if (deletedSet.contains(id)) continue

                    val dateStr = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(timestamp))


                    val moneyStr = formatMoney(amount)

                    if (type == "EXPENSE") {
                        notificationList.add(NotificationItem(
                            id,
                            "Expense Alert",
                            "You spent $moneyStr on $category.",
                            dateStr,
                            "INFO"
                        ))
                    } else {
                        notificationList.add(NotificationItem(
                            id,
                            "Income Received",
                            "You received $moneyStr from $category.",
                            dateStr,
                            "SUCCESS"
                        ))
                    }
                }


                addBudgetNotification(currentMonthExpense)

                adapter.notifyDataSetChanged()
            }
    }

    private fun addBudgetNotification(currentExpense: Double) {
        val prefs = getSharedPreferences("BudgetPrefs", Context.MODE_PRIVATE)
        val limit = prefs.getFloat("limit", 1000f)


        val spentStr = formatMoney(currentExpense)
        val limitStr = formatMoney(limit.toDouble())

        val item: NotificationItem

        if (currentExpense > limit) {
            item = NotificationItem(
                "budget_status",
                "Budget Exceeded!",
                "You have spent $spentStr which is over your $limitStr limit.",
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
                "You are within your monthly budget.",
                "Today",
                "SUCCESS"
            )
        }

        notificationList.add(0, item)
    }

    private fun deleteNotification(id: String, position: Int) {
        if (id == "budget_status") {
            Toast.makeText(this, "The budget status notification cannot be dismissed.", Toast.LENGTH_SHORT).show()
            return
        }

        if (position < notificationList.size) {
            notificationList.removeAt(position)
            adapter.notifyItemRemoved(position)
            adapter.notifyItemRangeChanged(position, notificationList.size)
        }

        val prefs = getSharedPreferences("DeletedNotifs", Context.MODE_PRIVATE)
        val set = prefs.getStringSet("ids", HashSet())?.toMutableSet() ?: HashSet()
        set.add(id)
        prefs.edit().putStringSet("ids", set).apply()

        Toast.makeText(this, "Notification dismissed", Toast.LENGTH_SHORT).show()
    }
}