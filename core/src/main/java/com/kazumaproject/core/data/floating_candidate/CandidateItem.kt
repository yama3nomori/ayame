package com.kazumaproject.core.data.floating_candidate

data class CandidateItem(
    val word: String,
    val length: UByte,
    val annotation: String? = null
)
