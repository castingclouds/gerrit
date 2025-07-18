package ai.fluxuate.gerrit.api.dto

import com.fasterxml.jackson.annotation.JsonInclude

/**
 * Generic API response wrapper matching Gerrit's response format.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApiResponse<T>(
    val data: T? = null,
    val error: String? = null,
    val status: Int? = null
)

/**
 * Error response for API endpoints.
 */
data class ErrorResponse(
    val message: String,
    val code: String? = null,
    val details: Map<String, Any>? = null
)

/**
 * Success response for operations that don't return data.
 */
data class SuccessResponse(
    val message: String = "OK"
)
