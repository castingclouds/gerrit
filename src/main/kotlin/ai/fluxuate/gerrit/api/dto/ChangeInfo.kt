package ai.fluxuate.gerrit.api.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

/**
 * Information about a change, matching Gerrit's ChangeInfo structure.
 */
data class ChangeInfo(
    val id: String,
    val project: String,
    val branch: String,
    val topic: String? = null,
    val assignee: AccountInfo? = null,
    val hashtags: List<String> = emptyList(),
    val changeId: String,
    val subject: String,
    val status: ChangeStatus,
    val created: Instant,
    val updated: Instant,
    val submitted: Instant? = null,
    val submitter: AccountInfo? = null,
    val starred: Boolean = false,
    val stars: List<String> = emptyList(),
    val reviewed: Boolean = false,
    val submit_type: SubmitType? = null,
    val mergeable: Boolean? = null,
    val submittable: Boolean? = null,
    val insertions: Int = 0,
    val deletions: Int = 0,
    val total_comment_count: Int = 0,
    val unresolved_comment_count: Int = 0,
    val has_review_started: Boolean = false,
    val meta_rev_id: String? = null,
    val number: Long,
    val owner: AccountInfo,
    val actions: Map<String, ActionInfo> = emptyMap(),
    val labels: Map<String, LabelInfo> = emptyMap(),
    val permitted_labels: Map<String, List<String>> = emptyMap(),
    val removable_reviewers: List<AccountInfo> = emptyList(),
    val reviewers: ReviewerInfo = ReviewerInfo(),
    val pending_reviewers: ReviewerInfo = ReviewerInfo(),
    val reviewer_updates: List<ReviewerUpdateInfo> = emptyList(),
    val messages: List<ChangeMessageInfo> = emptyList(),
    val current_revision: String? = null,
    val revisions: Map<String, RevisionInfo> = emptyMap(),
    val tracking_ids: List<TrackingIdInfo> = emptyList(),
    @JsonProperty("_number") val _number: Long = number,
    val more_changes: Boolean? = null,
    val problems: List<ProblemInfo> = emptyList(),
    val is_private: Boolean = false,
    val work_in_progress: Boolean = false,
    val has_review_started_: Boolean = has_review_started,
    val revert_of: Long? = null,
    val submission_id: String? = null,
    val cherry_pick_of_change: Long? = null,
    val cherry_pick_of_patch_set: Int? = null,
    val contains_git_conflicts: Boolean? = null
)

/**
 * Change status enumeration.
 */
enum class ChangeStatus {
    NEW,
    MERGED,
    ABANDONED
}

/**
 * Submit type enumeration.
 */
enum class SubmitType {
    MERGE_IF_NECESSARY,
    FAST_FORWARD_ONLY,
    REBASE_IF_NECESSARY,
    REBASE_ALWAYS,
    MERGE_ALWAYS,
    CHERRY_PICK
}

/**
 * Account information.
 */
data class AccountInfo(
    val _account_id: Long,
    val name: String? = null,
    val display_name: String? = null,
    val email: String? = null,
    val secondary_emails: List<String> = emptyList(),
    val username: String? = null,
    val avatars: List<AvatarInfo> = emptyList(),
    val _more_accounts: Boolean? = null,
    val status: String? = null,
    val inactive: Boolean? = null,
    val tags: List<String> = emptyList()
) {
    // CamelCase aliases for Accounts API compatibility
    val accountId: Long get() = _account_id
    val displayName: String? get() = display_name
    val secondaryEmails: List<String> get() = secondary_emails
    val moreAccounts: Boolean? get() = _more_accounts
}

/**
 * Avatar information.
 */
data class AvatarInfo(
    val url: String,
    val height: Int? = null,
    val width: Int? = null
)

/**
 * Action information.
 */
data class ActionInfo(
    val method: String? = null,
    val label: String? = null,
    val title: String? = null,
    val enabled: Boolean? = null
)

/**
 * Label information.
 */
data class LabelInfo(
    val optional: Boolean? = null,
    val approved: AccountInfo? = null,
    val rejected: AccountInfo? = null,
    val recommended: AccountInfo? = null,
    val disliked: AccountInfo? = null,
    val blocking: Boolean? = null,
    val value: Int? = null,
    val default_value: Int? = null,
    val values: Map<String, String> = emptyMap(),
    val all: List<ApprovalInfo> = emptyList()
)

/**
 * Approval information.
 */
data class ApprovalInfo(
    val value: Int,
    val permitted_voting_range: VotingRangeInfo? = null,
    val date: Instant? = null,
    val tag: String? = null,
    val post_submit: Boolean? = null
) {
    // Extends AccountInfo
    val _account_id: Long = 0
    val name: String? = null
    val display_name: String? = null
    val email: String? = null
    val secondary_emails: List<String> = emptyList()
    val username: String? = null
    val avatars: List<AvatarInfo> = emptyList()
    val _more_accounts: Boolean? = null
    val status: String? = null
    val inactive: Boolean? = null
    val tags: List<String> = emptyList()
}

/**
 * Voting range information.
 */
data class VotingRangeInfo(
    val min: Int,
    val max: Int
)

/**
 * Reviewer information.
 */
data class ReviewerInfo(
    val REVIEWER: List<AccountInfo> = emptyList(),
    val CC: List<AccountInfo> = emptyList(),
    val REMOVED: List<AccountInfo> = emptyList()
)

/**
 * Reviewer update information.
 */
data class ReviewerUpdateInfo(
    val updated: Instant,
    val updated_by: AccountInfo,
    val reviewer: AccountInfo,
    val state: ReviewerState
)

/**
 * Reviewer state enumeration.
 */
enum class ReviewerState {
    REVIEWER,
    CC,
    REMOVED
}

/**
 * Change message information.
 */
data class ChangeMessageInfo(
    val id: String,
    val author: AccountInfo? = null,
    val real_author: AccountInfo? = null,
    val date: Instant,
    val message: String,
    val tag: String? = null,
    val _revision_number: Int? = null
)

/**
 * Revision information.
 */
data class RevisionInfo(
    val kind: String,
    val _number: Int,
    val created: Instant,
    val uploader: AccountInfo,
    val ref: String,
    val fetch: Map<String, FetchInfo> = emptyMap(),
    val commit: CommitInfo? = null,
    val actions: Map<String, ActionInfo> = emptyMap(),
    val reviewed: Boolean? = null,
    val commit_with_footers: Boolean? = null,
    val push_certificate: PushCertificateInfo? = null,
    val description: String? = null
)

/**
 * Fetch information.
 */
data class FetchInfo(
    val url: String,
    val ref: String,
    val commands: Map<String, String> = emptyMap()
)

/**
 * Commit information.
 */
data class CommitInfo(
    val commit: String,
    val parents: List<CommitInfo> = emptyList(),
    val author: GitPersonInfo,
    val committer: GitPersonInfo,
    val subject: String,
    val message: String
)

/**
 * Git person information.
 */
data class GitPersonInfo(
    val name: String,
    val email: String,
    val date: Instant,
    val tz: Int
)

/**
 * Push certificate information.
 */
data class PushCertificateInfo(
    val certificate: String,
    val key: GpgKeyInfo
)

/**
 * GPG key information.
 */
data class GpgKeyInfo(
    val status: String,
    val problems: List<String> = emptyList()
)

/**
 * Tracking ID information.
 */
data class TrackingIdInfo(
    val system: String,
    val id: String
)

/**
 * Problem information.
 */
data class ProblemInfo(
    val message: String,
    val status: String? = null,
    val outcome: String? = null
)
