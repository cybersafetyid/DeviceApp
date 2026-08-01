package com.enterprise.busvalidator.core.common

/**
 * Generic domain result wrapper for repository and usecase operations.
 */
sealed class ResourceResult<out T> {
    data class Success<out T>(val data: T) : ResourceResult<T>()
    data class Error(val exception: Throwable, val message: String? = exception.message) : ResourceResult<Nothing>()
    object Loading : ResourceResult<Nothing>()
}
