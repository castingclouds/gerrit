package ai.fluxuate.gerrit.api.dto

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * The EmailInfo entity contains information about an email address of an account.
 */
data class EmailInfo(
    @JsonProperty("email")
    val email: String,
    
    @JsonProperty("preferred")
    val preferred: Boolean? = null,
    
    @JsonProperty("pending_confirmation")
    val pendingConfirmation: Boolean? = null
)

/**
 * The EmailInput entity contains information for adding an email address.
 */
data class EmailInput(
    @JsonProperty("email")
    val email: String,
    
    @JsonProperty("preferred")
    val preferred: Boolean? = null,
    
    @JsonProperty("no_confirmation")
    val noConfirmation: Boolean? = null
)
