package ai.fluxuate.gerrit.api.controller

import ai.fluxuate.gerrit.api.dto.*
import ai.fluxuate.gerrit.service.AccountService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * REST controller for Gerrit Accounts API.
 * Provides endpoints for managing user accounts, emails, SSH keys, and preferences.
 */
@RestController
@RequestMapping("/a/accounts")
class AccountsController(
    private val accountService: AccountService
) {
    
    /**
     * Query accounts.
     * GET /a/accounts/
     */
    @GetMapping("/")
    fun queryAccounts(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) active: Boolean?,
        @RequestParam(defaultValue = "25") n: Int,
        @RequestParam(defaultValue = "0") S: Int
    ): Map<String, AccountInfo> {
        return accountService.queryAccounts(
            query = q,
            active = active,
            limit = n,
            start = S
        )
    }
    
    /**
     * Get account.
     * GET /a/accounts/{account-id}
     */
    @GetMapping("/{accountId}")
    fun getAccount(@PathVariable accountId: String): AccountInfo {
        return accountService.getAccount(accountId)
    }
    
    /**
     * Create account.
     * PUT /a/accounts/{account-id}
     */
    @PutMapping("/{accountId}")
    fun createAccount(
        @PathVariable accountId: String,
        @RequestBody input: AccountInput
    ): ResponseEntity<AccountInfo> {
        val account = accountService.createAccount(accountId, input)
        return ResponseEntity.status(HttpStatus.CREATED).body(account)
    }
    
    /**
     * Update account.
     * POST /a/accounts/{account-id}
     */
    @PostMapping("/{accountId}")
    fun updateAccount(
        @PathVariable accountId: String,
        @RequestBody input: AccountInput
    ): AccountInfo {
        return accountService.updateAccount(accountId, input)
    }
    
    /**
     * Delete account.
     * DELETE /a/accounts/{account-id}
     */
    @DeleteMapping("/{accountId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteAccount(@PathVariable accountId: String) {
        accountService.deleteAccount(accountId)
    }
    
    /**
     * Get account detail.
     * GET /a/accounts/{account-id}/detail
     */
    @GetMapping("/{accountId}/detail")
    fun getAccountDetail(@PathVariable accountId: String): AccountDetailInfo {
        return accountService.getAccountDetail(accountId)
    }
    
    // Account Name Management
    
    /**
     * Get account name.
     * GET /a/accounts/{account-id}/name
     */
    @GetMapping("/{accountId}/name")
    fun getAccountName(@PathVariable accountId: String): ResponseEntity<String> {
        val name = accountService.getAccountName(accountId)
        return if (name != null) {
            ResponseEntity.ok("\"$name\"")
        } else {
            ResponseEntity.noContent().build()
        }
    }
    
    /**
     * Set account name.
     * PUT /a/accounts/{account-id}/name
     */
    @PutMapping("/{accountId}/name")
    fun setAccountName(
        @PathVariable accountId: String,
        @RequestBody input: AccountNameInput
    ): ResponseEntity<String> {
        val name = accountService.setAccountName(accountId, input)
        return if (name != null) {
            ResponseEntity.ok("\"$name\"")
        } else {
            ResponseEntity.noContent().build()
        }
    }
    
    /**
     * Delete account name.
     * DELETE /a/accounts/{account-id}/name
     */
    @DeleteMapping("/{accountId}/name")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteAccountName(@PathVariable accountId: String) {
        accountService.deleteAccountName(accountId)
    }
    
    // Display Name Management
    
    /**
     * Set display name.
     * PUT /a/accounts/{account-id}/displayname
     */
    @PutMapping("/{accountId}/displayname")
    fun setDisplayName(
        @PathVariable accountId: String,
        @RequestBody input: DisplayNameInput
    ): ResponseEntity<String> {
        // For now, display name is the same as name
        val nameInput = AccountNameInput(name = input.displayName)
        val name = accountService.setAccountName(accountId, nameInput)
        return if (name != null) {
            ResponseEntity.ok("\"$name\"")
        } else {
            ResponseEntity.noContent().build()
        }
    }
    
    // Status Management
    
    /**
     * Get account status.
     * GET /a/accounts/{account-id}/status
     */
    @GetMapping("/{accountId}/status")
    fun getAccountStatus(@PathVariable accountId: String): ResponseEntity<String> {
        val status = accountService.getAccountStatus(accountId)
        return if (status != null) {
            ResponseEntity.ok("\"$status\"")
        } else {
            ResponseEntity.noContent().build()
        }
    }
    
    /**
     * Set account status.
     * PUT /a/accounts/{account-id}/status
     */
    @PutMapping("/{accountId}/status")
    fun setAccountStatus(
        @PathVariable accountId: String,
        @RequestBody input: AccountStatusInput
    ): ResponseEntity<String> {
        val status = accountService.setAccountStatus(accountId, input)
        return if (status != null) {
            ResponseEntity.ok("\"$status\"")
        } else {
            ResponseEntity.noContent().build()
        }
    }
    
    // Active State Management
    
    /**
     * Get account active state.
     * GET /a/accounts/{account-id}/active
     */
    @GetMapping("/{accountId}/active")
    fun getAccountActive(@PathVariable accountId: String): ResponseEntity<String> {
        val active = accountService.getAccountActive(accountId)
        return if (active) {
            ResponseEntity.ok("\"ok\"")
        } else {
            ResponseEntity.noContent().build()
        }
    }
    
    /**
     * Set account active.
     * PUT /a/accounts/{account-id}/active
     */
    @PutMapping("/{accountId}/active")
    fun setAccountActive(@PathVariable accountId: String): ResponseEntity<String> {
        accountService.setAccountActive(accountId, true)
        return ResponseEntity.ok("\"ok\"")
    }
    
    /**
     * Set account inactive.
     * DELETE /a/accounts/{account-id}/active
     */
    @DeleteMapping("/{accountId}/active")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun setAccountInactive(@PathVariable accountId: String) {
        accountService.setAccountActive(accountId, false)
    }
    
    // Username Management
    
    /**
     * Get username.
     * GET /a/accounts/{account-id}/username
     */
    @GetMapping("/{accountId}/username")
    fun getUsername(@PathVariable accountId: String): ResponseEntity<String> {
        val account = accountService.getAccount(accountId)
        return if (account.username != null) {
            ResponseEntity.ok("\"${account.username}\"")
        } else {
            ResponseEntity.noContent().build()
        }
    }
    
    /**
     * Set username (not allowed).
     * PUT /a/accounts/{account-id}/username
     */
    @PutMapping("/{accountId}/username")
    fun setUsername(@PathVariable accountId: String): ResponseEntity<String> {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
            .body("Username cannot be changed.")
    }
    
    // Email Management
    
    /**
     * List account emails.
     * GET /a/accounts/{account-id}/emails/
     */
    @GetMapping("/{accountId}/emails/")
    fun getAccountEmails(@PathVariable accountId: String): List<EmailInfo> {
        return accountService.getAccountEmails(accountId)
    }
    
    /**
     * Get specific email.
     * GET /a/accounts/{account-id}/emails/{email-id}
     */
    @GetMapping("/{accountId}/emails/{emailId}")
    fun getAccountEmail(
        @PathVariable accountId: String,
        @PathVariable emailId: String
    ): EmailInfo {
        val emails = accountService.getAccountEmails(accountId)
        return emails.find { it.email == emailId }
            ?: throw ai.fluxuate.gerrit.api.exception.NotFoundException("Email not found: $emailId")
    }
    
    /**
     * Add email to account.
     * PUT /a/accounts/{account-id}/emails/{email-id}
     */
    @PutMapping("/{accountId}/emails/{emailId}")
    fun addAccountEmail(
        @PathVariable accountId: String,
        @PathVariable emailId: String,
        @RequestBody(required = false) input: EmailInput?
    ): ResponseEntity<EmailInfo> {
        val emailInput = input ?: EmailInput(email = emailId)
        val email = accountService.addAccountEmail(accountId, emailId, emailInput)
        return ResponseEntity.status(HttpStatus.CREATED).body(email)
    }
    
    /**
     * Delete email from account.
     * DELETE /a/accounts/{account-id}/emails/{email-id}
     */
    @DeleteMapping("/{accountId}/emails/{emailId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteAccountEmail(
        @PathVariable accountId: String,
        @PathVariable emailId: String
    ) {
        accountService.deleteAccountEmail(accountId, emailId)
    }
    
    /**
     * Set preferred email.
     * PUT /a/accounts/{account-id}/emails/{email-id}/preferred
     */
    @PutMapping("/{accountId}/emails/{emailId}/preferred")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun setPreferredEmail(
        @PathVariable accountId: String,
        @PathVariable emailId: String
    ) {
        // This would update the preferred email in the account
        val input = AccountInput(email = emailId)
        accountService.updateAccount(accountId, input)
    }
    
    // SSH Key Management
    
    /**
     * List SSH keys.
     * GET /a/accounts/{account-id}/sshkeys/
     */
    @GetMapping("/{accountId}/sshkeys/")
    fun getAccountSshKeys(@PathVariable accountId: String): List<SshKeyInfo> {
        return accountService.getAccountSshKeys(accountId)
    }
    
    /**
     * Get specific SSH key.
     * GET /a/accounts/{account-id}/sshkeys/{ssh-key-id}
     */
    @GetMapping("/{accountId}/sshkeys/{sshKeyId}")
    fun getAccountSshKey(
        @PathVariable accountId: String,
        @PathVariable sshKeyId: Int
    ): SshKeyInfo {
        val sshKeys = accountService.getAccountSshKeys(accountId)
        return sshKeys.find { it.seq == sshKeyId }
            ?: throw ai.fluxuate.gerrit.api.exception.NotFoundException("SSH key not found: $sshKeyId")
    }
    
    /**
     * Add SSH key.
     * POST /a/accounts/{account-id}/sshkeys/
     */
    @PostMapping("/{accountId}/sshkeys/")
    fun addAccountSshKey(
        @PathVariable accountId: String,
        @RequestBody input: AddSshKeyInput
    ): ResponseEntity<SshKeyInfo> {
        val sshKey = accountService.addAccountSshKey(accountId, input)
        return ResponseEntity.status(HttpStatus.CREATED).body(sshKey)
    }
    
    /**
     * Delete SSH key.
     * DELETE /a/accounts/{account-id}/sshkeys/{ssh-key-id}
     */
    @DeleteMapping("/{accountId}/sshkeys/{sshKeyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteAccountSshKey(
        @PathVariable accountId: String,
        @PathVariable sshKeyId: Int
    ) {
        accountService.deleteAccountSshKey(accountId, sshKeyId)
    }
    
    // Preferences Management
    
    /**
     * Get general preferences.
     * GET /a/accounts/{account-id}/preferences
     */
    @GetMapping("/{accountId}/preferences")
    fun getAccountPreferences(@PathVariable accountId: String): PreferencesInfo {
        return accountService.getAccountPreferences(accountId)
    }
    
    /**
     * Set general preferences.
     * PUT /a/accounts/{account-id}/preferences
     */
    @PutMapping("/{accountId}/preferences")
    fun setAccountPreferences(
        @PathVariable accountId: String,
        @RequestBody preferences: PreferencesInfo
    ): PreferencesInfo {
        return accountService.setAccountPreferences(accountId, preferences)
    }
    
    /**
     * Get diff preferences.
     * GET /a/accounts/{account-id}/preferences.diff
     */
    @GetMapping("/{accountId}/preferences.diff")
    fun getAccountDiffPreferences(@PathVariable accountId: String): DiffPreferencesInfo {
        // For now, return empty diff preferences
        return DiffPreferencesInfo()
    }
    
    /**
     * Set diff preferences.
     * PUT /a/accounts/{account-id}/preferences.diff
     */
    @PutMapping("/{accountId}/preferences.diff")
    fun setAccountDiffPreferences(
        @PathVariable accountId: String,
        @RequestBody preferences: DiffPreferencesInfo
    ): DiffPreferencesInfo {
        // For now, just return the input
        return preferences
    }
    
    /**
     * Get edit preferences.
     * GET /a/accounts/{account-id}/preferences.edit
     */
    @GetMapping("/{accountId}/preferences.edit")
    fun getAccountEditPreferences(@PathVariable accountId: String): EditPreferencesInfo {
        // For now, return empty edit preferences
        return EditPreferencesInfo()
    }
    
    /**
     * Set edit preferences.
     * PUT /a/accounts/{account-id}/preferences.edit
     */
    @PutMapping("/{accountId}/preferences.edit")
    fun setAccountEditPreferences(
        @PathVariable accountId: String,
        @RequestBody preferences: EditPreferencesInfo
    ): EditPreferencesInfo {
        // For now, just return the input
        return preferences
    }
    
    // Capabilities
    
    /**
     * Get account capabilities.
     * GET /a/accounts/{account-id}/capabilities
     */
    @GetMapping("/{accountId}/capabilities")
    fun getAccountCapabilities(@PathVariable accountId: String): CapabilityInfo {
        return accountService.getAccountCapabilities(accountId)
    }
    
    /**
     * Check specific capability.
     * GET /a/accounts/{account-id}/capabilities/{capability-id}
     */
    @GetMapping("/{accountId}/capabilities/{capabilityId}")
    fun getAccountCapability(
        @PathVariable accountId: String,
        @PathVariable capabilityId: String
    ): ResponseEntity<String> {
        val capabilities = accountService.getAccountCapabilities(accountId)
        
        val hasCapability = when (capabilityId) {
            "createProject" -> capabilities.createProject ?: false
            "createAccount" -> capabilities.createAccount ?: false
            "administrateServer" -> capabilities.administrateServer ?: false
            "viewPlugins" -> capabilities.viewPlugins ?: false
            else -> false
        }
        
        return if (hasCapability) {
            ResponseEntity.ok("\"ok\"")
        } else {
            ResponseEntity.noContent().build()
        }
    }
    
    // Groups (placeholder)
    
    /**
     * Get account groups.
     * GET /a/accounts/{account-id}/groups
     */
    @GetMapping("/{accountId}/groups")
    fun getAccountGroups(@PathVariable accountId: String): List<Map<String, Any>> {
        // For now, return empty list
        return emptyList()
    }
    
    // Watched Projects (placeholder)
    
    /**
     * Get watched projects.
     * GET /a/accounts/{account-id}/watched.projects
     */
    @GetMapping("/{accountId}/watched.projects")
    fun getWatchedProjects(@PathVariable accountId: String): List<ProjectWatchInfo> {
        // For now, return empty list
        return emptyList()
    }
    
    /**
     * Add watched projects.
     * POST /a/accounts/{account-id}/watched.projects
     */
    @PostMapping("/{accountId}/watched.projects")
    fun addWatchedProjects(
        @PathVariable accountId: String,
        @RequestBody input: List<WatchedProjectInput>
    ): List<ProjectWatchInfo> {
        // For now, return empty list
        return emptyList()
    }
    
    /**
     * Delete watched projects.
     * POST /a/accounts/{account-id}/watched.projects:delete
     */
    @PostMapping("/{accountId}/watched.projects:delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteWatchedProjects(
        @PathVariable accountId: String,
        @RequestBody input: List<WatchedProjectInput>
    ) {
        // For now, do nothing
    }
    
    // Starred Changes (placeholder)
    
    /**
     * Get starred changes.
     * GET /a/accounts/{account-id}/starred.changes
     */
    @GetMapping("/{accountId}/starred.changes")
    fun getStarredChanges(@PathVariable accountId: String): List<Map<String, Any>> {
        // For now, return empty list
        return emptyList()
    }
    
    // Draft Comments Management (placeholder)
    
    /**
     * Delete draft comments.
     * POST /a/accounts/{account-id}/drafts:delete
     */
    @PostMapping("/{accountId}/drafts:delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteDraftComments(@PathVariable accountId: String) {
        // For now, do nothing
    }
    
    // External IDs (placeholder)
    
    /**
     * Get external IDs.
     * GET /a/accounts/{account-id}/external.ids
     */
    @GetMapping("/{accountId}/external.ids")
    fun getExternalIds(@PathVariable accountId: String): List<Map<String, Any>> {
        // For now, return empty list
        return emptyList()
    }
    
    /**
     * Delete external IDs.
     * POST /a/accounts/{account-id}/external.ids:delete
     */
    @PostMapping("/{accountId}/external.ids:delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteExternalIds(
        @PathVariable accountId: String,
        @RequestBody input: List<String>
    ) {
        // For now, do nothing
    }
    
    // GPG Keys (placeholder)
    
    /**
     * Get GPG keys.
     * GET /a/accounts/{account-id}/gpgkeys
     */
    @GetMapping("/{accountId}/gpgkeys")
    fun getGpgKeys(@PathVariable accountId: String): Map<String, Any> {
        // For now, return empty map
        return emptyMap()
    }
    
    /**
     * Add GPG keys.
     * POST /a/accounts/{account-id}/gpgkeys
     */
    @PostMapping("/{accountId}/gpgkeys")
    fun addGpgKeys(
        @PathVariable accountId: String,
        @RequestBody input: List<String>
    ): Map<String, Any> {
        // For now, return empty map
        return emptyMap()
    }
    
    /**
     * Get specific GPG key.
     * GET /a/accounts/{account-id}/gpgkeys/{gpg-key-id}
     */
    @GetMapping("/{accountId}/gpgkeys/{gpgKeyId}")
    fun getGpgKey(
        @PathVariable accountId: String,
        @PathVariable gpgKeyId: String
    ): Map<String, Any> {
        // For now, return empty map
        return emptyMap()
    }
    
    /**
     * Delete GPG key.
     * DELETE /a/accounts/{account-id}/gpgkeys/{gpg-key-id}
     */
    @DeleteMapping("/{accountId}/gpgkeys/{gpgKeyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteGpgKey(
        @PathVariable accountId: String,
        @PathVariable gpgKeyId: String
    ) {
        // For now, do nothing
    }
    
    // Account Indexing (placeholder)
    
    /**
     * Index account.
     * POST /a/accounts/{account-id}/index
     */
    @PostMapping("/{accountId}/index")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun indexAccount(@PathVariable accountId: String) {
        // For now, do nothing
    }
    
    // Avatar (placeholder - returns 404 as per legacy API)
    
    /**
     * Get avatar.
     * GET /a/accounts/{account-id}/avatar
     */
    @GetMapping("/{accountId}/avatar")
    fun getAvatar(@PathVariable accountId: String): ResponseEntity<String> {
        return ResponseEntity.notFound().build()
    }
    
    /**
     * Get avatar change URL.
     * GET /a/accounts/{account-id}/avatar.change.url
     */
    @GetMapping("/{accountId}/avatar.change.url")
    fun getAvatarChangeUrl(@PathVariable accountId: String): ResponseEntity<String> {
        return ResponseEntity.notFound().build()
    }
    
    // OAuth Token (placeholder - returns 404 as per legacy API)
    
    /**
     * Get OAuth token.
     * GET /a/accounts/{account-id}/oauthtoken
     */
    @GetMapping("/{accountId}/oauthtoken")
    fun getOAuthToken(@PathVariable accountId: String): ResponseEntity<String> {
        return ResponseEntity.notFound().build()
    }
    
    // Agreements (placeholder)
    
    /**
     * Get agreements.
     * GET /a/accounts/{account-id}/agreements
     */
    @GetMapping("/{accountId}/agreements")
    fun getAgreements(@PathVariable accountId: String): List<Map<String, Any>> {
        // For now, return empty list
        return emptyList()
    }
    
    /**
     * Sign agreement.
     * PUT /a/accounts/{account-id}/agreements
     */
    @PutMapping("/{accountId}/agreements")
    fun signAgreement(
        @PathVariable accountId: String,
        @RequestBody input: Map<String, Any>
    ): List<Map<String, Any>> {
        // For now, return empty list
        return emptyList()
    }
}
