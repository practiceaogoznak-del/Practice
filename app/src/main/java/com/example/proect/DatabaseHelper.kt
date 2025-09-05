package com.example.proect

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DatabaseHelper(private val context: Context) {
    private val networkHelper = NetworkHelper(context)

    suspend fun checkNumberStatus(number: String): Triple<String, Boolean, Int> = withContext(Dispatchers.IO) {
        networkHelper.checkNumberStatus(number)
    }

    suspend fun updateNumberStatus(number: String, operatorTabnom: String): Boolean = withContext(Dispatchers.IO) {
        networkHelper.updateNumberStatus(number, operatorTabnom)
    }

    suspend fun testConnection(): String = withContext(Dispatchers.IO) {
        networkHelper.testConnection()
    }
}