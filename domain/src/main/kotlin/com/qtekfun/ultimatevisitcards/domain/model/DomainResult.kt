package com.qtekfun.ultimatevisitcards.domain.model

/** Outcome of a domain operation that can fail. Errors never reach the UI as exceptions (see CONTRIBUTING.md). */
sealed interface DomainResult<out T> {
    data class Success<out T>(val value: T) : DomainResult<T>

    data class Failure(val error: DomainError) : DomainResult<Nothing>
}

inline fun <T, R> DomainResult<T>.map(transform: (T) -> R): DomainResult<R> = when (this) {
    is DomainResult.Success -> DomainResult.Success(transform(value))
    is DomainResult.Failure -> this
}

fun <T> DomainResult<T>.getOrNull(): T? = (this as? DomainResult.Success)?.value
