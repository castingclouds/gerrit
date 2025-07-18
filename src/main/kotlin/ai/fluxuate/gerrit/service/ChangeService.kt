package ai.fluxuate.gerrit.service

import ai.fluxuate.gerrit.model.ChangeEntity
import ai.fluxuate.gerrit.model.ChangeStatus
import ai.fluxuate.gerrit.repository.ChangeEntityRepository
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.regex.Pattern


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
}
