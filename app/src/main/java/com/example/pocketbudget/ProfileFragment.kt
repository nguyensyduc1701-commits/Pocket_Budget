package com.example.pocketbudget

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

class ProfileFragment : Fragment() {

    private lateinit var progressBar: ProgressBar
    private lateinit var tvExpenseStatus: TextView
    private lateinit var tvBudgetLimit: TextView
    private lateinit var btnCurrency: TextView

    private var budgetLimit: Float = 1000f
    private var currentExpense: Double = 0.0
    private var currencySymbol: String = "$"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_profile, container, false)
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser

        val tvEmail = view.findViewById<TextView>(R.id.tvUserEmail)
        val btnLogout = view.findViewById<TextView>(R.id.btnLogout)
        val btnReset = view.findViewById<TextView>(R.id.btnResetPassword)
        val btnClear = view.findViewById<TextView>(R.id.btnClearData)
        val btnExport = view.findViewById<TextView>(R.id.btnExportReport)

        btnCurrency = view.findViewById(R.id.btnCurrency)
        val btnHelp = view.findViewById<TextView>(R.id.btnHelp)

        progressBar = view.findViewById(R.id.progressBarBudget)
        tvExpenseStatus = view.findViewById(R.id.tvExpenseStatus)
        tvBudgetLimit = view.findViewById(R.id.tvBudgetLimit)
        val btnEditBudget = view.findViewById<ImageView>(R.id.btnEditBudget)

        tvEmail.text = user?.email ?: "Guest"

        val prefs = requireActivity().getSharedPreferences("BudgetPrefs", Context.MODE_PRIVATE)
        budgetLimit = prefs.getFloat("limit", 1000f)
        currencySymbol = prefs.getString("currency", "$") ?: "$"

        btnCurrency.text = "Currency: $currencySymbol"

        val db = FirebaseFirestore.getInstance()
        val userId = user?.uid
        if (userId != null) {
            db.collection("users").document(userId).collection("transactions")
                .addSnapshotListener { snapshots, _ ->
                    if (snapshots != null) {
                        var totalExp = 0.0
                        for (doc in snapshots) {
                            val amount = doc.getDouble("amount") ?: 0.0
                            val type = doc.getString("type") ?: "EXPENSE"
                            if (type == "EXPENSE") totalExp += amount
                        }
                        currentExpense = totalExp
                        updateBudgetUI()
                    }
                }
        }

        btnEditBudget.setOnClickListener {
            val input = EditText(context)
            input.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            input.hint = "Enter Monthly Limit"
            input.setText(budgetLimit.toString())
            input.setTextColor(Color.BLACK)
            input.setPadding(50, 50, 50, 50)

            AlertDialog.Builder(requireContext())
                .setTitle("Set Monthly Budget")
                .setView(input)
                .setPositiveButton("Save") { _, _ ->
                    val newLimit = input.text.toString().toFloatOrNull()
                    if (newLimit != null && newLimit > 0) {
                        budgetLimit = newLimit
                        prefs.edit().putFloat("limit", budgetLimit).apply()
                        updateBudgetUI()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        btnCurrency.setOnClickListener {
            val currencies = arrayOf("$ (Dollar)", "€ (Euro)", "£ (Pound)", "¥ (Yen)", "₫ (Dong)")
            val symbols = arrayOf("$", "€", "£", "¥", "₫")

            AlertDialog.Builder(requireContext())
                .setTitle("Select Currency")
                .setItems(currencies) { _, which ->
                    currencySymbol = symbols[which]
                    prefs.edit().putString("currency", currencySymbol).apply()

                    btnCurrency.text = "Currency: $currencySymbol"
                    updateBudgetUI()
                    Toast.makeText(context, "Currency set to $currencySymbol", Toast.LENGTH_SHORT).show()
                }
                .show()
        }

        btnHelp.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("About Pocket Budget")
                .setMessage(
                        "How to use:\n" +
                        "1. Use the + button to add Income or Expenses.\n" +
                        "2. View your balance on the Home Dashboard.\n" +
                        "3. Check the Stats tab for visual breakdowns.\n" +
                        "4. Set a monthly spending limit here in Profile."
                        )
                .setPositiveButton("Got it", null)
                .show()
        }

        btnExport.setOnClickListener { exportData() }

        btnLogout.setOnClickListener {
            auth.signOut()
            val intent = Intent(context, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }

        btnReset.setOnClickListener {
            val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_change_password, null)
            val etCurrentPass = dialogView.findViewById<EditText>(R.id.etCurrentPassword)
            val etNewPass = dialogView.findViewById<EditText>(R.id.etNewPassword)
            val etConfirmPass = dialogView.findViewById<EditText>(R.id.etConfirmPassword)

            AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setPositiveButton("Update") { _, _ ->
                    val currentPass = etCurrentPass.text.toString()
                    val newPass = etNewPass.text.toString()
                    val confirmPass = etConfirmPass.text.toString()
                    val email = user?.email

                    if (currentPass.isNotEmpty() && newPass.isNotEmpty() && newPass == confirmPass) {
                        val credential = EmailAuthProvider.getCredential(email!!, currentPass)
                        user.reauthenticate(credential).addOnSuccessListener {
                            user.updatePassword(newPass).addOnSuccessListener {
                                Toast.makeText(context, "Password Updated", Toast.LENGTH_SHORT).show()
                            }
                        }.addOnFailureListener {
                            Toast.makeText(context, "Incorrect Old Password", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "Check inputs", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        btnClear.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Delete Everything?")
                .setMessage("Permanently remove all transactions?")
                .setPositiveButton("Delete") { _, _ ->
                    if (userId != null) {
                        db.collection("users").document(userId).collection("transactions")
                            .get().addOnSuccessListener { snapshot ->
                                val batch = db.batch()
                                for (doc in snapshot) batch.delete(doc.reference)
                                batch.commit().addOnSuccessListener {
                                    Toast.makeText(context, "Data cleared", Toast.LENGTH_SHORT).show()
                                }
                            }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        return view
    }

    private fun updateBudgetUI() {
        val formattedLimit = if (currencySymbol == "₫") {
            "${String.format("%.0f", budgetLimit)} $currencySymbol"
        } else {
            "$currencySymbol${String.format("%.0f", budgetLimit)}"
        }

        val formattedExpense = if (currencySymbol == "₫") {
            "${String.format("%.0f", currentExpense)} $currencySymbol"
        } else {
            "$currencySymbol${String.format("%.0f", currentExpense)}"
        }

        tvBudgetLimit.text = "Limit: $formattedLimit"
        tvExpenseStatus.text = "Spent: $formattedExpense"

        val percentage = (currentExpense / budgetLimit) * 100
        progressBar.progress = percentage.toInt()

        if (percentage < 50) {
            progressBar.progressTintList = ColorStateList.valueOf(Color.parseColor("#00E676"))
        } else if (percentage < 85) {
            progressBar.progressTintList = ColorStateList.valueOf(Color.parseColor("#FFC107"))
        } else {
            progressBar.progressTintList = ColorStateList.valueOf(Color.parseColor("#FF5252"))
        }
    }

    private fun exportData() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()

        Toast.makeText(context, "Generating Report...", Toast.LENGTH_SHORT).show()

        db.collection("users").document(userId).collection("transactions")
            .get()
            .addOnSuccessListener { documents ->
                try {
                    val csvHeader = "Date,Type,Category,Amount,Description\n"
                    val sb = StringBuilder()
                    sb.append(csvHeader)

                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

                    for (doc in documents) {
                        val t = doc.toObject(Transaction::class.java)
                        val date = sdf.format(Date(t.timestamp))
                        val descSafe = t.description.replace(",", " ")
                        sb.append("$date,${t.type},${t.category},${t.amount},$descSafe\n")
                    }

                    val fileName = "PocketBudget_Report_${System.currentTimeMillis()}.csv"
                    val file = File(requireContext().cacheDir, fileName)
                    val writer = FileWriter(file)
                    writer.write(sb.toString())
                    writer.close()

                    val uri = FileProvider.getUriForFile(
                        requireContext(),
                        "${requireContext().packageName}.provider",
                        file
                    )

                    val intent = Intent(Intent.ACTION_SEND)
                    intent.type = "text/csv"
                    intent.putExtra(Intent.EXTRA_SUBJECT, "Pocket Budget Report")
                    intent.putExtra(Intent.EXTRA_STREAM, uri)
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

                    startActivity(Intent.createChooser(intent, "Share Report"))

                } catch (e: Exception) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
    }
}