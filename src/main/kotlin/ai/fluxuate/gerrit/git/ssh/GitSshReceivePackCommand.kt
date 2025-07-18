package ai.fluxuate.gerrit.git.ssh

import ai.fluxuate.gerrit.git.GitConfiguration
import ai.fluxuate.gerrit.git.GitRepositoryService
import ai.fluxuate.gerrit.service.ChangeIdService
import ai.fluxuate.gerrit.service.ChangeService
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.transport.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component


class GitSshReceivePackCommand(
    gitConfiguration: GitConfiguration,
    repositoryService: GitRepositoryService,
    private val changeService: ChangeService,
    private val changeIdService: ChangeIdService
) : AbstractGitSshCommand(gitConfiguration, repositoryService) {

    override fun runImpl() {
        try {
            logger.info("Starting git-receive-pack for repository: ${repository?.directory}")
            
            val repo = repository ?: throw IllegalStateException("Repository not available")
            val receivePack = ReceivePack(repo)
            
            // Configure receive pack based on configuration
            receivePack.setAllowCreates(gitConfiguration.allowCreates)
            receivePack.setAllowDeletes(gitConfiguration.allowDeletes)
            receivePack.setAllowNonFastForwards(gitConfiguration.allowNonFastForwards)
            
            // Set up hooks for Gerrit-specific processing
            receivePack.setPreReceiveHook(GerritPreReceiveHook())
            receivePack.setPostReceiveHook(GerritPostReceiveHook())
            
            // Set up advertise refs hook for virtual branch advertisement
            receivePack.setAdvertiseRefsHook(GerritReceiveAdvertiseRefsHook())
            
            val inputStream = getInputStream() ?: throw IllegalStateException("Input stream not available")
            val outputStream = getOutputStream() ?: throw IllegalStateException("Output stream not available")
            val errorStream = getErrorStream() ?: throw IllegalStateException("Error stream not available")
            
            // Execute the receive pack operation
            receivePack.receive(inputStream, outputStream, errorStream)
            
            logger.info("Completed git-receive-pack operation")
            
        } catch (e: Exception) {
            logger.error("Error in git-receive-pack", e)
            throw e
        }
    }


    private inner class GerritPreReceiveHook : PreReceiveHook {
        override fun onPreReceive(rp: ReceivePack, commands: Collection<ReceiveCommand>) {
            logger.info("Pre-receive hook processing ${commands.size} commands")
            
            for (command in commands) {
                val refName = command.refName
                logger.debug("Processing ref: $refName")
                
                // Handle magic branch refs/for/*
                if (refName.startsWith("refs/for/")) {
                    processMagicBranch(command)
                } else {
                    // Handle regular refs (direct push to branches)
                    processRegularRef(command, rp.getRepository())
                }
            }
        }
        
        private fun processMagicBranch(command: ReceiveCommand) {
            val refName = command.refName
            val targetBranch = refName.removePrefix("refs/for/")
            
            logger.info("Processing magic branch push to: $targetBranch")
            
            try {
                val repo = repository ?: throw IllegalStateException("Repository not available")
                
                // Get the commit being pushed
                val commit = repo.parseCommit(command.newId)
                
                // Extract Change-Id from commit message
                var changeId = changeIdService.extractChangeId(commit.fullMessage)
                
                if (changeId == null) {
                    val generatedChangeId = changeIdService.generateChangeId(
                        treeId = commit.tree.id,
                        parentIds = commit.parents.map { it.id },
                        author = commit.authorIdent,
                        committer = commit.committerIdent,
                        commitMessage = commit.fullMessage
                    )
                    logger.info("Generated Change-Id: $generatedChangeId for commit: ${commit.id.name}")
                    
                    // Use the generated Change-Id for processing
                    changeId = generatedChangeId
                }
                
                // Process the change (create or update)
                val result = changeService.processChange(
                    changeId = changeId,
                    commit = commit,
                    targetBranch = targetBranch,
                    repository = repo
                )
                
                if (result.success) {
                    logger.info("Successfully processed change: ${result.message}")
                    command.setResult(ReceiveCommand.Result.OK)
                } else {
                    logger.warn("Failed to process change: ${result.message}")
                    command.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, result.message)
                }
                
            } catch (e: Exception) {
                logger.error("Error processing magic branch: $refName", e)
                command.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, 
                    "Internal error processing change: ${e.message}")
            }
        }
        
        private fun processRegularRef(command: ReceiveCommand, repo: Repository) {
            val refName = command.refName
            logger.debug("Processing regular ref: $refName")
            
            // Validate permissions for direct branch pushes
            if (!validateDirectPushPermissions(refName)) {
                command.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, 
                    "Direct push to $refName not allowed. Use refs/for/$refName for code review.")
                return
            }
            
            // Apply branch protection rules
            if (isBranchProtected(refName)) {
                command.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, 
                    "Branch $refName is protected and cannot be pushed to directly.")
                return
            }
            
            // Validate commit messages and content for direct pushes
            if (command.type != ReceiveCommand.Type.DELETE) {
                try {
                    val commit = repo.parseCommit(command.newId)
                    if (!validateCommitForDirectPush(commit)) {
                        command.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, 
                            "Commit validation failed for direct push.")
                        return
                    }
                } catch (e: Exception) {
                    logger.error("Error validating commit for direct push: $refName", e)
                    command.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, 
                        "Error validating commit: ${e.message}")
                    return
                }
            }
            
            // Allow the ref update
            command.setResult(ReceiveCommand.Result.OK)
        }
        
        private fun validateDirectPushPermissions(refName: String): Boolean {
            // In a full implementation, this would check user permissions
            // For now, allow direct pushes to non-protected branches
            return !refName.startsWith("refs/heads/master") && 
                   !refName.startsWith("refs/heads/main") &&
                   !refName.startsWith("refs/heads/develop")
        }
        
        private fun isBranchProtected(refName: String): Boolean {
            // Define protected branches that require code review
            val protectedBranches = listOf(
                "refs/heads/master",
                "refs/heads/main", 
                "refs/heads/develop",
                "refs/heads/release/"
            )
            return protectedBranches.any { refName.startsWith(it) }
        }
        
        private fun validateCommitForDirectPush(commit: RevCommit): Boolean {
            // Basic commit validation for direct pushes
            val message = commit.fullMessage
            
            // Ensure commit message is not empty
            if (message.isBlank()) {
                logger.warn("Empty commit message not allowed for direct push")
                return false
            }
            
            // Ensure commit message has reasonable length
            if (message.length < 10) {
                logger.warn("Commit message too short for direct push")
                return false
            }
            
            return true
        }
    }

    /**
     * Post-receive hook for Gerrit-specific post-processing.
     */
    private inner class GerritPostReceiveHook : PostReceiveHook {
        override fun onPostReceive(rp: ReceivePack, commands: Collection<ReceiveCommand>) {
            logger.info("Post-receive hook processing ${commands.size} commands")
            
            for (command in commands) {
                if (command.result == ReceiveCommand.Result.OK) {
                    logger.info("Successfully processed ref: ${command.refName}")
                    
                    try {
                        processPostReceiveCommand(command, rp.repository)
                    } catch (e: Exception) {
                        logger.error("Error in post-receive processing for ${command.refName}", e)
                    }
                }
            }
        }
        
        private fun processPostReceiveCommand(command: ReceiveCommand, repo: Repository) {
            val refName = command.refName
            
            if (refName.startsWith("refs/for/")) {
                // Handle magic branch post-processing
                processMagicBranchPostReceive(command, repo)
            } else {
                // Handle regular ref post-processing
                processRegularRefPostReceive(command, repo)
            }
        }
        
        private fun processMagicBranchPostReceive(command: ReceiveCommand, repo: Repository) {
            try {
                val commit = repo.parseCommit(command.newId)
                val changeId = changeIdService.extractChangeId(commit.fullMessage)
                
                if (changeId != null) {
                    // Send notifications to reviewers
                    sendChangeNotifications(changeId, commit, command.refName)
                    
                    // Update change status
                    updateChangeStatus(changeId, commit)
                    
                    // Trigger CI/CD pipelines
                    triggerCiCdPipelines(changeId, commit, command.refName)
                    
                    // Update metrics and statistics
                    updateMetrics(changeId, commit, "change_created_or_updated")
                }
            } catch (e: Exception) {
                logger.error("Error processing magic branch post-receive: ${command.refName}", e)
            }
        }
        
        private fun processRegularRefPostReceive(command: ReceiveCommand, repo: Repository) {
            try {
                // Send notifications for direct pushes
                sendDirectPushNotifications(command, repo)
                
                // Trigger CI/CD pipelines for direct pushes
                triggerDirectPushPipelines(command, repo)
                
                // Update metrics for direct pushes
                updateMetrics(null, null, "direct_push", command.refName)
            } catch (e: Exception) {
                logger.error("Error processing regular ref post-receive: ${command.refName}", e)
            }
        }
        
        private fun sendChangeNotifications(changeId: String, commit: RevCommit, refName: String) {
            logger.info("Sending notifications for change: $changeId")
            // In a full implementation, this would:
            // - Find reviewers and watchers for the change
            // - Send email notifications
            // - Send webhook notifications
            // - Update activity feeds
        }
        
        private fun updateChangeStatus(changeId: String, commit: RevCommit) {
            logger.info("Updating change status for: $changeId")
            // In a full implementation, this would:
            // - Update the change entity in the database
            // - Set appropriate status (NEW, DRAFT, etc.)
            // - Update patch set information
        }
        
        private fun triggerCiCdPipelines(changeId: String, commit: RevCommit, refName: String) {
            logger.info("Triggering CI/CD pipelines for change: $changeId")
            // In a full implementation, this would:
            // - Trigger Jenkins/GitHub Actions/GitLab CI
            // - Run automated tests
            // - Perform code quality checks
            // - Update verification status
        }
        
        private fun sendDirectPushNotifications(command: ReceiveCommand, repo: Repository) {
            logger.info("Sending notifications for direct push to: ${command.refName}")
            // In a full implementation, this would:
            // - Notify branch watchers
            // - Send commit notifications
            // - Update activity feeds
        }
        
        private fun triggerDirectPushPipelines(command: ReceiveCommand, repo: Repository) {
            logger.info("Triggering CI/CD pipelines for direct push to: ${command.refName}")
            // In a full implementation, this would:
            // - Trigger build pipelines
            // - Run deployment scripts
            // - Update deployment status
        }
        
        private fun updateMetrics(changeId: String?, commit: RevCommit?, eventType: String, refName: String? = null) {
            logger.info("Updating metrics for event: $eventType")
            // In a full implementation, this would:
            // - Update Prometheus metrics
            // - Log to analytics systems
            // - Update dashboard statistics
            // - Track performance metrics
        }
    }

    /**
     * Advertise refs hook for receive-pack operations.
     * Controls which refs are advertised to the client.
     */
    private inner class GerritReceiveAdvertiseRefsHook : AdvertiseRefsHook {
        override fun advertiseRefs(rp: ReceivePack) {
            logger.debug("Advertising refs for receive-pack on repository: ${repository?.directory}")

            try {
                // Apply permission-based ref filtering
                filterRefsForReceive(rp)
                
                // Advertise virtual branches for changes
                advertiseVirtualBranches(rp)
                
                // Filter out internal Gerrit refs
                filterInternalRefs(rp)
                
            } catch (e: Exception) {
                logger.error("Error advertising refs for receive-pack", e)
            }
        }
        
        private fun filterRefsForReceive(rp: ReceivePack) {
            // Filter refs based on user permissions for push operations
            val repo = rp.repository
            val refDatabase = repo.refDatabase
            
            try {
                val allRefs = refDatabase.refs
                val allowedRefs = mutableMapOf<String, org.eclipse.jgit.lib.Ref>()
                
                for (ref in allRefs) {
                    val refName = ref.name
                    if (canPushToRef(refName)) {
                        allowedRefs[refName] = ref
                    } else {
                        logger.debug("Filtered out ref for push: $refName")
                    }
                }
                
                // In a full implementation, we would modify the advertised refs
                // For now, just log the filtering
                logger.debug("Advertising ${allowedRefs.size} refs out of ${allRefs.size} total refs")
                
            } catch (e: Exception) {
                logger.error("Error filtering refs for receive-pack", e)
            }
        }
        
        private fun advertiseVirtualBranches(rp: ReceivePack) {
            // Advertise virtual branches for changes (refs/changes/XX/CHANGEID/PATCHSET)
            try {
                // In a full implementation, this would:
                // 1. Query the database for active changes
                // 2. Generate virtual refs for each patch set
                // 3. Add them to the advertised refs
                
                logger.debug("Advertising virtual branches for changes")
                
                // Example of what would be advertised:
                // refs/changes/01/1/1 -> commit SHA for change 1, patch set 1
                // refs/changes/01/1/2 -> commit SHA for change 1, patch set 2
                // refs/changes/34/1234/1 -> commit SHA for change 1234, patch set 1
                
            } catch (e: Exception) {
                logger.error("Error advertising virtual branches", e)
            }
        }
        
        private fun filterInternalRefs(rp: ReceivePack) {
            // Filter out internal Gerrit refs that shouldn't be advertised
            val internalRefPrefixes = listOf(
                "refs/meta/",           // Gerrit metadata refs
                "refs/users/",          // User-specific refs
                "refs/groups/",         // Group-specific refs
                "refs/cache-automerge/" // Auto-merge cache refs
            )
            
            try {
                // In a full implementation, we would remove these refs from advertisement
                logger.debug("Filtering internal refs: ${internalRefPrefixes.joinToString(", ")}")
                
            } catch (e: Exception) {
                logger.error("Error filtering internal refs", e)
            }
        }
        
        private fun canPushToRef(refName: String): Boolean {
            // Check if the current user can push to this ref
            // In a full implementation, this would check:
            // - User permissions
            // - Branch protection rules
            // - Project-specific access controls
            
            return when {
                refName.startsWith("refs/heads/") -> {
                    // Allow push to most branches, but check protection
                    !isBranchProtected(refName) || hasDirectPushPermission(refName)
                }
                refName.startsWith("refs/tags/") -> {
                    // Tags usually require special permissions
                    hasTagPushPermission(refName)
                }
                refName.startsWith("refs/for/") -> {
                    // Magic branches are always allowed for change creation
                    true
                }
                refName.startsWith("refs/meta/") -> {
                    // Internal refs require admin permissions
                    false
                }
                else -> {
                    // Default to allowing other refs
                    true
                }
            }
        }
        
        private fun hasDirectPushPermission(refName: String): Boolean {
            // In a full implementation, check user's direct push permissions
            // For now, assume no direct push permissions to protected branches
            return false
        }
        
        private fun hasTagPushPermission(refName: String): Boolean {
            // In a full implementation, check user's tag push permissions
            // For now, assume no tag push permissions
            return false
        }
        
        private fun isBranchProtected(refName: String): Boolean {
            // In a full implementation, this would check:
            // - Branch protection rules
            // - Project-specific protection settings
            // - Global protection policies
            
            return when {
                refName == "refs/heads/master" || refName == "refs/heads/main" -> true
                refName.startsWith("refs/heads/release/") -> true
                refName.startsWith("refs/heads/hotfix/") -> true
                else -> false
            }
        }
        
        override fun advertiseRefs(up: UploadPack) {
            throw UnsupportedOperationException("ReceiveAdvertiseRefsHook cannot be used for UploadPack")
        }
    }
}
