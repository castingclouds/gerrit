package ai.fluxuate.gerrit.model

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

/**
 * Clean JPA entity for Change, avoiding embedded types that cause Hibernate conflicts.
 * Stores patch sets, comments, and approvals as JSONB for flexibility and performance.
 */
@Entity
@Table(
    name = "changes",
    indexes = [
        Index(name = "idx_changes_key", columnList = "change_key", unique = true),
        Index(name = "idx_changes_owner", columnList = "owner_id"),
        Index(name = "idx_changes_project", columnList = "project_name"),
        Index(name = "idx_changes_branch", columnList = "dest_branch"),
        Index(name = "idx_changes_status", columnList = "status"),
        Index(name = "idx_changes_created", columnList = "created_on"),
        Index(name = "idx_changes_updated", columnList = "last_updated_on")
    ]
)
data class ChangeEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Int = 0,

    @Column(name = "change_key", nullable = false, unique = true, length = 255)
    val changeKey: String,

    @Column(name = "owner_id", nullable = false)
    val ownerId: Int,

    @Column(name = "project_name", nullable = false, length = 255)
    val projectName: String,

    @Column(name = "dest_branch", nullable = false, length = 255)
    val destBranch: String,

    @Column(name = "subject", nullable = false, length = 1000)
    val subject: String,

    @Column(name = "topic", length = 255)
    val topic: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    val status: ChangeStatus = ChangeStatus.NEW,

    @Column(name = "current_patch_set_id", nullable = false)
    val currentPatchSetId: Int = 1,

    @Column(name = "created_on", nullable = false)
    val createdOn: Instant = Instant.now(),

    @Column(name = "last_updated_on", nullable = false)
    val lastUpdatedOn: Instant = Instant.now(),

    @Column(name = "revert_of")
    val revertOf: Int? = null,

    @Column(name = "cherry_pick_of")
    val cherryPickOf: Int? = null,

    /**
     * Patch sets stored as JSONB array.
     * Each patch set contains: id, commitId, uploader, createdOn, description, etc.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "patch_sets", columnDefinition = "jsonb")
    val patchSets: List<Map<String, Any>> = emptyList(),

    /**
     * Comments stored as JSONB array.
     * Each comment contains: id, author, message, line, file, patchSet, etc.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "comments", columnDefinition = "jsonb")
    val comments: List<Map<String, Any>> = emptyList(),

    /**
     * Approvals/votes stored as JSONB array.
     * Each approval contains: label, value, user, granted, etc.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "approvals", columnDefinition = "jsonb")
    val approvals: List<Map<String, Any>> = emptyList(),

    /**
     * Change metadata stored as JSONB.
     * Contains: hashtags, reviewers, watchers, etc.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    val metadata: Map<String, Any> = emptyMap()
) {
    /** Returns true if this change is new (open for review). */
    val isNew: Boolean
        get() = status == ChangeStatus.NEW

    /** Returns true if this change is merged. */
    val isMerged: Boolean
        get() = status == ChangeStatus.MERGED

    /** Returns true if this change is abandoned. */
    val isAbandoned: Boolean
        get() = status == ChangeStatus.ABANDONED

    /** Returns true if this change is open (not closed). */
    val isOpen: Boolean
        get() = status.isOpen

    /** Returns true if this change is closed (merged or abandoned). */
    val isClosed: Boolean
        get() = status.isClosed

    override fun toString(): String = "ChangeEntity(id=$id, changeKey='$changeKey', subject='$subject')"
}

enum class ChangeStatus(val code: Char, val isOpen: Boolean, val isClosed: Boolean) {
    NEW('n', isOpen = true, isClosed = false),
    MERGED('M', isOpen = false, isClosed = true),
    ABANDONED('A', isOpen = false, isClosed = true);

    companion object {
        fun forCode(code: Char): ChangeStatus? {
            return values().find { it.code == code }
        }
    }
}
