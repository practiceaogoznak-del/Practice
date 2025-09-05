package com.example.proect.dto

data class StatusResponseDto(
    val message: String,
    val status: String,
    val color: String,
    val canScan: Boolean
)