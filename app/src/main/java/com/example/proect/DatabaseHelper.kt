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


    private val server = "06SQL"
    private val databaseName = "PPFDATA"
    private val username = "arm79100"
    private val password = "Android79100"
    private val connectionUrl = "jdbc:jtds:sqlserver://$server;databaseName=$databaseName;user=$username;password=$password;encrypt=true;trustServerCertificate=true"

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
                INSERT INTO arm79100.zadelka (series1, series2, number, ci, input_tabnom, input_datetime, defect_id, comment)
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


    suspend fun checkNumberStatus(number: String, workerId: String, ci: String): Triple<String, Boolean, Int> = withContext(Dispatchers.IO) {
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

            // Добавляем фильтр по CI
            val selectQuery = """
            SELECT id, checked_datetime
            FROM arm79100.zadelka
            WHERE series1 = ? AND series2 = ? AND number = ? AND ci = ?
        """
            preparedStatement = connection.prepareStatement(selectQuery)
            preparedStatement.setString(1, series1)
            preparedStatement.setString(2, series2)
            preparedStatement.setString(3, num)
            preparedStatement.setString(4, ci)
            resultSet = preparedStatement.executeQuery()

            if (!resultSet.next()) {
                return@withContext Triple("Не найден", false, android.R.color.holo_red_light)
            }

            val zadelkaId = resultSet.getInt("id")
            val checkedDatetime = resultSet.getTimestamp("checked_datetime")

            resultSet.close()
            preparedStatement.close()

            return@withContext if (checkedDatetime != null) {
                Triple("Заделка была проверена ранее", false, android.R.color.holo_orange_light)
            } else {
                val updateQuery = """
                UPDATE arm79100.zadelka
                SET checked_tabnom = ?, checked_datetime = GETDATE()
                WHERE id = ?
            """
                preparedStatement = connection.prepareStatement(updateQuery)
                preparedStatement.setString(1, workerId)
                preparedStatement.setInt(2, zadelkaId)
                val rowsAffected = preparedStatement.executeUpdate()

                if (rowsAffected > 0) {
                    Triple("Заделка проверена", false, android.R.color.holo_green_light)
                } else {
                    Triple("Ошибка обновления", false, android.R.color.holo_red_light)
                }
            }

        } catch (e: SQLException) {
            Log.e("DBHelper", "CheckNumberStatus failed", e)
            return@withContext Triple("Ошибка: ${e.message}", false, android.R.color.holo_red_light)
        } finally {
            resultSet?.close()
            preparedStatement?.close()
            connection?.close()
        }
    }

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
                FROM arm79100.zadelka
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
                UPDATE arm79100.zadelka
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

    suspend fun updateRecordDetails(id: Int, defectId: Int, comment: String?): Boolean = withContext(Dispatchers.IO) {
        var connection: Connection? = null
        var preparedStatement: PreparedStatement? = null
        try {
            Class.forName("net.sourceforge.jtds.jdbc.Driver")
            connection = DriverManager.getConnection(connectionUrl)
            val query = """
                UPDATE arm79100.zadelka
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

    suspend fun deleteRecord(id: Int): Boolean = withContext(Dispatchers.IO) {
        var connection: Connection? = null
        var preparedStatement: PreparedStatement? = null
        try {
            Class.forName("net.sourceforge.jtds.jdbc.Driver")
            connection = DriverManager.getConnection(connectionUrl)
            val query = "DELETE FROM arm79100.zadelka WHERE id = ?"
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