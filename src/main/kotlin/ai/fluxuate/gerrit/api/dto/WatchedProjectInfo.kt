package ai.fluxuate.gerrit.api.dto

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * The ProjectWatchInfo entity contains information about a project watch.
 */
data class ProjectWatchInfo(
    @JsonProperty("project")
    val project: String,
    
    @JsonProperty("filter")
    val filter: String? = null,
    
    @JsonProperty("notify_new_changes")
    val notifyNewChanges: Boolean? = null,
    
    @JsonProperty("notify_new_patch_sets")
    val notifyNewPatchSets: Boolean? = null,
    
    @JsonProperty("notify_all_comments")
    val notifyAllComments: Boolean? = null,
    
    @JsonProperty("notify_submitted_changes")
    val notifySubmittedChanges: Boolean? = null,
    
    @JsonProperty("notify_abandoned_changes")
    val notifyAbandonedChanges: Boolean? = null
)

/**
 * Input for adding/updating watched projects.
 */
data class WatchedProjectInput(
    @JsonProperty("project")
    val project: String,
    
    @JsonProperty("filter")
    val filter: String? = null,
    
    @JsonProperty("notify_new_changes")
    val notifyNewChanges: Boolean? = null,
    
    @JsonProperty("notify_new_patch_sets")
    val notifyNewPatchSets: Boolean? = null,
    
    @JsonProperty("notify_all_comments")
    val notifyAllComments: Boolean? = null,
    
    @JsonProperty("notify_submitted_changes")
    val notifySubmittedChanges: Boolean? = null,
    
    @JsonProperty("notify_abandoned_changes")
    val notifyAbandonedChanges: Boolean? = null
)
