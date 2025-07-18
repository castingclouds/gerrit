package ai.fluxuate.gerrit.repository

import ai.fluxuate.gerrit.model.PatchSetEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.QueryHints
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

/**
 * Repository for PatchSetEntity operations.
 * 
 * Provides structured queries for patch sets while maintaining compatibility
 * with the hybrid JSONB storage approach in ChangeEntity.
 */
@Repository
interface PatchSetRepository : JpaRepository<PatchSetEntity, Long> {

    /**
     * Find all patch sets for a change, ordered by patch set number.
     */
    fun findByChangeIdOrderByPatchSetNumberAsc(changeId: Long): List<PatchSetEntity>

    /**
     * Find the latest patch set for a change.
     */
    fun findTopByChangeIdOrderByPatchSetNumberDesc(changeId: Long): PatchSetEntity?

    /**
     * Find a specific patch set by change ID and patch set number.
     */
    fun findByChangeIdAndPatchSetNumber(changeId: Long, patchSetNumber: Int): PatchSetEntity?

    /**
     * Find patch sets by uploader.
     */
    fun findByUploaderIdOrderByCreatedOnDesc(uploaderId: Long, pageable: Pageable): Page<PatchSetEntity>

    /**
     * Find patch sets by commit ID.
     */
    fun findByCommitId(commitId: String): List<PatchSetEntity>

    /**
     * Find patch sets that belong to specific groups.
     */
    @Query(value = """
        SELECT * FROM patch_sets p 
        WHERE p.groups @> CAST(:groupFilter AS jsonb)
        ORDER BY p.created_on DESC
    """, nativeQuery = true)
    fun findByGroups(@Param("groupFilter") groupFilter: String, pageable: Pageable): Page<PatchSetEntity>

    /**
     * Find related patch sets based on groups.
     */
    @Query(value = """
        SELECT DISTINCT p.* FROM patch_sets p 
        WHERE p.change_id != :changeId 
        AND EXISTS (
            SELECT 1 FROM jsonb_array_elements_text(p.groups) AS group_elem
            WHERE group_elem IN (
                SELECT jsonb_array_elements_text(ref.groups) 
                FROM patch_sets ref 
                WHERE ref.change_id = :changeId
            )
        )
        ORDER BY p.created_on DESC
    """, nativeQuery = true)
    fun findRelatedByGroups(@Param("changeId") changeId: Long, pageable: Pageable): Page<PatchSetEntity>

    /**
     * Find patch sets created within a date range.
     */
    @Query("""
        SELECT p FROM PatchSetEntity p 
        WHERE p.createdOn >= :startDate AND p.createdOn <= :endDate
        ORDER BY p.createdOn DESC
    """)
    fun findByCreatedOnBetween(
        @Param("startDate") startDate: java.time.Instant,
        @Param("endDate") endDate: java.time.Instant,
        pageable: Pageable
    ): Page<PatchSetEntity>

    /**
     * Count patch sets for a change.
     */
    fun countByChangeId(changeId: Long): Long

    /**
     * Find patch sets with file modifications containing specific paths.
     */
    @Query(value = """
        SELECT * FROM patch_sets p 
        WHERE p.file_modifications @> CAST(:pathFilter AS jsonb)
        ORDER BY p.created_on DESC
    """, nativeQuery = true)
    fun findByFileModifications(@Param("pathFilter") pathFilter: String, pageable: Pageable): Page<PatchSetEntity>
}
