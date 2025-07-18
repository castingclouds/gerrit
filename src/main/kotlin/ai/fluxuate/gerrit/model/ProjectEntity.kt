package ai.fluxuate.gerrit.model

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

/**
 * Clean JPA entity for Project, avoiding embedded types that cause Hibernate conflicts.
 * Stores project configuration and metadata with JSONB for complex data.
 */
@Entity
@Table(
    name = "projects",
    indexes = [
        Index(name = "idx_projects_name", columnList = "name", unique = true),
        Index(name = "idx_projects_parent", columnList = "parent_name"),
        Index(name = "idx_projects_state", columnList = "state")
    ]
)
data class ProjectEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Int = 0,

    @Column(name = "name", nullable = false, unique = true, length = 255)
    val name: String,

    @Column(name = "parent_name", length = 255)
    val parentName: String? = null,

    @Column(name = "description", length = 1000)
    val description: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false)
    val state: ProjectState = ProjectState.ACTIVE,

    /**
     * Project configuration stored as JSONB.
     * Contains: submit rules, access rights, branch permissions, etc.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config", columnDefinition = "jsonb")
    val config: Map<String, Any> = emptyMap(),

    /**
     * Project metadata stored as JSONB.
     * Contains: labels, dashboards, etc.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    val metadata: Map<String, Any> = emptyMap()
) {
    override fun toString(): String = "ProjectEntity(id=$id, name='$name', state=$state)"
}

enum class ProjectState {
    ACTIVE,
    READ_ONLY,
    HIDDEN
}
