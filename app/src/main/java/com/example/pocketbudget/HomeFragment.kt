package com.example.pocketbudget

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.util.*

class HomeFragment : Fragment() {

    private val transactionList = mutableListOf<Transaction>()
    private val filteredList = mutableListOf<Transaction>()
    private lateinit var adapter: TransactionAdapter

    private lateinit var tvBalance: TextView
    private lateinit var tvIncome: TextView
    private lateinit var tvExpense: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvFilterLabel: TextView
    private lateinit var notificationBadge: View

    private var currencySymbol: String = "$"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        val db = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()

        tvBalance = view.findViewById(R.id.tvTotalBalance)
        tvIncome = view.findViewById(R.id.tvTotalIncome)
        tvExpense = view.findViewById(R.id.tvTotalExpense)
        recyclerView = view.findViewById(R.id.recyclerView)
        val btnFilter = view.findViewById<LinearLayout>(R.id.btnFilter)
        val btnSort = view.findViewById<LinearLayout>(R.id.btnSort)
        tvFilterLabel = view.findViewById(R.id.tvFilterLabel)
        notificationBadge = view.findViewById(R.id.notificationBadge)

        val ivNotification = view.findViewById<ImageView>(R.id.ivNotification)
        ivNotification.setOnClickListener {
            startActivity(Intent(context, NotificationActivity::class.java))
        }

        val prefs = requireActivity().getSharedPreferences("BudgetPrefs", Context.MODE_PRIVATE)
        currencySymbol = prefs.getString("currency", "$") ?: "$"

        recyclerView.layoutManager = LinearLayoutManager(context)
        adapter = TransactionAdapter(filteredList, currencySymbol,
            onItemClick = { transaction ->
                val intent = Intent(context, AddTransactionActivity::class.java)
                intent.putExtra("transactionId", transaction.id)
                intent.putExtra("amount", transaction.amount)
                intent.putExtra("category", transaction.category)
                intent.putExtra("description", transaction.description)
                intent.putExtra("type", transaction.type)
                intent.putExtra("timestamp", transaction.timestamp)
                startActivity(intent)
            },
            onDeleteClick = {}
        )
        recyclerView.adapter = adapter

        btnFilter.setOnClickListener { showFilterMenu(it) }
        btnSort.setOnClickListener { showSortMenu(it) } // Sort Click

        val userId = auth.currentUser?.uid
        if (userId != null) {
            db.collection("users").document(userId).collection("transactions")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshots, _ ->
                    if (!isAdded || snapshots == null) return@addSnapshotListener

                    transactionList.clear()
                    var totalIncome = 0.0
                    var totalExpense = 0.0

                    for (doc in snapshots) {
                        val t = doc.toObject(Transaction::class.java)
                        t.id = doc.id
                        transactionList.add(t)

                        if (t.type == "INCOME") totalIncome += t.amount
                        else totalExpense += t.amount
                    }

                    val balVal = totalIncome - totalExpense
                    val fmtBalance = if(currencySymbol == "₫") "${String.format("%.0f", balVal)} $currencySymbol" else "$currencySymbol${String.format("%.2f", balVal)}"
                    val fmtIncome = if(currencySymbol == "₫") "${String.format("%.0f", totalIncome)} $currencySymbol" else "$currencySymbol${String.format("%.2f", totalIncome)}"
                    val fmtExpense = if(currencySymbol == "₫") "${String.format("%.0f", totalExpense)} $currencySymbol" else "$currencySymbol${String.format("%.2f", totalExpense)}"

                    tvBalance.text = fmtBalance
                    tvIncome.text = fmtIncome
                    tvExpense.text = fmtExpense

                    applyFilter(tvFilterLabel.text.toString())
                    checkNotificationBadge(snapshots.size())
                }
        }

        return view
    }

    private fun checkNotificationBadge(totalTransactions: Int) {
        val safeContext = context ?: return
        val prefs = safeContext.getSharedPreferences("DeletedNotifs", Context.MODE_PRIVATE)
        val deletedSet = prefs.getStringSet("ids", HashSet()) ?: HashSet()
        val deletedCount = deletedSet.size

        if (totalTransactions > deletedCount) {
            notificationBadge.visibility = View.VISIBLE
        } else {
            notificationBadge.visibility = View.GONE
        }
    }

    private fun showFilterMenu(view: View) {
        val popup = PopupMenu(context, view)
        popup.menu.add("All")
        popup.menu.add("Food"); popup.menu.add("Transport"); popup.menu.add("Rent")
        popup.menu.add("Bills"); popup.menu.add("Shopping"); popup.menu.add("Entertainment")
        popup.menu.add("Health"); popup.menu.add("Education"); popup.menu.add("Other")
        popup.menu.add("Salary"); popup.menu.add("Business"); popup.menu.add("Gift"); popup.menu.add("Investment")

        popup.setOnMenuItemClickListener { item ->
            val category = item.title.toString()
            tvFilterLabel.text = category
            applyFilter(category)
            true
        }
        popup.show()
    }

    //  Sort Menu
    private fun showSortMenu(view: View) {
        val popup = PopupMenu(context, view)
        popup.menu.add(0, 0, 0, "Date: Newest First")
        popup.menu.add(0, 1, 1, "Date: Oldest First")
        popup.menu.add(0, 2, 2, "Amount: Highest First")
        popup.menu.add(0, 3, 3, "Amount: Lowest First")

        popup.setOnMenuItemClickListener { item ->
            when(item.itemId) {
                0 -> filteredList.sortByDescending { it.timestamp }
                1 -> filteredList.sortBy { it.timestamp }
                2 -> filteredList.sortByDescending { it.amount }
                3 -> filteredList.sortBy { it.amount }
            }
            adapter.notifyDataSetChanged()
            true
        }
        popup.show()
    }

    private fun applyFilter(category: String) {
        filteredList.clear()
        if (category == "All" || category == "Filter") {
            filteredList.addAll(transactionList)
        } else {
            filteredList.addAll(transactionList.filter { it.category == category })
        }
        // Re-apply default sort (Newest) when filter changes
        filteredList.sortByDescending { it.timestamp }
        adapter.notifyDataSetChanged()
    }
}