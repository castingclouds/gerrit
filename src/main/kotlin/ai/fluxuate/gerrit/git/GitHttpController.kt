package ai.fluxuate.gerrit.git

import ai.fluxuate.gerrit.service.ChangeService
import ai.fluxuate.gerrit.util.ChangeIdUtil

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
import java.io.IOException

@RestController
@RequestMapping("/git")
class GitHttpController(
    private val gitConfiguration: GitConfiguration,
    private val repositoryService: GitRepositoryService,
    private val changeService: ChangeService
) {

    private val logger = LoggerFactory.getLogger(GitHttpController::class.java)

    @GetMapping("/{projectName}/info/refs")
    fun getInfoRefs(
        @PathVariable projectName: String,
        @RequestParam("service", required = false) service: String?,
        request: HttpServletRequest,
        authentication: Authentication?
    ): ResponseEntity<StreamingResponseBody> {
        
        logger.debug("Git info/refs request for project: $projectName, service: $service")
        
        if (!hasRepositoryAccess(projectName, service, authentication)) {
            logger.warn("Access denied for project: $projectName, service: $service")
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        
        return try {
            val repository = repositoryService.getRepository(projectName)
            
            when (service) {
                "git-upload-pack" -> handleUploadPackInfoRefs(repository)
                "git-receive-pack" -> handleReceivePackInfoRefs(repository)
                else -> {
                    logger.warn("Unknown or missing service parameter: $service")
                    ResponseEntity.badRequest().build()
                }
            }
        } catch (e: Exception) {
            logger.error("Error handling info/refs for project: $projectName", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    @PostMapping("/{projectName}/git-upload-pack")
    fun uploadPack(
        @PathVariable projectName: String,
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication?
    ): ResponseEntity<StreamingResponseBody> {
        
        logger.debug("Git upload-pack request for project: $projectName")
        
        if (!hasRepositoryAccess(projectName, "git-upload-pack", authentication)) {
            logger.warn("Access denied for project: $projectName, service: git-upload-pack")
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        
        return try {
            val repository = repositoryService.getRepository(projectName)
            
            ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/x-git-upload-pack-result"))
                .body(object : StreamingResponseBody {
                    @Throws(IOException::class)
                    override fun writeTo(outputStream: java.io.OutputStream) {
                        val uploadPack = UploadPack(repository)
                        configureUploadPack(uploadPack)
                        uploadPack.upload(request.inputStream, outputStream, null)
                    }
                })
        } catch (e: Exception) {
            logger.error("Error in upload-pack for project: $projectName", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    @PostMapping("/{projectName}/git-receive-pack")
    fun receivePack(
        @PathVariable projectName: String,
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication?
    ): ResponseEntity<StreamingResponseBody> {
        
        logger.debug("Git receive-pack request for project: $projectName")
        
        if (!hasRepositoryAccess(projectName, "git-receive-pack", authentication)) {
            logger.warn("Access denied for project: $projectName, service: git-receive-pack")
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        
        return try {
            val repository = repositoryService.getRepository(projectName)
            
            ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/x-git-receive-pack-result"))
                .body(object : StreamingResponseBody {
                    @Throws(IOException::class)
                    override fun writeTo(outputStream: java.io.OutputStream) {
                        val receivePack = ReceivePack(repository)
                        configureReceivePack(receivePack, projectName, authentication)
                        receivePack.receive(request.inputStream, outputStream, null)
                    }
                })
        } catch (e: Exception) {
            logger.error("Error in receive-pack for project: $projectName", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    private fun configureUploadPack(uploadPack: UploadPack) {
        logger.debug("Configuring upload pack for Git fetch operations")
    }

    private fun configureReceivePack(receivePack: ReceivePack, projectName: String, authentication: Authentication?) {
        receivePack.isAllowCreates = gitConfiguration.allowCreates
        receivePack.isAllowDeletes = gitConfiguration.allowDeletes
        receivePack.isAllowNonFastForwards = gitConfiguration.allowNonFastForwards
        
        receivePack.setPreReceiveHook { _, commands ->
            logger.debug("Pre-receive hook called with ${commands.size} commands")
            val errors = validatePushCommands(commands, projectName, authentication)
            if (errors.isNotEmpty()) {
                logger.warn("Push validation failed: ${errors.joinToString(", ")}")
            }
        }
        
        receivePack.setPostReceiveHook { _, commands ->
            logger.debug("Post-receive hook called with ${commands.size} commands")
            processPushCommands(commands, projectName, authentication)
        }
    }

    private fun handleUploadPackInfoRefs(repository: Repository): ResponseEntity<StreamingResponseBody> {
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/x-git-upload-pack-advertisement"))
            .header(HttpHeaders.CACHE_CONTROL, "no-cache")
            .body(object : StreamingResponseBody {
                @Throws(IOException::class)
                override fun writeTo(outputStream: java.io.OutputStream) {
                    val uploadPack = UploadPack(repository)
                    configureUploadPack(uploadPack)
                    
                    outputStream.write("001e# service=git-upload-pack\n".toByteArray())
                    outputStream.write("0000".toByteArray())
                    
                    val pktOut = PacketLineOut(outputStream)
                    val refAdvertiser = RefAdvertiser.PacketLineOutRefAdvertiser(pktOut)
                    uploadPack.sendAdvertisedRefs(refAdvertiser)
                }
            })
    }

    private fun handleReceivePackInfoRefs(repository: Repository): ResponseEntity<StreamingResponseBody> {
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/x-git-receive-pack-advertisement"))
            .header(HttpHeaders.CACHE_CONTROL, "no-cache")
            .body(object : StreamingResponseBody {
                @Throws(IOException::class)
                override fun writeTo(outputStream: java.io.OutputStream) {
                    val receivePack = ReceivePack(repository)
                    configureReceivePack(receivePack, "", null)
                    
                    outputStream.write("001f# service=git-receive-pack\n".toByteArray())
                    outputStream.write("0000".toByteArray())
                    
                    val pktOut = PacketLineOut(outputStream)
                    val refAdvertiser = RefAdvertiser.PacketLineOutRefAdvertiser(pktOut)
                    receivePack.sendAdvertisedRefs(refAdvertiser)
                }
            })
    }

    private fun processPushCommands(
        commands: Collection<org.eclipse.jgit.transport.ReceiveCommand>,
        projectName: String,
        authentication: Authentication?
    ) {
        for (command in commands) {
            val refName = command.refName
            if (refName.startsWith("refs/for/")) {
                logger.info("Creating/updating change for ref: $refName in project: $projectName")
                processChangeCreation(command, projectName, authentication)
            } else {
                logger.info("Processing direct push to ref: $refName in project: $projectName")
                processDirectBranchPush(command, projectName, authentication)
            }
        }
    }

    private fun hasRepositoryAccess(
        projectName: String,
        service: String?,
        authentication: Authentication?
    ): Boolean {
        // Check if user is authenticated for write operations
        if (service == "git-receive-pack" && authentication == null) {
            logger.warn("Unauthenticated push attempt to project: $projectName")
            return false
        }
        
        // For now, allow read access to all authenticated users and anonymous read access
        // TODO: Integrate with proper Gerrit permission system
        if (service == "git-upload-pack") {
            logger.debug("Allowing read access to project: $projectName for user: ${authentication?.name ?: "anonymous"}")
            return true
        }
        
        // For push operations, require authentication
        if (service == "git-receive-pack") {
            if (authentication != null) {
                logger.debug("Allowing write access to project: $projectName for user: ${authentication.name}")
                return true
            }
            return false
        }
        
        // Default to allowing access for other operations
        return true
    }

    private fun validatePushCommands(
        commands: Collection<org.eclipse.jgit.transport.ReceiveCommand>,
        projectName: String,
        authentication: Authentication?
    ): List<String> {
        val errors = mutableListOf<String>()
        
        // Validate authentication for push operations
        if (authentication == null) {
            errors.add("Authentication required for push operations")
            return errors
        }
        
        for (command in commands) {
            val refName = command.refName
            
            // Validate refs/for/* pushes (Gerrit change creation)
            if (refName.startsWith("refs/for/")) {
                try {
                    val repository = repositoryService.getRepository(projectName)
                    repository.use { repo ->
                        val revWalk = org.eclipse.jgit.revwalk.RevWalk(repo)
                        try {
                            val commit = revWalk.parseCommit(command.newId)
                            val changeId = ChangeIdUtil.extractChangeId(commit.fullMessage)
                            
                            if (changeId == null) {
                                errors.add("Missing Change-Id in commit ${command.newId.name}")
                            } else if (!isValidChangeId(changeId)) {
                                errors.add("Invalid Change-Id format in commit ${command.newId.name}: $changeId")
                            }
                            
                            // Validate target branch exists
                            val targetBranch = refName.removePrefix("refs/for/")
                            val branchRef = repo.findRef("refs/heads/$targetBranch")
                            if (branchRef == null) {
                                errors.add("Target branch '$targetBranch' does not exist")
                            }
                            
                        } finally {
                            revWalk.dispose()
                        }
                    }
                } catch (e: Exception) {
                    errors.add("Error validating commit ${command.newId.name}: ${e.message}")
                }
            }
            
            // Validate direct branch pushes
            else if (refName.startsWith("refs/heads/")) {
                // Check if direct pushes are allowed
                if (!gitConfiguration.allowDirectPush) {
                    errors.add("Direct pushes to branches are not allowed. Use refs/for/* instead.")
                }
            }
            
            // Validate tag operations
            else if (refName.startsWith("refs/tags/")) {
                if (command.type == org.eclipse.jgit.transport.ReceiveCommand.Type.DELETE && !gitConfiguration.allowDeletes) {
                    errors.add("Tag deletion not allowed: $refName")
                }
            }
        }
        
        return errors
    }

    private fun processChangeCreation(
        command: org.eclipse.jgit.transport.ReceiveCommand,
        projectName: String,
        authentication: Authentication?
    ) {
        val refName = command.refName
        val targetBranch = refName.removePrefix("refs/for/")
        val newObjectId = command.newId
        logger.info("Processing change creation for target branch: $targetBranch, commit: ${newObjectId.name}")
        
        val repository = repositoryService.getRepository(projectName)
        repository.use { repo ->
            try {
                val userId = extractUserId(authentication)
                val result = changeService.processRefsForPush(
                    repository = repo,
                    refName = refName,
                    oldObjectId = command.oldId,
                    newObjectId = newObjectId,
                    projectName = projectName,
                    ownerId = userId
                )
                
                if (result.success) {
                    logger.info("Successfully processed change: ${result.message}")
                } else {
                    logger.error("Failed to process change: ${result.message}")
                }
            } catch (e: Exception) {
                logger.error("Error processing change creation for commit ${newObjectId.name}", e)
            }
        }
    }

    private fun processDirectBranchPush(
        command: org.eclipse.jgit.transport.ReceiveCommand,
        projectName: String,
        authentication: Authentication?
    ) {
        val refName = command.refName
        val newObjectId = command.newId
        logger.info("Processing direct branch push to: $refName, commit: ${newObjectId.name}")
        
        // TODO: Implement direct branch push logic if needed
        // For now, log the operation
        logger.debug("Direct push by user: ${authentication?.name ?: "anonymous"} to ref: $refName")
    }

    /**
     * Validates Change-Id format according to Gerrit standards.
     */
    private fun isValidChangeId(changeId: String): Boolean {
        // Change-Id should be "I" followed by 40 hexadecimal characters
        return changeId.matches(Regex("^I[0-9a-f]{40}$"))
    }

    /**
     * Extracts user ID from authentication object.
     */
    private fun extractUserId(authentication: Authentication?): Int {
        if (authentication == null) {
            logger.warn("No authentication provided, using anonymous user ID")
            return 0 // Anonymous user
        }
        
        // Try to extract user ID from authentication details
        // This is a simplified implementation - in a real system, you'd integrate with your user management
        return when (authentication.principal) {
            is org.springframework.security.core.userdetails.UserDetails -> {
                val userDetails = authentication.principal as org.springframework.security.core.userdetails.UserDetails
                // For now, use a hash of the username as user ID
                // TODO: Integrate with proper user management system
                Math.abs(userDetails.username.hashCode())
            }
            is String -> {
                // Principal is just a username string
                Math.abs(authentication.name.hashCode())
            }
            else -> {
                logger.warn("Unknown authentication principal type: ${authentication.principal?.javaClass}")
                Math.abs(authentication.name.hashCode())
            }
        }
    }
}
