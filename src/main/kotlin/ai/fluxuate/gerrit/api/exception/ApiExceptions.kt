package ai.fluxuate.gerrit.api.exception

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

/**
 * Base exception for API-related errors.
 */
abstract class ApiException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

/**
 * Exception thrown when a resource is not found.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
class NotFoundException(message: String) : ApiException(message)

/**
 * Exception thrown when there's a conflict with the current state.
 */
@ResponseStatus(HttpStatus.CONFLICT)
class ConflictException(message: String) : ApiException(message)

/**
 * Exception thrown when the request is invalid.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
class BadRequestException(message: String) : ApiException(message)

/**
 * Exception thrown when the user is not authorized.
 */
@ResponseStatus(HttpStatus.UNAUTHORIZED)
class UnauthorizedException(message: String) : ApiException(message)

/**
 * Exception thrown when the user is forbidden from accessing a resource.
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
class ForbiddenException(message: String) : ApiException(message)

/**
 * Exception thrown when there's an unprocessable entity.
 */
@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
class UnprocessableEntityException(message: String) : ApiException(message)

/**
 * Exception thrown when there's an internal server error.
 */
@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
class InternalServerErrorException(message: String, cause: Throwable? = null) : ApiException(message, cause)
