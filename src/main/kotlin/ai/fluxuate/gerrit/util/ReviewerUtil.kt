package ai.fluxuate.gerrit.util

import ai.fluxuate.gerrit.model.UserEntity
import ai.fluxuate.gerrit.model.ChangeEntity
import ai.fluxuate.gerrit.api.dto.AccountInfo
import ai.fluxuate.gerrit.api.dto.SuggestedReviewerInfo
import ai.fluxuate.gerrit.repository.AccountRepository
import ai.fluxuate.gerrit.repository.ChangeEntityRepository
import ai.fluxuate.gerrit.git.GitConfiguration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.data.domain.PageRequest
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import java.io.File
import java.util.*

/**
 * Utility class for reviewer resolution and suggestion operations.
 * Based on official Gerrit patterns for handling reviewer logic, 
 * extracted from ChangeService to reduce service complexity.
 * 
 * This class follows the pattern of official Gerrit's utility classes and provides
 * centralized logic for reviewer-related operations extracted from ChangeService.
 */
@Component
class ReviewerUtil(
    private val accountRepository: AccountRepository,
    private val changeRepository: ChangeEntityRepository,
    private val gitConfiguration: GitConfiguration
) {

    /**
     * Resolve reviewer string to AccountInfo.
     * 
     * This handles multiple resolution strategies:
     * 1. Try to parse as account ID
     * 2. Try to find by email
     * 3. Try to find by username
     * 4. Try to find by display name
     * 5. Handle group names (future enhancement)
     * 
     * @param reviewer The reviewer identifier (ID, email, username, or display name)
     * @return AccountInfo if found, null otherwise
     */
    fun resolveReviewer(reviewer: String): AccountInfo? {
        return try {
            // Strategy 1: Try to parse as account ID
            val accountId = reviewer.toIntOrNull()
            if (accountId != null) {
                val account = accountRepository.findById(accountId).orElse(null)
                if (account != null) {
                    return convertToAccountInfo(account)
                }
            }
            
            // Strategy 2: Try to find by email (exact match)
            if (reviewer.contains("@")) {
                val account = accountRepository.findByPreferredEmail(reviewer).orElse(null)
                if (account != null) {
                    return convertToAccountInfo(account)
                }
            }
            
            // Strategy 3: Try to find by username (exact match)
            val accountByUsername = accountRepository.findByUsername(reviewer).orElse(null)
            if (accountByUsername != null) {
                return convertToAccountInfo(accountByUsername)
            }
            
            // Strategy 4: Try to find by full name (case-insensitive)
            val accountByFullName = accountRepository.findByFullNameContainingIgnoreCase(reviewer, PageRequest.of(0, 1))
            if (accountByFullName.hasContent()) {
                return convertToAccountInfo(accountByFullName.content.first())
            }
            
            // Strategy 5: Partial matches (fuzzy search)
            val partialMatches = findPartialMatches(reviewer)
            if (partialMatches.isNotEmpty()) {
                return convertToAccountInfo(partialMatches.first())
            }
            
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Suggest reviewers for a change based on various criteria.
     * 
     * This implements intelligent reviewer suggestion logic:
     * 1. Recent reviewers for the project
     * 2. File ownership/blame information
     * 3. Team/group memberships
     * 4. Active contributors to similar changes
     * 5. Query string filtering
     * 
     * @param change The change entity
     * @param query Optional search query to filter suggestions
     * @param limit Maximum number of suggestions to return
     * @return List of suggested reviewers
     */
    fun suggestReviewers(change: ChangeEntity, query: String?, limit: Int?): List<SuggestedReviewerInfo> {
        val suggestions = mutableListOf<SuggestedReviewerInfo>()
        val maxLimit = limit ?: 10
        
        try {
            // Strategy 1: Recent reviewers for this project
            val recentReviewers = getRecentProjectReviewers(change.projectName, maxLimit * 2)
            
            // Strategy 2: File ownership analysis (simplified)
            val fileOwners = getFileOwners(change)
            
            // Strategy 3: Active contributors
            val activeContributors = getActiveContributors(change.projectName, maxLimit)
            
            // Combine and score suggestions
            val candidateMap = mutableMapOf<Long, SuggestedReviewerInfo>()
            
            // Add recent reviewers with high score
            recentReviewers.forEach { account ->
                candidateMap[account.id.toLong()] = SuggestedReviewerInfo(
                    account = convertToAccountInfo(account),
                    count = 1
                )
            }
            
            // Add file owners with highest score
            fileOwners.forEach { account ->
                val existing = candidateMap[account.id.toLong()]
                if (existing != null) {
                    candidateMap[account.id.toLong()] = existing.copy(count = existing.count + 2)
                } else {
                    candidateMap[account.id.toLong()] = SuggestedReviewerInfo(
                        account = convertToAccountInfo(account),
                        count = 2
                    )
                }
            }
            
            // Add active contributors with medium score
            activeContributors.forEach { account ->
                val existing = candidateMap[account.id.toLong()]
                if (existing != null) {
                    candidateMap[account.id.toLong()] = existing.copy(count = existing.count + 1)
                } else {
                    candidateMap[account.id.toLong()] = SuggestedReviewerInfo(
                        account = convertToAccountInfo(account),
                        count = 1
                    )
                }
            }
            
            // Filter by query if provided
            val filteredCandidates = if (query != null && query.isNotBlank()) {
                candidateMap.values.filter { suggestion ->
                    val account = suggestion.account
                    account?.email?.contains(query, ignoreCase = true) == true ||
                    account?.username?.contains(query, ignoreCase = true) == true ||
                    account?.display_name?.contains(query, ignoreCase = true) == true
                }
            } else {
                candidateMap.values.toList()
            }
            
            // Sort by score (count) and return top suggestions
            suggestions.addAll(
                filteredCandidates
                    .sortedByDescending { it.count }
                    .take(maxLimit)
            )
            
        } catch (e: Exception) {
            // Return empty list if suggestion logic fails
        }
        
        return suggestions
    }

    /**
     * Validate that a reviewer can be added to a change.
     * 
     * @param change The change entity
     * @param reviewer The reviewer to validate
     * @return true if reviewer can be added, false otherwise
     */
    fun canAddReviewer(change: ChangeEntity, reviewer: AccountInfo): Boolean {
        return try {
            // Check if reviewer is active
            if (reviewer.inactive == true) {
                return false
            }
            
            // Check if reviewer is already a reviewer
            val currentReviewers = getCurrentReviewers(change)
            if (currentReviewers.any { it._account_id == reviewer._account_id }) {
                return false
            }
            
            // Check if reviewer is the change owner
            if (change.metadata["owner"] is Map<*, *>) {
                val owner = change.metadata["owner"] as Map<String, Any>
                val ownerId = owner["_account_id"] as? Long
                if (ownerId == reviewer._account_id) {
                    return false
                }
            }
            
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get current reviewers for a change.
     * 
     * @param change The change entity
     * @return List of current reviewer AccountInfo objects
     */
    fun getCurrentReviewers(change: ChangeEntity): List<AccountInfo> {
        return try {
            val reviewersData = change.metadata["reviewers"] as? Map<String, Any> ?: return emptyList()
            val reviewersList = reviewersData["REVIEWER"] as? List<Map<String, Any>> ?: emptyList()
            
            reviewersList.mapNotNull { reviewerMap ->
                try {
                    AccountInfo(
                        _account_id = (reviewerMap["_account_id"] as? Number)?.toLong() ?: 0L,
                        email = reviewerMap["email"] as? String,
                        username = reviewerMap["username"] as? String,
                        display_name = reviewerMap["display_name"] as? String,
                        inactive = reviewerMap["inactive"] as? Boolean ?: false
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Convert UserEntity to AccountInfo DTO.
     */
    private fun convertToAccountInfo(account: UserEntity): AccountInfo {
        return AccountInfo(
            _account_id = account.id.toLong(),
            email = account.preferredEmail,
            username = account.username,
            display_name = account.fullName ?: account.username,
            inactive = !account.active
        )
    }

    /**
     * Find partial matches for reviewer search.
     */
    private fun findPartialMatches(query: String): List<UserEntity> {
        return try {
            // Search for partial matches in email, username, and full name
            val accounts = mutableSetOf<UserEntity>()
            
            // Partial username match
            val usernameMatches = accountRepository.findByUsernameContainingIgnoreCase(query, PageRequest.of(0, 5))
            accounts.addAll(usernameMatches.content)
            
            // Partial full name match
            val fullNameMatches = accountRepository.findByFullNameContainingIgnoreCase(query, PageRequest.of(0, 5))
            accounts.addAll(fullNameMatches.content)
            
            accounts.filter { it.active }.take(5)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Get recent reviewers for a project.
     */
    private fun getRecentProjectReviewers(projectName: String, limit: Int): List<UserEntity> {
        return try {
            // Query changes for the project and extract reviewer information
            val recentChanges = changeRepository.findByProjectName(
                projectName, 
                PageRequest.of(0, 50)
            )
            
            val reviewerIds = mutableSetOf<Int>()
            
            // Extract reviewer IDs from change metadata
            for (change in recentChanges.content) {
                val metadata = change.metadata as? Map<String, Any> ?: continue
                val reviewers = metadata["reviewers"] as? List<Map<String, Any>> ?: continue
                
                for (reviewer in reviewers) {
                    val accountId = (reviewer["accountId"] as? Number)?.toInt()
                    if (accountId != null) {
                        reviewerIds.add(accountId)
                    }
                }
                
                if (reviewerIds.size >= limit) break
            }
            
            // Find and return the user entities
            if (reviewerIds.isNotEmpty()) {
                reviewerIds.take(limit).mapNotNull { id ->
                    accountRepository.findById(id).orElse(null)
                }.filter { it.active }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Get file owners based on Git blame/history analysis.
     */
    private fun getFileOwners(change: ChangeEntity): List<UserEntity> {
        return try {
            // Get the latest patch set from the change
            val latestPatchSet = change.patchSets.lastOrNull() ?: return emptyList()
            val revision = latestPatchSet.commitId ?: return emptyList()
            
            // Get files modified in this change
            val modifiedFiles = GitUtil.getAllFilesInRevision(change, latestPatchSet)
            val ownerIds = mutableSetOf<Int>()
            
            // For each modified file, analyze Git blame to find frequent contributors
            for ((filePath, _) in modifiedFiles.entries.take(10)) { // Limit to first 10 files for performance
                try {
                    // Use Git blame to find who last modified each line
                    val blameInfo = getFileBlameInfo(change.projectName, revision, filePath)
                    for (authorEmail in blameInfo) {
                        // Try to find user by email
                        val userOptional = accountRepository.findByPreferredEmail(authorEmail)
                        if (userOptional.isPresent) {
                            val user = userOptional.get()
                            if (user.active) {
                                ownerIds.add(user.id)
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Skip this file if blame fails
                    continue
                }
                
                if (ownerIds.size >= 10) break // Limit total owners
            }
            
            // Return found users, sorted by activity/frequency
            ownerIds.mapNotNull { id ->
                accountRepository.findById(id).orElse(null)
            }.filter { it.active }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Get file blame information to find authors of lines in a file.
     */
    private fun getFileBlameInfo(projectName: String, revision: String, filePath: String): List<String> {
        return try {
            // Get the Git repository for the project using configured repository base path
            val gitDir = File(gitConfiguration.repositoryBasePath, "$projectName.git")
            val repository = FileRepositoryBuilder()
                .setGitDir(gitDir)
                .readEnvironment()
                .findGitDir()
                .build()
            
            try {
                val git = Git(repository)
                val revisionObjectId = ObjectId.fromString(revision)
                
                // Run git blame command
                val blameResult = git.blame()
                    .setFilePath(filePath)
                    .setStartCommit(revisionObjectId)
                    .call()
                
                val authorEmails = mutableSetOf<String>()
                
                // Extract unique author emails from blame result
                if (blameResult != null) {
                    for (i in 0 until blameResult.resultContents.size()) {
                        val commit = blameResult.getSourceCommit(i)
                        if (commit != null) {
                            val authorEmail = commit.authorIdent?.emailAddress
                            if (authorEmail != null && authorEmail.isNotBlank()) {
                                authorEmails.add(authorEmail)
                            }
                        }
                    }
                }
                
                git.close()
                authorEmails.toList()
            } finally {
                repository.close()
            }
        } catch (e: Exception) {
            // If blame fails (file doesn't exist, etc.), return empty list
            emptyList()
        }
    }

    /**
     * Get active contributors for a project.
     */
    private fun getActiveContributors(projectName: String, limit: Int): List<UserEntity> {
        return try {
            // Query recent changes for the project to find active contributors
            val recentChanges = changeRepository.findByProjectName(
                projectName, 
                PageRequest.of(0, 100)
            )
            
            val contributorIds = mutableSetOf<Int>()
            
            // Extract owner/author IDs from recent changes
            for (change in recentChanges.content) {
                contributorIds.add(change.ownerId)
                
                // Also extract uploader IDs from patch sets
                val patchSets = change.patchSets as? List<Map<String, Any>> ?: continue
                for (patchSet in patchSets) {
                    val uploaderId = (patchSet["uploader_id"] as? Number)?.toInt()
                    if (uploaderId != null) {
                        contributorIds.add(uploaderId)
                    }
                }
                
                if (contributorIds.size >= limit) break
            }
            
            // Find and return the user entities
            contributorIds.take(limit).mapNotNull { id ->
                accountRepository.findById(id).orElse(null)
            }.filter { it.active }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
