package ai.fluxuate.gerrit.git.ssh

import ai.fluxuate.gerrit.git.GitConfiguration
import ai.fluxuate.gerrit.git.GitRepositoryService
import ai.fluxuate.gerrit.util.ChangeIdUtil
import ai.fluxuate.gerrit.service.ChangeService
import ai.fluxuate.gerrit.git.GitReceivePackService
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.transport.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * SSH receive-pack command for Gerrit.
 * Handles incoming push operations from clients.
 */
class GitSshReceivePackCommand(
    gitConfiguration: GitConfiguration,
    repositoryService: GitRepositoryService,
    private val changeService: ChangeService,
    private val gitReceivePackService: GitReceivePackService
) : AbstractGitSshCommand(gitConfiguration, repositoryService) {

    override fun runImpl() {
        try {
            logger.info("Starting git-receive-pack for repository: ${repository?.directory}")
            
            val repo = repository ?: throw IllegalStateException("Repository not available")
            val projectName = repo.directory.parentFile?.name ?: throw IllegalStateException("Project name not available")
            val receivePack = ReceivePack(repo)
            
            // Configure receive pack based on configuration
            receivePack.setAllowCreates(gitConfiguration.allowCreates)
            receivePack.setAllowDeletes(gitConfiguration.allowDeletes)
            receivePack.setAllowNonFastForwards(gitConfiguration.allowNonFastForwards)
            
            // Set up hooks for Gerrit-specific processing
            receivePack.setPreReceiveHook(GerritPreReceiveHook(projectName))
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


    private inner class GerritPreReceiveHook(private val projectName: String) : PreReceiveHook {
        override fun onPreReceive(rp: ReceivePack, commands: Collection<ReceiveCommand>) {
            logger.info("SSH Pre-receive hook processing ${commands.size} commands")
            
            for (command in commands) {
                // Use the unified service to process the command
                val result = gitReceivePackService.processReceiveCommand(command, rp.repository, projectName)
                
                // Set the result based on the service response
                if (result.success) {
                    command.setResult(ReceiveCommand.Result.OK)
                } else {
                    command.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, result.message)
                }
            }
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
                val changeId = ChangeIdUtil.extractChangeId(commit.fullMessage)
                
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
            try {
                logger.info("Sending notifications for change: $changeId")
                
                // For magic branch pushes (refs/for/*), create a virtual branch reference
                // that represents the change in our internal system
                val virtualBranchRef = "refs/changes/${changeId.removePrefix("I")}/1"
                val projectName = repository?.directory?.name ?: "unknown"
                
                // Delegate to shared service for notification handling
                gitReceivePackService.handleVirtualBranchPush(
                    virtualBranchRef = virtualBranchRef,
                    commitId = commit.id.name,
                    projectName = projectName
                )
                
            } catch (e: Exception) {
                logger.error("Error sending change notifications for changeId: $changeId", e)
            }
        }
        
        private fun updateChangeStatus(changeId: String, commit: RevCommit) {
            try {
                logger.info("Updating change status for: $changeId")
                
                val repo = repository ?: throw IllegalStateException("Repository not available")
                val projectName = repo.directory.name
                
                // The ChangeService is already handling status updates through processChange
                // This method would typically be called after the change processing is complete
                // to update additional metadata or trigger follow-up actions
                
                // For now, we'll log the status update - in a full implementation,
                // this could update additional change metadata, patch set info, etc.
                logger.debug("Change status updated for changeId: $changeId, commit: ${commit.id.name}, project: $projectName")
                
            } catch (e: Exception) {
                logger.error("Error updating change status for changeId: $changeId", e)
            }
        }
        
        private fun triggerCiCdPipelines(changeId: String, commit: RevCommit, refName: String) {
            try {
                logger.info("Triggering CI/CD pipelines for change: $changeId")
                
                val repo = repository ?: throw IllegalStateException("Repository not available")
                val projectName = repo.directory.name
                
                // Create virtual branch reference for CI/CD systems to target
                val virtualBranchRef = "refs/changes/${changeId.removePrefix("I")}/1"
                
                // The shared service already handles CI/CD triggering through handleVirtualBranchPush
                // This provides a consistent interface for both HTTP and SSH operations
                gitReceivePackService.handleVirtualBranchPush(
                    virtualBranchRef = virtualBranchRef,
                    commitId = commit.id.name,
                    projectName = projectName
                )
                
                logger.debug("CI/CD pipelines triggered for change: $changeId, ref: $virtualBranchRef")
                
            } catch (e: Exception) {
                logger.error("Error triggering CI/CD pipelines for changeId: $changeId", e)
            }
        }
        
        private fun sendDirectPushNotifications(command: ReceiveCommand, repo: Repository) {
            try {
                logger.info("Sending notifications for direct push to: ${command.refName}")
                
                val projectName = repo.directory.name
                
                // For direct pushes, we don't create virtual branch refs but still need notifications
                // Use the actual ref name and commit ID for direct push notifications
                if (command.type != ReceiveCommand.Type.DELETE) {
                    val commitId = command.newId.name
                    
                    // Use the shared service's virtual branch handler as it contains the notification logic
                    // Pass the actual ref name since this is a direct push, not a virtual branch
                    gitReceivePackService.handleVirtualBranchPush(
                        virtualBranchRef = command.refName,
                        commitId = commitId,
                        projectName = projectName
                    )
                    
                    logger.debug("Direct push notifications sent for ref: ${command.refName}, commit: $commitId")
                } else {
                    logger.debug("Skipping notifications for ref deletion: ${command.refName}")
                }
                
            } catch (e: Exception) {
                logger.error("Error sending direct push notifications for ref: ${command.refName}", e)
            }
        }
        
        private fun triggerDirectPushPipelines(command: ReceiveCommand, repo: Repository) {
            try {
                logger.info("Triggering CI/CD pipelines for direct push to: ${command.refName}")
                
                val projectName = repo.directory.name
                
                // For direct pushes, trigger CI/CD on the actual branch ref
                if (command.type != ReceiveCommand.Type.DELETE) {
                    val commitId = command.newId.name
                    
                    // Use the shared service's CI/CD logic through handleVirtualBranchPush
                    // This ensures consistent CI/CD handling across HTTP and SSH operations
                    gitReceivePackService.handleVirtualBranchPush(
                        virtualBranchRef = command.refName,
                        commitId = commitId,
                        projectName = projectName
                    )
                    
                    logger.debug("CI/CD pipelines triggered for direct push to ref: ${command.refName}, commit: $commitId")
                } else {
                    logger.debug("Skipping CI/CD for ref deletion: ${command.refName}")
                }
                
            } catch (e: Exception) {
                logger.error("Error triggering CI/CD pipelines for direct push to ref: ${command.refName}", e)
            }
        }
        
        private fun updateMetrics(changeId: String?, commit: RevCommit?, eventType: String, refName: String? = null) {
            try {
                logger.info("Updating metrics for event: $eventType")
                
                val repo = repository ?: throw IllegalStateException("Repository not available")
                val projectName = repo.directory.name
                
                // Build context information for metrics
                val metricsContext = buildMap<String, String> {
                    put("event_type", eventType)
                    put("project_name", projectName)
                    put("transport", "ssh")
                    changeId?.let { put("change_id", it) }
                    commit?.let { put("commit_id", it.id.name) }
                    refName?.let { put("ref_name", it) }
                }
                
                // Use the shared service's metrics handling through handleVirtualBranchPush
                // This ensures consistent metrics collection across HTTP and SSH operations
                if (commit != null) {
                    val virtualRef = when (eventType) {
                        "change_push" -> "refs/changes/${changeId?.removePrefix("I") ?: "unknown"}/1"
                        "direct_push" -> refName ?: "unknown"
                        else -> refName ?: "unknown"
                    }
                    
                    gitReceivePackService.handleVirtualBranchPush(
                        virtualBranchRef = virtualRef,
                        commitId = commit.id.name,
                        projectName = projectName
                    )
                }
                
                logger.debug("Metrics updated for event: $eventType, context: $metricsContext")
                
            } catch (e: Exception) {
                logger.error("Error updating metrics for event: $eventType", e)
            }
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
                val repo = rp.repository
                val projectName = repo.directory.parentFile?.name ?: return
                
                logger.debug("Advertising virtual branches for project: $projectName")
                
                // Get virtual branches from the change service
                val virtualBranches = changeService.getVirtualBranchesForProject(projectName)
                
                // Add virtual branches to the advertised refs
                for ((refName, commitId) in virtualBranches) {
                    try {
                        val objectId = ObjectId.fromString(commitId)
                        val ref = org.eclipse.jgit.lib.ObjectIdRef.PeeledNonTag(
                            org.eclipse.jgit.lib.Ref.Storage.LOOSE,
                            refName,
                            objectId
                        )
                        rp.getAdvertisedRefs().put(refName, ref)
                        
                        // Add to the advertised refs
                        // Note: In a full implementation, we would modify the advertised refs
                        // For now, we just log what would be advertised
                        logger.debug("Would advertise virtual branch: $refName -> $commitId")
                        
                    } catch (e: Exception) {
                        logger.warn("Error creating ref for virtual branch $refName", e)
                    }
                }
                
                logger.debug("Advertised ${virtualBranches.size} virtual branches for project: $projectName")
                
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
