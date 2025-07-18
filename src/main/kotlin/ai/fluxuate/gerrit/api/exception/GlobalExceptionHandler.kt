package ai.fluxuate.gerrit.api.exception

import ai.fluxuate.gerrit.api.dto.ErrorResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest

/**
 * Global exception handler for API controllers.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(NotFoundException::class)
    fun handleNotFoundException(ex: NotFoundException, request: WebRequest): ResponseEntity<ErrorResponse> {
        logger.warn("Not found: {}", ex.message)
        val error = ErrorResponse(
            message = ex.message ?: "Resource not found",
            code = "NOT_FOUND"
        )
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error)
    }

    @ExceptionHandler(ConflictException::class)
    fun handleConflictException(ex: ConflictException, request: WebRequest): ResponseEntity<ErrorResponse> {
        logger.warn("Conflict: {}", ex.message)
        val error = ErrorResponse(
            message = ex.message ?: "Conflict with current state",
            code = "CONFLICT"
        )
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error)
    }

    @ExceptionHandler(BadRequestException::class)
    fun handleBadRequestException(ex: BadRequestException, request: WebRequest): ResponseEntity<ErrorResponse> {
        logger.warn("Bad request: {}", ex.message)
        val error = ErrorResponse(
            message = ex.message ?: "Invalid request",
            code = "BAD_REQUEST"
        )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error)
    }

    @ExceptionHandler(UnauthorizedException::class)
    fun handleUnauthorizedException(ex: UnauthorizedException, request: WebRequest): ResponseEntity<ErrorResponse> {
        logger.warn("Unauthorized: {}", ex.message)
        val error = ErrorResponse(
            message = ex.message ?: "Authentication required",
            code = "UNAUTHORIZED"
        )
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error)
    }

    @ExceptionHandler(ForbiddenException::class)
    fun handleForbiddenException(ex: ForbiddenException, request: WebRequest): ResponseEntity<ErrorResponse> {
        logger.warn("Forbidden: {}", ex.message)
        val error = ErrorResponse(
            message = ex.message ?: "Access forbidden",
            code = "FORBIDDEN"
        )
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error)
    }

    @ExceptionHandler(UnprocessableEntityException::class)
    fun handleUnprocessableEntityException(ex: UnprocessableEntityException, request: WebRequest): ResponseEntity<ErrorResponse> {
        logger.warn("Unprocessable entity: {}", ex.message)
        val error = ErrorResponse(
            message = ex.message ?: "Unprocessable entity",
            code = "UNPROCESSABLE_ENTITY"
        )
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(error)
    }

    @ExceptionHandler(InternalServerErrorException::class)
    fun handleInternalServerErrorException(ex: InternalServerErrorException, request: WebRequest): ResponseEntity<ErrorResponse> {
        logger.error("Internal server error: {}", ex.message, ex)
        val error = ErrorResponse(
            message = "Internal server error",
            code = "INTERNAL_SERVER_ERROR"
        )
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(ex: MethodArgumentNotValidException, request: WebRequest): ResponseEntity<ErrorResponse> {
        logger.warn("Validation error: {}", ex.message)
        
        val fieldErrors = ex.bindingResult.fieldErrors.associate { fieldError: FieldError ->
            fieldError.field to (fieldError.defaultMessage ?: "Invalid value")
        }
        
        val error = ErrorResponse(
            message = "Validation failed",
            code = "VALIDATION_ERROR",
            details = fieldErrors
        )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error)
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception, request: WebRequest): ResponseEntity<ErrorResponse> {
        logger.error("Unexpected error: {}", ex.message, ex)
        val error = ErrorResponse(
            message = "An unexpected error occurred",
            code = "INTERNAL_SERVER_ERROR"
        )
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error)
    }
}
