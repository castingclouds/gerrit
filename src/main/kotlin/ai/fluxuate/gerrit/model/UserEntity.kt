package ai.fluxuate.gerrit.model

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

/**
 * Clean JPA entity for User, avoiding embedded types that cause Hibernate conflicts.
 * Stores user profile and preferences with JSONB for complex data.
 */
@Entity
@Table(
    name = "users",
    indexes = [
        Index(name = "idx_users_username", columnList = "username", unique = true),
        Index(name = "idx_users_email", columnList = "preferred_email", unique = true),
        Index(name = "idx_users_external_id", columnList = "external_id"),
        Index(name = "idx_users_active", columnList = "active")
    ]
)
data class UserEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Int = 0,

    @Column(name = "username", nullable = false, unique = true, length = 255)
    val username: String,

    @Column(name = "full_name", length = 255)
    val fullName: String? = null,

    @Column(name = "preferred_email", unique = true, length = 255)
    val preferredEmail: String? = null,

    @Column(name = "external_id", length = 255)
    val externalId: String? = null,

    @Column(name = "active", nullable = false)
    val active: Boolean = true,

    @Column(name = "registered_on", nullable = false)
    val registeredOn: Instant = Instant.now(),

    @Column(name = "last_login_on")
    val lastLoginOn: Instant? = null,

    /**
     * User preferences stored as JSONB.
     * Contains: timezone, dateFormat, timeFormat, emailStrategy, etc.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preferences", columnDefinition = "jsonb")
    val preferences: Map<String, Any> = emptyMap(),

    /**
     * User contact information stored as JSONB.
     * Contains: emails, ssh keys, etc.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "contact_info", columnDefinition = "jsonb")
    val contactInfo: Map<String, Any> = emptyMap()
) {
    override fun toString(): String = "UserEntity(id=$id, username='$username', fullName='$fullName')"
}
