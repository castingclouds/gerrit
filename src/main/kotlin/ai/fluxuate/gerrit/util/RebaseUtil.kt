package ai.fluxuate.gerrit.util

import ai.fluxuate.gerrit.model.ChangeEntity
import ai.fluxuate.gerrit.model.PatchSetEntity
import ai.fluxuate.gerrit.model.ChangeStatus
import ai.fluxuate.gerrit.api.exception.ConflictException
import ai.fluxuate.gerrit.api.exception.BadRequestException
import ai.fluxuate.gerrit.api.exception.NotFoundException
import ai.fluxuate.gerrit.git.GitConfiguration
import org.springframework.stereotype.Component
import org.slf4j.LoggerFactory
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.RebaseCommand
import org.eclipse.jgit.api.RebaseResult
import org.eclipse.jgit.api.MergeCommand
import org.eclipse.jgit.api.MergeResult
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import java.io.File
import java.time.Instant

/**
 * Practical utility class for rebase and merge operations.
 * 
 * This utility provides simplified methods for:
 * - Validating rebase/merge preconditions
 * - Performing basic rebase logic
 * - Handling submit/merge operations
 * - Status validation
 */
@Component
class RebaseUtil(
    private val gitConfiguration: GitConfiguration
) {
    
    private val logger = LoggerFactory.getLogger(RebaseUtil::class.java)

    /**
     * Validates that a change can be rebased.
     * 
     * @param change The change to validate
     * @throws ConflictException if change cannot be rebased
     */
    fun validateRebasePreconditions(change: ChangeEntity) {
        if (change.status != ChangeStatus.NEW) {
            throw ConflictException("Cannot rebase change with status ${change.status}")
        }
        
        logger.debug("Rebase preconditions validated for change ${change.changeKey}")
    }

    /**
     * Validates that a change can be submitted.
     * 
     * @param change The change to validate
     * @throws ConflictException if change cannot be submitted
     */
    fun validateSubmitPreconditions(change: ChangeEntity) {
        if (change.status != ChangeStatus.NEW) {
            throw ConflictException("Cannot submit change with status ${change.status}")
        }
        
        logger.debug("Submit preconditions validated for change ${change.changeKey}")
    }

    /**
     * Validates that a revision exists in a change.
     * 
     * @param change The change to check
     * @param revisionId The revision ID to find
     * @return The patch set map if found
     * @throws NotFoundException if revision not found
     */
    fun validateRevisionExists(change: ChangeEntity, revisionId: String): Map<String, Any> {
        val patchSet = change.patchSets.find { (it["revisionId"] as? String) == revisionId }
        if (patchSet == null) {
            throw NotFoundException("Revision $revisionId not found in change ${change.changeKey}")
        }
        return patchSet
    }

    /**
     * Performs a rebase operation on a change.
     * 
     * Uses JGit to perform actual Git rebase operations.
     * 
     * @param change The change to rebase
     * @return Updated change entity
     */
    fun performRebase(change: ChangeEntity): ChangeEntity {
        validateRebasePreconditions(change)
        
        logger.info("Performing rebase for change ${change.changeKey}")
        
        return try {
            val gitDir = File(gitConfiguration.repositoryBasePath, "${change.projectName}.git")
            val repository = FileRepositoryBuilder()
                .setGitDir(gitDir)
                .readEnvironment()
                .findGitDir()
                .build()
            
            try {
                val git = Git(repository)
                
                // Get the latest patch set revision
                val latestPatchSet = change.patchSets.lastOrNull()
                if (latestPatchSet == null) {
                    logger.warn("No patch sets found for change ${change.changeKey}")
                    return change.copy(lastUpdatedOn = Instant.now())
                }
                
                val revisionId = latestPatchSet["revisionId"] as? String
                if (revisionId.isNullOrBlank()) {
                    logger.warn("No revision ID found in latest patch set for change ${change.changeKey}")
                    return change.copy(lastUpdatedOn = Instant.now())
                }
                
                // Perform rebase against the destination branch
                val targetBranch = change.destBranch
                
                logger.debug("Rebasing commit $revisionId onto $targetBranch")
                
                val rebaseResult = git.rebase()
                    .setUpstream(targetBranch)
                    .setOperation(RebaseCommand.Operation.BEGIN)
                    .call()
                
                git.close()
                
                when (rebaseResult.status) {
                    RebaseResult.Status.OK -> {
                        logger.info("Rebase completed successfully for change ${change.changeKey}")
                        change.copy(lastUpdatedOn = Instant.now())
                    }
                    RebaseResult.Status.CONFLICTS -> {
                        logger.warn("Rebase conflicts detected for change ${change.changeKey}")
                        throw ConflictException("Rebase failed due to conflicts")
                    }
                    else -> {
                        logger.warn("Rebase failed with status ${rebaseResult.status} for change ${change.changeKey}")
                        throw ConflictException("Rebase operation failed: ${rebaseResult.status}")
                    }
                }
            } finally {
                repository.close()
            }
        } catch (e: ConflictException) {
            throw e
        } catch (e: Exception) {
            logger.error("Error performing rebase for change ${change.changeKey}", e)
            throw ConflictException("Rebase operation failed: ${e.message}")
        }
    }

    /**
     * Performs a submit/merge operation on a change.
     * 
     * Uses JGit to perform actual Git merge operations.
     * 
     * @param change The change to submit
     * @return Updated change entity
     */
    fun performSubmit(change: ChangeEntity): ChangeEntity {
        validateSubmitPreconditions(change)
        
        logger.info("Performing submit for change ${change.changeKey}")
        
        return try {
            val gitDir = File(gitConfiguration.repositoryBasePath, "${change.projectName}.git")
            val repository = FileRepositoryBuilder()
                .setGitDir(gitDir)
                .readEnvironment()
                .findGitDir()
                .build()
            
            try {
                val git = Git(repository)
                
                // Get the latest patch set revision
                val latestPatchSet = change.patchSets.lastOrNull()
                if (latestPatchSet == null) {
                    logger.warn("No patch sets found for change ${change.changeKey}")
                    return change.copy(
                        status = ChangeStatus.MERGED,
                        lastUpdatedOn = Instant.now()
                    )
                }
                
                val revisionId = latestPatchSet["revisionId"] as? String
                if (revisionId.isNullOrBlank()) {
                    logger.warn("No revision ID found in latest patch set for change ${change.changeKey}")
                    return change.copy(
                        status = ChangeStatus.MERGED,
                        lastUpdatedOn = Instant.now()
                    )
                }
                
                val commitId = ObjectId.fromString(revisionId)
                
                // Perform merge into the destination branch
                val targetBranch = change.destBranch
                
                logger.debug("Merging commit $revisionId into $targetBranch")
                
                // Check out the target branch first
                git.checkout()
                    .setName(targetBranch)
                    .call()
                
                // Perform the merge
                val mergeResult = git.merge()
                    .include(commitId)
                    .setCommit(true)
                    .setMessage("Merge change ${change.changeKey}: ${change.subject}")
                    .call()
                
                git.close()
                
                when (mergeResult.mergeStatus) {
                    MergeResult.MergeStatus.MERGED -> {
                        logger.info("Merge completed successfully for change ${change.changeKey}")
                        change.copy(
                            status = ChangeStatus.MERGED,
                            lastUpdatedOn = Instant.now()
                        )
                    }
                    MergeResult.MergeStatus.CONFLICTING -> {
                        logger.warn("Merge conflicts detected for change ${change.changeKey}")
                        throw ConflictException("Submit failed due to merge conflicts")
                    }
                    MergeResult.MergeStatus.FAILED -> {
                        logger.warn("Merge failed for change ${change.changeKey}")
                        throw ConflictException("Submit operation failed: merge failed")
                    }
                    else -> {
                        logger.warn("Merge completed with status ${mergeResult.mergeStatus} for change ${change.changeKey}")
                        change.copy(
                            status = ChangeStatus.MERGED,
                            lastUpdatedOn = Instant.now()
                        )
                    }
                }
            } finally {
                repository.close()
            }
        } catch (e: ConflictException) {
            throw e
        } catch (e: Exception) {
            logger.error("Error performing submit for change ${change.changeKey}", e)
            throw ConflictException("Submit operation failed: ${e.message}")
        }
    }

    /**
     * Performs a rebase operation on a specific revision.
     * 
     * Uses JGit to perform actual Git rebase operations on the specified revision.
     * 
     * @param change The change containing the revision
     * @param revisionId The revision to rebase
     * @return Updated change entity
     */
    fun performRevisionRebase(change: ChangeEntity, revisionId: String): ChangeEntity {
        validateRebasePreconditions(change)
        validateRevisionExists(change, revisionId)
        
        logger.info("Performing revision rebase for change ${change.changeKey}, revision $revisionId")
        
        return try {
            val gitDir = File(gitConfiguration.repositoryBasePath, "${change.projectName}.git")
            val repository = FileRepositoryBuilder()
                .setGitDir(gitDir)
                .readEnvironment()
                .findGitDir()
                .build()
            
            try {
                val git = Git(repository)
                
                val commitId = ObjectId.fromString(revisionId)
                
                // Perform rebase against the destination branch
                val targetBranch = change.destBranch
                
                logger.debug("Rebasing specific commit $revisionId onto $targetBranch")
                
                // Check out the specific revision first
                git.checkout()
                    .setName(revisionId)
                    .call()
                
                val rebaseResult = git.rebase()
                    .setUpstream(targetBranch)
                    .setOperation(RebaseCommand.Operation.BEGIN)
                    .call()
                
                git.close()
                
                when (rebaseResult.status) {
                    RebaseResult.Status.OK -> {
                        logger.info("Revision rebase completed successfully for change ${change.changeKey}, revision $revisionId")
                        change.copy(lastUpdatedOn = Instant.now())
                    }
                    RebaseResult.Status.CONFLICTS -> {
                        logger.warn("Revision rebase conflicts detected for change ${change.changeKey}, revision $revisionId")
                        throw ConflictException("Revision rebase failed due to conflicts")
                    }
                    else -> {
                        logger.warn("Revision rebase failed with status ${rebaseResult.status} for change ${change.changeKey}, revision $revisionId")
                        throw ConflictException("Revision rebase operation failed: ${rebaseResult.status}")
                    }
                }
            } finally {
                repository.close()
            }
        } catch (e: ConflictException) {
            throw e
        } catch (e: Exception) {
            logger.error("Error performing revision rebase for change ${change.changeKey}, revision $revisionId", e)
            throw ConflictException("Revision rebase operation failed: ${e.message}")
        }
    }

    /**
     * Performs a submit operation on a specific revision.
     * 
     * Uses JGit to perform actual Git merge operations on the specified revision.
     * 
     * @param change The change containing the revision
     * @param revisionId The revision to submit
     * @return Updated change entity
     */
    fun performRevisionSubmit(change: ChangeEntity, revisionId: String): ChangeEntity {
        validateSubmitPreconditions(change)
        validateRevisionExists(change, revisionId)
        
        logger.info("Performing revision submit for change ${change.changeKey}, revision $revisionId")
        
        return try {
            val gitDir = File(gitConfiguration.repositoryBasePath, "${change.projectName}.git")
            val repository = FileRepositoryBuilder()
                .setGitDir(gitDir)
                .readEnvironment()
                .findGitDir()
                .build()
            
            try {
                val git = Git(repository)
                
                val commitId = ObjectId.fromString(revisionId)
                
                // Perform merge into the destination branch
                val targetBranch = change.destBranch
                
                logger.debug("Merging specific commit $revisionId into $targetBranch")
                
                // Check out the target branch first
                git.checkout()
                    .setName(targetBranch)
                    .call()
                
                // Perform the merge
                val mergeResult = git.merge()
                    .include(commitId)
                    .setCommit(true)
                    .setMessage("Merge change ${change.changeKey} revision $revisionId: ${change.subject}")
                    .call()
                
                git.close()
                
                when (mergeResult.mergeStatus) {
                    MergeResult.MergeStatus.MERGED -> {
                        logger.info("Revision merge completed successfully for change ${change.changeKey}, revision $revisionId")
                        change.copy(
                            status = ChangeStatus.MERGED,
                            lastUpdatedOn = Instant.now()
                        )
                    }
                    MergeResult.MergeStatus.CONFLICTING -> {
                        logger.warn("Revision merge conflicts detected for change ${change.changeKey}, revision $revisionId")
                        throw ConflictException("Revision submit failed due to merge conflicts")
                    }
                    MergeResult.MergeStatus.FAILED -> {
                        logger.warn("Revision merge failed for change ${change.changeKey}, revision $revisionId")
                        throw ConflictException("Revision submit operation failed: merge failed")
                    }
                    else -> {
                        logger.warn("Revision merge completed with status ${mergeResult.mergeStatus} for change ${change.changeKey}, revision $revisionId")
                        change.copy(
                            status = ChangeStatus.MERGED,
                            lastUpdatedOn = Instant.now()
                        )
                    }
                }
            } finally {
                repository.close()
            }
        } catch (e: ConflictException) {
            throw e
        } catch (e: Exception) {
            logger.error("Error performing revision submit for change ${change.changeKey}, revision $revisionId", e)
            throw ConflictException("Revision submit operation failed: ${e.message}")
        }
    }
}
