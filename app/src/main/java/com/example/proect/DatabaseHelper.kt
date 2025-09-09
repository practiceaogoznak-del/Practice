package com.example.proect

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Timestamp

data class ZadelkaRecord(
    val id: Int,
    val series1: String,
    val series2: String,
    val number: String,
    val ci: String,
    val inputTabnom: String,
    val inputDatetime: Timestamp?,
    val checkedTabnom: String?,
    val checkedDatetime: Timestamp?,
    val defectId: Int,
    val comment: String?
)

class DatabaseHelper(private val context: Context) {

    // Database connection details (replace with your actual values)
    // WARNING: Do NOT hardcode credentials in production code! Use secure storage (e.g., Android Keystore) or environment variables.
    private val server = "your_server_ip_or_hostname"  // e.g., "192.168.1.100" or "sqlserver.example.com"
    private val port = "1433"  // Default SQL Server port
    private val databaseName = "your_database_name"
    private val username = "your_sql_username"
    private val password = "your_sql_password"
    private val connectionUrl = "jdbc:jtds:sqlserver://$server:$port;databaseName=$databaseName;user=$username;password=$password;encrypt=true;trustServerCertificate=true"

    // Test the database connection
    suspend fun testConnection(): String = withContext(Dispatchers.IO) {
        var connection: Connection? = null
        try {
            Class.forName("net.sourceforge.jtds.jdbc.Driver")
            connection = DriverManager.getConnection(connectionUrl)
            return@withContext "Подключение успешно"
        } catch (e: SQLException) {
            Log.e("DBHelper", "Connection failed", e)
            return@withContext "Ошибка подключения: ${e.message}"
        } finally {
            connection?.close()
        }
    }

    // Create a new record in the zadelka table
    suspend fun createRecord(
        series1: String,
        series2: String,
        number: String,
        ci: String,
        inputTabnom: String,
        defectId: Int,
        comment: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        var connection: Connection? = null
        var preparedStatement: PreparedStatement? = null
        try {
            if (series1.length != 2 || series2.length != 2 || number.length != 6 || ci.length != 2 || inputTabnom.length != 6) {
                Log.e("DBHelper", "Invalid input lengths")
                return@withContext false
            }
            Class.forName("net.sourceforge.jtds.jdbc.Driver")
            connection = DriverManager.getConnection(connectionUrl)
            val query = """
                INSERT INTO zadelka (series1, series2, number, ci, input_tabnom, input_datetime, defect_id, comment)
                VALUES (?, ?, ?, ?, ?, GETDATE(), ?, ?)
            """
            preparedStatement = connection.prepareStatement(query)
            preparedStatement.setString(1, series1)
            preparedStatement.setString(2, series2)
            preparedStatement.setString(3, number)
            preparedStatement.setString(4, ci)
            preparedStatement.setString(5, inputTabnom)
            preparedStatement.setInt(6, defectId)
            preparedStatement.setString(7, comment)
            val rowsAffected = preparedStatement.executeUpdate()
            return@withContext rowsAffected > 0
        } catch (e: SQLException) {
            Log.e("DBHelper", "Insert failed", e)
            return@withContext false
        } finally {
            preparedStatement?.close()
            connection?.close()
        }
    }

    // Read a record by number (series1 + series2 + number)
    suspend fun checkNumberStatus(number: String): Triple<String, Boolean, Int> = withContext(Dispatchers.IO) {
        var connection: Connection? = null
        var preparedStatement: PreparedStatement? = null
        var resultSet: ResultSet? = null
        try {
            if (number.length != 10) {
                return@withContext Triple("Неправильный формат номера", false, android.R.color.holo_red_light)
            }
            val series1 = number.substring(0, 2)
            val series2 = number.substring(2, 4)
            val num = number.substring(4)
            Class.forName("net.sourceforge.jtds.jdbc.Driver")
            connection = DriverManager.getConnection(connectionUrl)
            val query = """
                SELECT checked_tabnom, checked_datetime, defect_id, comment
                FROM zadelka
                WHERE series1 = ? AND series2 = ? AND number = ?
            """
            preparedStatement = connection.prepareStatement(query)
            preparedStatement.setString(1, series1)
            preparedStatement.setString(2, series2)
            preparedStatement.setString(3, num)
            resultSet = preparedStatement.executeQuery()
            if (resultSet.next()) {
                val checkedTabnom = resultSet.getString("checked_tabnom")
                val checkedDatetime = resultSet.getTimestamp("checked_datetime")
                return@withContext if (checkedTabnom != null && checkedDatetime != null) {
                    Triple("Отсканирован ($checkedTabnom)", false, android.R.color.holo_red_light)
                } else {
                    Triple("Активен", true, android.R.color.holo_green_light)
                }
            } else {
                return@withContext Triple("Не найден", false, android.R.color.holo_red_light)
            }
        } catch (e: SQLException) {
            Log.e("DBHelper", "Query failed", e)
            return@withContext Triple("Ошибка: ${e.message}", false, android.R.color.holo_red_light)
        } finally {
            resultSet?.close()
            preparedStatement?.close()
            connection?.close()
        }
    }

