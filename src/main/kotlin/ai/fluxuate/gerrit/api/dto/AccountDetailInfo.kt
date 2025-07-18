package ai.fluxuate.gerrit.api.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

/**
 * The AccountDetailInfo entity contains detailed information about an account.
 * This extends AccountInfo with additional fields for detailed account views.
 */
data class AccountDetailInfo(
    @JsonProperty("_account_id")
    val accountId: Int,
    
    @JsonProperty("name")
    val name: String? = null,
    
    @JsonProperty("display_name")
    val displayName: String? = null,
    
    @JsonProperty("email")
    val email: String? = null,
    
    @JsonProperty("secondary_emails")
    val secondaryEmails: List<String>? = null,
    
    @JsonProperty("username")
    val username: String? = null,
    
    @JsonProperty("avatars")
    val avatars: List<AvatarInfo>? = null,
    
    @JsonProperty("status")
    val status: String? = null,
    
    @JsonProperty("inactive")
    val inactive: Boolean? = null,
    
    @JsonProperty("tags")
    val tags: List<String>? = null,
    
    // Additional fields for detailed view
    @JsonProperty("registered_on")
    val registeredOn: Instant? = null,
    
    @JsonProperty("_more_accounts")
    val moreAccounts: Boolean? = null
)
