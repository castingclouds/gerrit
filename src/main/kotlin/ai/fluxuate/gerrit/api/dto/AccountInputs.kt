package ai.fluxuate.gerrit.api.dto

/**
 * Input for setting account name.
 */
data class AccountNameInput(
    val name: String
)

/**
 * Input for setting account status.
 */
data class AccountStatusInput(
    val status: String
)

/**
 * Input for setting display name.
 */
data class DisplayNameInput(
    val displayName: String
)
