package ai.fluxuate.gerrit.api.controller

import ai.fluxuate.gerrit.api.dto.*
import ai.fluxuate.gerrit.service.AccountService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

data class RegisterRequest(
    val username: String,
    val email: String,
    val name: String,
    val password: String
)

data class LoginRequest(
    val username: String,
    val password: String
)

data class AuthResponse(
    val token: String,
    val user: AccountInfo
)

@RestController
@RequestMapping("/api")
class AuthController {

    @Autowired
    private lateinit var accountService: AccountService

    /**
     * Register a new user account
     * POST /api/register
     */
    @PostMapping("/register")
    fun register(@RequestBody request: RegisterRequest): ResponseEntity<AccountInfo> {
        try {
            // Create the account input with password
            val accountInput = AccountInput(
                name = request.name,
                email = request.email,
                username = request.username,
                httpPassword = request.password // Store password for HTTP authentication
            )

            // Create the account using the existing account service
            val accountInfo = accountService.createAccount(request.username, accountInput)
            
            return ResponseEntity.ok(accountInfo)
        } catch (e: Exception) {
            throw e
        }
    }

    /**
     * Login endpoint
     * POST /api/login
     */
    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest): ResponseEntity<AuthResponse> {
        try {
            // Verify password
            if (!accountService.verifyPassword(request.username, request.password)) {
                throw ai.fluxuate.gerrit.api.exception.UnauthorizedException("Invalid username or password")
            }
            
            // Get the user account
            val accountInfo = accountService.getAccount(request.username)
            
            // Create authentication token
            val token = "Basic " + java.util.Base64.getEncoder().encodeToString("${request.username}:${request.password}".toByteArray())
            
            val response = AuthResponse(
                token = token,
                user = accountInfo
            )
            
            return ResponseEntity.ok(response)
        } catch (e: Exception) {
            throw e
        }
    }
}
