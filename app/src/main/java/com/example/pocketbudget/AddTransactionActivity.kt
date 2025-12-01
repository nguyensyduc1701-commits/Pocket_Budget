package com.example.pocketbudget

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class AddTransactionActivity : AppCompatActivity() {

    private lateinit var spinnerCategory: Spinner
    private lateinit var btnDate: Button
    private var selectedTimestamp: Long = System.currentTimeMillis()
    private val incomeCategories = listOf("Salary", "Business", "Gift", "Investment", "Other")
    private val expenseCategories = listOf("Food", "Transport", "Rent", "Bills", "Shopping", "Entertainment", "Health", "Education", "Other")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_transaction)

        val etAmount = findViewById<EditText>(R.id.etAmount)
        val etDescription = findViewById<EditText>(R.id.etDescription)
        spinnerCategory = findViewById(R.id.spinnerCategory)
        btnDate = findViewById(R.id.btnDate)
        val rgType = findViewById<RadioGroup>(R.id.rgType)
        val rbIncome = findViewById<RadioButton>(R.id.rbIncome)
        val rbExpense = findViewById<RadioButton>(R.id.rbExpense)
        val btnSave = findViewById<Button>(R.id.btnSaveTransaction)
        val btnDelete = findViewById<Button>(R.id.btnDeleteTransaction)

        // Back Button Logic
        val btnBack = findViewById<ImageView>(R.id.btnBack)
        btnBack.setOnClickListener {
            finish() // Close Activity
        }

        updateSpinnerList(expenseCategories)
        updateDateButtonText()

        // Date Picker
        btnDate.setOnClickListener {
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = selectedTimestamp
            DatePickerDialog(this, { _, year, month, day ->
                val newDate = Calendar.getInstance()
                newDate.set(year, month, day)
                selectedTimestamp = newDate.timeInMillis
                updateDateButtonText()
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }

        rgType.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.rbIncome) updateSpinnerList(incomeCategories)
            else updateSpinnerList(expenseCategories)
        }

        // --- EDIT MODE ---
        val transactionId = intent.getStringExtra("transactionId")
        if (transactionId != null) {
            etAmount.setText(intent.getDoubleExtra("amount", 0.0).toString())
            etDescription.setText(intent.getStringExtra("description"))
            selectedTimestamp = intent.getLongExtra("timestamp", System.currentTimeMillis())
            updateDateButtonText()

            val type = intent.getStringExtra("type")
            val category = intent.getStringExtra("category")

            if (type == "INCOME") {
                rbIncome.isChecked = true
                updateSpinnerList(incomeCategories)
                spinnerCategory.setSelection(incomeCategories.indexOf(category))
            } else {
                rbExpense.isChecked = true
                updateSpinnerList(expenseCategories)
                spinnerCategory.setSelection(expenseCategories.indexOf(category))
            }

            btnSave.text = "Update"
            btnDelete.visibility = View.VISIBLE

            btnDelete.setOnClickListener {
                deleteTransaction(transactionId)
            }
        }

        btnSave.setOnClickListener {
            val amountStr = etAmount.text.toString()
            if (amountStr.isEmpty()) { etAmount.error = "Required"; return@setOnClickListener }

            val data = hashMapOf(
                "amount" to amountStr.toDouble(),
                "category" to spinnerCategory.selectedItem.toString(),
                "description" to etDescription.text.toString(),
                "type" to if (rbIncome.isChecked) "INCOME" else "EXPENSE",
                "timestamp" to selectedTimestamp
            )

            val db = FirebaseFirestore.getInstance()
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@setOnClickListener
            val ref = db.collection("users").document(userId).collection("transactions")

            if (transactionId != null) {
                ref.document(transactionId).update(data as Map<String, Any>).addOnSuccessListener { finish() }
            } else {
                ref.add(data).addOnSuccessListener { finish() }
            }
        }
    }

    private fun deleteTransaction(docId: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance().collection("users").document(userId)
            .collection("transactions").document(docId).delete()
            .addOnSuccessListener { finish() }
    }

    private fun updateSpinnerList(items: List<String>) {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, items)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCategory.adapter = adapter
    }

    private fun updateDateButtonText() {
        val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        btnDate.text = sdf.format(Date(selectedTimestamp))
    }
}