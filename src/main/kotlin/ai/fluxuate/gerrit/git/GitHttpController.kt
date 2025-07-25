package ai.fluxuate.gerrit.git

import org.springframework.web.bind.annotation.*
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.slf4j.LoggerFactory

/**
 * REST API endpoints for Git operations.
 * The actual Git HTTP protocol is handled by JGit's GitServlet.
 * This controller provides additional REST endpoints for Git-related operations.
 */
@RestController
@RequestMapping("/api/git")
class GitHttpController(
    private val gitRepositoryService: GitRepositoryService,
    private val gitConfiguration: GitConfiguration
) {

    private val logger = LoggerFactory.getLogger(GitHttpController::class.java)

    /**
     * Health check endpoint for Git operations
     */
    @GetMapping("/health")
    fun healthCheck(): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.ok(mapOf(
            "status" to "ok",
            "gitHttpEnabled" to gitConfiguration.httpEnabled,
            "gitSshEnabled" to gitConfiguration.sshEnabled,
            "repositoryBasePath" to gitConfiguration.repositoryBasePath
        ))
    }

    /**
     * Get Git configuration
     */
    @GetMapping("/config")
    fun getConfig(): ResponseEntity<GitConfiguration> {
        return ResponseEntity.ok(gitConfiguration)
    }
}
