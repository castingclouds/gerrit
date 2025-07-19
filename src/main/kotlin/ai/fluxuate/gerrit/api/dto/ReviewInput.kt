package ai.fluxuate.gerrit.api.dto

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Input for reviewing a revision.
 */
data class ReviewInput(
    /**
     * The message to be added as review comment.
     */
    val message: String? = null,
    
    /**
     * The votes that should be added to the revision as a map that maps the label names to the voting values.
     */
    val labels: Map<String, Int>? = null,
    
    /**
     * Add or remove reviewers from the change.
     */
    val reviewers: List<ReviewerInput>? = null,
    
    /**
     * Whether all draft comments should be published.
     */
    @JsonProperty("drafts")
    val publishDrafts: String? = null,
    
    /**
     * Notify handling that defines to whom email notifications should be sent after the review is stored.
     */
    val notify: String? = null,
    
    /**
     * Additional information about whom to notify about the update as a map of recipient type to NotifyInfo entity.
     */
    @JsonProperty("notify_details")
    val notifyDetails: Map<String, NotifyInfo>? = null,
    
    /**
     * If true, then the review request is done on behalf of the uploader of the patch set.
     */
    @JsonProperty("on_behalf_of")
    val onBehalfOf: Long? = null,
    
    /**
     * Comments to be added as map that maps a file path to a list of CommentInput entities.
     */
    val comments: Map<String, List<CommentInput>>? = null,
    
    /**
     * Robot comments to be added as map that maps a file path to a list of RobotCommentInput entities.
     */
    @JsonProperty("robot_comments")
    val robotComments: Map<String, List<RobotCommentInput>>? = null,
    
    /**
     * How to process draft comments already in the database that were not also described in this input.
     */
    @JsonProperty("ignore_automatic_attention_set_rules")
    val ignoreAutomaticAttentionSetRules: Boolean? = null,
    
    /**
     * Updates to the attention set.
     */
    @JsonProperty("add_to_attention_set")
    val addToAttentionSet: List<AttentionSetInput>? = null,
    
    /**
     * Updates to the attention set.
     */
    @JsonProperty("remove_from_attention_set")
    val removeFromAttentionSet: List<AttentionSetInput>? = null,
    
    /**
     * Whether the change should be marked as ready for review.
     */
    val ready: Boolean? = null,
    
    /**
     * Whether the change should be marked as work in progress.
     */
    @JsonProperty("work_in_progress")
    val workInProgress: Boolean? = null,
    
    /**
     * Custom keyed values to add to the change.
     */
    @JsonProperty("custom_keyed_values")
    val customKeyedValues: Map<String, String>? = null
)

/**
 * Input for adding comments.
 */
data class CommentInput(
    /**
     * The comment message.
     */
    val message: String,
    
    /**
     * The file path for the comment.
     */
    val path: String? = null,
    
    /**
     * The line number for the comment.
     */
    val line: Int? = null,
    
    /**
     * The range within the line for the comment.
     */
    val range: CommentRange? = null,
    
    /**
     * The side of the file for the comment.
     */
    val side: CommentSide? = null,
    
    /**
     * The parent patch set number (for base vs revision comparisons).
     */
    val parent: Int? = null,
    
    /**
     * Comment ID this comment is in reply to.
     */
    @JsonProperty("in_reply_to")
    val inReplyTo: String? = null,
    
    /**
     * Tag for the comment.
     */
    val tag: String? = null,
    
    /**
     * Whether this comment is unresolved.
     */
    val unresolved: Boolean? = null
)

/**
 * Input for adding robot comments.
 */
data class RobotCommentInput(
    /**
     * The comment message.
     */
    val message: String,
    
    /**
     * The robot ID that generated this comment.
     */
    @JsonProperty("robot_id")
    val robotId: String,
    
    /**
     * The robot run ID.
     */
    @JsonProperty("robot_run_id")
    val robotRunId: String,
    
    /**
     * The line number for the comment.
     */
    val line: Int? = null,
    
    /**
     * The range within the line for the comment.
     */
    val range: CommentRange? = null,
    
    /**
     * The side of the file for the comment.
     */
    val side: String? = null,
    
    /**
     * Suggested fixes for this robot comment.
     */
    val fixes: List<FixSuggestion>? = null
)

/**
 * Input for attention set updates.
 */
data class AttentionSetInput(
    /**
     * The account to add/remove from attention set.
     */
    val user: String,
    
    /**
     * The reason for the attention set update.
     */
    val reason: String
)

/**
 * Suggested fix for robot comments.
 */
data class FixSuggestion(
    /**
     * Description of the fix.
     */
    val description: String,
    
    /**
     * List of replacements to apply.
     */
    val replacements: List<FixReplacement>
)

/**
 * Replacement for a fix suggestion.
 */
data class FixReplacement(
    /**
     * Path of the file to modify.
     */
    val path: String,
    
    /**
     * Range to replace.
     */
    val range: CommentRange,
    
    /**
     * Replacement text.
     */
    val replacement: String
)
