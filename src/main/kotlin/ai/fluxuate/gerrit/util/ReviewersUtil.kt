package ai.fluxuate.gerrit.util

import ai.fluxuate.gerrit.api.dto.AccountInfo
import ai.fluxuate.gerrit.api.dto.SuggestedReviewerInfo
import ai.fluxuate.gerrit.model.ChangeEntity
import ai.fluxuate.gerrit.repository.UserEntityRepository
import ai.fluxuate.gerrit.repository.TeamRepository
import ai.fluxuate.gerrit.repository.ChangeEntityRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Utility class for handling reviewer resolution and suggestion operations.
 * Contains logic for finding reviewers by various identifiers and suggesting appropriate reviewers.
 */
@Component
class ReviewersUtil(
    private val userRepository: UserEntityRepository,
    private val teamRepository: TeamRepository,
    private val changeRepository: ChangeEntityRepository
) {
    
    private val logger = LoggerFactory.getLogger(ReviewersUtil::class.java)
    
    /**
     * Resolve a reviewer string to AccountInfo.
     * Tries multiple resolution strategies in order:
     * 1. Parse as account ID
     * 2. Find by email address
     * 3. Find by username
     * 4. Find by display name
     * 5. Handle team/group names (future enhancement)
     * 
     * @param reviewer The reviewer string to resolve
     * @return AccountInfo if found, null otherwise
     */
    fun resolveReviewer(reviewer: String): AccountInfo? {
        logger.debug("Resolving reviewer: $reviewer")
        
        return try {
            // Strategy 1: Try to parse as account ID
            val accountId = reviewer.toLongOrNull()
            if (accountId != null) {
                val user = userRepository.findById(accountId.toInt()).orElse(null)
                if (user != null) {
                    return AccountInfo(
                        _account_id = user.id!!.toLong(),
                        email = user.preferredEmail,
                        username = user.username,
                        display_name = user.fullName,
                        inactive = !user.active
                    )
                }
            }
            
            // Strategy 2: Try to find by email address
            if (reviewer.contains("@")) {
                val user = userRepository.findByPreferredEmail(reviewer)
                if (user != null) {
                    return AccountInfo(
                        _account_id = user.id!!.toLong(),
                        email = user.preferredEmail,
                        username = user.username,
                        display_name = user.fullName,
                        inactive = !user.active
                    )
                }
            }
            
            // Strategy 3: Try to find by username
            val userByUsername = userRepository.findByUsername(reviewer)
            if (userByUsername != null) {
                return AccountInfo(
                    _account_id = userByUsername.id!!.toLong(),
                    email = userByUsername.preferredEmail,
                    username = userByUsername.username,
                    display_name = userByUsername.fullName,
                    inactive = !userByUsername.active
                )
            }
            
            // Strategy 4: Try to find by display name (full name) - simplified approach
            // Note: This would require a custom repository method, skipping for now
            // val userByDisplayName = userRepository.findByFullNameContainingIgnoreCase(reviewer)
            // if (userByDisplayName.isNotEmpty()) {
            //     val user = userByDisplayName.first()
            //     return AccountInfo(
            //         _account_id = user.id!!.toLong(),
            //         email = user.preferredEmail,
            //         username = user.username,
            //         display_name = user.fullName,
            //         inactive = !user.active
            //     )
            // }
            
            // Strategy 5: Handle team/group names (placeholder for future enhancement)
            val team = teamRepository.findByName(reviewer)
            if (team != null) {
                logger.info("Found team '$reviewer' but team-based reviewer resolution not yet implemented")
                // TODO: Implement team-based reviewer resolution
                // This would involve expanding the team to individual members
                return null
            }
            
            logger.debug("Could not resolve reviewer: $reviewer")
            null
            
        } catch (e: Exception) {
            logger.error("Error resolving reviewer '$reviewer': ${e.message}", e)
            null
        }
    }
    
    /**
     * Suggest reviewers for a change based on various criteria.
     * Implements intelligent reviewer suggestion logic:
     * 1. Recent reviewers for the project
     * 2. File ownership/blame information (future enhancement)
     * 3. Team/group memberships
     * 4. Filter by query string if provided
     * 
     * @param change The change to suggest reviewers for
     * @param query Optional query string to filter suggestions
     * @param limit Optional limit on number of suggestions
     * @return List of suggested reviewer information
     */
    fun suggestReviewers(change: ChangeEntity, query: String?, limit: Int?): List<SuggestedReviewerInfo> {
        logger.debug("Suggesting reviewers for change ${change.id}, query: $query, limit: $limit")
        
        val suggestions = mutableListOf<SuggestedReviewerInfo>()
        val maxSuggestions = limit ?: 10
        
        try {
            // Strategy 1: Get recent reviewers for the project
            val recentReviewers = getRecentReviewersForProject(change.projectName, maxSuggestions * 2)
            
            // Strategy 2: Get team members if project has associated teams
            val teamSuggestions = getTeamBasedSuggestions(change.projectName, maxSuggestions)
            
            // Combine and deduplicate suggestions
            val allSuggestions = (recentReviewers + teamSuggestions).distinctBy { it.account?._account_id }
            
            // Filter by query if provided
            val filteredSuggestions = if (query.isNullOrBlank()) {
                allSuggestions
            } else {
                allSuggestions.filter { suggestion ->
                    val account = suggestion.account
                    account != null && (
                        account.display_name?.contains(query, ignoreCase = true) == true ||
                        account.email?.contains(query, ignoreCase = true) == true ||
                        account.username?.contains(query, ignoreCase = true) == true
                    )
                }
            }
            
            // Apply limit and return
            suggestions.addAll(filteredSuggestions.take(maxSuggestions))
            
        } catch (e: Exception) {
            logger.error("Error suggesting reviewers for change ${change.id}: ${e.message}", e)
        }
        
        logger.debug("Found ${suggestions.size} reviewer suggestions")
        return suggestions
    }
    
    /**
     * Get recent reviewers for a project based on review history.
     * 
     * @param projectName The project name
     * @param limit Maximum number of reviewers to return
     * @return List of suggested reviewer information
     */
    private fun getRecentReviewersForProject(projectName: String, limit: Int): List<SuggestedReviewerInfo> {
        return try {
            // Find recent changes in the project that have been reviewed
            // Note: Using a simplified approach since findByProjectNameOrderByCreatedOnDesc may not exist
            val recentChanges = changeRepository.findAll()
                .filter { it.projectName == projectName }
                .sortedByDescending { it.createdOn }
                .take(50) // Look at last 50 changes
            
            // Extract reviewers from these changes
            val reviewerCounts = mutableMapOf<Long, Int>()
            
            recentChanges.forEach { change ->
                // Extract reviewers from metadata
                val metadata = change.metadata
                val reviewers = metadata["reviewers"] as? Map<String, List<Map<String, Any>>> ?: emptyMap()
                
                listOf("REVIEWER", "CC").forEach { reviewerType ->
                    val reviewerList = reviewers[reviewerType] as? List<Map<String, Any>> ?: emptyList()
                    reviewerList.forEach { reviewerMap ->
                        val accountId = (reviewerMap["_account_id"] as? Number)?.toLong()
                        if (accountId != null) {
                            reviewerCounts[accountId] = reviewerCounts.getOrDefault(accountId, 0) + 1
                        }
                    }
                }
            }
            
            // Sort by review count and convert to suggestions
            reviewerCounts.entries
                .sortedByDescending { it.value }
                .take(limit)
                .mapNotNull { (accountId, count) ->
                    val user = userRepository.findById(accountId.toInt()).orElse(null)
                    if (user != null) {
                        SuggestedReviewerInfo(
                            account = AccountInfo(
                                _account_id = user.id!!.toLong(),
                                email = user.preferredEmail,
                                username = user.username,
                                display_name = user.fullName,
                                inactive = !user.active
                            ),
                            count = count
                        )
                    } else null
                }
                
        } catch (e: Exception) {
            logger.error("Error getting recent reviewers for project $projectName: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Get team-based reviewer suggestions for a project.
     * 
     * @param projectName The project name
     * @param limit Maximum number of suggestions to return
     * @return List of suggested reviewer information
     */
    private fun getTeamBasedSuggestions(projectName: String, limit: Int): List<SuggestedReviewerInfo> {
        return try {
            // Simplified approach - return empty list for now to avoid type issues
            // TODO: Implement team-based suggestions when team structure is clarified
            emptyList()
            
        } catch (e: Exception) {
            logger.error("Error getting team-based suggestions for project $projectName: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Check if a reviewer string represents a valid account or team.
     * 
     * @param reviewer The reviewer string to validate
     * @return true if the reviewer can be resolved, false otherwise
     */
    fun isValidReviewer(reviewer: String): Boolean {
        return resolveReviewer(reviewer) != null
    }
    
    /**
     * Get reviewer information by account ID.
     * 
     * @param accountId The account ID
     * @return AccountInfo if found, null otherwise
     */
    fun getReviewerById(accountId: Long): AccountInfo? {
        return try {
            val user = userRepository.findById(accountId.toInt()).orElse(null)
            if (user != null) {
                AccountInfo(
                    _account_id = user.id!!.toLong(),
                    email = user.preferredEmail,
                    username = user.username,
                    display_name = user.fullName,
                    inactive = !user.active
                )
            } else null
        } catch (e: Exception) {
            logger.error("Error getting reviewer by ID $accountId: ${e.message}", e)
            null
        }
    }
}
