package ai.fluxuate.gerrit.util

import ai.fluxuate.gerrit.git.GitConfiguration
import ai.fluxuate.gerrit.model.DiffEntity
import ai.fluxuate.gerrit.model.PatchSetEntity
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.diff.RenameDetector
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.eclipse.jgit.treewalk.EmptyTreeIterator
import org.eclipse.jgit.util.io.DisabledOutputStream
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

/**
 * Utility class for calculating file modifications between git commits.
 * Generates DiffEntity objects with unified diff content for storage in the database.
 */
@Component
class FileModificationsUtil(
    private val gitConfiguration: GitConfiguration
) {
    
    private val logger = LoggerFactory.getLogger(FileModificationsUtil::class.java)
    
    /**
     * Calculate file modifications for a new patch set compared to its parent.
     * 
     * @param patchSet The patch set entity these diffs belong to
     * @param projectName The name of the project
     * @param newCommitId The commit ID of the new patch set
     * @param baseCommitId The commit ID to compare against (null for initial patch set)
     * @return List of DiffEntity objects
     */
    fun calculateFileModifications(
        patchSet: PatchSetEntity,
        repository: Repository,
        newCommitId: ObjectId,
        baseCommitId: ObjectId? = null
    ): List<DiffEntity> {
        logger.info("calculateFileModifications called for newCommit: ${newCommitId.name}, baseCommit: ${baseCommitId?.name}")
        return try {
            calculateFileModificationsInternal(patchSet, repository, newCommitId, baseCommitId)
        } catch (e: Exception) {
            logger.error("Failed to calculate file modifications", e)
            emptyList()
        }
    }
    
    /**
     * Internal method to calculate file modifications using JGit.
     * 
     * @param patchSet The patch set entity these diffs belong to
     * @param repository The git repository
     * @param newCommitId The new commit ID
     * @param baseCommitId The base commit ID (null for empty tree comparison)
     * @return List of DiffEntity objects
     */
    private fun calculateFileModificationsInternal(
        patchSet: PatchSetEntity,
        repository: Repository,
        newCommitId: ObjectId,
        baseCommitId: ObjectId?
    ): List<DiffEntity> {
        val reader = repository.newObjectReader()
        val revWalk = RevWalk(repository)
        val diffFormatter = DiffFormatter(DisabledOutputStream.INSTANCE)
        
        return try {
            diffFormatter.setRepository(repository)
            diffFormatter.setDetectRenames(true)
            
            val newCommit = revWalk.parseCommit(newCommitId)
            val newTree = newCommit.tree
            
            // Create tree iterators for diff comparison
            val oldTreeIterator = if (baseCommitId != null) {
                val baseCommit = revWalk.parseCommit(baseCommitId)
                CanonicalTreeParser().apply {
                    reset(reader, baseCommit.tree)
                }
            } else {
                EmptyTreeIterator()
            }
            
            val newTreeIterator = CanonicalTreeParser().apply {
                reset(reader, newTree)
            }
            
            // Calculate diff entries
            val diffEntries = diffFormatter.scan(oldTreeIterator, newTreeIterator)
            
            // Enable rename detection
            val renameDetector = RenameDetector(repository)
            renameDetector.addAll(diffEntries)
            val diffEntriesWithRenames = renameDetector.compute()
            
            // Convert diff entries to DiffEntity objects
            val diffEntities = diffEntriesWithRenames.map { entry ->
                convertDiffEntryToDiffEntity(patchSet, repository, entry)
            }
            
            logger.info("Generated ${diffEntities.size} diff entities for ${diffEntriesWithRenames.size} diff entries")
            diffEntities
            
        } catch (e: IOException) {
            logger.error("Failed to calculate diff between commits", e)
            emptyList()
        } finally {
            reader.close()
            revWalk.close()
            diffFormatter.close()
        }
    }
    
    /**
     * Convert a JGit DiffEntry to a DiffEntity with unified diff content.
     * 
     * @param patchSet The patch set entity this diff belongs to
     * @param repository The git repository
     * @param entry The JGit DiffEntry
     * @return DiffEntity object
     */
    private fun convertDiffEntryToDiffEntity(
        patchSet: PatchSetEntity,
        repository: Repository,
        entry: DiffEntry
    ): DiffEntity {
        return try {
            // Generate unified diff using JGit's DiffFormatter
            val diffText = generateUnifiedDiff(repository, entry)
            
            val changeType = when (entry.changeType) {
                DiffEntry.ChangeType.ADD -> "ADDED"
                DiffEntry.ChangeType.DELETE -> "DELETED"
                DiffEntry.ChangeType.MODIFY -> "MODIFIED"
                DiffEntry.ChangeType.RENAME -> "RENAMED"
                DiffEntry.ChangeType.COPY -> "COPIED"
            }
            val filePath = entry.newPath.takeIf { it != DiffEntry.DEV_NULL } ?: entry.oldPath
            
            // Create JSONB data structure
            val diffDataJson = """
                {
                    "changeType": "$changeType",
                    "filePath": "${filePath.replace("\\", "\\\\").replace("\"", "\\\"")}",
                    "diff": "${diffText.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")}"
                }
            """.trimIndent()
            
            // Create DiffEntity with searchable fields and diff content
            DiffEntity(
                patchSet = patchSet,
                changeType = changeType,
                filePath = filePath,
                diffData = diffDataJson
            )
            
        } catch (e: Exception) {
            logger.warn("Failed to generate diff for ${entry.newPath ?: entry.oldPath}", e)
            val filePath = entry.newPath ?: entry.oldPath ?: "unknown"
            val diffDataJson = """
                {
                    "changeType": "UNKNOWN",
                    "filePath": "${filePath.replace("\\", "\\\\").replace("\"", "\\\"")}",
                    "diff": ""
                }
            """.trimIndent()
            
            DiffEntity(
                patchSet = patchSet,
                changeType = "UNKNOWN",
                filePath = filePath,
                diffData = diffDataJson
            )
        }
    }
    
    /**
     * Generate unified diff text for a DiffEntry using JGit's DiffFormatter.
     * 
     * @param repository The git repository
     * @param entry The JGit DiffEntry
     * @return Unified diff text
     */
    private fun generateUnifiedDiff(repository: Repository, entry: DiffEntry): String {
        return try {
            val outputStream = ByteArrayOutputStream()
            val diffFormatter = DiffFormatter(outputStream)
            
            diffFormatter.setRepository(repository)
            diffFormatter.format(entry)
            diffFormatter.close()
            
            outputStream.toString("UTF-8")
        } catch (e: Exception) {
            logger.warn("Failed to format diff for ${entry.newPath ?: entry.oldPath}", e)
            ""
        }
    }
}
