package ai.fluxuate.gerrit.model

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

/**
 * JPA Entity representing a Comment on a code review.
 * 
 * This entity works alongside the JSONB storage in ChangeEntity:
 * - ChangeEntity stores comments as JSONB for performance
 * - CommentEntity provides structured queries and relationships
 */
@Entity
@Table(name = "comments")
data class CommentEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "change_id", nullable = false)
    val changeId: Long,

    @Column(name = "patch_set_id", nullable = false)
    val patchSetId: Long,

    @Column(name = "author_id", nullable = false)
    val authorId: Long,

    @Column(name = "message", columnDefinition = "text", nullable = false)
    val message: String,

    @Column(name = "written_on", nullable = false)
    val writtenOn: Instant,

    @Column(name = "file_path")
    val filePath: String? = null,

    @Column(name = "line_number")
    val lineNumber: Int? = null,

    @Column(name = "side")
    @Enumerated(EnumType.STRING)
    val side: Side? = null,

    @Column(name = "range_start_line")
    val rangeStartLine: Int? = null,

    @Column(name = "range_start_char")
    val rangeStartChar: Int? = null,

    @Column(name = "range_end_line")
    val rangeEndLine: Int? = null,

    @Column(name = "range_end_char")
    val rangeEndChar: Int? = null,

    @Column(name = "parent_uuid")
    val parentUuid: String? = null,

    @Column(name = "uuid", nullable = false, unique = true)
    val uuid: String,

    @Column(name = "unresolved")
    val unresolved: Boolean = false,

    /**
     * Additional metadata stored as JSONB.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    val metadata: Map<String, Any> = emptyMap()
) {
    enum class Side {
        REVISION,
        PARENT
    }

    /**
     * Check if this is a file-level comment (no line number).
     */
    val isFileComment: Boolean
        get() = lineNumber == null

    /**
     * Check if this is a range comment.
     */
    val isRangeComment: Boolean
        get() = rangeStartLine != null && rangeEndLine != null

    /**
     * Check if this is a reply to another comment.
     */
    val isReply: Boolean
        get() = parentUuid != null
}
