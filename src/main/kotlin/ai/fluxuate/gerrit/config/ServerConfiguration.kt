package ai.fluxuate.gerrit.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import org.springframework.validation.annotation.Validated
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Max

/**
 * Configuration properties for server settings in Gerrit.
 */
@ConfigurationProperties(prefix = "server")
@Component
@Validated
class ServerConfiguration {
    @field:NotNull
    @field:Min(1024)
    @field:Max(65535)
    var port: Int = 8080
    
    @field:NotBlank
    var protocol: String = "http"
    
    @field:NotBlank 
    var host: String = "localhost"
    
    /**
     * Get the base URL for the server
     */
    fun getBaseUrl(): String {
        return "$protocol://$host:$port"
    }
    
    /**
     * Get the full URL for a project
     */
    fun getProjectUrl(projectName: String): String {
        return "${getBaseUrl()}/$projectName"
    }
}
