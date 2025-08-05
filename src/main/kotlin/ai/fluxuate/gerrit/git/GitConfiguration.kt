package ai.fluxuate.gerrit.git

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Max

/**
 * Configuration properties for Git operations in Gerrit.
 */
@ConfigurationProperties(prefix = "gerrit.git")
@Validated
class GitConfiguration {
    @field:NotBlank
    var repositoryBasePath: String = "./repositories"
    
    @field:NotNull
    var maxCachedRepositories: Int = 100
    
    @field:NotNull
    var repositoryCacheTtlSeconds: Long = 3600
    
    @field:NotNull
    var httpEnabled: Boolean = true
    
    @field:NotNull
    var httpPort: Int = 8080
    
    @field:NotNull
    var anonymousReadEnabled: Boolean = true
    
    /**
     * SSH protocol settings
     */
    // SSH Configuration
    var sshEnabled: Boolean = true
    var sshHost: String = "localhost"
    @get:Min(1024)
    @get:Max(65535)
    var sshPort: Int = 29418
    @get:NotBlank
    var sshHostKeyPath: String = "\${gerrit.git.repositoryBasePath}/ssh_host_key"
    @field:NotNull
    var sshIdleTimeoutSeconds: Long = 300
    @field:NotNull
    var sshReadTimeoutSeconds: Long = 30

    // Git Command Configuration
    @field:NotNull
    var receivePackEnabled: Boolean = true
    @field:NotNull
    var uploadPackEnabled: Boolean = true
    @field:NotNull
    var pushTimeoutSeconds: Long = 300
    @field:NotNull
    var fetchTimeoutSeconds: Long = 300

    // Trunk Branch Configuration
    @field:NotBlank
    var trunkBranchName: String = "trunk"

    // ReceivePack Configuration
    @field:NotNull
    var allowCreates: Boolean = true
    @field:NotNull
    var allowDeletes: Boolean = true
    @field:NotNull
    var allowNonFastForwards: Boolean = false
    @field:NotNull
    var allowDirectPush: Boolean = false

    // Upload-pack configuration
    @field:NotNull
    var maxUploadObjects: Int = 10000
    @field:NotNull
    var maxUploadRefs: Int = 1000
    @field:NotNull
    var maxNegotiationRounds: Int = 100
    @field:NotNull
    var maxPackObjects: Int = 50000

    // Repository Validation
    @field:NotNull
    var validateRepositoryNames: Boolean = true
    @field:NotBlank
    var allowedRepositoryNamePattern: String = "[a-zA-Z0-9][a-zA-Z0-9._/-]*[a-zA-Z0-9]"
    @field:NotNull
    @field:Min(1)
    var maxRepositoryNameLength: Int = 255

    // Advanced Git Features
    @field:NotNull
    var allowPartialClone: Boolean = true
    
    @field:NotNull
    var allowTipSha1InWant: Boolean = true
    
    @field:NotNull
    var allowReachableSha1InWant: Boolean = true
    
    @field:NotNull
    var maxConcurrentOperations: Int = 10
    
    @field:NotNull
    var operationTimeoutSeconds: Long = 300
    
    // LFS Configuration
    @field:NotNull
    var lfsEnabled: Boolean = false
    @field:NotNull
    var lfsMaxFileSize: Long = 104857600 // 100MB default
    @field:NotBlank
    var lfsStoragePath: String = "./lfs-storage"
    
    @field:NotNull
    var virtualBranchesEnabled: Boolean = false
    
    @field:NotNull
    var maxPatchSetsPerChange: Int = 0
    
    @field:NotNull
    var changeIdValidationEnabled: Boolean = false
    
    @field:NotNull
    var changeIdRequired: Boolean = false
    
    @field:NotNull
    var autoGenerateChangeId: Boolean = false
    
    @field:NotNull
    var changeIdGenerationEnabled: Boolean = false
    
    @field:NotNull
    var refAdvertisementEnabled: Boolean = false
    
    @field:NotNull
    var pushHookEnabled: Boolean = false
    
    @field:NotNull
    var gcEnabled: Boolean = false
    
    @field:NotNull
    var gcIntervalHours: Int = 0
    
    @field:NotNull
    var packRefsEnabled: Boolean = false
    
    @field:NotNull
    var packRefsIntervalHours: Int = 0
}
