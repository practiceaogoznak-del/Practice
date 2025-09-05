package com.example.proect

import android.content.Context
import android.util.Log
import com.example.proect.dto.ApiResponseDto
import com.example.proect.dto.StatusResponseDto
import com.example.proect.dto.UpdateStatusRequestDto
import com.google.gson.Gson
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class NetworkHelper(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val baseUrl = "https://api-practice-fzn4.onrender.com" // Исправлено: убраны пробелы
    private val apiPath = "/api/zadelka" // Путь к API контроллеру

    // Проверка статуса номера
    suspend fun checkNumberStatus(number: String): Triple<String, Boolean, Int> {
        return try {
            val request = Request.Builder()
                .url("$baseUrl$apiPath/status/$number") // Исправлено: добавлен правильный путь
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use Triple("Ошибка сети: ${response.code}", false, R.color.dim_red)
                }

                val body = response.body?.string()
                if (body != null) {
                    val statusResponse = gson.fromJson(body, StatusResponseDto::class.java)
                    val colorRes = when (statusResponse.color) {
                        "green" -> R.color.dim_green
                        "yellow" -> R.color.dim_yellow
                        "red" -> R.color.dim_red
                        else -> R.color.dim_red
                    }
                    Triple(statusResponse.message, statusResponse.canScan, colorRes)
                } else {
                    Triple("Пустой ответ от сервера", false, R.color.dim_red)
                }
            }
        } catch (e: Exception) {
            Log.e("Network", "Error checking number status", e)
            Triple("Ошибка: ${e.message}", false, R.color.dim_red)
        }
    }

    // Обновление статуса номера
    suspend fun updateNumberStatus(number: String, operatorTabnom: String): Boolean {
        return try {
            val requestDto = UpdateStatusRequestDto(number, operatorTabnom)
            val json = gson.toJson(requestDto)

            val body = json.toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$baseUrl$apiPath/update") // Исправлено: добавлен правильный путь
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use false
                }

                val responseBody = response.body?.string()
                if (responseBody != null) {
                    val apiResponse = gson.fromJson(responseBody, ApiResponseDto::class.java)
                    apiResponse.success ?: false
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            Log.e("Network", "Error updating number status", e)
            false
        }
    }

    // Тестирование подключения
    suspend fun testConnection(): String {
        return try {
            val request = Request.Builder()
                .url("$baseUrl$apiPath/test") // Исправлено: добавлен правильный путь
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use "❌ Ошибка подключения: ${response.code}"
                }

                val body = response.body?.string()
                if (body != null) {
                    val apiResponse = gson.fromJson(body, ApiResponseDto::class.java)
                    apiResponse.message ?: "Неизвестная ошибка"
                } else {
                    "❌ Пустой ответ от сервера"
                }
            }
        } catch (e: Exception) {
            "❌ Ошибка: ${e.message}"
        }
    }
}