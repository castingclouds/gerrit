package ai.fluxuate.gerrit.git

import ai.fluxuate.gerrit.git.GitConfiguration
import ai.fluxuate.gerrit.util.ChangeIdUtil
import ai.fluxuate.gerrit.service.ChangeService
import ai.fluxuate.gerrit.service.ProjectService
import ai.fluxuate.gerrit.repository.ChangeEntityRepository
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.transport.ReceiveCommand
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class GitReceivePackService(
    private val gitConfiguration: GitConfiguration,
    private val changeService: ChangeService,
    private val projectService: ProjectService,
    private val changeRepository: ChangeEntityRepository
) {

    private val logger: Logger = LoggerFactory.getLogger(GitReceivePackService::class.java)

    fun processReceiveCommand(command: ReceiveCommand, repository: Repository, projectName: String): ChangeService.ProcessResult {
        val refName = command.refName
        logger.debug("Processing ref: $refName for project: $projectName")

        val defaultBranch = getProjectDefaultBranch(projectName)
        val trunkRef = "refs/heads/$defaultBranch"
        if (refName == trunkRef) {
            return processTrunkBranchPush(command, repository, defaultBranch, projectName)
        }

        if (refName.startsWith("refs/for/")) {
            return processMagicBranch(command, repository, projectName)
        }

        return processRegularRef(command, repository, defaultBranch)
    }

    private fun getProjectDefaultBranch(projectName: String): String {
        return try {
            projectService.getProjectDefaultBranch(projectName)
        } catch (e: Exception) {
            logger.warn("Failed to get project default branch for $projectName, using global default: ${e.message}")
            gitConfiguration.trunkBranchName
        }
    }

    private fun processTrunkBranchPush(command: ReceiveCommand, repository: Repository, defaultBranch: String, projectName: String): ChangeService.ProcessResult {
        if (command.type == ReceiveCommand.Type.DELETE) {
            return ChangeService.ProcessResult(success = true, message = "Default branch deletion allowed")
        }

        try {
            val commit = repository.parseCommit(command.newId)
            
            // Extract Change-Id from commit message (should be added by client-side hook)
            val changeId = ai.fluxuate.gerrit.util.ChangeIdUtil.extractChangeId(commit.fullMessage)
                ?: return ChangeService.ProcessResult(success = false, message = "Missing Change-Id in commit message. Install the commit-msg hook: curl -Lo .git/hooks/commit-msg http://localhost:8080/tools/hooks/commit-msg && chmod +x .git/hooks/commit-msg")

            val result = changeService.processChange(
                changeId = changeId,
                commit = commit,
                targetBranch = defaultBranch,
                repository = repository,
                projectName = projectName
            )
            
            return result
        } catch (e: Exception) {
            logger.error("Error processing default branch push: ${command.refName}", e)
            return ChangeService.ProcessResult(success = false, message = "Error processing default branch push: ${e.message}")
        }
    }
    

    
    /**
     * Unified post-receive handler for all push types.
     * This method consolidates notification, CI/CD, and metrics logic to avoid duplication.
     */
    fun handlePostReceive(commands: Collection<ReceiveCommand>, repository: Repository) {
        logger.info("Post-receive hook processing ${commands.size} commands")
        
        for (command in commands) {
            val refName = command.refName
            logger.debug("Post-receive processing ref: $refName")
            
            val projectName = repository.directory.name.removeSuffix(".git")
            
            // Handle trunk branch pushes (use project-specific default branch)
            val defaultBranch = getProjectDefaultBranch(projectName)
            val trunkRef = "refs/heads/$defaultBranch"
            if (refName == trunkRef) {
                handleTrunkPostReceive(command, repository, projectName)
                continue
            }
            
            // Handle magic branch refs/for/*
            if (refName.startsWith("refs/for/")) {
                handleMagicBranchPostReceive(command, projectName)
                continue
            }
            
            // Handle regular refs (direct push to branches)
            handleRegularRefPostReceive(command, projectName)
        }
    }
    
    private fun handleTrunkPostReceive(command: ReceiveCommand, repository: Repository, projectName: String) {
        if (command.type == ReceiveCommand.Type.DELETE) {
            logger.info("Trunk branch deletion processed successfully")
            return
        }
        
        try {
            val commit = repository.parseCommit(command.newId)
            val commitId = commit.id.name
            
            // Extract the actual Change-Id from commit message
            val changeId = ChangeIdUtil.extractChangeId(commit.fullMessage)
                ?: throw RuntimeException("No Change-Id found in commit message. Please install the commit-msg hook.")
            
            // Look up the change to get the actual patch set number that was just created
            val change = changeRepository.findByChangeKey(changeId)
                ?: throw RuntimeException("Change not found for Change-Id: $changeId")
            val patchSetId = change.currentPatchSetId
            
            // Generate the virtual branch ref using actual Change-Id (without 'I' prefix) and patch set number
            val changeIdHash = changeId.substring(1) // Remove 'I' prefix
            val virtualBranchRef = "refs/changes/${changeIdHash.takeLast(2)}/${changeIdHash}/${patchSetId}"
            
            logger.info("Trunk push created virtual branch: $virtualBranchRef")
            handleVirtualBranchPostReceive(virtualBranchRef, commitId, projectName)
            
        } catch (e: Exception) {
            logger.error("Error in trunk post-receive: ${command.refName}", e)
        }
    }
    
    private fun handleMagicBranchPostReceive(command: ReceiveCommand, projectName: String) {
        val refName = command.refName
        
        try {
            // Send notifications for change creation/update
            logger.info("Sending notifications for magic branch push: $refName")
            
            // Trigger CI/CD pipelines for code review changes
            logger.info("Triggering CI/CD for magic branch push: $refName")
            
            // Update metrics
            logger.info("Updating metrics for magic branch push: $refName")
            
        } catch (e: Exception) {
            logger.error("Error in magic branch post-receive: $refName", e)
        }
    }
    
    private fun handleVirtualBranchPostReceive(virtualBranchRef: String, commitId: String, projectName: String) {
        try {
            // Send notifications for virtual branch operations
            logger.info("Sending notifications for virtual branch push: $virtualBranchRef")
            
            // Trigger CI/CD pipelines for virtual branch changes
            logger.info("Triggering CI/CD for virtual branch push: $virtualBranchRef")
            
            // Update metrics for virtual branch operations
            logger.info("Updating metrics for virtual branch push: $virtualBranchRef")
            
        } catch (e: Exception) {
            logger.error("Error in virtual branch post-receive: $virtualBranchRef", e)
        }
    }
    
    private fun handleRegularRefPostReceive(command: ReceiveCommand, projectName: String) {
        val refName = command.refName
        
        try {
            // Send notifications for direct pushes
            logger.info("Sending notifications for regular ref push: $refName")
            
            // Trigger CI/CD pipelines
            logger.info("Triggering CI/CD for regular ref push: $refName")
            
            // Update metrics
            logger.info("Updating metrics for regular ref push: $refName")
            
        } catch (e: Exception) {
            logger.error("Error in regular ref post-receive: $refName", e)
        }
    }
    
    /**
     * Handle post-receive processing for virtual branch operations.
     * This method can be called by both HTTP and SSH servers.
     * @deprecated Use handlePostReceive() for unified post-receive handling
     */
    @Deprecated("Use handlePostReceive() for unified post-receive handling")
    fun handleVirtualBranchPush(virtualBranchRef: String, commitId: String, projectName: String) {
        handleVirtualBranchPostReceive(virtualBranchRef, commitId, projectName)
    }

    private fun processMagicBranch(command: ReceiveCommand, repository: Repository, projectName: String): ChangeService.ProcessResult {
        val refName = command.refName
        val targetBranch = refName.removePrefix("refs/for/")

        try {
            val commit = repository.parseCommit(command.newId)

            var changeId = ChangeIdUtil.extractChangeId(commit.fullMessage)

            if (changeId != null && !ChangeIdUtil.isValidChangeId(changeId)) {
                changeId = null
            }

            if (changeId == null) {
                val generatedChangeId = ChangeIdUtil.generateChangeId(
                    treeId = commit.tree.id,
                    parentIds = commit.parents.map { it.id },
                    author = commit.authorIdent,
                    committer = commit.committerIdent,
                    commitMessage = commit.fullMessage
                )
                logger.info("Generated Change-Id: $generatedChangeId for commit: ${commit.id.name}")

                changeId = generatedChangeId
            }

            return changeService.processChange(
                changeId = changeId,
                commit = commit,
                targetBranch = targetBranch,
                repository = repository,
                projectName = projectName
            )
        } catch (e: Exception) {
            logger.error("Error processing magic branch: $refName", e)
            return ChangeService.ProcessResult(success = false, message = "Internal error processing change: ${e.message}")
        }
    }

    private fun processRegularRef(command: ReceiveCommand, repository: Repository, defaultBranch: String): ChangeService.ProcessResult {
        val refName = command.refName

        if (!validateDirectPushPermissions(refName, defaultBranch)) {
            return ChangeService.ProcessResult(success = false, message = "Direct push to $refName not allowed. Use refs/for/$refName for code review.")
        }

        if (isBranchProtected(refName)) {
            return ChangeService.ProcessResult(success = false, message = "Branch $refName is protected and cannot be pushed to directly.")
        }

        if (command.type != ReceiveCommand.Type.DELETE) {
            try {
                val commit = repository.parseCommit(command.newId)
                if (!validateCommitForDirectPush(commit)) {
                    return ChangeService.ProcessResult(success = false, message = "Commit validation failed for direct push.")
                }
            } catch (e: Exception) {
                logger.error("Error validating commit for direct push: $refName", e)
                return ChangeService.ProcessResult(success = false, message = "Error validating commit: ${e.message}")
            }
        }

        return ChangeService.ProcessResult(success = true, message = "Regular ref push allowed")
    }

    private fun validateDirectPushPermissions(refName: String, defaultBranch: String): Boolean {
        val trunkRef = "refs/heads/$defaultBranch"
        if (refName == trunkRef) {
            return true
        }

        return !isBranchProtected(refName)
    }

    private fun isBranchProtected(refName: String): Boolean {
        val protectedBranches = listOf(
            "refs/heads/master",
            "refs/heads/main",
            "refs/heads/develop"
        )

        return protectedBranches.any { protectedRef -> refName == protectedRef }
    }

    private fun validateCommitForDirectPush(commit: RevCommit): Boolean {
        val message = commit.fullMessage
        if (message.isBlank()) {
            return false
        }

        if (message.length > 4096) {
            return false
        }

        return true
    }
}
