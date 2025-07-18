package ai.fluxuate.gerrit.git

import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.transport.ReceivePack
import org.eclipse.jgit.transport.UploadPack
import org.eclipse.jgit.transport.RefAdvertiser
import org.eclipse.jgit.transport.PacketLineOut
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody
import org.slf4j.LoggerFactory
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Modern Spring Boot controller for Git HTTP protocol operations.
 * Handles Git clone, fetch, and push operations using Jakarta Servlet API.
 */
@RestController
@RequestMapping("/git")
class GitHttpController(
    private val gitRepositoryService: GitRepositoryService,
    private val gitConfig: GitConfiguration
) {

    private val logger = LoggerFactory.getLogger(GitHttpController::class.java)

    /**
     * Handles Git info/refs requests for both upload-pack (fetch) and receive-pack (push).
     * This is the initial request Git clients make to discover available refs.
     */
    @GetMapping("/{projectName}/info/refs")
    fun getInfoRefs(
        @PathVariable projectName: String,
        @RequestParam("service", required = false) service: String?,
        request: HttpServletRequest,
        authentication: Authentication?
    ): ResponseEntity<StreamingResponseBody> {
        
        logger.debug("Git info/refs request for project: $projectName, service: $service")
        
        try {
            // Validate project name and check if repository exists
            if (!gitRepositoryService.repositoryExists(projectName)) {
                logger.warn("Repository not found: $projectName")
                return ResponseEntity.notFound().build()
            }
            
            // Check permissions
            if (!hasRepositoryAccess(projectName, service, authentication)) {
                logger.warn("Access denied for project: $projectName, service: $service")
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            }
            
            val repository = gitRepositoryService.openRepository(projectName)
            
            return when (service) {
                "git-upload-pack" -> handleUploadPackInfoRefs(repository)
                "git-receive-pack" -> handleReceivePackInfoRefs(repository)
                else -> {
                    // Dumb HTTP protocol (not recommended for Gerrit)
                    logger.warn("Dumb HTTP protocol requested for project: $projectName")
                    ResponseEntity.badRequest().build()
                }
            }
            
        } catch (e: Exception) {
            logger.error("Error handling info/refs for project: $projectName", e)
            return ResponseEntity.internalServerError().build()
        }
    }

    /**
     * Handles Git upload-pack requests (fetch/clone operations).
     */
    @PostMapping("/{projectName}/git-upload-pack")
    fun uploadPack(
        @PathVariable projectName: String,
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication?
    ): ResponseEntity<StreamingResponseBody> {
        
        logger.debug("Git upload-pack request for project: $projectName")
        
        try {
            // Validate repository and permissions
            if (!gitRepositoryService.repositoryExists(projectName)) {
                return ResponseEntity.notFound().build()
            }
            
            if (!hasRepositoryAccess(projectName, "git-upload-pack", authentication)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            }
            
            val repository = gitRepositoryService.openRepository(projectName)
            
            return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/x-git-upload-pack-result"))
                .body(StreamingResponseBody { outputStream ->
                    val uploadPack = UploadPack(repository)
                    configureUploadPack(uploadPack)
                    uploadPack.upload(request.inputStream, outputStream, null)
                })
                
        } catch (e: Exception) {
            logger.error("Error in upload-pack for project: $projectName", e)
            return ResponseEntity.internalServerError().build()
        }
    }

    /**
     * Handles Git receive-pack requests (push operations).
     */
    @PostMapping("/{projectName}/git-receive-pack")
    fun receivePack(
        @PathVariable projectName: String,
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication?
    ): ResponseEntity<StreamingResponseBody> {
        
        logger.debug("Git receive-pack request for project: $projectName")
        
        try {
            // Validate repository and permissions
            if (!gitRepositoryService.repositoryExists(projectName)) {
                return ResponseEntity.notFound().build()
            }
            
            if (!hasRepositoryAccess(projectName, "git-receive-pack", authentication)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            }
            
            val repository = gitRepositoryService.openRepository(projectName)
            
            return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/x-git-receive-pack-result"))
                .body(StreamingResponseBody { outputStream ->
                    val receivePack = ReceivePack(repository)
                    configureReceivePack(receivePack, projectName, authentication)
                    receivePack.receive(request.inputStream, outputStream, null)
                })
                
        } catch (e: Exception) {
            logger.error("Error in receive-pack for project: $projectName", e)
            return ResponseEntity.internalServerError().build()
        }
    }

    /**
     * Configures upload-pack for Gerrit-specific behavior.
     */
    private fun configureUploadPack(uploadPack: UploadPack) {
        // Enable virtual branch advertisement for refs/changes/*
        if (gitConfig.virtualBranchesEnabled) {
            uploadPack.setRefFilter { refs ->
                // Advertise all refs including virtual branches
                refs.forEach { (refName, ref) ->
                    logger.debug("Advertising ref: $refName")
                }
                refs
            }
        }
        
        // Set timeout (converted to int seconds)
        uploadPack.setTimeout(gitConfig.operationTimeoutSeconds.toInt())
        
        // Enable bidirectional pipe for better performance
        uploadPack.isBiDirectionalPipe = true
    }

    /**
     * Configures receive-pack for Gerrit's change-based workflow.
     */
    private fun configureReceivePack(
        receivePack: ReceivePack, 
        projectName: String, 
        authentication: Authentication?
    ) {
        // Set up ref filter for Gerrit workflow
        receivePack.setRefFilter { refs ->
            val filteredRefs = refs.filter { (refName, _) ->
                // Allow pushes to refs/for/* for change creation
                refName.startsWith("refs/for/") || 
                refName.startsWith("refs/heads/") ||
                refName.startsWith("refs/tags/")
            }
            filteredRefs
        }
        
        // Set timeout (converted to int seconds)
        receivePack.setTimeout(gitConfig.operationTimeoutSeconds.toInt())
        
        // Set up pre-receive hook for Change-Id validation
        receivePack.setPreReceiveHook { rp, commands ->
            logger.info("Processing push with ${commands.size} commands for project: $projectName")
            validatePushCommands(commands, projectName, authentication)
        }
        
        // Set up post-receive hook for change creation
        receivePack.setPostReceiveHook { rp, commands ->
            logger.info("Post-processing push commands for project: $projectName")
            processPushCommands(commands, projectName, authentication)
        }
        
        // Enable atomic transactions
        receivePack.isAtomic = true
    }

    /**
     * Handles upload-pack info/refs response.
     */
    private fun handleUploadPackInfoRefs(repository: Repository): ResponseEntity<StreamingResponseBody> {
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/x-git-upload-pack-advertisement"))
            .header(HttpHeaders.CACHE_CONTROL, "no-cache")
            .body(StreamingResponseBody { outputStream ->
                val uploadPack = UploadPack(repository)
                configureUploadPack(uploadPack)
                
                // Write the service advertisement
                outputStream.write("001e# service=git-upload-pack\n".toByteArray())
                outputStream.write("0000".toByteArray())
                
                // Generate and write refs advertisement
                val pktOut = PacketLineOut(outputStream)
                val refAdvertiser = RefAdvertiser.PacketLineOutRefAdvertiser(pktOut)
                uploadPack.sendAdvertisedRefs(refAdvertiser)
            })
    }

    /**
     * Handles receive-pack info/refs response.
     */
    private fun handleReceivePackInfoRefs(repository: Repository): ResponseEntity<StreamingResponseBody> {
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/x-git-receive-pack-advertisement"))
            .header(HttpHeaders.CACHE_CONTROL, "no-cache")
            .body(StreamingResponseBody { outputStream ->
                val receivePack = ReceivePack(repository)
                configureReceivePack(receivePack, "", null)
                
                // Write the service advertisement
                outputStream.write("001f# service=git-receive-pack\n".toByteArray())
                outputStream.write("0000".toByteArray())
                
                // Generate and write refs advertisement
                val pktOut = PacketLineOut(outputStream)
                val refAdvertiser = RefAdvertiser.PacketLineOutRefAdvertiser(pktOut)
                receivePack.sendAdvertisedRefs(refAdvertiser)
            })
    }

    /**
     * Validates push commands for Gerrit-specific rules.
     */
    private fun validatePushCommands(
        commands: Collection<org.eclipse.jgit.transport.ReceiveCommand>,
        projectName: String,
        authentication: Authentication?
    ) {
        for (command in commands) {
            val refName = command.refName
            
            logger.debug("Validating push command: ${command.type} for ref $refName in project $projectName")
            
            // Handle refs/for/* pushes (change creation/update)
            if (refName.startsWith("refs/for/")) {
                validateChangeCreation(command, projectName, authentication)
            }
            
            // Handle direct branch pushes (should be restricted in Gerrit)
            if (refName.startsWith("refs/heads/")) {
                validateDirectBranchPush(command, projectName, authentication)
            }
        }
    }

    /**
     * Processes push commands after successful validation.
     */
    private fun processPushCommands(
        commands: Collection<org.eclipse.jgit.transport.ReceiveCommand>,
        projectName: String,
        authentication: Authentication?
    ) {
        for (command in commands) {
            val refName = command.refName
            
            if (refName.startsWith("refs/for/")) {
                // TODO: Create or update Gerrit change
                logger.info("Creating/updating change for ref: $refName in project: $projectName")
            }
        }
    }

    /**
     * Validates change creation requests.
     */
    private fun validateChangeCreation(
        command: org.eclipse.jgit.transport.ReceiveCommand,
        projectName: String,
        authentication: Authentication?
    ) {
        // TODO: Implement Change-Id validation
        // TODO: Check if user can create changes on target branch
        logger.debug("Validating change creation for ${command.refName} in project $projectName")
    }

    /**
     * Validates direct branch push requests.
     */
    private fun validateDirectBranchPush(
        command: org.eclipse.jgit.transport.ReceiveCommand,
        projectName: String,
        authentication: Authentication?
    ) {
        // In Gerrit, direct branch pushes should typically be restricted
        logger.warn("Direct branch push attempted to ${command.refName} in project $projectName")
        
        // TODO: Check if user has direct push permissions
        // For now, allow all direct pushes (should be configurable)
    }

    /**
     * Checks if the user has access to the repository for the given operation.
     */
    private fun hasRepositoryAccess(
        projectName: String,
        service: String?,
        authentication: Authentication?
    ): Boolean {
        // Allow anonymous read access for upload-pack
        if (service == "git-upload-pack" && gitConfig.anonymousReadEnabled) {
            return true
        }
        
        // Require authentication for write operations
        if (service == "git-receive-pack" && (authentication == null || !authentication.isAuthenticated)) {
            return false
        }
        
        // TODO: Implement proper permission checking based on project settings
        // For now, allow all authenticated users
        return authentication?.isAuthenticated ?: false
    }
}
