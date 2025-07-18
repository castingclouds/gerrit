package ai.fluxuate.gerrit.service

import ai.fluxuate.gerrit.model.ChangeEntity
import ai.fluxuate.gerrit.model.ChangeStatus
import ai.fluxuate.gerrit.repository.ChangeEntityRepository
import ai.fluxuate.gerrit.api.dto.*
import ai.fluxuate.gerrit.api.exception.NotFoundException
import ai.fluxuate.gerrit.api.exception.BadRequestException
import ai.fluxuate.gerrit.api.exception.ConflictException
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.regex.Pattern
import kotlin.random.Random


@Service
class ChangeService(
    private val changeRepository: ChangeEntityRepository
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
                    updateExistingChange(existingChange, commit, newObjectId, targetBranch, ownerId)
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
     * This is a simplified version of processRefsForPush for SSH command usage.
     */
    fun processChange(
        changeId: String,
        commit: RevCommit,
        targetBranch: String,
        repository: Repository
    ): ProcessResult {
        // For now, delegate to processRefsForPush with default values
        // In a full implementation, this would extract more context from the SSH session
        val projectName = repository.directory.parentFile?.name ?: "unknown"
        val ownerId = 1 // This should come from authenticated user context
        
        return processRefsForPush(
            repository = repository,
            refName = "refs/for/$targetBranch",
            oldObjectId = null,
            newObjectId = commit.id,
            projectName = projectName,
            ownerId = ownerId
        )
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
     */
    private fun extractChangeId(commitMessage: String): String? {
        val matcher = CHANGE_ID_FOOTER_PATTERN.matcher(commitMessage)
        return if (matcher.find()) {
            matcher.group(1)
        } else {
            null
        }
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
        ownerId: Int
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
     * Generate virtual ref name for a change patch set.
     * Format: refs/changes/XX/CHANGEID/PATCHSET
     */
    fun generateVirtualRef(changeId: Int, patchSetId: Int): String {
        val lastTwoDigits = String.format("%02d", changeId % 100)
        return "refs/changes/$lastTwoDigits/$changeId/$patchSetId"
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
            ownerId = 1, // TODO: Get from security context
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
        
        if (change.status != ChangeStatus.NEW) {
            throw ConflictException("Cannot submit change with status ${change.status}")
        }
        
        val updatedChange = change.copy(
            status = ChangeStatus.MERGED,
            lastUpdatedOn = Instant.now()
        )
        
        val savedChange = changeRepository.save(updatedChange)
        return convertToChangeInfo(savedChange)
    }

    /**
     * Rebase a change.
     */
    @Transactional
    fun rebaseChange(changeId: String, input: RebaseInput): ChangeInfo {
        val change = findChangeByIdentifier(changeId)
        
        if (change.status != ChangeStatus.NEW) {
            throw ConflictException("Cannot rebase change with status ${change.status}")
        }
        
        // TODO: Implement actual rebase logic with Git operations
        val updatedChange = change.copy(lastUpdatedOn = Instant.now())
        
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
}
