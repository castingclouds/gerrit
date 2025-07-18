package ai.fluxuate.gerrit.repository

import ai.fluxuate.gerrit.model.UserEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

/**
 * Clean JPA repository for UserEntity, avoiding complex embedded types.
 */
@Repository
interface UserEntityRepository : JpaRepository<UserEntity, Int> {

    /**
     * Find a user by username.
     */
    fun findByUsername(username: String): UserEntity?

    /**
     * Find a user by preferred email.
     */
    fun findByPreferredEmail(email: String): UserEntity?

    /**
     * Find a user by external ID.
     */
    fun findByExternalId(externalId: String): UserEntity?

    /**
     * Find active users.
     */
    fun findByActiveTrue(pageable: Pageable): Page<UserEntity>

    /**
     * Find users by full name containing text (case-insensitive).
     */
    fun findByFullNameContainingIgnoreCase(name: String, pageable: Pageable): Page<UserEntity>

    /**
     * Find users with specific preference using JSONB query.
     */
    @Query("""
        SELECT u FROM UserEntity u 
        WHERE jsonb_extract_path_text(u.preferences, :key) = :value
    """)
    fun findByPreference(
        @Param("key") key: String,
        @Param("value") value: String,
        pageable: Pageable
    ): Page<UserEntity>

    /**
     * Check if username exists.
     */
    fun existsByUsername(username: String): Boolean

    /**
     * Check if email exists.
     */
    fun existsByPreferredEmail(email: String): Boolean
}
