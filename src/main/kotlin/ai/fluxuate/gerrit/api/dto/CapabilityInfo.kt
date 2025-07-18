package ai.fluxuate.gerrit.api.dto

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * The CapabilityInfo entity contains information about the global capabilities of an account.
 */
data class CapabilityInfo(
    @JsonProperty("accessDatabase")
    val accessDatabase: Boolean? = null,
    
    @JsonProperty("administrateServer")
    val administrateServer: Boolean? = null,
    
    @JsonProperty("createAccount")
    val createAccount: Boolean? = null,
    
    @JsonProperty("createGroup")
    val createGroup: Boolean? = null,
    
    @JsonProperty("createProject")
    val createProject: Boolean? = null,
    
    @JsonProperty("emailReviewers")
    val emailReviewers: Boolean? = null,
    
    @JsonProperty("flushCaches")
    val flushCaches: Boolean? = null,
    
    @JsonProperty("killTask")
    val killTask: Boolean? = null,
    
    @JsonProperty("maintainServer")
    val maintainServer: Boolean? = null,
    
    @JsonProperty("priority")
    val priority: QueueType? = null,
    
    @JsonProperty("queryLimit")
    val queryLimit: QueryLimitInfo? = null,
    
    @JsonProperty("runAs")
    val runAs: Boolean? = null,
    
    @JsonProperty("runGC")
    val runGC: Boolean? = null,
    
    @JsonProperty("streamEvents")
    val streamEvents: Boolean? = null,
    
    @JsonProperty("viewAllAccounts")
    val viewAllAccounts: Boolean? = null,
    
    @JsonProperty("viewCaches")
    val viewCaches: Boolean? = null,
    
    @JsonProperty("viewConnections")
    val viewConnections: Boolean? = null,
    
    @JsonProperty("viewPlugins")
    val viewPlugins: Boolean? = null,
    
    @JsonProperty("viewQueue")
    val viewQueue: Boolean? = null
)

/**
 * The QueryLimitInfo entity contains information about query limits.
 */
data class QueryLimitInfo(
    @JsonProperty("min")
    val min: Int,
    
    @JsonProperty("max")
    val max: Int
)

/**
 * Queue type for priority capability.
 */
enum class QueueType {
    INTERACTIVE,
    BATCH
}
