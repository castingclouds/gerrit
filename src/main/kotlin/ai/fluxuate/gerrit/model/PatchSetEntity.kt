package ai.fluxuate.gerrit.model

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

/**
 * JPA Entity representing a PatchSet - a single revision of a Change.
 * 
 * This entity works alongside the JSONB storage in ChangeEntity:
 * - ChangeEntity stores patchSets as JSONB for performance
 * - PatchSetEntity provides structured queries and relationships
 */
@Entity
@Table(name = "patch_sets")
data class PatchSetEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "change_id", nullable = false)
    val change: ChangeEntity,

    @Column(name = "patch_set_number", nullable = false)
    val patchSetNumber: Int,

    @Column(name = "commit_id", nullable = false, length = 40)
    val commitId: String,

    @Column(name = "uploader_id", nullable = false)
    val uploaderId: Long,

    @Column(name = "real_uploader_id", nullable = false)
    val realUploaderId: Long,

    @Column(name = "created_on", nullable = false)
    val createdOn: Instant,

    @Column(name = "description", columnDefinition = "text")
    val description: String? = null,

    @Column(name = "branch")
    val branch: String? = null,

    /**
     * Groups this patch set belongs to (for related changes).
     * Stored as JSONB array of strings.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "groups", columnDefinition = "jsonb")
    val groups: List<String> = emptyList(),

    /**
     * File modifications in this patch set.
     * One-to-many relationship with DiffEntity for better scalability.
     */
    @OneToMany(mappedBy = "patchSet", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val diffs: List<DiffEntity> = emptyList(),

    /**
     * Additional metadata stored as JSONB.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    val metadata: Map<String, Any> = emptyMap()
) {
    /**
     * Generate the Git ref name for this patch set.
     * Format: refs/changes/XX/YYYY/Z where XX is last 2 digits of change ID,
     * YYYY is change ID, Z is patch set number.
     */
    val refName: String
        get() {
            val changeIdStr = change.id.toString()
            val lastTwoDigits = changeIdStr.takeLast(2).padStart(2, '0')
            return "refs/changes/$lastTwoDigits/${change.id}/$patchSetNumber"
        }

    /**
     * Check if this is the initial patch set.
     */
    val isInitial: Boolean
        get() = patchSetNumber == 1
}
