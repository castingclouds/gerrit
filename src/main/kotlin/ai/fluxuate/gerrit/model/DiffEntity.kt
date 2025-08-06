package ai.fluxuate.gerrit.model

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime

/**
 * Entity representing a file diff within a patch set.
 * 
 * This table stores individual file modifications with proper indexing
 * for efficient querying and better scalability compared to JSONB arrays.
 */
@Entity
@Table(
    name = "diffs",
    indexes = [
        Index(name = "idx_diff_patch_set", columnList = "patch_set_id"),
        Index(name = "idx_diff_change_type", columnList = "change_type"),
        Index(name = "idx_diff_file_path", columnList = "file_path"),
        Index(name = "idx_diff_patch_set_file", columnList = "patch_set_id, file_path"),
        Index(name = "idx_diff_patch_set_change_type", columnList = "patch_set_id, change_type")
    ]
)
data class DiffEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    
    /**
     * Reference to the patch set this diff belongs to.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patch_set_id", nullable = false)
    val patchSet: PatchSetEntity,
    
    /**
     * Type of change: ADDED, DELETED, MODIFIED, RENAMED, COPIED
     */
    @Column(name = "change_type", nullable = false, length = 20)
    val changeType: String,
    
    /**
     * Path to the file that was modified.
     * For renames, this is the new path.
     */
    @Column(name = "file_path", nullable = false, length = 1000)
    val filePath: String,
    
    /**
     * JSONB column containing diff data as JSON.
     * Contains: changeType, filePath, and diff fields.
     */
    @Column(name = "diff_data", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    val diffData: String,
    
    /**
     * Timestamp when this diff was created.
     */
    @Column(name = "created_on", nullable = false)
    val createdOn: LocalDateTime = LocalDateTime.now()
)
