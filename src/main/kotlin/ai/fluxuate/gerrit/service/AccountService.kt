package ai.fluxuate.gerrit.service

import ai.fluxuate.gerrit.api.dto.*
import ai.fluxuate.gerrit.api.exception.BadRequestException
import ai.fluxuate.gerrit.api.exception.ConflictException
import ai.fluxuate.gerrit.api.exception.NotFoundException
import ai.fluxuate.gerrit.model.UserEntity
import ai.fluxuate.gerrit.repository.AccountRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.*

/**
 * Service for managing accounts and account-related operations.
 * Provides business logic for the Accounts REST API.
 */
@Service
@Transactional
class AccountService(
    private val accountRepository: AccountRepository
) {
    
    /**
     * Query accounts with optional filters and pagination.
     */
    fun queryAccounts(
        query: String? = null,
        active: Boolean? = null,
        limit: Int = 25,
        start: Int = 0
    ): Map<String, AccountInfo> {
        val pageable = PageRequest.of(start / limit, limit)
        val page = accountRepository.queryAccounts(query, active, pageable)
        
        return page.content.associate { entity ->
            entity.username to toAccountInfo(entity)
        }
    }
    
    /**
     * Get account by identifier (username, email, or account ID).
     */
    fun getAccount(accountId: String): AccountInfo {
        val entity = resolveAccount(accountId)
        return toAccountInfo(entity)
    }
    
    /**
     * Get detailed account information.
     */
    fun getAccountDetail(accountId: String): AccountDetailInfo {
        val entity = resolveAccount(accountId)
        return toAccountDetailInfo(entity)
    }
    
    /**
     * Create a new account.
     */
    fun createAccount(username: String, input: AccountInput): AccountInfo {
        // Validate username
        if (username.isBlank()) {
            throw BadRequestException("Username cannot be empty")
        }
        
        // Check if username already exists
        if (accountRepository.existsByUsername(username)) {
            throw ConflictException("Username '$username' already exists")
        }
        
        // Check if email already exists
        input.email?.let { email ->
            if (accountRepository.existsByPreferredEmail(email)) {
                throw ConflictException("Email '$email' already exists")
            }
        }
        
        // Create new user entity
        val entity = UserEntity(
            username = username,
            fullName = input.name,
            preferredEmail = input.email,
            active = true,
            registeredOn = Instant.now(),
            preferences = emptyMap(),
            contactInfo = buildContactInfo(input)
        )
        
        val savedEntity = accountRepository.save(entity)
        return toAccountInfo(savedEntity)
    }
    
    /**
     * Update an existing account.
     */
    fun updateAccount(accountId: String, input: AccountInput): AccountInfo {
        val entity = resolveAccount(accountId)
        
        // Check email conflicts if changing email
        input.email?.let { newEmail ->
            if (newEmail != entity.preferredEmail && accountRepository.existsByPreferredEmail(newEmail)) {
                throw ConflictException("Email '$newEmail' already exists")
            }
        }
        
        val updatedEntity = entity.copy(
            fullName = input.name ?: entity.fullName,
            preferredEmail = input.email ?: entity.preferredEmail,
            contactInfo = mergeContactInfo(entity.contactInfo, input)
        )
        
        val savedEntity = accountRepository.save(updatedEntity)
        return toAccountInfo(savedEntity)
    }
    
    /**
     * Delete an account (self-deletion only).
     */
    fun deleteAccount(accountId: String) {
        val entity = resolveAccount(accountId)
        // In a real implementation, this would have additional security checks
        accountRepository.delete(entity)
    }
    
    /**
     * Get account name.
     */
    fun getAccountName(accountId: String): String? {
        val entity = resolveAccount(accountId)
        return entity.fullName
    }
    
    /**
     * Set account name.
     */
    fun setAccountName(accountId: String, input: AccountNameInput): String? {
        val entity = resolveAccount(accountId)
        val updatedEntity = entity.copy(fullName = input.name)
        val savedEntity = accountRepository.save(updatedEntity)
        return savedEntity.fullName
    }
    
    /**
     * Delete account name.
     */
    fun deleteAccountName(accountId: String) {
        val entity = resolveAccount(accountId)
        val updatedEntity = entity.copy(fullName = null)
        accountRepository.save(updatedEntity)
    }
    
    /**
     * Get account status.
     */
    fun getAccountStatus(accountId: String): String? {
        val entity = resolveAccount(accountId)
        return entity.preferences?.get("status") as? String
    }
    
    /**
     * Set account status.
     */
    fun setAccountStatus(accountId: String, input: AccountStatusInput): String? {
        val entity = resolveAccount(accountId)
        val updatedPreferences = entity.preferences.toMutableMap()
        updatedPreferences["status"] = input.status ?: ""
        
        val updatedEntity = entity.copy(preferences = updatedPreferences)
        val savedEntity = accountRepository.save(updatedEntity)
        return savedEntity.preferences?.get("status") as? String
    }
    
    /**
     * Get account active state.
     */
    fun getAccountActive(accountId: String): Boolean {
        val entity = resolveAccount(accountId)
        return entity.active
    }
    
    /**
     * Set account active state.
     */
    fun setAccountActive(accountId: String, active: Boolean): Boolean {
        val entity = resolveAccount(accountId)
        val updatedEntity = entity.copy(active = active)
        val savedEntity = accountRepository.save(updatedEntity)
        return savedEntity.active
    }
    
    /**
     * Get account emails.
     */
    fun getAccountEmails(accountId: String): List<EmailInfo> {
        val entity = resolveAccount(accountId)
        val emails = mutableListOf<EmailInfo>()
        
        // Add preferred email
        entity.preferredEmail?.let { email ->
            emails.add(EmailInfo(email = email, preferred = true))
        }
        
        // Add secondary emails from contact info
        val contactEmails = entity.contactInfo["emails"] as? List<*>
        contactEmails?.forEach { emailData ->
            if (emailData is Map<*, *>) {
                val email = emailData["email"] as? String
                val preferred = emailData["preferred"] as? Boolean ?: false
                if (email != null && email != entity.preferredEmail) {
                    emails.add(EmailInfo(email = email, preferred = preferred))
                }
            }
        }
        
        return emails
    }
    
    /**
     * Add email to account.
     */
    fun addAccountEmail(accountId: String, email: String, input: EmailInput): EmailInfo {
        val entity = resolveAccount(accountId)
        
        // Validate email format
        if (!isValidEmail(email)) {
            throw BadRequestException("Invalid email format: $email")
        }
        
        // Check if email already exists
        if (accountRepository.existsByPreferredEmail(email)) {
            throw ConflictException("Email '$email' already exists")
        }
        
        val contactInfo = entity.contactInfo.toMutableMap()
        val emails = (contactInfo["emails"] as? MutableList<Map<String, Any>>) ?: mutableListOf()
        
        // Add new email
        emails.add(mapOf(
            "email" to email,
            "preferred" to (input.preferred ?: false),
            "pendingConfirmation" to !(input.noConfirmation ?: false)
        ))
        
        contactInfo["emails"] = emails
        
        val updatedEntity = entity.copy(contactInfo = contactInfo)
        accountRepository.save(updatedEntity)
        
        return EmailInfo(
            email = email,
            preferred = input.preferred,
            pendingConfirmation = !(input.noConfirmation ?: false)
        )
    }
    
    /**
     * Delete email from account.
     */
    fun deleteAccountEmail(accountId: String, email: String) {
        val entity = resolveAccount(accountId)
        
        if (entity.preferredEmail == email) {
            throw BadRequestException("Cannot delete preferred email")
        }
        
        val contactInfo = entity.contactInfo.toMutableMap()
        val emails = (contactInfo["emails"] as? MutableList<Map<String, Any>>) ?: mutableListOf()
        
        emails.removeIf { emailData ->
            emailData["email"] == email
        }
        
        contactInfo["emails"] = emails
        
        val updatedEntity = entity.copy(contactInfo = contactInfo)
        accountRepository.save(updatedEntity)
    }
    
    /**
     * Get account SSH keys.
     */
    fun getAccountSshKeys(accountId: String): List<SshKeyInfo> {
        val entity = resolveAccount(accountId)
        val sshKeys = entity.contactInfo["sshKeys"] as? List<*> ?: emptyList<Any>()
        
        return sshKeys.mapIndexedNotNull { index, keyData ->
            if (keyData is Map<*, *>) {
                SshKeyInfo(
                    seq = index + 1,
                    sshPublicKey = keyData["publicKey"] as? String ?: "",
                    encodedKey = keyData["encodedKey"] as? String ?: "",
                    algorithm = keyData["algorithm"] as? String ?: "unknown",
                    comment = keyData["comment"] as? String,
                    valid = keyData["valid"] as? Boolean ?: true
                )
            } else null
        }
    }
    
    /**
     * Add SSH key to account.
     */
    fun addAccountSshKey(accountId: String, input: AddSshKeyInput): SshKeyInfo {
        val entity = resolveAccount(accountId)
        
        // Parse SSH key
        val keyInfo = parseSshKey(input.sshPublicKey)
        // Filter out null values
        val cleanKeyInfo = keyInfo.filterValues { it != null }.mapValues { it.value!! }
        
        val contactInfo = entity.contactInfo.toMutableMap()
        val sshKeys = (contactInfo["sshKeys"] as? MutableList<Map<String, Any>>) ?: mutableListOf()
        
        // Add new SSH key
        sshKeys.add(cleanKeyInfo)
        contactInfo["sshKeys"] = sshKeys
        
        val updatedEntity = entity.copy(contactInfo = contactInfo)
        accountRepository.save(updatedEntity)
        
        return SshKeyInfo(
            seq = sshKeys.size,
            sshPublicKey = input.sshPublicKey,
            encodedKey = keyInfo["encodedKey"] as String,
            algorithm = keyInfo["algorithm"] as String,
            comment = keyInfo["comment"] as? String,
            valid = keyInfo["valid"] as Boolean
        )
    }
    
    /**
     * Delete SSH key from account.
     */
    fun deleteAccountSshKey(accountId: String, keySeq: Int) {
        val entity = resolveAccount(accountId)
        
        val contactInfo = entity.contactInfo.toMutableMap()
        val sshKeys = (contactInfo["sshKeys"] as? MutableList<Map<String, Any>>) ?: mutableListOf()
        
        if (keySeq < 1 || keySeq > sshKeys.size) {
            throw NotFoundException("SSH key not found")
        }
        
        sshKeys.removeAt(keySeq - 1)
        contactInfo["sshKeys"] = sshKeys
        
        val updatedEntity = entity.copy(contactInfo = contactInfo)
        accountRepository.save(updatedEntity)
    }
    
    /**
     * Get account preferences.
     */
    fun getAccountPreferences(accountId: String): PreferencesInfo {
        val entity = resolveAccount(accountId)
        return toPreferencesInfo(entity.preferences)
    }
    
    /**
     * Set account preferences.
     */
    fun setAccountPreferences(accountId: String, preferences: PreferencesInfo): PreferencesInfo {
        val entity = resolveAccount(accountId)
        val updatedPreferences = fromPreferencesInfo(preferences)
        
        val updatedEntity = entity.copy(preferences = updatedPreferences)
        val savedEntity = accountRepository.save(updatedEntity)
        return toPreferencesInfo(savedEntity.preferences)
    }
    
    /**
     * Get account capabilities.
     */
    fun getAccountCapabilities(accountId: String): CapabilityInfo {
        val entity = resolveAccount(accountId)
        // In a real implementation, this would check user permissions and groups
        return CapabilityInfo(
            createProject = true,
            createAccount = false,
            administrateServer = false
        )
    }
    
    /**
     * Resolve account by various identifiers.
     */
    private fun resolveAccount(accountId: String): UserEntity {
        return when {
            accountId == "self" || accountId == "me" -> {
                // In a real implementation, this would get the current authenticated user
                throw BadRequestException("Authentication not implemented")
            }
            accountId.toIntOrNull() != null -> {
                accountRepository.findById(accountId.toInt())
                    .orElseThrow { NotFoundException("Account not found: $accountId") }
            }
            accountId.contains("@") -> {
                accountRepository.findByPreferredEmail(accountId)
                    .orElseThrow { NotFoundException("Account not found: $accountId") }
            }
            else -> {
                accountRepository.findByUsername(accountId)
                    .orElseThrow { NotFoundException("Account not found: $accountId") }
            }
        }
    }
    
    /**
     * Convert UserEntity to AccountInfo.
     */
    private fun toAccountInfo(entity: UserEntity): AccountInfo {
        return AccountInfo(
            _account_id = entity.id.toLong(),
            name = entity.fullName,
            email = entity.preferredEmail,
            username = entity.username,
            status = entity.preferences?.get("status") as? String,
            inactive = if (entity.active) null else true
        )
    }
    
    /**
     * Convert UserEntity to AccountDetailInfo.
     */
    private fun toAccountDetailInfo(entity: UserEntity): AccountDetailInfo {
        return AccountDetailInfo(
            accountId = entity.id,
            name = entity.fullName,
            email = entity.preferredEmail,
            username = entity.username,
            status = entity.preferences?.get("status") as? String,
            inactive = if (entity.active) null else true,
            registeredOn = entity.registeredOn
        )
    }
    
    /**
     * Build contact info from account input.
     */
    private fun buildContactInfo(input: AccountInput): Map<String, Any> {
        val contactInfo = mutableMapOf<String, Any>()
        
        if (input.sshKey != null) {
            val keyInfo = parseSshKey(input.sshKey)
            // Filter out null values
            val cleanKeyInfo = keyInfo.filterValues { it != null }.mapValues { it.value!! }
            contactInfo["sshKeys"] = listOf(cleanKeyInfo)
        }
        
        return contactInfo
    }
    
    /**
     * Merge contact info with account input.
     */
    private fun mergeContactInfo(existing: Map<String, Any>, input: AccountInput): Map<String, Any> {
        val contactInfo = existing.toMutableMap()
        
        if (input.sshKey != null) {
            val keyInfo = parseSshKey(input.sshKey)
            // Filter out null values
            val cleanKeyInfo = keyInfo.filterValues { it != null }.mapValues { it.value!! }
            val sshKeys = (contactInfo["sshKeys"] as? MutableList<Map<String, Any>>) ?: mutableListOf()
            sshKeys.add(cleanKeyInfo)
            contactInfo["sshKeys"] = sshKeys
        }
        
        return contactInfo
    }
    
    /**
     * Parse SSH public key.
     */
    private fun parseSshKey(sshKey: String): Map<String, Any?> {
        val parts = sshKey.trim().split(" ")
        if (parts.size < 2) {
            throw BadRequestException("Invalid SSH key format")
        }
        
        return mapOf(
            "publicKey" to sshKey,
            "encodedKey" to parts[1],
            "algorithm" to parts[0],
            "comment" to if (parts.size > 2) parts.drop(2).joinToString(" ") else null,
            "valid" to true
        )
    }
    
    /**
     * Validate email format.
     */
    private fun isValidEmail(email: String): Boolean {
        return email.contains("@") && email.contains(".")
    }
    
    /**
     * Convert preferences map to PreferencesInfo.
     */
    private fun toPreferencesInfo(preferences: Map<String, Any>): PreferencesInfo {
        return PreferencesInfo(
            changesPerPage = preferences["changesPerPage"] as? Int,
            theme = (preferences["theme"] as? String)?.let { 
                try { Theme.valueOf(it.uppercase()) } catch (e: IllegalArgumentException) { null }
            },
            dateFormat = (preferences["dateFormat"] as? String)?.let { 
                try { DateFormat.valueOf(it.uppercase()) } catch (e: IllegalArgumentException) { null }
            },
            timeFormat = (preferences["timeFormat"] as? String)?.let { 
                try { TimeFormat.valueOf(it.uppercase()) } catch (e: IllegalArgumentException) { null }
            },
            emailStrategy = (preferences["emailStrategy"] as? String)?.let { 
                try { EmailStrategy.valueOf(it.uppercase()) } catch (e: IllegalArgumentException) { null }
            }
        )
    }
    
    /**
     * Convert PreferencesInfo to preferences map.
     */
    private fun fromPreferencesInfo(preferences: PreferencesInfo): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        
        preferences.changesPerPage?.let { map["changesPerPage"] = it }
        preferences.theme?.let { map["theme"] = it.name }
        preferences.dateFormat?.let { map["dateFormat"] = it.name }
        preferences.timeFormat?.let { map["timeFormat"] = it.name }
        preferences.emailStrategy?.let { map["emailStrategy"] = it.name }
        
        return map
    }
}
