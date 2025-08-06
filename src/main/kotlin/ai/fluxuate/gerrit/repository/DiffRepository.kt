package ai.fluxuate.gerrit.repository

import ai.fluxuate.gerrit.model.DiffEntity
import ai.fluxuate.gerrit.model.PatchSetEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

/**
 * Repository for managing DiffEntity operations.
 * 
 * Provides efficient querying of file diffs with proper indexing
 * for better performance compared to JSONB array queries.
 */
@Repository
interface DiffRepository : JpaRepository<DiffEntity, Long> {
    
    /**
     * Find all diffs for a specific patch set.
     */
    fun findByPatchSetOrderByFilePath(patchSet: PatchSetEntity): List<DiffEntity>
    
    /**
     * Find all diffs for a specific patch set by ID.
     */
    fun findByPatchSetIdOrderByFilePath(patchSetId: Long): List<DiffEntity>
    
    /**
     * Find diffs by change type for a specific patch set.
     */
    fun findByPatchSetAndChangeTypeOrderByFilePath(
        patchSet: PatchSetEntity, 
        changeType: String
    ): List<DiffEntity>
    
    /**
     * Find diffs for a specific file path across patch sets.
     */
    fun findByFilePathOrderByPatchSetIdDesc(filePath: String): List<DiffEntity>
    
    /**
     * Find diffs for a specific file within a specific patch set.
     */
    fun findByPatchSetAndFilePath(patchSet: PatchSetEntity, filePath: String): DiffEntity?
    
    /**
     * Find all files that were added in a patch set.
     */
    fun findByPatchSetAndChangeType(patchSet: PatchSetEntity, changeType: String): List<DiffEntity>
    
    /**
     * Count total number of file changes in a patch set.
     */
    fun countByPatchSet(patchSet: PatchSetEntity): Long
    
    /**
     * Count changes by type for a patch set.
     */
    fun countByPatchSetAndChangeType(patchSet: PatchSetEntity, changeType: String): Long
    
    /**
     * Find diffs matching file path patterns (for file type queries).
     */
    @Query("SELECT d FROM DiffEntity d WHERE d.patchSet = :patchSet AND d.filePath LIKE :pattern ORDER BY d.filePath")
    fun findByPatchSetAndFilePathPattern(
        @Param("patchSet") patchSet: PatchSetEntity,
        @Param("pattern") pattern: String
    ): List<DiffEntity>
    
    /**
     * Get diff statistics for a patch set.
     */
    @Query("""
        SELECT d.changeType as changeType, COUNT(*) as count 
        FROM DiffEntity d 
        WHERE d.patchSet = :patchSet 
        GROUP BY d.changeType
    """)
    fun getDiffStatistics(@Param("patchSet") patchSet: PatchSetEntity): List<Map<String, Any>>
}
