package ai.fluxuate.gerrit.api.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

/**
 * Team information DTO for API responses
 */
data class TeamInfo(
    @JsonProperty("id")
    val id: String,
    
    @JsonProperty("name")
    val name: String,
    
    @JsonProperty("url")
    val url: String? = null,
    
    @JsonProperty("options")
    val options: TeamOptionsInfo? = null,
    
    @JsonProperty("description")
    val description: String? = null,
    
    @JsonProperty("group_id")
    val groupId: Int? = null,
    
    @JsonProperty("owner")
    val owner: String? = null,
    
    @JsonProperty("owner_id")
    val ownerId: String? = null,
    
    @JsonProperty("created_on")
    val createdOn: Instant? = null,
    
    @JsonProperty("_more_groups")
    val moreGroups: Boolean? = null,
    
    @JsonProperty("members")
    val members: List<AccountInfo>? = null,
    
    @JsonProperty("includes")
    val includes: List<TeamInfo>? = null
)

/**
 * Team options information
 */
data class TeamOptionsInfo(
    @JsonProperty("visible_to_all")
    val visibleToAll: Boolean = false
)

/**
 * Team input DTO for API requests
 */
data class TeamInput(
    @JsonProperty("name")
    val name: String? = null,
    
    @JsonProperty("uuid")
    val uuid: String? = null,
    
    @JsonProperty("description")
    val description: String? = null,
    
    @JsonProperty("visible_to_all")
    val visibleToAll: Boolean? = null,
    
    @JsonProperty("owner_id")
    val ownerId: String? = null,
    
    @JsonProperty("members")
    val members: List<String>? = null
)

/**
 * Members input DTO for adding/removing team members
 */
data class MembersInput(
    @JsonProperty("_one_member")
    val oneMember: String? = null,
    
    @JsonProperty("members")
    val members: List<String>? = null
)

/**
 * Teams input DTO for adding/removing subteams
 */
data class TeamsInput(
    @JsonProperty("_one_group")
    val oneGroup: String? = null,
    
    @JsonProperty("groups")
    val groups: List<String>? = null
)
