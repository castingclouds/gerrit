package ai.fluxuate.gerrit.util

import ai.fluxuate.gerrit.model.ChangeEntity
import ai.fluxuate.gerrit.model.PatchSetEntity
import ai.fluxuate.gerrit.api.dto.*
import ai.fluxuate.gerrit.api.exception.NotFoundException
import ai.fluxuate.gerrit.config.ServerConfiguration
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Utility class for handling patch set and revision operations.
 * Contains logic for finding, converting, and managing patch sets and revisions.
 */
@Component
class PatchUtil(
    private val serverConfiguration: ServerConfiguration
) {

    /**
     * Find a patch set by revision ID.
     * 
     * @param change The change entity
     * @param revisionId The revision ID to find (can be "current", patch set number, or commit/revision hash)
     * @return The patch set entity if found, null otherwise
     */
    fun findPatchSetByRevisionId(change: ChangeEntity, revisionId: String): PatchSetEntity? {
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
                    val commitId = patchSet.commitId
                    val revision = patchSet.commitId // Note: revision and commitId are the same field
                    (commitId != null && commitId.startsWith(revisionId)) ||
                    (revision != null && revision.startsWith(revisionId))
                }
            }
        }
    }

    /**
     * Convert patch set entity to RevisionInfo DTO.
     * 
     * @param patchSet The patch set entity to convert
     * @param change The change entity containing the patch set
     * @return RevisionInfo DTO
     */
    fun convertPatchSetToRevisionInfo(patchSet: PatchSetEntity, change: ChangeEntity): RevisionInfo {
        // Use uploader info from patch set entity
        val createdOn = patchSet.createdOn.toString()
        
        return RevisionInfo(
            kind = "REWORK", // TODO: Determine actual change kind
            _number = change.patchSets.indexOf(patchSet) + 1,
            created = Instant.parse(createdOn),
            uploader = AccountInfo(
                _account_id = patchSet.uploaderId.toLong(),
                name = null, // TODO: Implement user lookup for uploader name
                email = null, // TODO: Implement user lookup for uploader email
                username = null // TODO: Implement user lookup for uploader username
            ),
            ref = "refs/changes/${change.id.toString().takeLast(2).padStart(2, '0')}/${change.id}/${change.patchSets.indexOf(patchSet) + 1}",
            fetch = mapOf(
                "http" to FetchInfo(
                    url = serverConfiguration.getProjectUrl(change.projectName),
                    ref = "refs/changes/${change.id.toString().takeLast(2).padStart(2, '0')}/${change.id}/${change.patchSets.indexOf(patchSet) + 1}"
                )
            ),
            commit = convertPatchSetToCommitInfo(patchSet, change),
            description = null // TODO: Add description field to PatchSetEntity if needed
        )
    }

    /**
     * Convert patch set entity to CommitInfo DTO.
     * 
     * @param patchSet The patch set entity to convert
     * @param change The change entity containing the patch set
     * @return CommitInfo DTO
     */
    fun convertPatchSetToCommitInfo(patchSet: PatchSetEntity, change: ChangeEntity): CommitInfo {
        val commitId = patchSet.commitId ?: "unknown"
        // Use uploader info from patch set entity
        val subject = change.subject // TODO: Add subject field to PatchSetEntity if needed
        val message = subject // TODO: Add message field to PatchSetEntity if needed
        val createdOn = patchSet.createdOn.toString()
        
        return CommitInfo(
            commit = commitId,
            parents = emptyList(), // TODO: Extract parent commits
            author = GitPersonInfo(
                name = "User-${patchSet.uploaderId}", // TODO: Implement user lookup for author name
                email = "user${patchSet.uploaderId}@example.com", // TODO: Implement user lookup for author email
                date = Instant.parse(createdOn),
                tz = 0 // UTC timezone offset
            ),
            committer = GitPersonInfo(
                name = "User-${patchSet.uploaderId}", // TODO: Implement user lookup for committer name
                email = "user${patchSet.uploaderId}@example.com", // TODO: Implement user lookup for committer email
                date = Instant.parse(createdOn),
                tz = 0 // UTC timezone offset
            ),
            subject = subject,
            message = message
        )
    }

    /**
     * Convert all patch sets in a change to a map of revision ID to RevisionInfo.
     * 
     * @param change The change entity containing patch sets
     * @return Map of revision ID to RevisionInfo DTO
     */
    fun convertPatchSetsToRevisionInfoMap(change: ChangeEntity): Map<String, RevisionInfo> {
        val patchSets = change.patchSets
        
        return patchSets.mapIndexed { index, patchSetEntity ->
            val revisionId = patchSetEntity.commitId ?: "revision-${index + 1}"
            val revisionInfo = convertPatchSetToRevisionInfo(patchSetEntity, change)
            revisionId to revisionInfo
        }.toMap()
    }

    /**
     * Get the current (latest) patch set from a change.
     * 
     * @param change The change entity
     * @return The current patch set map, or null if no patch sets exist
     */
    fun getCurrentPatchSet(change: ChangeEntity): PatchSetEntity? {
        return change.patchSets.lastOrNull()
    }

    /**
     * Get patch set by number (1-indexed).
     * 
     * @param change The change entity
     * @param patchSetNumber The patch set number (1-indexed)
     * @return The patch set map if found, null otherwise
     */
    fun getPatchSetByNumber(change: ChangeEntity, patchSetNumber: Int): PatchSetEntity? {
        return change.patchSets.getOrNull(patchSetNumber - 1)
    }

    /**
     * Get the patch set number for a given patch set entity.
     * 
     * @param change The change entity containing the patch set
     * @param patchSet The patch set entity
     * @return The 1-indexed patch set number
     */
    fun getPatchSetNumber(change: ChangeEntity, patchSet: PatchSetEntity): Int {
        return change.patchSets.indexOf(patchSet) + 1
    }

    /**
     * Generate the virtual ref name for a change patch set.
     * Format: refs/changes/XX/CHANGEID/PATCHSET
     * 
     * @param changeId The change ID
     * @param patchSetNumber The patch set number
     * @return The virtual ref name
     */
    fun generateVirtualRef(changeId: Int, patchSetNumber: Int): String {
        val paddedChangeId = changeId.toString().takeLast(2).padStart(2, '0')
        return "refs/changes/$paddedChangeId/$changeId/$patchSetNumber"
    }

    /**
     * Validate that a revision exists and return the patch set.
     * 
     * @param change The change entity
     * @param revisionId The revision ID to validate
     * @return The patch set entity if found
     * @throws NotFoundException if revision not found
     */
    fun validateRevisionExists(change: ChangeEntity, revisionId: String): PatchSetEntity {
        return findPatchSetByRevisionId(change, revisionId)
            ?: throw NotFoundException("Revision $revisionId not found in change ${change.id}")
    }

    /**
     * Get revision ID from a patch set map.
     * 
     * @param patchSet The patch set map
     * @param fallbackIndex The fallback index to use if no revision ID is found
     * @return The revision ID
     */
    fun getRevisionId(patchSet: Map<String, Any>, fallbackIndex: Int): String {
        return patchSet["revision"] as? String ?: "revision-${fallbackIndex + 1}"
    }

    /**
     * Get commit ID from a patch set map.
     * 
     * @param patchSet The patch set map
     * @return The commit ID or "unknown" if not found
     */
    fun getCommitId(patchSet: Map<String, Any>): String {
        return patchSet["commitId"] as? String ?: "unknown"
    }
}
