package com.example.pocketbudget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class StatsFragment : Fragment() {

    private lateinit var barChart: BarChart
    private lateinit var emptyStateLayout: LinearLayout
    private lateinit var tvIncomeBox: TextView
    private lateinit var tvExpenseBox: TextView
    private lateinit var recyclerCategory: RecyclerView
    private lateinit var tabIncome: TextView
    private lateinit var tabExpenses: TextView
    private lateinit var spinnerTimeFilter: Spinner

    private var allTransactions = listOf<Transaction>()
    private var currentTab = "EXPENSE"
    private var currencySymbol: String = "$"
    private var snapshotListener: ListenerRegistration? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_stats, container, false)

        barChart = view.findViewById(R.id.barChart)
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout)
        tvIncomeBox = view.findViewById(R.id.tvIncomeBox)
        tvExpenseBox = view.findViewById(R.id.tvExpenseBox)
        recyclerCategory = view.findViewById(R.id.recyclerCategory)
        tabIncome = view.findViewById(R.id.tabIncome)
        tabExpenses = view.findViewById(R.id.tabExpenses)
        spinnerTimeFilter = view.findViewById(R.id.spinnerTimeFilter)

        val prefs = requireActivity().getSharedPreferences("BudgetPrefs", Context.MODE_PRIVATE)
        currencySymbol = prefs.getString("currency", "$") ?: "$"

        recyclerCategory.layoutManager = LinearLayoutManager(context)
        recyclerCategory.isNestedScrollingEnabled = false

        val filters = arrayOf("Monthly", "Weekly", "Yearly")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, filters)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerTimeFilter.adapter = adapter

        spinnerTimeFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                (view as? TextView)?.setTextColor(Color.WHITE)
                when (position) {
                    0 -> updateBarChart("MONTHLY")
                    1 -> updateBarChart("WEEKLY")
                    2 -> updateBarChart("YEARLY")
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        tabIncome.setOnClickListener { switchTab("INCOME") }
        tabExpenses.setOnClickListener { switchTab("EXPENSE") }

        setupRealtimeListener()
        return view
    }

    private fun switchTab(type: String) {
        currentTab = type
        if (type == "INCOME") {
            tabIncome.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#7C4DFF"))
            tabIncome.setTextColor(Color.WHITE)

            tabExpenses.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#263238"))
            tabExpenses.setTextColor(Color.parseColor("#B0BEC5"))
        } else {
            tabExpenses.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FF5722"))
            tabExpenses.setTextColor(Color.WHITE)

            tabIncome.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#263238"))
            tabIncome.setTextColor(Color.parseColor("#B0BEC5"))
        }
        updateCategoryList()
    }

    private fun setupRealtimeListener() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        snapshotListener = FirebaseFirestore.getInstance().collection("users").document(userId)
            .collection("transactions")
            .addSnapshotListener { documents, e ->
                if (e != null || documents == null) return@addSnapshotListener
                if (!isAdded) return@addSnapshotListener

                val list = mutableListOf<Transaction>()
                var totalInc = 0.0
                var totalExp = 0.0

                for (doc in documents) {
                    val t = doc.toObject(Transaction::class.java)
                    t.id = doc.id
                    list.add(t)
                    if(t.type == "INCOME") totalInc += t.amount
                    else totalExp += t.amount
                }
                allTransactions = list

                val fmtIncome = if(currencySymbol == "₫") "${String.format("%.0f", totalInc)} $currencySymbol" else "$currencySymbol${String.format("%.0f", totalInc)}"
                val fmtExpense = if(currencySymbol == "₫") "${String.format("%.0f", totalExp)} $currencySymbol" else "$currencySymbol${String.format("%.0f", totalExp)}"

                tvIncomeBox.text = fmtIncome
                tvExpenseBox.text = fmtExpense

                if (list.isEmpty()) {
                    barChart.visibility = View.GONE
                    emptyStateLayout.visibility = View.VISIBLE
                } else {
                    barChart.visibility = View.VISIBLE
                    emptyStateLayout.visibility = View.GONE

                    when (spinnerTimeFilter.selectedItemPosition) {
                        0 -> updateBarChart("MONTHLY")
                        1 -> updateBarChart("WEEKLY")
                        2 -> updateBarChart("YEARLY")
                        else -> updateBarChart("MONTHLY")
                    }
                    updateCategoryList()
                }
            }
    }

    private fun updateCategoryList() {
        // Filter by Type
        val filtered = allTransactions.filter { it.type == currentTab }

        // Group by Category
        val categoryMap = HashMap<String, Double>()
        for (t in filtered) {
            val cat = t.category.trim()
            categoryMap[cat] = categoryMap.getOrDefault(cat, 0.0) + t.amount
        }

        val statList = ArrayList<CategoryStat>()
        for ((name, amount) in categoryMap) {
            statList.add(CategoryStat(name, amount, currentTab))
        }
        statList.sortByDescending { it.totalAmount }

        recyclerCategory.adapter = StatCategoryAdapter(statList, currencySymbol)


    }

    private fun updateBarChart(mode: String) {
        if (allTransactions.isEmpty()) return

        val incomeMap = TreeMap<String, Float>()
        val expenseMap = TreeMap<String, Float>()
        val sortedKeys = TreeSet<String>()

        val sdf = when (mode) {
            "WEEKLY" -> SimpleDateFormat("yyyyww", Locale.getDefault())
            "YEARLY" -> SimpleDateFormat("yyyy", Locale.getDefault())
            else -> SimpleDateFormat("yyyyMM", Locale.getDefault())
        }

        val displayFmt = when (mode) {
            "WEEKLY" -> SimpleDateFormat("yyyy 'Wk' w", Locale.getDefault())
            "YEARLY" -> SimpleDateFormat("yyyy", Locale.getDefault())
            else -> SimpleDateFormat("MMM yyyy", Locale.getDefault())
        }

        val dataMap = TreeMap<String, Pair<Float, Float>>()
        val labelMap = HashMap<String, String>()

        for (t in allTransactions) {
            val date = Date(t.timestamp)
            val sortKey = sdf.format(date)

            var displayLabel: String
            if (mode == "WEEKLY") {
                val cal = Calendar.getInstance(Locale.getDefault())
                cal.time = date
                val weekNum = cal.get(Calendar.WEEK_OF_YEAR)
                val monthYearFmt = SimpleDateFormat("MMM yyyy", Locale.getDefault())
                val monthYearStr = monthYearFmt.format(date).toLowerCase(Locale.ROOT)
                displayLabel = "wk $weekNum $monthYearStr"
            } else {
                displayLabel = displayFmt.format(date)
            }

            labelMap[sortKey] = displayLabel

            val current = dataMap.getOrDefault(sortKey, Pair(0f, 0f))
            if (t.type == "INCOME") {
                dataMap[sortKey] = Pair(current.first + t.amount.toFloat(), current.second)
            } else {
                dataMap[sortKey] = Pair(current.first, current.second + t.amount.toFloat())
            }
        }

        val incomeEntries = ArrayList<BarEntry>()
        val expenseEntries = ArrayList<BarEntry>()
        val labels = ArrayList<String>()

        var index = 0
        for ((sortKey, totals) in dataMap) {
            val finalLabel = labelMap[sortKey] ?: sortKey
            labels.add(finalLabel)
            incomeEntries.add(BarEntry(index.toFloat(), totals.first))
            expenseEntries.add(BarEntry(index.toFloat(), totals.second))
            index++
        }

        val incomeSet = BarDataSet(incomeEntries, "Income").apply {
            color = Color.parseColor("#7C4DFF")
            valueTextColor = Color.TRANSPARENT
            setDrawValues(false)
        }
        val expenseSet = BarDataSet(expenseEntries, "Expense").apply {
            color = Color.parseColor("#FF5722")
            valueTextColor = Color.TRANSPARENT
            setDrawValues(false)
        }

        val data = BarData(incomeSet, expenseSet)
        val groupSpace = 0.60f
        val barSpace = 0.04f
        val barWidth = 0.16f
        data.barWidth = barWidth

        barChart.data = data

        barChart.xAxis.apply {
            valueFormatter = IndexAxisValueFormatter(labels)
            position = XAxis.XAxisPosition.BOTTOM
            granularity = 1f
            setCenterAxisLabels(true)
            axisMinimum = 0f
            axisMaximum = 0f + barChart.barData.getGroupWidth(groupSpace, barSpace) * labels.size
            textColor = Color.parseColor("#B0BEC5")
            setDrawGridLines(false)
            setDrawAxisLine(false)
        }

        barChart.axisLeft.apply {
            axisMinimum = 0f
            textColor = Color.parseColor("#B0BEC5")
            enableGridDashedLine(10f, 10f, 0f)
            setDrawAxisLine(false)
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return if (value >= 1000) "${(value / 1000).toInt()}k" else "${value.toInt()}"
                }
            }
        }

        barChart.axisRight.isEnabled = false
        barChart.legend.isEnabled = false
        barChart.description.isEnabled = false
        barChart.setDrawGridBackground(false)
        barChart.setDrawBorders(false)
        barChart.setExtraOffsets(0f, 0f, 0f, 10f)

        if (labels.size > 5) barChart.setVisibleXRangeMaximum(5f) else barChart.fitScreen()
        barChart.groupBars(0f, groupSpace, barSpace)
        barChart.invalidate()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        snapshotListener?.remove()
    }
}