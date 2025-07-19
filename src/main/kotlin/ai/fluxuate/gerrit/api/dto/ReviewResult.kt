package ai.fluxuate.gerrit.api.dto

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Result of a review operation.
 */
data class ReviewResult(
    /**
     * Map of label names to label values that were applied.
     */
    val labels: Map<String, Int>? = null,
    
    /**
     * Map of reviewer states that were updated.
     */
    val reviewers: Map<String, AddReviewerResult>? = null,
    
    /**
     * Whether the change is ready for review.
     */
    val ready: Boolean? = null,
    
    /**
     * Error message if the review operation failed.
     */
    val error: String? = null
)
