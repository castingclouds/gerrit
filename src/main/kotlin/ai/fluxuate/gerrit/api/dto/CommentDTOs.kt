package ai.fluxuate.gerrit.api.dto

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Input for deleting comments
 */
data class DeleteCommentInput(
    val reason: String? = null
)

/**
 * Input for creating multiple comments in a batch
 */
data class CommentsInput(
    val comments: Map<String, List<CommentInput>>
)

/**
 * Response when creating/updating comments
 */
data class CommentResult(
    val comments: Map<String, List<CommentInfo>>
)
