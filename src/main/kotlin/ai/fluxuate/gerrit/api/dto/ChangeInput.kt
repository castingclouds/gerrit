package ai.fluxuate.gerrit.api.dto

/**
 * Input for creating a new change.
 */
data class ChangeInput(
    val project: String,
    val branch: String,
    val subject: String,
    val topic: String? = null,
    val status: ChangeStatus? = null,
    val is_private: Boolean = false,
    val work_in_progress: Boolean = false,
    val base_change: String? = null,
    val base_commit: String? = null,
    val new_branch: Boolean = false,
    val validation_options: Map<String, String> = emptyMap(),
    val custom_key_values: Map<String, String> = emptyMap(),
    val author: AccountInput? = null,
    val notify: NotifyHandling? = null,
    val notify_details: Map<RecipientType, NotifyInfo> = emptyMap(),
    val merge: MergeInput? = null
)

/**
 * Account input for change operations.
 */
data class AccountInput(
    val name: String? = null,
    val email: String? = null
)

/**
 * Notification handling options.
 */
enum class NotifyHandling {
    NONE,
    OWNER,
    OWNER_REVIEWERS,
    ALL
}

/**
 * Recipient type for notifications.
 */
enum class RecipientType {
    TO,
    CC,
    BCC
}

/**
 * Notification information.
 */
data class NotifyInfo(
    val accounts: List<String> = emptyList()
)

/**
 * Merge input for creating changes.
 */
data class MergeInput(
    val source: String,
    val source_branch: String? = null,
    val strategy: String? = null,
    val allow_conflicts: Boolean = false
)

/**
 * Input for abandoning a change.
 */
data class AbandonInput(
    val message: String? = null,
    val notify: NotifyHandling? = null,
    val notify_details: Map<RecipientType, NotifyInfo> = emptyMap()
)

/**
 * Input for restoring a change.
 */
data class RestoreInput(
    val message: String? = null,
    val notify: NotifyHandling? = null,
    val notify_details: Map<RecipientType, NotifyInfo> = emptyMap()
)

/**
 * Input for submitting a change.
 */
data class SubmitInput(
    val notify: NotifyHandling? = null,
    val notify_details: Map<RecipientType, NotifyInfo> = emptyMap(),
    val on_behalf_of: String? = null
)

/**
 * Input for rebasing a change.
 */
data class RebaseInput(
    val base: String? = null,
    val strategy: String? = null,
    val allow_conflicts: Boolean = false,
    val validation_options: Map<String, String> = emptyMap(),
    val on_behalf_of: String? = null
)

/**
 * Input for cherry-picking a change.
 */
data class CherryPickInput(
    val message: String? = null,
    val destination: String,
    val base: String? = null,
    val parent: Int? = null,
    val notify: NotifyHandling? = null,
    val notify_details: Map<RecipientType, NotifyInfo> = emptyMap(),
    val keep_reviewers: Boolean = false,
    val allow_conflicts: Boolean = false,
    val validation_options: Map<String, String> = emptyMap()
)

/**
 * Input for moving a change.
 */
data class MoveInput(
    val destination_branch: String,
    val message: String? = null,
    val keep_all_votes: Boolean = false
)

/**
 * Input for reverting a change.
 */
data class RevertInput(
    val message: String? = null,
    val notify: NotifyHandling? = null,
    val notify_details: Map<RecipientType, NotifyInfo> = emptyMap(),
    val topic: String? = null,
    val work_in_progress: Boolean = false
)

/**
 * Input for setting a topic.
 */
data class TopicInput(
    val topic: String? = null
)

/**
 * Input for setting private status.
 */
data class PrivateInput(
    val message: String? = null
)

/**
 * Input for setting work-in-progress status.
 */
data class WorkInProgressInput(
    val message: String? = null
)

/**
 * Input for setting ready-for-review status.
 */
data class ReadyForReviewInput(
    val message: String? = null
)
