package com.siren.mobile.data

/** One-shot message surfaced as a snackbar by AppRoot. */
data class UiMessage(
    val text: String,
    val isError: Boolean = false,
)

/** Outcome of a parent typing a student's linking code. */
sealed interface LinkResult {
    data class Success(val studentName: String) : LinkResult
    data object NotFound : LinkResult
    data object AlreadyLinked : LinkResult
    data class Failed(val reason: String) : LinkResult
}
