package ai.fluxuate.gerrit.api.dto

/**
 * Input for adding reviewers to a change.
 */
data class ReviewerInput(
    val reviewer: String,
    val state: ReviewerState? = ReviewerState.REVIEWER,
    val confirmed: Boolean = false,
    val notify: NotifyHandling? = NotifyHandling.ALL,
    val notify_details: Map<RecipientType, NotifyInfo> = emptyMap()
)

/**
 * Input for adding multiple reviewers to a change.
 */
data class AddReviewerInput(
    val reviewers: List<ReviewerInput>
)

/**
 * Input for removing a reviewer from a change.
 */
data class DeleteReviewerInput(
    val notify: NotifyHandling? = NotifyHandling.ALL,
    val notify_details: Map<RecipientType, NotifyInfo> = emptyMap()
)

/**
 * Result of adding a reviewer.
 */
data class AddReviewerResult(
    val input: String,
    val reviewers: List<AccountInfo> = emptyList(),
    val ccs: List<AccountInfo> = emptyList(),
    val error: String? = null,
    val confirm: Boolean? = null
)

/**
 * Input for suggesting reviewers.
 */
data class SuggestReviewersInput(
    val query: String,
    val n: Int? = null,
    val exclude_groups: Boolean = false,
    val project: String? = null
)

/**
 * Suggested reviewer information.
 */
data class SuggestedReviewerInfo(
    val account: AccountInfo? = null,
    val group: GroupInfo? = null,
    val count: Int = 1
)

/**
 * Group information for suggested reviewers.
 */
data class GroupInfo(
    val id: String,
    val name: String? = null,
    val url: String? = null,
    val options: GroupOptionsInfo? = null,
    val description: String? = null,
    val group_id: Int? = null,
    val owner: String? = null,
    val owner_id: String? = null,
    val created_on: String? = null,
    val more_groups: Boolean? = null,
    val members: List<AccountInfo> = emptyList(),
    val includes: List<GroupInfo> = emptyList()
)

/**
 * Group options information.
 */
data class GroupOptionsInfo(
    val visible_to_all: Boolean = false
)
