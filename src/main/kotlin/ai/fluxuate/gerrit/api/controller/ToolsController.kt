package ai.fluxuate.gerrit.api.controller

import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.slf4j.LoggerFactory
import java.io.InputStream

/**
 * Controller for serving Git hooks and tools.
 * Provides endpoints for downloading hooks like commit-msg.
 */
@RestController
@RequestMapping("/tools")
class ToolsController {

    private val logger = LoggerFactory.getLogger(ToolsController::class.java)

    /**
     * Serve the commit-msg hook.
     * GET /tools/hooks/commit-msg
     */
    @GetMapping("/hooks/commit-msg")
    fun serveCommitMsgHook(): ResponseEntity<String> {
        return try {
            val hookContent = getCommitMsgHookContent()
            ResponseEntity.ok()
                .header("Content-Type", "application/octet-stream")
                .body(hookContent)
        } catch (e: Exception) {
            logger.error("Error serving commit-msg hook", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error loading commit-msg hook: ${e.message}")
        }
    }
    
    @GetMapping("/hooks/update")
    fun serveUpdateHook(): ResponseEntity<String> {
        return try {
            val hookContent = getUpdateHookContent()
            ResponseEntity.ok()
                .header("Content-Type", "application/octet-stream")
                .body(hookContent)
        } catch (e: Exception) {
            logger.error("Error serving update hook", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error loading update hook: ${e.message}")
        }
    }
    
    /**
     * List available hooks.
     * GET /tools/hooks/
     */
    @GetMapping("/hooks/")
    fun listHooks(): ResponseEntity<Map<String, String>> {
        val hooks = mapOf(
            "commit-msg" to "curl -Lo .git/hooks/commit-msg http://localhost:8080/tools/hooks/commit-msg && chmod +x .git/hooks/commit-msg",
            "update" to "curl -Lo .git/hooks/update http://localhost:8080/tools/hooks/update && chmod +x .git/hooks/update"
        )
        
        return ResponseEntity.ok(hooks)
    }

    /**
     * Get the content of the commit-msg hook from classpath resources.
     */
    private fun getCommitMsgHookContent(): String {
        val resourceStream = javaClass.classLoader.getResourceAsStream("git-hooks/commit-msg")
            ?: throw RuntimeException("Failed to load commit-msg hook from resources")
        
        return resourceStream.bufferedReader().use { it.readText() }
    }

    /**
     * Get the content of the update hook from classpath resources.
     */
    private fun getUpdateHookContent(): String {
        val resourceStream = javaClass.classLoader.getResourceAsStream("git-hooks/update")
            ?: throw RuntimeException("Failed to load update hook from resources")
        
        return resourceStream.bufferedReader().use { it.readText() }
    }
} 