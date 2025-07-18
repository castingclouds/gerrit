package ai.fluxuate.gerrit.repository

import ai.fluxuate.gerrit.model.UserEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.*

/**
 * Repository interface for account-related operations.
 * Extends the UserEntity repository with account-specific query methods.
 */
@Repository
interface AccountRepository : JpaRepository<UserEntity, Int> {
    
    /**
     * Find account by username.
     */
    fun findByUsername(username: String): Optional<UserEntity>
    
    /**
     * Find account by preferred email.
     */
    fun findByPreferredEmail(email: String): Optional<UserEntity>
    
    /**
     * Find account by external ID.
     */
    fun findByExternalId(externalId: String): Optional<UserEntity>
    
    /**
     * Check if username exists.
     */
    fun existsByUsername(username: String): Boolean
    
    /**
     * Check if email exists.
     */
    fun existsByPreferredEmail(email: String): Boolean
    
    /**
     * Query accounts with optional filters.
     */
    @Query("""
        SELECT * FROM users u 
        WHERE (:query IS NULL OR 
               LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%')) OR
               (u.full_name IS NOT NULL AND LOWER(u.full_name) LIKE LOWER(CONCAT('%', :query, '%'))) OR
               (u.preferred_email IS NOT NULL AND LOWER(u.preferred_email) LIKE LOWER(CONCAT('%', :query, '%'))))
        AND (:active IS NULL OR u.active = :active)
        ORDER BY u.username
    """, nativeQuery = true)
    fun queryAccounts(
        @Param("query") query: String?,
        @Param("active") active: Boolean?,
        pageable: Pageable
    ): Page<UserEntity>
    
    /**
     * Find accounts by username pattern.
     */
    @Query("""
        SELECT u FROM UserEntity u 
        WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :pattern, '%'))
        ORDER BY u.username
    """)
    fun findByUsernameContainingIgnoreCase(
        @Param("pattern") pattern: String,
        pageable: Pageable
    ): Page<UserEntity>
    
    /**
     * Find accounts by full name pattern.
     */
    @Query("""
        SELECT u FROM UserEntity u 
        WHERE u.fullName IS NOT NULL 
        AND LOWER(u.fullName) LIKE LOWER(CONCAT('%', :pattern, '%'))
        ORDER BY u.fullName
    """)
    fun findByFullNameContainingIgnoreCase(
        @Param("pattern") pattern: String,
        pageable: Pageable
    ): Page<UserEntity>
    
    /**
     * Find active accounts only.
     */
    fun findByActiveTrue(pageable: Pageable): Page<UserEntity>
    
    /**
     * Find inactive accounts only.
     */
    fun findByActiveFalse(pageable: Pageable): Page<UserEntity>
    
    /**
     * Count total active accounts.
     */
    fun countByActiveTrue(): Long
    
    /**
     * Count total inactive accounts.
     */
    fun countByActiveFalse(): Long
    
    /**
     * Find accounts with email addresses in contact info.
     */
    @Query("""
        SELECT * FROM users u 
        WHERE u.contact_info->>'emails' IS NOT NULL
        AND jsonb_array_length(u.contact_info->'emails') > 0
    """, nativeQuery = true)
    fun findAccountsWithEmails(pageable: Pageable): Page<UserEntity>
    
    /**
     * Find accounts with SSH keys in contact info.
     */
    @Query("""
        SELECT * FROM users u 
        WHERE u.contact_info->>'sshKeys' IS NOT NULL
        AND jsonb_array_length(u.contact_info->'sshKeys') > 0
    """, nativeQuery = true)
    fun findAccountsWithSshKeys(pageable: Pageable): Page<UserEntity>
}
