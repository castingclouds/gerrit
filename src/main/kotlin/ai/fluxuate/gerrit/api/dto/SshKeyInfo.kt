package ai.fluxuate.gerrit.api.dto

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * The SshKeyInfo entity contains information about an SSH key of an account.
 */
data class SshKeyInfo(
    @JsonProperty("seq")
    val seq: Int,
    
    @JsonProperty("ssh_public_key")
    val sshPublicKey: String,
    
    @JsonProperty("encoded_key")
    val encodedKey: String,
    
    @JsonProperty("algorithm")
    val algorithm: String,
    
    @JsonProperty("comment")
    val comment: String? = null,
    
    @JsonProperty("valid")
    val valid: Boolean
)

/**
 * The AddSshKeyInput entity contains information for adding an SSH key.
 */
data class AddSshKeyInput(
    @JsonProperty("ssh_public_key")
    val sshPublicKey: String
)
