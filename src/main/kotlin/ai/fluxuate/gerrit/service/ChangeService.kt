package ai.fluxuate.gerrit.service

import ai.fluxuate.gerrit.util.ChangeIdUtil
import ai.fluxuate.gerrit.util.CommitMessagesUtil
import ai.fluxuate.gerrit.util.GitUtil
import ai.fluxuate.gerrit.util.PatchUtil
import ai.fluxuate.gerrit.util.RebaseUtil
import ai.fluxuate.gerrit.util.ReviewersUtil
import ai.fluxuate.gerrit.git.GitRepositoryService
import ai.fluxuate.gerrit.model.ChangeEntity
import ai.fluxuate.gerrit.model.ChangeStatus
import ai.fluxuate.gerrit.repository.ChangeEntityRepository
import ai.fluxuate.gerrit.api.dto.*
import ai.fluxuate.gerrit.api.exception.NotFoundException
import ai.fluxuate.gerrit.api.exception.BadRequestException
import ai.fluxuate.gerrit.api.exception.ConflictException
import ai.fluxuate.gerrit.api.exception.UnauthorizedException
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.RefUpdate
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.regex.Pattern
import kotlin.random.Random


@Service
class ChangeService(
    private val changeRepository: ChangeEntityRepository,
    private val accountService: AccountService,
    private val rebaseUtil: RebaseUtil,
    private val patchUtil: PatchUtil,
    private val reviewersUtil: ReviewersUtil,
    private val gitRepositoryService: GitRepositoryService
) {
    
    private val logger = LoggerFactory.getLogger(ChangeService::class.java)
    
    companion object {
        // Change-Id pattern: I followed by 40 hex characters
        private val CHANGE_ID_PATTERN = Pattern.compile("^I[0-9a-f]{40}$")
        
        // Pattern to extract Change-Id from commit message
        private val CHANGE_ID_FOOTER_PATTERN = Pattern.compile(
            "^Change-Id:\\s*(I[0-9a-f]{40})\\s*$", 
            Pattern.MULTILINE
        )
        
        // Valid label names and their allowed vote ranges
        private val VALID_LABELS = mapOf(
            "Code-Review" to (-2..2),
            "Verified" to (-1..1)
        )
    }

    /**
     * Get the current authenticated user account.
     * Uses Spring Security context to get the authenticated user and retrieves their account info.
     */
    private fun getCurrentUser(): AccountInfo {
        val authentication = SecurityContextHolder.getContext().authentication
        
        if (authentication == null || !authentication.isAuthenticated) {
            throw UnauthorizedException("Authentication required")
        }
        
        // Extract user identifier from authentication
        val userId = when (authentication.principal) {
            is org.springframework.security.core.userdetails.UserDetails -> {
                val userDetails = authentication.principal as org.springframework.security.core.userdetails.UserDetails
                userDetails.username
            }
            is String -> {
                authentication.name
            }
            else -> {
                authentication.name
            }
        }
        
        // Get the user account from AccountService - user must exist
        return try {
            accountService.getAccount(userId)
        } catch (e: NotFoundException) {
            throw UnauthorizedException("User account not found: $userId")
        }
    }

    fun processRefsForPush(
        repository: Repository,
        refName: String,
        oldObjectId: ObjectId?,
        newObjectId: ObjectId,
        projectName: String,
        ownerId: Int
    ): ProcessResult {
        try {
            // Extract target branch from refs/for/branch
            val targetBranch = extractTargetBranch(refName)
                ?: return ProcessResult.error("Invalid refs/for/ format: $refName")
            
            logger.info("Processing refs/for push: $refName -> $targetBranch for project $projectName")
            
            // Get the commit being pushed
            RevWalk(repository).use { revWalk ->
                val commit = revWalk.parseCommit(newObjectId)
                
                // Extract and validate Change-Id from commit message
                val changeId = extractChangeId(commit.fullMessage)
                    ?: return ProcessResult.error("Missing Change-Id in commit message")
                
                if (!isValidChangeId(changeId)) {
                    return ProcessResult.error("Invalid Change-Id format: $changeId")
                }
                
                // Check if this is a new change or update to existing change
                val existingChange = changeRepository.findByChangeKey(changeId)
                
                return if (existingChange != null) {
                    updateExistingChange(existingChange, commit, newObjectId, targetBranch, ownerId, projectName)
                } else {
                    createNewChange(changeId, commit, newObjectId, projectName, targetBranch, ownerId)
                }
            }
            
        } catch (e: Exception) {
            logger.error("Error processing refs/for push: $refName", e)
            return ProcessResult.error("Internal error: ${e.message}")
        }
    }
    
    /**
     * Process a change (create or update) based on Change-Id and commit.
     * This handles trunk branch direct pushes where the commit SHA is used as the Change-Id.
     */
    fun processChange(
        changeId: String,
        commit: RevCommit,
        targetBranch: String,
        repository: Repository,
        projectName: String
    ): ProcessResult {
        try {
            val ownerId = 1 // This should come from authenticated user context
            
            logger.info("Processing trunk branch direct push: commit ${commit.id.name} to $targetBranch for project $projectName")
            
            // For trunk branch direct pushes, we use the commit SHA as the Change-Id
            // and bypass the Change-Id extraction from commit message
            
            // Check if this is a new change or update to existing change
            val existingChange = changeRepository.findByChangeKey(changeId)
            
            return if (existingChange != null) {
                updateExistingChange(existingChange, commit, commit.id, targetBranch, ownerId, projectName)
            } else {
                createNewChange(changeId, commit, commit.id, projectName, targetBranch, ownerId)
            }
            
        } catch (e: Exception) {
            logger.error("Error processing trunk branch push for commit ${commit.id.name}", e)
            return ProcessResult(success = false, message = "Error processing trunk branch push: ${e.message}")
        }
    }
    
    /**
     * Extract target branch from refs/for/branch format.
     */
    private fun extractTargetBranch(refName: String): String? {
        if (!refName.startsWith("refs/for/")) {
            return null
        }
        
        val branch = refName.substring("refs/for/".length)
        return if (branch.isNotEmpty()) branch else null
    }
    
    /**
     * Extract Change-Id from commit message.
     * Uses CommitMessagesUtil for enhanced parsing capabilities.
     */
    private fun extractChangeId(commitMessage: String): String? {
        // Use the enhanced utility for better parsing
        val parsed = CommitMessagesUtil.parseCommitMessage(commitMessage)
        return parsed.changeId
    }
    
    /**
     * Validate Change-Id format.
     */
    private fun isValidChangeId(changeId: String): Boolean {
        return CHANGE_ID_PATTERN.matcher(changeId).matches()
    }
    
    /**
     * Create a new change for the given commit.
     */
    private fun createNewChange(
        changeId: String,
        commit: RevCommit,
        commitObjectId: ObjectId,
        projectName: String,
        targetBranch: String,
        ownerId: Int
    ): ProcessResult {
        try {
            val subject = extractSubject(commit.fullMessage)
            val now = Instant.now()
            
            // Create initial patch set
            val patchSet = mapOf(
                "id" to 1,
                "commitId" to commitObjectId.name,
                "uploader_id" to ownerId,
                "createdOn" to now.toString(),
                "description" to "Initial patch set",
                "isDraft" to false
            )
            
            val change = ChangeEntity(
                changeKey = changeId,
                ownerId = ownerId,
                projectName = projectName,
                destBranch = targetBranch,
                subject = subject,
                status = ChangeStatus.NEW,
                currentPatchSetId = 1,
                createdOn = now,
                lastUpdatedOn = now,
                patchSets = listOf(patchSet)
            )
            
            val savedChange = changeRepository.save(change)
            
            logger.info("Created new change: ${savedChange.id} with Change-Id: $changeId")
            
            // Create the virtual branch in the Git repository
            createVirtualBranch(changeId, 1, commitObjectId, projectName)
            
            return ProcessResult.success(
                "Created new change ${savedChange.id}",
                savedChange.id,
                1
            )
            
        } catch (e: Exception) {
            logger.error("Error creating new change for Change-Id: $changeId", e)
            return ProcessResult.error("Failed to create change: ${e.message}")
        }
    }
    
    /**
     * Update an existing change with a new patch set.
     */
    private fun updateExistingChange(
        existingChange: ChangeEntity,
        commit: RevCommit,
        commitObjectId: ObjectId,
        targetBranch: String,
        ownerId: Int,
        projectName: String
    ): ProcessResult {
        try {
            // Validate that the target branch matches
            if (existingChange.destBranch != targetBranch) {
                return ProcessResult.error(
                    "Change-Id ${existingChange.changeKey} is for branch ${existingChange.destBranch}, " +
                    "but you're pushing to $targetBranch"
                )
            }
            
            // Check if change is closed
            if (existingChange.isClosed) {
                return ProcessResult.error(
                    "Change ${existingChange.id} is ${existingChange.status.name.lowercase()}"
                )
            }
            
            val newPatchSetId = existingChange.currentPatchSetId + 1
            val now = Instant.now()
            
            // Create new patch set
            val newPatchSet = mapOf(
                "id" to newPatchSetId,
                "commitId" to commitObjectId.name,
                "uploader_id" to ownerId,
                "createdOn" to now.toString(),
                "description" to "Patch Set $newPatchSetId",
                "isDraft" to false
            )
            
            // Update the change with new patch set
            val updatedPatchSets = existingChange.patchSets + newPatchSet
            val updatedSubject = extractSubject(commit.fullMessage)
            
            val updatedChange = existingChange.copy(
                subject = updatedSubject,
                currentPatchSetId = newPatchSetId,
                lastUpdatedOn = now,
                patchSets = updatedPatchSets
            )
            
            val savedChange = changeRepository.save(updatedChange)
            
            logger.info("Updated change ${savedChange.id} with new patch set $newPatchSetId")
            
            // Create the virtual branch in the Git repository
            createVirtualBranch(existingChange.changeKey, newPatchSetId, commitObjectId, projectName)
            
            return ProcessResult.success(
                "Updated change ${savedChange.id} with patch set $newPatchSetId",
                savedChange.id,
                newPatchSetId
            )
            
        } catch (e: Exception) {
            logger.error("Error updating change ${existingChange.id}", e)
            return ProcessResult.error("Failed to update change: ${e.message}")
        }
    }
    
    /**
     * Extract subject line from commit message.
     */
    private fun extractSubject(commitMessage: String): String {
        val lines = commitMessage.split("\n")
        return if (lines.isNotEmpty()) {
            lines[0].trim().take(1000) // Limit to 1000 chars as per entity constraint
        } else {
            "No subject"
        }
    }
    
    /**
     * Generate virtual ref name for a change patch set using standard Gerrit format.
     * Format: refs/changes/XX/CHANGEID_HASH/PATCHSET
     * Where XX is the last 2 characters of the Change-Id hash (without 'I' prefix)
     * and CHANGEID_HASH is the full Change-Id hash (without 'I' prefix).
     */
    fun generateVirtualRef(changeIdHash: String, patchSetId: Int): String {
        // Remove 'I' prefix if present
        val hash = if (changeIdHash.startsWith("I")) changeIdHash.substring(1) else changeIdHash
        val lastTwoChars = hash.takeLast(2)
        return "refs/changes/$lastTwoChars/$hash/$patchSetId"
    }
    
    /**
     * Create a virtual branch in the Git repository.
     * This creates the actual refs/changes/XX/CHANGEID_HASH/PATCHSET reference.
     */
    private fun createVirtualBranch(changeIdHash: String, patchSetId: Int, commitId: ObjectId, projectName: String) {
        try {
            val virtualRefName = generateVirtualRef(changeIdHash, patchSetId)
            logger.info("Creating virtual branch: $virtualRefName -> ${commitId.name}")
            
            // Get the repository service to create the ref
            val repository = gitRepositoryService.openRepository(projectName)
            try {
                val refUpdate = repository.updateRef(virtualRefName)
                refUpdate.setNewObjectId(commitId)
                refUpdate.setForceUpdate(true) // Virtual branches can be force-updated
                
                val result = refUpdate.update()
                when (result) {
                    RefUpdate.Result.NEW,
                    RefUpdate.Result.FAST_FORWARD,
                    RefUpdate.Result.FORCED -> {
                        logger.info("Successfully created virtual branch: $virtualRefName")
                    }
                    else -> {
                        logger.warn("Failed to create virtual branch: $virtualRefName, result: $result")
                        throw RuntimeException("Failed to create virtual branch: $result")
                    }
                }
            } finally {
                repository.close()
            }
            
        } catch (e: Exception) {
            logger.error("Error creating virtual branch for commit ${commitId.name}, patch set $patchSetId", e)
            // Don't throw here - virtual branch creation failure shouldn't fail the entire change creation
            // The change and patch set are already saved in the database
        }
    }
    
    /**
     * Delete a virtual branch from the Git repository.
     * This is called when a change is abandoned or merged.
     */
    fun deleteVirtualBranch(changeId: Int, patchSetId: Int, projectName: String) {
        try {
            // Look up the patch set to get commit information
            val change = changeRepository.findById(changeId).orElseThrow {
                RuntimeException("Change $changeId not found")
            }
            val patchSet = change.patchSets.find { it["id"] == patchSetId }
                ?: throw RuntimeException("Patch set $patchSetId not found in change $changeId")
            
            val commitSha = patchSet["commitId"] as String
            
            // Get the repository to delete the virtual branch
            val repository = gitRepositoryService.openRepository(projectName)
            
            val virtualRefName = generateVirtualRef(change.changeKey, patchSetId)
            logger.info("Deleting virtual branch: $virtualRefName")
            try {
                val refUpdate = repository.updateRef(virtualRefName)
                refUpdate.setNewObjectId(ObjectId.zeroId()) // Delete the ref
                
                val result = refUpdate.update()
                when (result) {
                    RefUpdate.Result.FAST_FORWARD,
                    RefUpdate.Result.FORCED -> {
                        logger.info("Successfully deleted virtual branch: $virtualRefName")
                    }
                    else -> {
                        logger.warn("Failed to delete virtual branch: $virtualRefName, result: $result")
                    }
                }
            } finally {
                repository.close()
            }
            
        } catch (e: Exception) {
            logger.error("Error deleting virtual branch for change $changeId, patch set $patchSetId", e)
        }
    }
    
    /**
     * Get all virtual branches for a project.
     * This is used for ref advertisement.
     */
    fun getVirtualBranchesForProject(projectName: String): Map<String, String> {
        val virtualBranches = mutableMapOf<String, String>()
        
        try {
            // Get all active changes for this project
            val changes = changeRepository.findByProjectName(projectName, org.springframework.data.domain.PageRequest.of(0, 1000))
            
            for (change in changes) {
                // Get all patch sets for this change
                val patchSets = change.patchSets
                
                for (patchSet in patchSets) {
                    val patchSetId = patchSet["id"] as Int
                    val commitId = patchSet["commitId"] as String
                    
                    // Validate that the commit exists in the repository
                    val repository = gitRepositoryService.openRepository(projectName)
                    try {
                        val objectId = ObjectId.fromString(commitId)
                        if (repository.objectDatabase.has(objectId)) {
                            val virtualRefName = generateVirtualRef(change.changeKey, patchSetId)
                            virtualBranches[virtualRefName] = commitId
                        } else {
                            logger.warn("Commit $commitId for change ${change.id} patch set $patchSetId not found in repository")
                        }
                    } catch (e: Exception) {
                        logger.warn("Invalid commit ID $commitId for change ${change.id} patch set $patchSetId", e)
                    } finally {
                        repository.close()
                    }
                }
            }
            
        } catch (e: Exception) {
            logger.error("Error getting virtual branches for project: $projectName", e)
        }
        
        return virtualBranches
    }
    
    // REST API methods
    
    /**
     * Query changes with optional filters.
     */
    fun queryChanges(
        query: String?,
        limit: Int?,
        start: Int?,
        options: List<String>?
    ): List<ChangeInfo> {
        // For now, return all changes with basic pagination
        val pageSize = limit ?: 25
        val offset = start ?: 0
        
        val changes = changeRepository.findAll()
            .drop(offset)
            .take(pageSize)
            
        return changes.map { convertToChangeInfo(it) }
    }

    /**
     * Create a new change.
     */
    @Transactional
    fun createChange(input: ChangeInput): ChangeInfo {
        // Validate input
        if (input.project.isBlank()) {
            throw BadRequestException("Project name is required")
        }
        if (input.branch.isBlank()) {
            throw BadRequestException("Branch name is required")
        }
        if (input.subject.isBlank()) {
            throw BadRequestException("Subject is required")
        }

        // Create new change entity
        val change = ChangeEntity(
            changeKey = generateChangeId(),
            ownerId = getCurrentUser()._account_id.toInt(), // Get from security context
            projectName = input.project,
            destBranch = input.branch,
            subject = input.subject,
            topic = input.topic,
            status = input.status?.let { convertToEntityStatus(it) } ?: ChangeStatus.NEW,
            createdOn = Instant.now(),
            lastUpdatedOn = Instant.now()
        )

        val savedChange = changeRepository.save(change)
        return convertToChangeInfo(savedChange)
    }

    /**
     * Get change details by ID.
     */
    fun getChange(changeId: String, options: List<String>?): ChangeInfo {
        val change = findChangeByIdentifier(changeId)
        return convertToChangeInfo(change)
    }

    /**
     * Update change details.
     */
    @Transactional
    fun updateChange(changeId: String, input: ChangeInput): ChangeInfo {
        val change = findChangeByIdentifier(changeId)
        
        // Create updated change (since fields are val, we need to copy)
        val updatedChange = change.copy(
            subject = input.subject,
            topic = input.topic,
            lastUpdatedOn = Instant.now()
        )
        
        val savedChange = changeRepository.save(updatedChange)
        return convertToChangeInfo(savedChange)
    }

    /**
     * Delete a change.
     */
    @Transactional
    fun deleteChange(changeId: String) {
        val change = findChangeByIdentifier(changeId)
        
        // Only allow deletion of NEW changes
        if (change.status != ChangeStatus.NEW) {
            throw ConflictException("Cannot delete change with status ${change.status}")
        }
        
        changeRepository.delete(change)
    }

    /**
     * Abandon a change.
     */
    @Transactional
    fun abandonChange(changeId: String, input: AbandonInput): ChangeInfo {
        val change = findChangeByIdentifier(changeId)
        
        if (change.status != ChangeStatus.NEW) {
            throw ConflictException("Cannot abandon change with status ${change.status}")
        }
        
        val updatedChange = change.copy(
            status = ChangeStatus.ABANDONED,
            lastUpdatedOn = Instant.now()
        )
        
        val savedChange = changeRepository.save(updatedChange)
        return convertToChangeInfo(savedChange)
    }

    /**
     * Restore a change.
     */
    @Transactional
    fun restoreChange(changeId: String, input: RestoreInput): ChangeInfo {
        val change = findChangeByIdentifier(changeId)
        
        if (change.status != ChangeStatus.ABANDONED) {
            throw ConflictException("Cannot restore change with status ${change.status}")
        }
        
        val updatedChange = change.copy(
            status = ChangeStatus.NEW,
            lastUpdatedOn = Instant.now()
        )
        
        val savedChange = changeRepository.save(updatedChange)
        return convertToChangeInfo(savedChange)
    }

    /**
     * Submit a change.
     */
    @Transactional
    fun submitChange(changeId: String, input: SubmitInput): ChangeInfo {
        val change = findChangeByIdentifier(changeId)
        val updatedChange = rebaseUtil.performSubmit(change)
        val savedChange = changeRepository.save(updatedChange)
        return convertToChangeInfo(savedChange)
    }

    /**
     * Rebase a change.
     */
    @Transactional
    fun rebaseChange(changeId: String, input: RebaseInput): ChangeInfo {
        val change = findChangeByIdentifier(changeId)
        val updatedChange = rebaseUtil.performRebase(change)
        val savedChange = changeRepository.save(updatedChange)
        return convertToChangeInfo(savedChange)
    }

    /**
     * Cherry-pick a change.
     */
    @Transactional
    fun cherryPickChange(changeId: String, revisionId: String, input: CherryPickInput): ChangeInfo {
        val originalChange = findChangeByIdentifier(changeId)
        
        // Create new change for cherry-pick
        val cherryPickChange = ChangeEntity(
            changeKey = generateChangeId(),
            ownerId = originalChange.ownerId,
            projectName = originalChange.projectName,
            destBranch = input.destination,
            subject = input.message ?: originalChange.subject,
            topic = originalChange.topic,
            status = ChangeStatus.NEW,
            createdOn = Instant.now(),
            lastUpdatedOn = Instant.now()
        )
        
        val savedChange = changeRepository.save(cherryPickChange)
        return convertToChangeInfo(savedChange)
    }

    /**
     * Move a change to a different branch.
     */
    @Transactional
    fun moveChange(changeId: String, input: MoveInput): ChangeInfo {
        val change = findChangeByIdentifier(changeId)
        
        if (change.status != ChangeStatus.NEW) {
            throw ConflictException("Cannot move change with status ${change.status}")
        }
        
        val updatedChange = change.copy(
            destBranch = input.destination_branch,
            lastUpdatedOn = Instant.now()
        )
        
        val savedChange = changeRepository.save(updatedChange)
        return convertToChangeInfo(savedChange)
    }

    /**
     * Revert a change.
     */
    @Transactional
    fun revertChange(changeId: String, input: RevertInput): ChangeInfo {
        val originalChange = findChangeByIdentifier(changeId)
        
        if (originalChange.status != ChangeStatus.MERGED) {
            throw ConflictException("Cannot revert change with status ${originalChange.status}")
        }
        
        // Create revert change
        val revertChange = ChangeEntity(
            changeKey = generateChangeId(),
            ownerId = originalChange.ownerId,
            projectName = originalChange.projectName,
            destBranch = originalChange.destBranch,
            subject = input.message ?: "Revert \"${originalChange.subject}\"",
            topic = input.topic,
            status = ChangeStatus.NEW,
            createdOn = Instant.now(),
            lastUpdatedOn = Instant.now()
        )
        
        val savedChange = changeRepository.save(revertChange)
        return convertToChangeInfo(savedChange)
    }

    /**
     * Get topic of a change.
     */
    fun getTopic(changeId: String): String {
        val change = findChangeByIdentifier(changeId)
        return change.topic ?: ""
    }

    /**
     * Set topic of a change.
     */
    @Transactional
    fun setTopic(changeId: String, input: TopicInput): String {
        val change = findChangeByIdentifier(changeId)
        val updatedChange = change.copy(
            topic = input.topic,
            lastUpdatedOn = Instant.now()
        )
        changeRepository.save(updatedChange)
        return updatedChange.topic ?: ""
    }

    /**
     * Delete topic of a change.
     */
    @Transactional
    fun deleteTopic(changeId: String) {
        val change = findChangeByIdentifier(changeId)
        val updatedChange = change.copy(
            topic = null,
            lastUpdatedOn = Instant.now()
        )
        changeRepository.save(updatedChange)
    }

    /**
     * Set change as private.
     */
    @Transactional
    fun setPrivate(changeId: String, input: PrivateInput): String {
        val change = findChangeByIdentifier(changeId)
        // Store privacy info in metadata for now
        val updatedMetadata = change.metadata.toMutableMap()
        updatedMetadata["is_private"] = true
        val updatedChange = change.copy(
            metadata = updatedMetadata,
            lastUpdatedOn = Instant.now()
        )
        changeRepository.save(updatedChange)
        return "OK"
    }

    /**
     * Unset change as private.
     */
    @Transactional
    fun unsetPrivate(changeId: String): String {
        val change = findChangeByIdentifier(changeId)
        // Store privacy info in metadata for now
        val updatedMetadata = change.metadata.toMutableMap()
        updatedMetadata["is_private"] = false
        val updatedChange = change.copy(
            metadata = updatedMetadata,
            lastUpdatedOn = Instant.now()
        )
        changeRepository.save(updatedChange)
        return "OK"
    }

    /**
     * Set work in progress.
     */
    @Transactional
    fun setWorkInProgress(changeId: String, input: WorkInProgressInput): String {
        val change = findChangeByIdentifier(changeId)
        // Store WIP info in metadata for now
        val updatedMetadata = change.metadata.toMutableMap()
        updatedMetadata["work_in_progress"] = true
        val updatedChange = change.copy(
            metadata = updatedMetadata,
            lastUpdatedOn = Instant.now()
        )
        changeRepository.save(updatedChange)
        return "OK"
    }

    /**
     * Set ready for review.
     */
    @Transactional
    fun setReadyForReview(changeId: String, input: ReadyForReviewInput): String {
        val change = findChangeByIdentifier(changeId)
        // Store WIP info in metadata for now
        val updatedMetadata = change.metadata.toMutableMap()
        updatedMetadata["work_in_progress"] = false
        val updatedChange = change.copy(
            metadata = updatedMetadata,
            lastUpdatedOn = Instant.now()
        )
        changeRepository.save(updatedChange)
        return "OK"
    }

    // Helper methods for REST API
    
    private fun findChangeByIdentifier(changeId: String): ChangeEntity {
        return when {
            changeId.matches(Regex("\\d+")) -> {
                // Numeric ID
                changeRepository.findById(changeId.toInt())
                    .orElseThrow { NotFoundException("Change not found: $changeId") }
            }
            changeId.matches(CHANGE_ID_PATTERN.toRegex()) -> {
                // Change-Id format
                changeRepository.findByChangeKey(changeId)
                    ?: throw NotFoundException("Change not found: $changeId")
            }
            changeId.contains("~") -> {
                // project~branch~Change-Id format
                val parts = changeId.split("~")
                if (parts.size == 3) {
                    changeRepository.findByProjectNameAndDestBranchAndChangeKey(parts[0], parts[1], parts[2])
                        ?: throw NotFoundException("Change not found: $changeId")
                } else {
                    throw BadRequestException("Invalid change identifier format: $changeId")
                }
            }
            else -> throw BadRequestException("Invalid change identifier format: $changeId")
        }
    }
    
    private fun convertToChangeInfo(change: ChangeEntity): ChangeInfo {
        return ChangeInfo(
            id = "${change.projectName}~${change.destBranch}~${change.changeKey}",
            project = change.projectName,
            branch = change.destBranch,
            topic = change.topic,
            changeId = change.changeKey,
            subject = change.subject,
            status = convertToApiStatus(change.status),
            created = change.createdOn,
            updated = change.lastUpdatedOn,
            number = change.id.toLong(),
            owner = AccountInfo(_account_id = change.ownerId.toLong()),
            is_private = change.metadata["is_private"] as? Boolean ?: false,
            work_in_progress = change.metadata["work_in_progress"] as? Boolean ?: false
        )
    }
    
    private fun convertToApiStatus(status: ChangeStatus): ai.fluxuate.gerrit.api.dto.ChangeStatus {
        return when (status) {
            ChangeStatus.NEW -> ai.fluxuate.gerrit.api.dto.ChangeStatus.NEW
            ChangeStatus.MERGED -> ai.fluxuate.gerrit.api.dto.ChangeStatus.MERGED
            ChangeStatus.ABANDONED -> ai.fluxuate.gerrit.api.dto.ChangeStatus.ABANDONED
        }
    }
    
    private fun convertToEntityStatus(status: ai.fluxuate.gerrit.api.dto.ChangeStatus): ChangeStatus {
        return when (status) {
            ai.fluxuate.gerrit.api.dto.ChangeStatus.NEW -> ChangeStatus.NEW
            ai.fluxuate.gerrit.api.dto.ChangeStatus.MERGED -> ChangeStatus.MERGED
            ai.fluxuate.gerrit.api.dto.ChangeStatus.ABANDONED -> ChangeStatus.ABANDONED
        }
    }
    
    private fun generateChangeId(): String {
        // Generate a Change-Id in the format I + 40 hex characters
        val chars = "0123456789abcdef"
        val changeId = StringBuilder("I")
        repeat(40) {
            changeId.append(chars[Random.nextInt(chars.length)])
        }
        return changeId.toString()
    }
    
    data class ProcessResult(
        val success: Boolean,
        val message: String,
        val changeId: Int? = null,
        val patchSetId: Int? = null
    ) {
        companion object {
            fun success(message: String, changeId: Int, patchSetId: Int) = 
                ProcessResult(true, message, changeId, patchSetId)
            
            fun error(message: String) = 
                ProcessResult(false, message)
        }
    }
    
    data class ChangeDto(
        val id: Int,
        val changeKey: String,
        val subject: String,
        val status: ChangeStatus,
        val createdOn: Instant,
        val lastUpdatedOn: Instant
    )

    // ================================
    // REVIEWERS MANAGEMENT METHODS
    // ================================

    /**
     * Get reviewers for a change.
     */
    @Transactional(readOnly = true)
    fun getReviewers(changeId: String): List<AccountInfo> {
        val change = findChangeByIdentifier(changeId)
        
        // Extract reviewers from JSONB metadata field
        val reviewersData = change.metadata["reviewers"] as? Map<String, Any> ?: emptyMap()
        val reviewersList = reviewersData["REVIEWER"] as? List<Map<String, Any>> ?: emptyList()
        val ccList = reviewersData["CC"] as? List<Map<String, Any>> ?: emptyList()
        
        return (reviewersList + ccList).map { reviewerMap ->
            AccountInfo(
                _account_id = (reviewerMap["_account_id"] as? Number)?.toLong() ?: 0L,
                name = reviewerMap["name"] as? String,
                display_name = reviewerMap["display_name"] as? String,
                email = reviewerMap["email"] as? String,
                username = reviewerMap["username"] as? String,
                inactive = !(reviewerMap["active"] as? Boolean ?: true)
            )
        }
    }

    /**
     * Add reviewer to a change.
     */
    @Transactional
    fun addReviewer(changeId: String, input: ReviewerInput): AddReviewerResult {
        val change = findChangeByIdentifier(changeId)
        
        // Use ReviewersUtil to resolve reviewer string to account(s)
        val reviewerAccount = reviewersUtil.resolveReviewer(input.reviewer)
        
        if (reviewerAccount == null) {
            return AddReviewerResult(
                input = input.reviewer,
                error = "Reviewer not found: ${input.reviewer}"
            )
        }
        
        // Get current metadata and reviewers
        val currentMetadata = change.metadata.toMutableMap()
        val reviewersData = (currentMetadata["reviewers"] as? MutableMap<String, Any>) ?: mutableMapOf()
        val state = input.state ?: ReviewerState.REVIEWER
        val stateKey = state.name
        
        val currentList = (reviewersData[stateKey] as? MutableList<Map<String, Any>>) ?: mutableListOf()
        
        // Check if reviewer already exists
        val existingReviewer = currentList.find { 
            val existingAccountId = (it["_account_id"] as? Number)?.toLong()
            existingAccountId == reviewerAccount._account_id 
        }
        
        if (existingReviewer != null) {
            return AddReviewerResult(
                input = input.reviewer,
                error = "Reviewer already added"
            )
        }
        
        // Add reviewer
        val reviewerMap = mapOf<String, Any>(
            "_account_id" to reviewerAccount._account_id,
            "name" to (reviewerAccount.name ?: ""),
            "display_name" to (reviewerAccount.display_name ?: ""),
            "email" to (reviewerAccount.email ?: ""),
            "username" to (reviewerAccount.username ?: ""),
            "active" to !(reviewerAccount.inactive ?: false)
        )
        
        currentList.add(reviewerMap)
        reviewersData[stateKey] = currentList
        currentMetadata["reviewers"] = reviewersData
        
        // Update change
        val updatedChange = change.copy(
            metadata = currentMetadata,
            lastUpdatedOn = Instant.now()
        )
        changeRepository.save(updatedChange)
        
        return AddReviewerResult(
            input = input.reviewer,
            reviewers = if (state == ReviewerState.REVIEWER) listOf(reviewerAccount) else emptyList(),
            ccs = if (state == ReviewerState.CC) listOf(reviewerAccount) else emptyList()
        )
    }

    /**
     * Get specific reviewer.
     */
    @Transactional(readOnly = true)
    fun getReviewer(changeId: String, reviewerId: String): AccountInfo {
        val change = findChangeByIdentifier(changeId)
        val reviewers = getReviewers(changeId)
        
        return reviewers.find { reviewer ->
            reviewer._account_id.toString() == reviewerId ||
            reviewer.email == reviewerId ||
            reviewer.username == reviewerId
        } ?: throw NotFoundException("Reviewer not found: $reviewerId")
    }

    /**
     * Remove reviewer from a change.
     */
    @Transactional
    fun removeReviewer(changeId: String, reviewerId: String, input: DeleteReviewerInput) {
        val change = findChangeByIdentifier(changeId)
        
        // Get current metadata and reviewers
        val currentMetadata = change.metadata.toMutableMap()
        val reviewersData = (currentMetadata["reviewers"] as? MutableMap<String, Any>) ?: mutableMapOf()
        var removed = false
        
        // Remove from both REVIEWER and CC lists
        for (state in listOf("REVIEWER", "CC")) {
            val currentList = (reviewersData[state] as? MutableList<Map<String, Any>>) ?: continue
            
            val iterator = currentList.iterator()
            while (iterator.hasNext()) {
                val reviewer = iterator.next()
                val accountId = (reviewer["_account_id"] as? Number)?.toString()
                val email = reviewer["email"] as? String
                val username = reviewer["username"] as? String
                
                if (accountId == reviewerId || email == reviewerId || username == reviewerId) {
                    iterator.remove()
                    removed = true
                    break
                }
            }
            
            reviewersData[state] = currentList
        }
        
        if (!removed) {
            throw NotFoundException("Reviewer not found: $reviewerId")
        }
        
        currentMetadata["reviewers"] = reviewersData
        
        // Update change
        val updatedChange = change.copy(
            metadata = currentMetadata,
            lastUpdatedOn = Instant.now()
        )
        changeRepository.save(updatedChange)
    }

    /**
     * Suggest reviewers for a change.
     */
    @Transactional(readOnly = true)
    fun suggestReviewers(changeId: String, query: String?, limit: Int?): List<SuggestedReviewerInfo> {
        val change = findChangeByIdentifier(changeId)
        
        // Use ReviewersUtil to implement actual reviewer suggestion logic
        return reviewersUtil.suggestReviewers(change, query, limit)
    }

    /**
     * Resolve reviewer string to AccountInfo.
     * Uses ReviewersUtil for actual reviewer resolution logic.
     */
    private fun resolveReviewer(reviewer: String): AccountInfo? {
        // Use ReviewersUtil to implement actual reviewer resolution logic
        return reviewersUtil.resolveReviewer(reviewer)
    }

    /**
     * Get revision patch.
     */
    fun getRevisionPatch(
        changeId: String,
        revisionId: String,
        zip: Boolean = false
    ): String {
        val change = findChangeByIdentifier(changeId)
        val patchSet = patchUtil.validateRevisionExists(change, revisionId)
        return GitUtil.generateRevisionPatch(change, patchSet, revisionId, zip)
    }

    // ================================
    // REVISIONS MANAGEMENT METHODS
    // ================================

    /**
     * Get all revisions for a change.
     */
    @Transactional(readOnly = true)
    fun getRevisions(changeId: String): Map<String, RevisionInfo> {
        val change = findChangeByIdentifier(changeId)
        return patchUtil.convertPatchSetsToRevisionInfoMap(change)
    }

    /**
     * Get a specific revision for a change.
     */
    @Transactional(readOnly = true)
    fun getRevision(changeId: String, revisionId: String): RevisionInfo {
        val change = findChangeByIdentifier(changeId)
        val patchSet = patchUtil.validateRevisionExists(change, revisionId)
        return patchUtil.convertPatchSetToRevisionInfo(patchSet, change)
    }

    /**
     * Submit a revision.
     */
    @Transactional
    fun submitRevision(changeId: String, revisionId: String, input: SubmitInput): ChangeInfo {
        val change = findChangeByIdentifier(changeId)
        val updatedChange = rebaseUtil.performRevisionSubmit(change, revisionId)
        val savedChange = changeRepository.save(updatedChange)
        return convertToChangeInfo(savedChange)
    }

    /**
     * Get commit info for a revision.
     */
    @Transactional(readOnly = true)
    fun getRevisionCommit(changeId: String, revisionId: String): CommitInfo {
        val change = findChangeByIdentifier(changeId)
        val patchSet = patchUtil.validateRevisionExists(change, revisionId)
        
        return patchUtil.convertPatchSetToCommitInfo(patchSet, change)
    }

    /**
     * Rebase a revision.
     */
    @Transactional
    fun rebaseRevision(changeId: String, revisionId: String, input: RebaseInput): ChangeInfo {
        val change = findChangeByIdentifier(changeId)
        val updatedChange = rebaseUtil.performRevisionRebase(change, revisionId)
        val savedChange = changeRepository.save(updatedChange)
        return convertToChangeInfo(savedChange)
    }

    /**
     * Review a revision.
     */
    @Transactional
    fun reviewRevision(changeId: String, revisionId: String, input: ReviewInput): ReviewResult {
        val change = findChangeByIdentifier(changeId)
        
        // Verify the revision exists
        findPatchSetByRevisionId(change, revisionId)
            ?: throw NotFoundException("Revision $revisionId not found in change $changeId")
        
        // Process votes/labels
        val appliedLabels = mutableMapOf<String, Int>()
        val reviewerResults = mutableMapOf<String, AddReviewerResult>()
        
        // Handle label votes
        input.labels?.forEach { (labelName, value) ->
            // Validate label and value
            val validRange = VALID_LABELS[labelName] ?: (-2..2) // Default range for custom labels
            if (value !in validRange) {
                throw BadRequestException("Invalid vote value $value for label $labelName. Valid range: ${validRange.first} to ${validRange.last}")
            }
            
            appliedLabels[labelName] = value
        }
        
        // Update change with new approvals
        val updatedChange = change.copy(
            approvals = if (input.labels?.isNotEmpty() == true) {
                val currentApprovals = (change.approvals as? List<Map<String, Any>>) ?: emptyList()
                val updatedApprovals = currentApprovals.toMutableList()
                val currentUser = getCurrentUser()
                
                input.labels.forEach { (labelName, value) ->
                    val existingApprovalIndex = updatedApprovals.indexOfFirst { existing ->
                        val existingLabel = existing["label"] as? String
                        val existingUser = existing["user"] as? Map<String, Any>
                        val existingAccountId = existingUser?.get("_account_id")
                        val existingAccountIdLong = when (existingAccountId) {
                            is Int -> existingAccountId.toLong()
                            is Long -> existingAccountId
                            else -> null
                        }
                        existingLabel == labelName && existingAccountIdLong == currentUser._account_id
                    }
                    
                    if (existingApprovalIndex >= 0) {
                        updatedApprovals[existingApprovalIndex] = mapOf(
                            "label" to labelName,
                            "value" to value,
                            "user" to mapOf(
                                "_account_id" to currentUser._account_id,
                                "name" to currentUser.name,
                                "email" to currentUser.email
                            ),
                            "granted" to Instant.now().toString(),
                            "revision_id" to revisionId
                        )
                    } else {
                        val approval = mapOf(
                            "label" to labelName,
                            "value" to value,
                            "user" to mapOf(
                                "_account_id" to currentUser._account_id,
                                "name" to currentUser.name,
                                "email" to currentUser.email
                            ),
                            "granted" to Instant.now().toString(),
                            "revision_id" to revisionId
                        )
                        
                        updatedApprovals.add(approval)
                    }
                }
                updatedApprovals
            } else change.approvals,
            lastUpdatedOn = Instant.now()
        )
        changeRepository.save(updatedChange)
        
        return ReviewResult(
            labels = appliedLabels,
            reviewers = reviewerResults
        )
    }

    // ===== COMMENT SERVICE METHODS =====

    /**
     * List comments for a revision.
     */
    fun listRevisionComments(changeId: String, revisionId: String): Map<String, List<CommentInfo>> {
        val change = findChangeByIdentifier(changeId)
        val patchSet = findPatchSetByRevisionId(change, revisionId)
        
        val metadata = change.metadata as? Map<String, Any> ?: emptyMap()
        val commentsData = metadata["comments"] as? Map<String, Any> ?: emptyMap()
        val revisionComments = commentsData[revisionId] as? Map<String, List<Map<String, Any>>> ?: emptyMap()
        
        return revisionComments.mapValues { (_, commentsList) ->
            commentsList.map { commentMap ->
                convertMapToCommentInfo(commentMap)
            }
        }
    }

    /**
     * List draft comments for a revision.
     */
    fun listRevisionDrafts(changeId: String, revisionId: String): Map<String, List<CommentInfo>> {
        val change = findChangeByIdentifier(changeId)
        val patchSet = findPatchSetByRevisionId(change, revisionId)
        
        val metadata = change.metadata as? Map<String, Any> ?: emptyMap()
        val draftsData = metadata["drafts"] as? Map<String, Any> ?: emptyMap()
        val revisionDrafts = draftsData[revisionId] as? Map<String, List<Map<String, Any>>> ?: emptyMap()
        
        return revisionDrafts.mapValues { (_, draftsList) ->
            draftsList.map { draftMap ->
                convertMapToCommentInfo(draftMap)
            }
        }
    }

    /**
     * Create/update draft comments for a revision.
     */
    @Transactional
    fun createRevisionDrafts(changeId: String, revisionId: String, input: CommentsInput): Map<String, List<CommentInfo>> {
        val change = findChangeByIdentifier(changeId)
        val patchSet = findPatchSetByRevisionId(change, revisionId)
        
        val metadata = change.metadata?.toMutableMap() ?: mutableMapOf<String, Any>()
        val draftsData = metadata.getOrPut("drafts") { mutableMapOf<String, Any>() } as MutableMap<String, Any>
        val revisionDrafts = draftsData.getOrPut(revisionId) { mutableMapOf<String, MutableList<Map<String, Any>>>() } as MutableMap<String, MutableList<Map<String, Any>>>
        
        // Store created comments with their IDs for consistent return
        val createdComments = mutableMapOf<String, MutableList<Pair<String, CommentInfo>>>()
        
        // Process each file's comments
        input.comments.forEach { (path, comments) ->
            val pathDrafts = revisionDrafts.getOrPut(path) { mutableListOf() }
            val pathCreatedComments = mutableListOf<Pair<String, CommentInfo>>()
            
            comments.forEach { commentInput ->
                val commentId = generateCommentId()
                val commentMap = convertCommentInputToMap(commentInput, commentId)
                pathDrafts.add(commentMap)
                
                // Store the created comment info for return
                val commentInfo = convertCommentInputToCommentInfo(commentInput, commentId)
                pathCreatedComments.add(commentId to commentInfo)
            }
            
            createdComments[path] = pathCreatedComments
        }
        
        // Save updated change
        val updatedChange = change.copy(metadata = metadata)
        changeRepository.save(updatedChange)
        
        // Return the created drafts with consistent IDs
        return createdComments.mapValues { (_, commentPairs) ->
            commentPairs.map { it.second }
        }
    }

    /**
     * Get a specific comment.
     */
    fun getRevisionComment(changeId: String, revisionId: String, commentId: String): CommentInfo {
        val change = findChangeByIdentifier(changeId)
        val patchSet = findPatchSetByRevisionId(change, revisionId)
        
        val metadata = change.metadata as? Map<String, Any> ?: emptyMap()
        val commentsData = metadata["comments"] as? Map<String, Any> ?: emptyMap()
        val revisionComments = commentsData[revisionId] as? Map<String, List<Map<String, Any>>> ?: emptyMap()
        
        // Find comment by ID across all files
        revisionComments.values.forEach { commentsList ->
            commentsList.forEach { commentMap ->
                if (commentMap["id"] == commentId) {
                    return convertMapToCommentInfo(commentMap)
                }
            }
        }
        
        throw NotFoundException("Comment not found: $commentId")
    }

    /**
     * Get a specific draft comment.
     */
    fun getRevisionDraft(changeId: String, revisionId: String, commentId: String): CommentInfo {
        val change = findChangeByIdentifier(changeId)
        val patchSet = findPatchSetByRevisionId(change, revisionId)
        
        val metadata = change.metadata as? Map<String, Any> ?: emptyMap()
        val draftsData = metadata["drafts"] as? Map<String, Any> ?: emptyMap()
        val revisionDrafts = draftsData[revisionId] as? Map<String, List<Map<String, Any>>> ?: emptyMap()
        
        // Find draft by ID across all files
        revisionDrafts.values.forEach { draftsList ->
            draftsList.forEach { draftMap ->
                if (draftMap["id"] == commentId) {
                    return convertMapToCommentInfo(draftMap)
                }
            }
        }
        
        throw NotFoundException("Draft comment not found: $commentId")
    }

    /**
     * Update a specific draft comment.
     */
    @Transactional
    fun updateRevisionDraft(changeId: String, revisionId: String, commentId: String, input: CommentInput): CommentInfo {
        val change = findChangeByIdentifier(changeId)
        val patchSet = findPatchSetByRevisionId(change, revisionId)
        
        val metadata = change.metadata?.toMutableMap() ?: mutableMapOf<String, Any>()
        val draftsData = metadata.getOrPut("drafts") { mutableMapOf<String, Any>() } as MutableMap<String, Any>
        val revisionDrafts = draftsData.getOrPut(revisionId) { mutableMapOf<String, MutableList<Map<String, Any>>>() } as MutableMap<String, MutableList<Map<String, Any>>>
        
        // Find and update the draft
        revisionDrafts.values.forEach { draftsList ->
            val draftIndex = draftsList.indexOfFirst { it["id"] == commentId }
            if (draftIndex >= 0) {
                val updatedDraft = convertCommentInputToMap(input, commentId)
                draftsList[draftIndex] = updatedDraft
                
                // Save updated change
                val updatedChange = change.copy(metadata = metadata)
                changeRepository.save(updatedChange)
                
                return convertMapToCommentInfo(updatedDraft)
            }
        }
        
        throw NotFoundException("Draft comment not found: $commentId")
    }

    /**
     * Delete a specific draft comment.
     */
    @Transactional
    fun deleteRevisionDraft(changeId: String, revisionId: String, commentId: String) {
        val change = findChangeByIdentifier(changeId)
        val patchSet = findPatchSetByRevisionId(change, revisionId)
        
        val metadata = change.metadata?.toMutableMap() ?: mutableMapOf<String, Any>()
        val draftsData = metadata.getOrPut("drafts") { mutableMapOf<String, Any>() } as MutableMap<String, Any>
        val revisionDrafts = draftsData.getOrPut(revisionId) { mutableMapOf<String, MutableList<Map<String, Any>>>() } as MutableMap<String, MutableList<Map<String, Any>>>
        
        // Find and remove the draft
        var found = false
        revisionDrafts.values.forEach { draftsList ->
            val draftIndex = draftsList.indexOfFirst { it["id"] == commentId }
            if (draftIndex >= 0) {
                draftsList.removeAt(draftIndex)
                found = true
                return@forEach
            }
        }
        
        if (!found) {
            throw NotFoundException("Draft comment not found: $commentId")
        }
        
        // Save updated change
        val updatedChange = change.copy(metadata = metadata)
        changeRepository.save(updatedChange)
    }

    /**
     * Delete a published comment (marks as deleted, doesn't actually remove).
     */
    @Transactional
    fun deleteRevisionComment(changeId: String, revisionId: String, commentId: String, input: DeleteCommentInput): CommentInfo {
        val change = findChangeByIdentifier(changeId)
        val patchSet = findPatchSetByRevisionId(change, revisionId)
        
        val metadata = change.metadata?.toMutableMap() ?: mutableMapOf<String, Any>()
        val commentsData = metadata.getOrPut("comments") { mutableMapOf<String, Any>() } as MutableMap<String, Any>
        val revisionComments = commentsData.getOrPut(revisionId) { mutableMapOf<String, MutableList<Map<String, Any>>>() } as MutableMap<String, MutableList<Map<String, Any>>>
        
        // Find and mark comment as deleted
        revisionComments.values.forEach { commentsList ->
            val commentIndex = commentsList.indexOfFirst { it["id"] == commentId }
            if (commentIndex >= 0) {
                val comment = commentsList[commentIndex].toMutableMap()
                comment["message"] = "[Comment deleted: ${input.reason ?: "No reason provided"}]"
                comment["deleted"] = true
                commentsList[commentIndex] = comment
                
                // Save updated change
                val updatedChange = change.copy(metadata = metadata)
                changeRepository.save(updatedChange)
                
                return convertMapToCommentInfo(comment)
            }
        }
        
        throw NotFoundException("Comment not found: $commentId")
    }

    /**
     * Convert CommentInput to internal map representation.
     */
    private fun convertCommentInputToMap(input: CommentInput, commentId: String): Map<String, Any> {
        val currentUser = getCurrentUser()
        return mapOf(
            "id" to commentId,
            "path" to (input.path ?: ""),
            "side" to (input.side?.name ?: "REVISION"),
            "parent" to (input.parent ?: 0),
            "line" to (input.line ?: 0),
            "range" to (input.range ?: emptyMap<String, Any>()),
            "in_reply_to" to (input.inReplyTo ?: ""),
            "message" to input.message,
            "tag" to (input.tag ?: ""),
            "unresolved" to (input.unresolved ?: false),
            "updated" to Instant.now().toString(),
            "author" to mapOf(
                "_account_id" to currentUser._account_id,
                "name" to (currentUser.name ?: ""),
                "email" to (currentUser.email ?: "")
            )
        )
    }

    /**
     * Convert CommentInput to CommentInfo DTO.
     */
    private fun convertCommentInputToCommentInfo(input: CommentInput, commentId: String): CommentInfo {
        val currentUser = getCurrentUser()
        return CommentInfo(
            id = commentId,
            updated = Instant.now(),
            patchSet = 1, // Default patch set
            path = input.path,
            side = input.side,
            parent = input.parent,
            line = input.line,
            range = input.range,
            inReplyTo = input.inReplyTo,
            message = input.message,
            author = AccountInfo(
                _account_id = currentUser._account_id,
                name = currentUser.name,
                email = currentUser.email
            ),
            tag = input.tag,
            unresolved = input.unresolved
        )
    }

    /**
     * Convert internal map representation to CommentInfo DTO.
     */
    private fun convertMapToCommentInfo(commentMap: Map<String, Any>): CommentInfo {
        val authorMap = commentMap["author"] as? Map<String, Any> ?: emptyMap()
        val rangeMap = commentMap["range"] as? Map<String, Any> ?: emptyMap()
        
        return CommentInfo(
            id = commentMap["id"] as? String ?: "",
            updated = Instant.parse(commentMap["updated"] as? String ?: Instant.now().toString()),
            patchSet = commentMap["patch_set"] as? Int ?: 1,
            path = commentMap["path"] as? String,
            side = (commentMap["side"] as? String)?.let { CommentSide.valueOf(it) },
            parent = commentMap["parent"] as? Int,
            line = commentMap["line"] as? Int,
            range = if (rangeMap.isNotEmpty()) {
                CommentRange(
                    startLine = rangeMap["start_line"] as? Int ?: 0,
                    startCharacter = rangeMap["start_character"] as? Int ?: 0,
                    endLine = rangeMap["end_line"] as? Int ?: 0,
                    endCharacter = rangeMap["end_character"] as? Int ?: 0
                )
            } else null,
            inReplyTo = commentMap["in_reply_to"] as? String,
            message = commentMap["message"] as? String,
            author = AccountInfo(
                _account_id = (authorMap["_account_id"] as? Number)?.toLong() ?: 1L,
                name = authorMap["name"] as? String,
                email = authorMap["email"] as? String,
                username = authorMap["username"] as? String
            ),
            tag = commentMap["tag"] as? String,
            unresolved = commentMap["unresolved"] as? Boolean ?: false
        )
    }

    /**
     * Generate a unique comment ID.
     */
    private fun generateCommentId(): String {
        return "comment_${System.currentTimeMillis()}_${Random.nextInt(1000, 9999)}"
    }

    // ===== FILES AND DIFFS METHODS =====

    /**
     * List files in a revision.
     */
    fun listRevisionFiles(
        changeId: String,
        revisionId: String,
        base: String? = null,
        parent: Int? = null,
        reviewed: Boolean? = null,
        query: String? = null
    ): Map<String, FileInfo> {
        val change = findChangeByIdentifier(changeId)
        val patchSet = findPatchSetByRevisionId(change, revisionId)
            ?: throw IllegalArgumentException("Patch set not found for revision: $revisionId")
        return GitUtil.listRevisionFiles(change, patchSet, base, parent, reviewed, query)
    }

    /**
     * Get content of a file in a revision.
     */
    fun getFileContent(
        changeId: String,
        revisionId: String,
        fileId: String,
        parent: Int? = null
    ): String {
        val change = findChangeByIdentifier(changeId)
        val patchSet = findPatchSetByRevisionId(change, revisionId)
            ?: throw IllegalArgumentException("Patch set not found for revision: $revisionId")
        return GitUtil.getFileContent(change, patchSet, fileId)
    }

    /**
     * Get file diff.
     */
    fun getFileDiff(
        changeId: String,
        revisionId: String,
        fileId: String,
        base: String? = null,
        parent: Int? = null,
        context: Int? = null,
        intraline: Boolean? = null,
        whitespace: String? = null
    ): DiffInfo {
        val change = findChangeByIdentifier(changeId)
        val patchSet = findPatchSetByRevisionId(change, revisionId)
            ?: throw IllegalArgumentException("Patch set not found for revision: $revisionId")
        return GitUtil.getFileDiff(change, patchSet, fileId, base, parent, context, intraline, whitespace)
    }

    // ===== HELPER METHODS FOR FILES =====

    private fun getAllFilesInRevision(change: ChangeEntity, patchSet: Map<String, Any>): Map<String, FileInfo> {
        return GitUtil.getAllFilesInRevision(change, patchSet)
    }

    private fun getFilesComparedToBase(change: ChangeEntity, patchSet: Map<String, Any>, base: String): Map<String, FileInfo> {
        return GitUtil.getFilesComparedToBase(change, patchSet, base)
    }

    private fun getFilesComparedToParent(change: ChangeEntity, patchSet: Map<String, Any>, parent: Int): Map<String, FileInfo> {
        return GitUtil.getFilesComparedToParent(change, patchSet, parent)
    }

    /**
     * Find a patch set by revision ID.
     */
    private fun findPatchSetByRevisionId(change: ChangeEntity, revisionId: String): Map<String, Any>? {
        return when {
            revisionId == "current" -> {
                // Return the current (latest) patch set
                change.patchSets.lastOrNull()
            }
            revisionId.matches(Regex("\\d+")) -> {
                // Revision ID is a patch set number
                val patchSetNumber = revisionId.toInt()
                change.patchSets.getOrNull(patchSetNumber - 1)
            }
            else -> {
                // Revision ID is a commit ID or revision hash
                change.patchSets.find { patchSet ->
                    val commitId = patchSet["commitId"] as? String
                    val revision = patchSet["revision"] as? String
                    (commitId != null && commitId.startsWith(revisionId)) ||
                    (revision != null && revision.startsWith(revisionId))
                }
            }
        }
    }
}
