package ai.fluxuate.gerrit.repository

import ai.fluxuate.gerrit.model.CommentEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

/**
 * Repository for CommentEntity operations.
 * 
 * Provides structured queries for comments while maintaining compatibility
 * with the hybrid JSONB storage approach in ChangeEntity.
 */
@Repository
interface CommentRepository : JpaRepository<CommentEntity, Long> {

    /**
     * Find all comments for a change, ordered by written date.
     */
    fun findByChangeIdOrderByWrittenOnAsc(changeId: Long): List<CommentEntity>

    /**
     * Find all comments for a specific patch set.
     */
    fun findByPatchSetIdOrderByWrittenOnAsc(patchSetId: Long): List<CommentEntity>

    /**
     * Find comments by author.
     */
    fun findByAuthorIdOrderByWrittenOnDesc(authorId: Long, pageable: Pageable): Page<CommentEntity>

    /**
     * Find comments on a specific file.
     */
    fun findByChangeIdAndFilePathOrderByLineNumberAsc(changeId: Long, filePath: String): List<CommentEntity>

    /**
     * Find comments by UUID.
     */
    fun findByUuid(uuid: String): CommentEntity?

    /**
     * Find replies to a specific comment.
     */
    fun findByParentUuidOrderByWrittenOnAsc(parentUuid: String): List<CommentEntity>

    /**
     * Find unresolved comments for a change.
     */
    fun findByChangeIdAndUnresolvedTrueOrderByWrittenOnAsc(changeId: Long): List<CommentEntity>

    /**
     * Find file-level comments (no line number).
     */
    fun findByChangeIdAndLineNumberIsNullOrderByWrittenOnAsc(changeId: Long): List<CommentEntity>

    /**
     * Find line comments for a specific file and line range.
     */
    @Query("""
        SELECT c FROM CommentEntity c 
        WHERE c.changeId = :changeId 
        AND c.filePath = :filePath 
        AND c.lineNumber >= :startLine 
        AND c.lineNumber <= :endLine
        ORDER BY c.lineNumber ASC, c.writtenOn ASC
    """)
    fun findByFileAndLineRange(
        @Param("changeId") changeId: Long,
        @Param("filePath") filePath: String,
        @Param("startLine") startLine: Int,
        @Param("endLine") endLine: Int
    ): List<CommentEntity>

    /**
     * Find range comments that overlap with a specific line range.
     */
    @Query("""
        SELECT c FROM CommentEntity c 
        WHERE c.changeId = :changeId 
        AND c.filePath = :filePath 
        AND c.rangeStartLine IS NOT NULL 
        AND c.rangeEndLine IS NOT NULL
        AND (
            (c.rangeStartLine <= :endLine AND c.rangeEndLine >= :startLine)
        )
        ORDER BY c.rangeStartLine ASC, c.writtenOn ASC
    """)
    fun findRangeCommentsOverlapping(
        @Param("changeId") changeId: Long,
        @Param("filePath") filePath: String,
        @Param("startLine") startLine: Int,
        @Param("endLine") endLine: Int
    ): List<CommentEntity>

    /**
     * Count comments for a change.
     */
    fun countByChangeId(changeId: Long): Long

    /**
     * Count unresolved comments for a change.
     */
    fun countByChangeIdAndUnresolvedTrue(changeId: Long): Long

    /**
     * Find comments containing specific text.
     */
    @Query(value = """
        SELECT * FROM comments c 
        WHERE c.change_id = :changeId 
        AND LOWER(c.message) LIKE LOWER(CONCAT('%', :searchText, '%'))
        ORDER BY c.written_on DESC
    """, nativeQuery = true)
    fun findByMessageContaining(
        @Param("changeId") changeId: Long,
        @Param("searchText") searchText: String,
        pageable: Pageable
    ): Page<CommentEntity>

    /**
     * Find comments written within a date range.
     */
    @Query(value = """
        SELECT * FROM comments c 
        WHERE c.written_on >= :startDate AND c.written_on <= :endDate
        ORDER BY c.written_on DESC
    """, nativeQuery = true)
    fun findByWrittenOnBetween(
        @Param("startDate") startDate: java.time.Instant,
        @Param("endDate") endDate: java.time.Instant,
        pageable: Pageable
    ): Page<CommentEntity>

    /**
     * Find comments with specific metadata properties.
     */
    @Query(value = """
        SELECT * FROM comments c 
        WHERE c.metadata @> CAST(:metadataFilter AS jsonb)
        ORDER BY c.created DESC
    """, nativeQuery = true)
    fun findByMetadata(@Param("metadataFilter") metadataFilter: String, pageable: Pageable): Page<CommentEntity>

    /**
     * Search comments by text content (case-insensitive).
     */
    @Query(value = """
        SELECT * FROM comments c 
        WHERE LOWER(c.message) LIKE LOWER(CONCAT('%', :searchText, '%'))
        ORDER BY c.created DESC
    """, nativeQuery = true)
    fun searchByText(@Param("searchText") searchText: String, pageable: Pageable): Page<CommentEntity>
}
