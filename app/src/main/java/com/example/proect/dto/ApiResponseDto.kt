package com.example.proect.dto

data class ApiResponseDto(
    val success: Boolean,
    val message: String? = null,
    val error: String? = null
)