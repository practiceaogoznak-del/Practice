package com.example.proect

import android.content.Context
import android.util.Log
import com.impossibl.postgres.jdbc.PGDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util.*

class DatabaseHelper(private val context: Context) {

    private val host = "10.6.50.167"
    private val port = 5433
    private val database = "postgres"
    private val user = "postgres"
    private val password = "1234"

    private val dataSource: PGDataSource by lazy {
        PGDataSource().apply {
            this.host = this@DatabaseHelper.host
            this.port = this@DatabaseHelper.port
            this.database = this@DatabaseHelper.database
            this.user = this@DatabaseHelper.user
            this.password = this@DatabaseHelper.password
        }
    }

    suspend fun checkNumberStatus(number: String): Triple<String, Boolean, Int> = withContext(Dispatchers.IO) {
        if (number.length != 10) return@withContext Triple("Неверный формат номера", false, R.color.dim_red)

        val series1 = number.substring(0, 2)
        val series2 = number.substring(2, 4)
        val numberPart = number.substring(4, 10)

        try {
            dataSource.connection.use { connection ->
                val stmt = connection.prepareStatement(
                    "SELECT checked_datetime, checked_tabnom FROM zadelka WHERE series1 = ? AND series2 = ? AND number = ?"
                )
                stmt.setString(1, series1)
                stmt.setString(2, series2)
                stmt.setString(3, numberPart)

                val rs = stmt.executeQuery()
                if (rs.next()) {
                    val checkedDate = rs.getTimestamp("checked_datetime")
                    val checkedTabnom = rs.getString("checked_tabnom")
                    if (checkedDate != null && checkedTabnom != null) {
                        val dateStr = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(checkedDate)
                        Triple("Заделан. Проверен ($dateStr)", false, R.color.dim_yellow)
                    } else {
                        Triple("Направлен на заделку", true, R.color.dim_green)
                    }
                } else {
                    Triple("На заделку не отправлялся", false, R.color.dim_red)
                }
            }
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error checking number status", e)
            Triple("Ошибка проверки статуса: ${e.message}", false, R.color.dim_red)
        }
    }

    suspend fun updateNumberStatus(number: String, workerId: String): Boolean = withContext(Dispatchers.IO) {
        if (number.length != 10) {
            Log.e("DatabaseHelper", "Invalid number format: $number")
            return@withContext false
        }

        val series1 = number.substring(0, 2)
        val series2 = number.substring(2, 4)
        val numberPart = number.substring(4, 10)

        try {
            dataSource.connection.use { connection ->
                val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val stmt = connection.prepareStatement(
                    """
                    INSERT INTO zadelka (series1, series2, number, input_tabnom, input_datetime, checked_tabnom, checked_datetime)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (series1, series2, number)
                    DO UPDATE SET checked_tabnom = ?, checked_datetime = ?
                    """
                )
                stmt.setString(1, series1)
                stmt.setString(2, series2)
                stmt.setString(3, numberPart)
                stmt.setString(4, workerId)
                stmt.setTimestamp(5, Timestamp(System.currentTimeMillis()))
                stmt.setString(6, workerId)
                stmt.setTimestamp(7, Timestamp.valueOf("$dateStr 00:00:00"))
                stmt.setString(8, workerId)
                stmt.setTimestamp(9, Timestamp.valueOf("$dateStr 00:00:00"))

                val rowsAffected = stmt.executeUpdate()
                Log.d("DatabaseHelper", "Rows affected: $rowsAffected")
                rowsAffected > 0
            }
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error updating number status", e)
            false
        }
    }

    suspend fun testConnection(): String = withContext(Dispatchers.IO) {
        try {
            dataSource.connection.use { connection ->
                "✅ Подключение успешно"
            }
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Connection test failed", e)
            "❌ Ошибка подключения: ${e.message}"
        }
    }
}
