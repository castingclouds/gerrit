package ai.fluxuate.gerrit.util

import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.PersonIdent
import java.security.MessageDigest
import java.util.regex.Pattern

/**
 * Utility object for Change-Id generation and validation.
 * Implements the same algorithm as Gerrit's commit-msg hook.
 * 
 * Based on the official Gerrit project patterns, this utility provides
 * static methods for Change-Id operations without requiring dependency injection.
 */
object ChangeIdUtil {
    
    // Change-Id pattern: I followed by 40 hex characters
    private val CHANGE_ID_PATTERN = Pattern.compile("^I[0-9a-f]{40}$")
    
    // Pattern to find Change-Id footer in commit message
    private val CHANGE_ID_FOOTER_PATTERN = Pattern.compile(
        "^Change-Id:\\s*(I[0-9a-f]{40})\\s*$", 
        Pattern.MULTILINE
    )
    
    // Pattern to find any Change-Id line (for replacement)
    private val CHANGE_ID_LINE_PATTERN = Pattern.compile(
        "^Change-Id:\\s*.*$", 
        Pattern.MULTILINE
    )
    
    /**
     * Generate a Change-Id using the same algorithm as Gerrit's commit-msg hook.
     * 
     * Based on:
     * - Tree ID of the commit
     * - Parent commit IDs
     * - Author information
     * - Commit message (without existing Change-Id)
     */
    fun generateChangeId(
        treeId: ObjectId,
        parentIds: List<ObjectId>,
        author: PersonIdent,
        committer: PersonIdent,
        commitMessage: String
    ): String {
        val digest = MessageDigest.getInstance("SHA-1")
        
        // Add tree ID
        digest.update("tree ".toByteArray())
        digest.update(treeId.name.toByteArray())
        digest.update("\n".toByteArray())
        
        // Add parent IDs
        for (parentId in parentIds) {
            digest.update("parent ".toByteArray())
            digest.update(parentId.name.toByteArray())
            digest.update("\n".toByteArray())
        }
        
        // Add author
        digest.update("author ".toByteArray())
        digest.update(formatPersonIdent(author).toByteArray())
        digest.update("\n".toByteArray())
        
        // Add committer
        digest.update("committer ".toByteArray())
        digest.update(formatPersonIdent(committer).toByteArray())
        digest.update("\n".toByteArray())
        
        // Add commit message without existing Change-Id
        digest.update("\n".toByteArray())
        val cleanMessage = removeChangeId(commitMessage)
        digest.update(cleanMessage.toByteArray())
        
        // Generate SHA-1 hash and format as Change-Id
        val hash = digest.digest()
        return "I" + hash.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Extract Change-Id from commit message.
     */
    fun extractChangeId(commitMessage: String): String? {
        val matcher = CHANGE_ID_FOOTER_PATTERN.matcher(commitMessage)
        return if (matcher.find()) {
            matcher.group(1)
        } else {
            null
        }
    }
    
    /**
     * Check if a Change-Id is valid format.
     */
    fun isValidChangeId(changeId: String): Boolean {
        return CHANGE_ID_PATTERN.matcher(changeId).matches()
    }
    
    /**
     * Add or update Change-Id in commit message.
     * If a Change-Id already exists, it will be preserved.
     * If no Change-Id exists, a new one will be generated and added.
     */
    fun addOrUpdateChangeId(
        commitMessage: String,
        treeId: ObjectId,
        parentIds: List<ObjectId>,
        author: PersonIdent,
        committer: PersonIdent
    ): String {
        // Check if Change-Id already exists
        val existingChangeId = extractChangeId(commitMessage)
        if (existingChangeId != null && isValidChangeId(existingChangeId)) {
            // Valid Change-Id already exists, return as-is
            return commitMessage
        }
        
        // Generate new Change-Id
        val newChangeId = generateChangeId(treeId, parentIds, author, committer, commitMessage)
        
        // Add Change-Id to commit message
        return addChangeIdToMessage(commitMessage, newChangeId)
    }
    
    /**
     * Add Change-Id footer to commit message.
     */
    fun addChangeIdToMessage(commitMessage: String, changeId: String): String {
        val lines = commitMessage.split("\n").toMutableList()
        
        // Find the position to insert Change-Id (before any existing footers)
        var insertPosition = lines.size
        
        // Look for existing footers (lines that look like "Key: Value")
        for (i in lines.indices.reversed()) {
            val line = lines[i].trim()
            if (line.isEmpty()) {
                continue
            }
            if (line.contains(":") && !line.startsWith("#")) {
                insertPosition = i
            } else {
                break
            }
        }
        
        // Insert Change-Id
        lines.add(insertPosition, "Change-Id: $changeId")
        
        return lines.joinToString("\n")
    }
    
    /**
     * Remove existing Change-Id from commit message.
     */
    fun removeChangeId(commitMessage: String): String {
        return CHANGE_ID_LINE_PATTERN.matcher(commitMessage).replaceAll("")
    }
    
    /**
     * Format PersonIdent for hash calculation.
     */
    private fun formatPersonIdent(person: PersonIdent): String {
        return "${person.name} <${person.emailAddress}> ${person.`when`.time / 1000} ${formatTimezone(person.timeZoneOffset)}"
    }
    
    /**
     * Format timezone offset for hash calculation.
     */
    private fun formatTimezone(offsetMinutes: Int): String {
        val hours = offsetMinutes / 60
        val minutes = Math.abs(offsetMinutes % 60)
        val sign = if (offsetMinutes >= 0) "+" else "-"
        return String.format("%s%02d%02d", sign, Math.abs(hours), minutes)
    }
    
    /**
     * Validate that a commit message has a proper Change-Id.
     */
    fun validateCommitMessage(commitMessage: String): ValidationResult {
        val changeId = extractChangeId(commitMessage)
        
        return when {
            changeId == null -> ValidationResult.error("Missing Change-Id in commit message")
            !isValidChangeId(changeId) -> ValidationResult.error("Invalid Change-Id format: $changeId")
            else -> ValidationResult.success(changeId)
        }
    }
    
    /**
     * Result of Change-Id validation.
     */
    data class ValidationResult(
        val valid: Boolean,
        val message: String,
        val changeId: String? = null
    ) {
        companion object {
            fun success(changeId: String) = ValidationResult(true, "Valid Change-Id", changeId)
            fun error(message: String) = ValidationResult(false, message)
        }
    }
}
