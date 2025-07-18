package ai.fluxuate.gerrit.repository

import ai.fluxuate.gerrit.model.ApprovalEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

/**
 * Repository for ApprovalEntity operations.
 * 
 * Provides structured queries for approvals/votes while maintaining compatibility
 * with the hybrid JSONB storage approach in ChangeEntity.
 */
@Repository
interface ApprovalRepository : JpaRepository<ApprovalEntity, Long> {

    /**
     * Find all approvals for a change, ordered by granted date.
     */
    fun findByChangeIdOrderByGrantedAsc(changeId: Long): List<ApprovalEntity>

    /**
     * Find all approvals for a specific patch set.
     */
    fun findByPatchSetIdOrderByGrantedAsc(patchSetId: Long): List<ApprovalEntity>

    /**
     * Find approvals by user.
     */
    fun findByUserIdOrderByGrantedDesc(userId: Long, pageable: Pageable): Page<ApprovalEntity>

    /**
     * Find approvals for a specific label on a change.
     */
    fun findByChangeIdAndLabelOrderByGrantedDesc(changeId: Long, label: String): List<ApprovalEntity>

    /**
     * Find the latest approval for a specific user and label on a change.
     */
    fun findTopByChangeIdAndUserIdAndLabelOrderByGrantedDesc(
        changeId: Long, 
        userId: Long, 
        label: String
    ): ApprovalEntity?

    /**
     * Find positive approvals for a change.
     */
    @Query("""
        SELECT a FROM ApprovalEntity a 
        WHERE a.changeId = :changeId AND a.value > 0
        ORDER BY a.granted DESC
    """)
    fun findPositiveApprovals(@Param("changeId") changeId: Long): List<ApprovalEntity>

    /**
     * Find negative approvals for a change.
     */
    @Query("""
        SELECT a FROM ApprovalEntity a 
        WHERE a.changeId = :changeId AND a.value < 0
        ORDER BY a.granted DESC
    """)
    fun findNegativeApprovals(@Param("changeId") changeId: Long): List<ApprovalEntity>

    /**
     * Find approvals by value range.
     */
    @Query("""
        SELECT a FROM ApprovalEntity a 
        WHERE a.changeId = :changeId 
        AND a.value >= :minValue 
        AND a.value <= :maxValue
        ORDER BY a.granted DESC
    """)
    fun findByValueRange(
        @Param("changeId") changeId: Long,
        @Param("minValue") minValue: Short,
        @Param("maxValue") maxValue: Short
    ): List<ApprovalEntity>

    /**
     * Find copied approvals (from previous patch sets).
     */
    fun findByChangeIdAndCopiedTrueOrderByGrantedDesc(changeId: Long): List<ApprovalEntity>

    /**
     * Find post-submit approvals.
     */
    fun findByChangeIdAndPostSubmitTrueOrderByGrantedDesc(changeId: Long): List<ApprovalEntity>

    /**
     * Find impersonated approvals (where real user differs from user).
     */
    @Query("""
        SELECT a FROM ApprovalEntity a 
        WHERE a.changeId = :changeId 
        AND a.realUserId IS NOT NULL 
        AND a.realUserId != a.userId
        ORDER BY a.granted DESC
    """)
    fun findImpersonatedApprovals(@Param("changeId") changeId: Long): List<ApprovalEntity>

    /**
     * Count approvals for a change.
     */
    fun countByChangeId(changeId: Long): Long

    /**
     * Count approvals by label for a change.
     */
    fun countByChangeIdAndLabel(changeId: Long, label: String): Long

    /**
     * Find latest approval for each user on a change.
     */
    @Query("""
        SELECT a FROM ApprovalEntity a 
        WHERE a.changeId = :changeId 
        AND a.granted = (
            SELECT MAX(a2.granted) 
            FROM ApprovalEntity a2 
            WHERE a2.changeId = a.changeId 
            AND a2.userId = a.userId 
            AND a2.label = a.label
        )
        ORDER BY a.granted DESC
    """)
    fun findLatestApprovalsByUser(@Param("changeId") changeId: Long): List<ApprovalEntity>

    /**
     * Find approvals granted within a date range.
     */
    @Query(value = """
        SELECT * FROM approvals a 
        WHERE a.granted >= :startDate AND a.granted <= :endDate
        ORDER BY a.granted DESC
    """, nativeQuery = true)
    fun findByGrantedBetween(
        @Param("startDate") startDate: java.time.Instant,
        @Param("endDate") endDate: java.time.Instant,
        pageable: Pageable
    ): Page<ApprovalEntity>

    /**
     * Find approvals with specific metadata properties.
     */
    @Query(value = """
        SELECT * FROM approvals a 
        WHERE a.metadata @> CAST(:metadataFilter AS jsonb)
        ORDER BY a.granted DESC
    """, nativeQuery = true)
    fun findByMetadata(@Param("metadataFilter") metadataFilter: String, pageable: Pageable): Page<ApprovalEntity>

    /**
     * Find approvals with specific tags.
     */
    fun findByTagOrderByGrantedDesc(tag: String, pageable: Pageable): Page<ApprovalEntity>

    /**
     * Check if a change has required approvals for a specific label.
     */
    @Query("""
        SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END
        FROM ApprovalEntity a 
        WHERE a.changeId = :changeId 
        AND a.label = :label 
        AND a.value >= :minValue
    """)
    fun hasRequiredApprovals(
        @Param("changeId") changeId: Long,
        @Param("label") label: String,
        @Param("minValue") minValue: Short
    ): Boolean
}
