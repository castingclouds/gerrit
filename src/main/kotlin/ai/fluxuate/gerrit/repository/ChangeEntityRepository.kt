package ai.fluxuate.gerrit.repository

import ai.fluxuate.gerrit.model.ChangeEntity
import ai.fluxuate.gerrit.model.ChangeStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant

/**
 * Clean JPA repository for ChangeEntity, avoiding complex embedded types.
 * Uses JSONB queries for complex data stored in patch_sets, comments, and approvals.
 */
@Repository
interface ChangeEntityRepository : JpaRepository<ChangeEntity, Int> {

    /**
     * Find a change by its globally unique key.
     */
    fun findByChangeKey(changeKey: String): ChangeEntity?

    /**
     * Find changes by owner.
     */
    fun findByOwnerId(ownerId: Int, pageable: Pageable): Page<ChangeEntity>

    /**
     * Find changes by project.
     */
    fun findByProjectName(projectName: String, pageable: Pageable): Page<ChangeEntity>

    /**
     * Find changes by status.
     */
    fun findByStatus(status: ChangeStatus, pageable: Pageable): Page<ChangeEntity>

    /**
     * Find changes by project and status.
     */
    fun findByProjectNameAndStatus(
        projectName: String,
        status: ChangeStatus,
        pageable: Pageable
    ): Page<ChangeEntity>

    /**
     * Find changes by topic.
     */
    fun findByTopic(topic: String, pageable: Pageable): Page<ChangeEntity>

    /**
     * Find changes that revert another change.
     */
    fun findByRevertOf(revertOf: Int): List<ChangeEntity>

    /**
     * Find changes created after a specific date.
     */
    fun findByCreatedOnAfter(createdOn: Instant, pageable: Pageable): Page<ChangeEntity>

    /**
     * Find changes updated after a specific date.
     */
    fun findByLastUpdatedOnAfter(lastUpdatedOn: Instant, pageable: Pageable): Page<ChangeEntity>

    /**
     * Find changes with patch sets by a specific uploader using JSONB query.
     */
    @Query(value = """
        SELECT * FROM changes c 
        WHERE EXISTS (
            SELECT 1 FROM jsonb_array_elements(c.patch_sets) AS ps 
            WHERE ps->>'uploader_id' = CAST(?1 AS TEXT)
        )
    """, nativeQuery = true)
    fun findChangesWithPatchSetsByUploader(
        uploaderId: Int,
        pageable: Pageable
    ): Page<ChangeEntity>

    /**
     * Find changes with comments by a specific author using JSONB query.
     */
    @Query(value = """
        SELECT * FROM changes c 
        WHERE EXISTS (
            SELECT 1 FROM jsonb_array_elements(c.comments) AS comment 
            WHERE comment->>'author_id' = CAST(?1 AS TEXT)
        )
    """, nativeQuery = true)
    fun findChangesWithCommentsByAuthor(
        authorId: Int,
        pageable: Pageable
    ): Page<ChangeEntity>

    /**
     * Find changes with specific approval using JSONB query.
     */
    @Query(value = """
        SELECT * FROM changes c 
        WHERE EXISTS (
            SELECT 1 FROM jsonb_array_elements(c.approvals) AS approval 
            WHERE approval->>'label' = ?1 AND approval->>'value' = CAST(?2 AS TEXT)
        )
    """, nativeQuery = true)
    fun findChangesWithApproval(
        label: String,
        value: Int,
        pageable: Pageable
    ): Page<ChangeEntity>

    /**
     * Find changes ready for submit (Code-Review +2 and Verified +1).
     */
    @Query("""
        SELECT c FROM ChangeEntity c 
        WHERE c.status = 'NEW'
        AND function('jsonb_path_exists', c.approvals, '$[*] ? (@.label == "Code-Review" && @.value == 2)') = true
        AND function('jsonb_path_exists', c.approvals, '$[*] ? (@.label == "Verified" && @.value == 1)') = true
    """)
    fun findChangesReadyForSubmit(pageable: Pageable): Page<ChangeEntity>

    /**
     * Find changes by subject containing text (case-insensitive).
     */
    fun findBySubjectContainingIgnoreCase(subject: String, pageable: Pageable): Page<ChangeEntity>

    /**
     * Count changes by owner.
     */
    fun countByOwnerId(ownerId: Int): Long

    /**
     * Count changes by project.
     */
    fun countByProjectName(projectName: String): Long

    /**
     * Check if change key exists.
     */
    fun existsByChangeKey(changeKey: String): Boolean
}
