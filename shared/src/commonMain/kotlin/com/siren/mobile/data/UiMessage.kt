package com.siren.mobile.data

/** One-shot message surfaced as a snackbar by AppRoot. */
data class UiMessage(
    val text: String,
    val isError: Boolean = false,
)

/**
 * Outcome of a parent typing a student's linking code.
 *
 * [Requested] rather than [Success] is the normal happy path now: the link is not live
 * until the student confirms the person really is their parent or guardian.
 */
sealed interface LinkResult {
    /** The request was raised and is waiting on the student. */
    data class Requested(val studentName: String) : LinkResult

    /** A request for this student is already outstanding. */
    data class AlreadyRequested(val studentName: String) : LinkResult

    /** Link completed immediately — only reachable by an adviser adding to their class. */
    data class Success(val studentName: String) : LinkResult
    data object NotFound : LinkResult
    data object AlreadyLinked : LinkResult
    data class Failed(val reason: String) : LinkResult
}
