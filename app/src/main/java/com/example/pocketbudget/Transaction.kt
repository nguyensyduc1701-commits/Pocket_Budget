package com.example.pocketbudget

data class Transaction(
    var id: String = "",
    var amount: Double = 0.0,
    var type: String = "EXPENSE",
    var category: String = "Other",
    var description: String = "",
    var timestamp: Long = 0
)