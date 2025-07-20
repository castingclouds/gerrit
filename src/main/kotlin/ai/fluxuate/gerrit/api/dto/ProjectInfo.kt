package ai.fluxuate.gerrit.api.dto

import ai.fluxuate.gerrit.api.dto.WebLinkInfo
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

/**
 * Project information DTO matching legacy Gerrit API structure.
 */
data class ProjectInfo @JsonCreator constructor(
    @JsonProperty("id") val id: String? = null,
    @JsonProperty("name") val name: String,
    @JsonProperty("parent") val parent: String? = null,
    @JsonProperty("description") val description: String? = null,
    @JsonProperty("state") val state: ProjectState? = null,
    @JsonProperty("branches") val branches: Map<String, String>? = null,
    @JsonProperty("labels") val labels: Map<String, LabelTypeInfo>? = null,
    @JsonProperty("web_links") val webLinks: List<WebLinkInfo>? = null,
    @JsonProperty("config_visible") val configVisible: Boolean? = null
)

/**
 * Project state enumeration.
 */
enum class ProjectState {
    ACTIVE,
    READ_ONLY,
    HIDDEN
}

/**
 * Label type information.
 */
data class LabelTypeInfo(
    val values: Map<String, String>? = null,
    @JsonProperty("default_value")
    val defaultValue: Int? = null,
    val function: String? = null,
    @JsonProperty("copy_condition")
    val copyCondition: String? = null,
    @JsonProperty("copy_min_score")
    val copyMinScore: Boolean? = null,
    @JsonProperty("copy_max_score")
    val copyMaxScore: Boolean? = null,
    @JsonProperty("copy_all_scores_if_no_change")
    val copyAllScoresIfNoChange: Boolean? = null,
    @JsonProperty("copy_all_scores_if_no_code_change")
    val copyAllScoresIfNoCodeChange: Boolean? = null,
    @JsonProperty("copy_all_scores_on_trivial_rebase")
    val copyAllScoresOnTrivialRebase: Boolean? = null,
    @JsonProperty("copy_all_scores_on_merge_first_parent_update")
    val copyAllScoresOnMergeFirstParentUpdate: Boolean? = null,
    @JsonProperty("copy_values")
    val copyValues: List<Int>? = null,
    @JsonProperty("allow_post_submit")
    val allowPostSubmit: Boolean? = null,
    @JsonProperty("ignore_self_approval")
    val ignoreSelfApproval: Boolean? = null
)
