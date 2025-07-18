package ai.fluxuate.gerrit.model

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

/**
 * JPA Entity representing an Approval/Vote on a code review.
 * 
 * This entity works alongside the JSONB storage in ChangeEntity:
 * - ChangeEntity stores approvals as JSONB for performance
 * - ApprovalEntity provides structured queries and relationships
 */
@Entity
@Table(name = "approvals")
data class ApprovalEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "change_id", nullable = false)
    val changeId: Long,

    @Column(name = "patch_set_id", nullable = false)
    val patchSetId: Long,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "label", nullable = false)
    val label: String,

    @Column(name = "value", nullable = false)
    val value: Short,

    @Column(name = "granted", nullable = false)
    val granted: Instant,

    @Column(name = "tag")
    val tag: String? = null,

    @Column(name = "real_user_id")
    val realUserId: Long? = null,

    @Column(name = "post_submit")
    val postSubmit: Boolean = false,

    @Column(name = "copied")
    val copied: Boolean = false,

    /**
     * Additional metadata stored as JSONB.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    val metadata: Map<String, Any> = emptyMap()
) {
    /**
     * Check if this is a positive vote.
     */
    val isPositive: Boolean
        get() = value > 0

    /**
     * Check if this is a negative vote.
     */
    val isNegative: Boolean
        get() = value < 0

    /**
     * Check if this is a neutral vote.
     */
    val isNeutral: Boolean
        get() = value == 0.toShort()

    /**
     * Check if this approval was granted by a different user (impersonation).
     */
    val isImpersonated: Boolean
        get() = realUserId != null && realUserId != userId

    /**
     * Get the effective user ID (real user if impersonated, otherwise regular user).
     */
    val effectiveUserId: Long
        get() = realUserId ?: userId
}
