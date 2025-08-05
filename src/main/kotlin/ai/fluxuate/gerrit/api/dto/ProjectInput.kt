package ai.fluxuate.gerrit.api.dto

import ai.fluxuate.gerrit.api.dto.SubmitType
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

/**
 * Project creation/update input DTO.
 */
data class ProjectInput(
    @field:NotBlank(message = "Project name is required")
    @field:Pattern(regexp = "^[a-zA-Z0-9._/-]+$", message = "Invalid project name format")
    val name: String,
    val parent: String? = null,
    val description: String? = null,
    @JsonProperty("create_empty_commit")
    val createEmptyCommit: Boolean? = null,
    val defaultBranch: String? = null,
    val branches: List<String>? = null,
    val owners: List<String>? = null,
    @JsonProperty("use_contributor_agreements")
    val useContributorAgreements: InheritableBoolean? = null,
    @JsonProperty("use_signed_off_by")
    val useSignedOffBy: InheritableBoolean? = null,
    @JsonProperty("use_content_merge")
    val useContentMerge: InheritableBoolean? = null,
    @JsonProperty("require_change_id")
    val requireChangeId: InheritableBoolean? = null,
    @JsonProperty("reject_implicit_merges")
    val rejectImplicitMerges: InheritableBoolean? = null,
    @JsonProperty("enable_signed_push")
    val enableSignedPush: InheritableBoolean? = null,
    @JsonProperty("require_signed_push")
    val requireSignedPush: InheritableBoolean? = null,
    @JsonProperty("max_object_size_limit")
    val maxObjectSizeLimit: String? = null,
    @JsonProperty("submit_type")
    val submitType: SubmitType? = null,
    val state: ProjectState? = null,
    val config: ConfigInput? = null
)

/**
 * Project configuration input.
 */
data class ConfigInput(
    val description: String? = null,
    @JsonProperty("use_contributor_agreements")
    val useContributorAgreements: InheritableBoolean? = null,
    @JsonProperty("use_content_merge")
    val useContentMerge: InheritableBoolean? = null,
    @JsonProperty("use_signed_off_by")
    val useSignedOffBy: InheritableBoolean? = null,
    @JsonProperty("require_change_id")
    val requireChangeId: InheritableBoolean? = null,
    @JsonProperty("reject_implicit_merges")
    val rejectImplicitMerges: InheritableBoolean? = null,
    @JsonProperty("private_by_default")
    val privateByDefault: InheritableBoolean? = null,
    @JsonProperty("work_in_progress_by_default")
    val workInProgressByDefault: InheritableBoolean? = null,
    @JsonProperty("enable_signed_push")
    val enableSignedPush: InheritableBoolean? = null,
    @JsonProperty("require_signed_push")
    val requireSignedPush: InheritableBoolean? = null,
    @JsonProperty("reject_empty_commit")
    val rejectEmptyCommit: InheritableBoolean? = null,
    @JsonProperty("max_object_size_limit")
    val maxObjectSizeLimit: String? = null,
    @JsonProperty("submit_type")
    val submitType: SubmitType? = null,
    val state: ProjectState? = null,
    @JsonProperty("commentlinks")
    val commentLinks: Map<String, CommentLinkInfo>? = null,
    @JsonProperty("plugin_config")
    val pluginConfig: Map<String, Map<String, ConfigValue>>? = null
)

/**
 * Inheritable boolean enumeration.
 */
enum class InheritableBoolean {
    TRUE,
    FALSE,
    INHERIT
}

/**
 * Comment link information.
 */
data class CommentLinkInfo(
    val match: String,
    val link: String? = null,
    val html: String? = null,
    val enabled: Boolean? = null
)

/**
 * Configuration value.
 */
data class ConfigValue(
    val value: String? = null,
    val values: List<String>? = null,
    val editable: Boolean? = null,
    val inheritable: Boolean? = null,
    val configured_value: String? = null,
    val inherited_value: String? = null,
    val permitted_values: List<String>? = null,
    val type: ConfigParameterType? = null,
    val description: String? = null,
    val warning: String? = null
)

/**
 * Configuration parameter type.
 */
enum class ConfigParameterType {
    STRING,
    INT,
    LONG,
    BOOLEAN,
    LIST,
    ARRAY
}

/**
 * Simple input DTOs for specific operations.
 */
data class DescriptionInput(
    val description: String? = null,
    @JsonProperty("commit_message")
    val commitMessage: String? = null
)

data class ParentInput(
    val parent: String,
    @JsonProperty("commit_message")
    val commitMessage: String? = null
)

data class HeadInput(
    val ref: String
)
