package ai.fluxuate.gerrit.git

import ai.fluxuate.gerrit.service.ChangeService
import ai.fluxuate.gerrit.util.ChangeIdUtil

import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.lib.Constants
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

/**
 * Git HTTP Controller that implements the complete Git HTTP protocol
 * based on JGit's GitFilter implementation.
 */
@RestController
@RequestMapping("/git")
class GitHttpController(
    private val gitConfiguration: GitConfiguration,
    private val repositoryService: GitRepositoryService,
    private val changeService: ChangeService
) {

    private val logger = LoggerFactory.getLogger(GitHttpController::class.java)

    // Smart HTTP Protocol Endpoints
    
    /**
     * Git info/refs endpoint - handles both upload-pack and receive-pack service discovery
     * Equivalent to JGit's InfoRefsServlet
     */
    @GetMapping("/{projectName}/info/refs")
    fun getInfoRefs(
        @PathVariable projectName: String,
        @RequestParam("service", required = false) service: String?,
        request: HttpServletRequest,
        authentication: Authentication
    ): ResponseEntity<StreamingResponseBody> {
        
        logger.debug("Git info/refs request for project: $projectName, service: $service, user: ${authentication.name}")
        
        return try {
            val repository = repositoryService.getRepository(projectName)
            
            when (service) {
                "git-upload-pack" -> handleUploadPackInfoRefs(repository)
                "git-receive-pack" -> handleReceivePackInfoRefs(repository)
                null -> handleDumbInfoRefs(repository) // Dumb HTTP protocol
                else -> {
                    logger.warn("Unknown service parameter: $service")
                    ResponseEntity.badRequest().build()
                }
            }
        } catch (e: Exception) {
            logger.error("Error handling info/refs for project: $projectName", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    /**
     * Git upload-pack endpoint - handles fetch/clone operations
     * Equivalent to JGit's UploadPackServlet
     */
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

    /**
     * Git receive-pack endpoint - handles push operations
     * Equivalent to JGit's ReceivePackServlet
     */
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

    // Dumb HTTP Protocol Endpoints (for compatibility)
    
    /**
     * HEAD file endpoint - serves the HEAD ref
     * Equivalent to JGit's TextFileServlet for HEAD
     */
    @GetMapping("/{projectName}/HEAD")
    fun getHead(
        @PathVariable projectName: String,
        authentication: Authentication?
    ): ResponseEntity<String> {
        
        if (!hasRepositoryAccess(projectName, null, authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        
        return try {
            val repository = repositoryService.getRepository(projectName)
            val head = repository.findRef(Constants.HEAD)
            val content = if (head?.isSymbolic == true) {
                "ref: ${head.target.name}\n"
            } else {
                "${head?.objectId?.name ?: "0000000000000000000000000000000000000000"}\n"
            }
            
            ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .body(content)
        } catch (e: Exception) {
            logger.error("Error serving HEAD for project: $projectName", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    /**
     * Objects info/packs endpoint - lists available pack files
     * Equivalent to JGit's InfoPacksServlet
     */
    @GetMapping("/{projectName}/objects/info/packs")
    fun getInfoPacks(
        @PathVariable projectName: String,
        authentication: Authentication?
    ): ResponseEntity<String> {
        
        if (!hasRepositoryAccess(projectName, null, authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        
        return try {
            val repository = repositoryService.getRepository(projectName)
            val objectsDir = repository.directory.resolve("objects")
            val packsDir = objectsDir.resolve("pack")
            
            val content = if (packsDir.exists()) {
                packsDir.listFiles { file -> file.name.endsWith(".pack") }
                    ?.joinToString("\n") { "P ${it.name}" } ?: ""
            } else {
                ""
            }
            
            ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .body(content + "\n")
        } catch (e: Exception) {
            logger.error("Error serving info/packs for project: $projectName", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    /**
     * Loose object endpoint - serves individual objects
     * Equivalent to JGit's ObjectFileServlet.Loose
     */
    @GetMapping("/{projectName}/objects/{dir}/{file}")
    fun getLooseObject(
        @PathVariable projectName: String,
        @PathVariable dir: String,
        @PathVariable file: String,
        authentication: Authentication?
    ): ResponseEntity<StreamingResponseBody> {
        
        if (!hasRepositoryAccess(projectName, null, authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        
        // Validate object name format
        if (!dir.matches(Regex("[0-9a-f]{2}")) || !file.matches(Regex("[0-9a-f]{38}"))) {
            return ResponseEntity.badRequest().build()
        }
        
        return try {
            val repository = repositoryService.getRepository(projectName)
            val objectsDir = repository.directory.resolve("objects")
            val objectFile = objectsDir.resolve(dir).resolve(file)
            
            if (!objectFile.exists()) {
                return ResponseEntity.notFound().build()
            }
            
            ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CACHE_CONTROL, "max-age=31536000")
                .body(StreamingResponseBody { outputStream ->
                    objectFile.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                })
        } catch (e: Exception) {
            logger.error("Error serving loose object $dir/$file for project: $projectName", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    /**
     * Pack file endpoint - serves pack files
     * Equivalent to JGit's ObjectFileServlet.Pack
     */
    @GetMapping("/{projectName}/objects/pack/{packFile}")
    fun getPackFile(
        @PathVariable projectName: String,
        @PathVariable packFile: String,
        authentication: Authentication?
    ): ResponseEntity<StreamingResponseBody> {
        
        if (!hasRepositoryAccess(projectName, null, authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        
        // Validate pack file name format
        if (!packFile.matches(Regex("pack-[0-9a-f]{40}\\.(pack|idx)"))) {
            return ResponseEntity.badRequest().build()
        }
        
        return try {
            val repository = repositoryService.getRepository(projectName)
            val objectsDir = repository.directory.resolve("objects")
            val packDir = objectsDir.resolve("pack")
            val file = packDir.resolve(packFile)
            
            if (!file.exists()) {
                return ResponseEntity.notFound().build()
            }
            
            val contentType = if (packFile.endsWith(".pack")) {
                MediaType.APPLICATION_OCTET_STREAM
            } else {
                MediaType.APPLICATION_OCTET_STREAM
            }
            
            ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CACHE_CONTROL, "max-age=31536000")
                .body(StreamingResponseBody { outputStream ->
                    file.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                })
        } catch (e: Exception) {
            logger.error("Error serving pack file $packFile for project: $projectName", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    // Private helper methods

    private fun handleUploadPackInfoRefs(repository: Repository): ResponseEntity<StreamingResponseBody> {
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/x-git-upload-pack-advertisement"))
            .header(HttpHeaders.CACHE_CONTROL, "no-cache")
            .body(object : StreamingResponseBody {
                @Throws(IOException::class)
                override fun writeTo(outputStream: java.io.OutputStream) {
                    val uploadPack = UploadPack(repository)
                    configureUploadPack(uploadPack)
                    
                    // Write service header
                    outputStream.write("001e# service=git-upload-pack\n".toByteArray())
                    outputStream.write("0000".toByteArray())
                    
                    // Use JGit's native RefAdvertiser to handle empty repositories properly
                    val pktOut = PacketLineOut(outputStream)
                    val refAdvertiser = RefAdvertiser.PacketLineOutRefAdvertiser(pktOut)
                    
                    try {
                        uploadPack.sendAdvertisedRefs(refAdvertiser)
                    } catch (e: org.eclipse.jgit.errors.MissingObjectException) {
                        // Handle empty repository case - JGit will gracefully handle this
                        // by not advertising any refs, which is correct for empty repos
                        logger.debug("Repository appears to be empty, no refs to advertise")
                    }
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

    private fun handleDumbInfoRefs(repository: Repository): ResponseEntity<StreamingResponseBody> {
        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_PLAIN)
            .header(HttpHeaders.CACHE_CONTROL, "no-cache")
            .body(object : StreamingResponseBody {
                @Throws(IOException::class)
                override fun writeTo(outputStream: java.io.OutputStream) {
                    val refs = repository.refDatabase.refs
                    for (ref in refs) {
                        val line = "${ref.objectId.name}\t${ref.name}\n"
                        outputStream.write(line.toByteArray())
                    }
                }
            })
    }

    private fun configureUploadPack(uploadPack: UploadPack) {
        logger.debug("Configuring upload pack for Git fetch operations")
        // Add any custom upload pack configuration here
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
                // Reject all commands with validation errors
                for (command in commands) {
                    command.setResult(org.eclipse.jgit.transport.ReceiveCommand.Result.REJECTED_OTHER_REASON, 
                                    errors.joinToString("; "))
                }
            }
        }
        
        receivePack.setPostReceiveHook { _, commands ->
            logger.debug("Post-receive hook called with ${commands.size} commands")
            processPushCommands(commands, projectName, authentication)
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
        if (service == "git-upload-pack" || service == null) {
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
                                errors.add("Commit ${command.newId.name.substring(0, 8)} needs Change-Id identifier. Install client hook to proceed.")
                            } else if (!isValidChangeId(changeId)) {
                                errors.add("Commit ${command.newId.name.substring(0, 8)} has invalid Change-Id format: $changeId")
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
