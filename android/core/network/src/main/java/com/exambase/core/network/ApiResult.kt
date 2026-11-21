package com.exambase.core.network

enum class FailureKind { NETWORK, UNAUTHORIZED, VALIDATION, RATE_LIMITED, OTHER }

data class ApiFailure(
    val kind: FailureKind,
    val code: String,
    val message: String,
    val fields: Map<String, String> = emptyMap(),
)

sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>
    data class Failure(val failure: ApiFailure) : ApiResult<Nothing>
}

inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(value))
    is ApiResult.Failure -> this
}