    // Read all records
    suspend fun getAllRecords(): List<ZadelkaRecord> = withContext(Dispatchers.IO) {
        var connection: Connection? = null
        var preparedStatement: PreparedStatement? = null
        var resultSet: ResultSet? = null
        val records = mutableListOf<ZadelkaRecord>()
        try {
            Class.forName("net.sourceforge.jtds.jdbc.Driver")
            connection = DriverManager.getConnection(connectionUrl)
            val query = """
                SELECT id, series1, series2, number, ci, input_tabnom, input_datetime, 
                       checked_tabnom, checked_datetime, defect_id, comment
                FROM zadelka
            """
            preparedStatement = connection.prepareStatement(query)
            resultSet = preparedStatement.executeQuery()
            while (resultSet.next()) {
                records.add(
                    ZadelkaRecord(
                        id = resultSet.getInt("id"),
                        series1 = resultSet.getString("series1"),
                        series2 = resultSet.getString("series2"),
                        number = resultSet.getString("number"),
                        ci = resultSet.getString("ci"),
                        inputTabnom = resultSet.getString("input_tabnom"),
                        inputDatetime = resultSet.getTimestamp("input_datetime"),
                        checkedTabnom = resultSet.getString("checked_tabnom"),
                        checkedDatetime = resultSet.getTimestamp("checked_datetime"),
                        defectId = resultSet.getInt("defect_id"),
                        comment = resultSet.getString("comment")
                    )
                )
            }
            return@withContext records
        } catch (e: SQLException) {
            Log.e("DBHelper", "Fetch all records failed", e)
            return@withContext emptyList()
        } finally {
            resultSet?.close()
            preparedStatement?.close()
            connection?.close()
        }
    }

    // Update the status of a number (set checked_tabnom and checked_datetime)
    suspend fun updateNumberStatus(number: String, workerId: String): Boolean = withContext(Dispatchers.IO) {
        var connection: Connection? = null
        var preparedStatement: PreparedStatement? = null
        try {
            if (number.length != 10) {
                Log.e("DBHelper", "Invalid number format: $number")
                return@withContext false
            }
            val series1 = number.substring(0, 2)
            val series2 = number.substring(2, 4)
            val num = number.substring(4)
            Class.forName("net.sourceforge.jtds.jdbc.Driver")
            connection = DriverManager.getConnection(connectionUrl)
            val query = """
                UPDATE zadelka
                SET checked_tabnom = ?, checked_datetime = GETDATE()
                WHERE series1 = ? AND series2 = ? AND number = ?
            """
            preparedStatement = connection.prepareStatement(query)
            preparedStatement.setString(1, workerId)
            preparedStatement.setString(2, series1)
            preparedStatement.setString(3, series2)
            preparedStatement.setString(4, num)
            val rowsAffected = preparedStatement.executeUpdate()
            return@withContext rowsAffected > 0
        } catch (e: SQLException) {
            Log.e("DBHelper", "Update failed", e)
            return@withContext false
        } finally {
            preparedStatement?.close()
            connection?.close()
        }
    }

    // Update additional fields (e.g., comment or defect_id)
    suspend fun updateRecordDetails(id: Int, defectId: Int, comment: String?): Boolean = withContext(Dispatchers.IO) {
        var connection: Connection? = null
        var preparedStatement: PreparedStatement? = null
        try {
            Class.forName("net.sourceforge.jtds.jdbc.Driver")
            connection = DriverManager.getConnection(connectionUrl)
            val query = """
                UPDATE zadelka
                SET defect_id = ?, comment = ?
                WHERE id = ?
            """
            preparedStatement = connection.prepareStatement(query)
            preparedStatement.setInt(1, defectId)
            preparedStatement.setString(2, comment)
            preparedStatement.setInt(3, id)
            val rowsAffected = preparedStatement.executeUpdate()
            return@withContext rowsAffected > 0
        } catch (e: SQLException) {
            Log.e("DBHelper", "Update details failed", e)
            return@withContext false
        } finally {
            preparedStatement?.close()
            connection?.close()
        }
    }

    // Delete a record by id
    suspend fun deleteRecord(id: Int): Boolean = withContext(Dispatchers.IO) {
        var connection: Connection? = null
        var preparedStatement: PreparedStatement? = null
        try {
            Class.forName("net.sourceforge.jtds.jdbc.Driver")
            connection = DriverManager.getConnection(connectionUrl)
            val query = "DELETE FROM zadelka WHERE id = ?"
            preparedStatement = connection.prepareStatement(query)
            preparedStatement.setInt(1, id)
            val rowsAffected = preparedStatement.executeUpdate()
            return@withContext rowsAffected > 0
        } catch (e: SQLException) {
            Log.e("DBHelper", "Delete failed", e)
            return@withContext false
        } finally {
            preparedStatement?.close()
            connection?.close()
        }
    }
}