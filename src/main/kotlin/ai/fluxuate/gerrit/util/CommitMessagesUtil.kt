package ai.fluxuate.gerrit.util

import ai.fluxuate.gerrit.model.ChangeEntity
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import java.time.Instant
import java.util.regex.Pattern

/**
 * Utility object for commit message operations in Gerrit.
 * 
 * This utility provides comprehensive commit message handling functionality including:
 * - Commit message validation and formatting
 * - Change-Id management and validation
 * - Commit message parsing and extraction
 * - Footer management (Signed-off-by, Reviewed-by, etc.)
 * - Message sanitization and normalization
 * 
 * Based on Gerrit's commit message standards and modernization requirements.
 */
object CommitMessagesUtil {
    
    // Patterns for various commit message components
    private val CHANGE_ID_PATTERN = Pattern.compile("^I[0-9a-f]{40}$")
    private val CHANGE_ID_FOOTER_PATTERN = Pattern.compile(
        "^Change-Id:\\s*(I[0-9a-f]{40})\\s*$", 
        Pattern.MULTILINE
    )
    private val SIGNED_OFF_BY_PATTERN = Pattern.compile(
        "^Signed-off-by:\\s*(.+)\\s*$", 
        Pattern.MULTILINE
    )
    private val REVIEWED_BY_PATTERN = Pattern.compile(
        "^Reviewed-by:\\s*(.+)\\s*$", 
        Pattern.MULTILINE
    )
    private val BUG_PATTERN = Pattern.compile(
        "^Bug:\\s*(.+)\\s*$", 
        Pattern.MULTILINE
    )
    private val FOOTER_PATTERN = Pattern.compile(
        "^([A-Za-z][A-Za-z0-9-]*):(.*)$"
    )
    
    // Constants for message validation
    private const val MIN_SUBJECT_LENGTH = 10
    private const val MAX_SUBJECT_LENGTH = 72
    private const val MAX_LINE_LENGTH = 72
    private const val MAX_MESSAGE_LENGTH = 65536
    
    /**
     * Data class representing a parsed commit message.
     */
    data class ParsedCommitMessage(
        val subject: String,
        val body: String?,
        val changeId: String?,
        val footers: Map<String, List<String>>,
        val signedOffBy: List<String>,
        val reviewedBy: List<String>,
        val bugs: List<String>
    )
    
    /**
     * Data class representing commit message validation result.
     */
    data class ValidationResult(
        val valid: Boolean,
        val errors: List<String>,
        val warnings: List<String>
    ) {
        companion object {
            fun success() = ValidationResult(true, emptyList(), emptyList())
            fun error(vararg errors: String) = ValidationResult(false, errors.toList(), emptyList())
            fun warning(vararg warnings: String) = ValidationResult(true, emptyList(), warnings.toList())
            fun errorAndWarning(errors: List<String>, warnings: List<String>) = 
                ValidationResult(errors.isEmpty(), errors, warnings)
        }
    }
    
    /**
     * Parse a commit message into its components.
     * 
     * @param commitMessage The raw commit message
     * @return ParsedCommitMessage containing all parsed components
     */
    fun parseCommitMessage(commitMessage: String): ParsedCommitMessage {
        val lines = commitMessage.split("\n")
        if (lines.isEmpty()) {
            return ParsedCommitMessage("", null, null, emptyMap(), emptyList(), emptyList(), emptyList())
        }
        
        val subject = lines[0].trim()
        
        // Find the start of the body (first non-empty line after subject)
        var bodyStartIndex = -1
        for (i in 1 until lines.size) {
            if (lines[i].trim().isNotEmpty()) {
                bodyStartIndex = i
                break
            }
        }
        
        // Find footers (lines at the end that match footer pattern)
        val footers = mutableMapOf<String, MutableList<String>>()
        val signedOffBy = mutableListOf<String>()
        val reviewedBy = mutableListOf<String>()
        val bugs = mutableListOf<String>()
        var bodyEndIndex = lines.size
        
        // Parse footers from the end
        for (i in lines.indices.reversed()) {
            val line = lines[i].trim()
            if (line.isEmpty()) continue
            
            val footerMatcher = FOOTER_PATTERN.matcher(line)
            if (footerMatcher.matches()) {
                val key = footerMatcher.group(1)
                val value = footerMatcher.group(2).trim()
                
                footers.getOrPut(key) { mutableListOf() }.add(value)
                
                // Special handling for common footers
                when (key.lowercase()) {
                    "signed-off-by" -> signedOffBy.add(value)
                    "reviewed-by" -> reviewedBy.add(value)
                    "bug" -> bugs.add(value)
                }
                
                if (bodyEndIndex == lines.size) {
                    bodyEndIndex = i
                }
            } else if (bodyEndIndex != lines.size) {
                // Found non-footer line after footers started, stop parsing footers
                break
            }
        }
        
        // Extract body (between subject and footers)
        val body = if (bodyStartIndex != -1 && bodyStartIndex < bodyEndIndex) {
            lines.subList(bodyStartIndex, bodyEndIndex)
                .joinToString("\n")
                .trim()
                .takeIf { it.isNotEmpty() }
        } else null
        
        // Extract Change-Id
        val changeId = ChangeIdUtil.extractChangeId(commitMessage)
        
        return ParsedCommitMessage(
            subject = subject,
            body = body,
            changeId = changeId,
            footers = footers,
            signedOffBy = signedOffBy,
            reviewedBy = reviewedBy,
            bugs = bugs
        )
    }
    
    /**
     * Validate a commit message according to Gerrit standards.
     * 
     * @param commitMessage The commit message to validate
     * @param requireChangeId Whether Change-Id is required
     * @param requireSignedOffBy Whether Signed-off-by is required
     * @return ValidationResult with errors and warnings
     */
    fun validateCommitMessage(
        commitMessage: String,
        requireChangeId: Boolean = true,
        requireSignedOffBy: Boolean = false
    ): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        // Basic message checks
        if (commitMessage.isBlank()) {
            errors.add("Commit message cannot be empty")
            return ValidationResult.error(*errors.toTypedArray())
        }
        
        if (commitMessage.length > MAX_MESSAGE_LENGTH) {
            errors.add("Commit message too long (${commitMessage.length} > $MAX_MESSAGE_LENGTH characters)")
        }
        
        val parsed = parseCommitMessage(commitMessage)
        
        // Subject line validation
        if (parsed.subject.isEmpty()) {
            errors.add("Subject line cannot be empty")
        } else {
            if (parsed.subject.length < MIN_SUBJECT_LENGTH) {
                warnings.add("Subject line is very short (${parsed.subject.length} < $MIN_SUBJECT_LENGTH characters)")
            }
            if (parsed.subject.length > MAX_SUBJECT_LENGTH) {
                warnings.add("Subject line is long (${parsed.subject.length} > $MAX_SUBJECT_LENGTH characters)")
            }
            if (parsed.subject.endsWith(".")) {
                warnings.add("Subject line should not end with a period")
            }
            if (!parsed.subject[0].isUpperCase()) {
                warnings.add("Subject line should start with a capital letter")
            }
        }
        
        // Line length validation
        val lines = commitMessage.split("\n")
        for ((index, line) in lines.withIndex()) {
            if (line.length > MAX_LINE_LENGTH && !isFooterLine(line)) {
                warnings.add("Line ${index + 1} is too long (${line.length} > $MAX_LINE_LENGTH characters)")
            }
        }
        
        // Change-Id validation
        if (requireChangeId) {
            if (parsed.changeId == null) {
                errors.add("Missing Change-Id in commit message")
            } else if (!ChangeIdUtil.isValidChangeId(parsed.changeId)) {
                errors.add("Invalid Change-Id format: ${parsed.changeId}")
            }
        }
        
        // Signed-off-by validation
        if (requireSignedOffBy && parsed.signedOffBy.isEmpty()) {
            errors.add("Missing Signed-off-by footer")
        }
        
        // Check for duplicate Change-Ids
        val changeIdMatches = CHANGE_ID_FOOTER_PATTERN.matcher(commitMessage)
        var changeIdCount = 0
        while (changeIdMatches.find()) {
            changeIdCount++
        }
        if (changeIdCount > 1) {
            errors.add("Multiple Change-Id footers found")
        }
        
        return ValidationResult.errorAndWarning(errors, warnings)
    }
    
    /**
     * Sanitize and normalize a commit message.
     * 
     * @param commitMessage The raw commit message
     * @param trimWhitespace Whether to trim excess whitespace
     * @param normalizeLineEndings Whether to normalize line endings
     * @return Sanitized commit message
     */
    fun sanitizeCommitMessage(
        commitMessage: String,
        trimWhitespace: Boolean = true,
        normalizeLineEndings: Boolean = true
    ): String {
        var sanitized = commitMessage
        
        if (normalizeLineEndings) {
            // Normalize line endings to \n
            sanitized = sanitized.replace("\r\n", "\n").replace("\r", "\n")
        }
        
        if (trimWhitespace) {
            // Remove trailing whitespace from each line
            sanitized = sanitized.split("\n")
                .joinToString("\n") { it.trimEnd() }
                .trim()
        }
        
        // Remove null characters and other control characters (except \n and \t)
        sanitized = sanitized.replace(Regex("[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]"), "")
        
        return sanitized
    }
    
    /**
     * Add or update a footer in a commit message.
     * 
     * @param commitMessage The original commit message
     * @param footerKey The footer key (e.g., "Reviewed-by")
     * @param footerValue The footer value
     * @param allowDuplicates Whether to allow duplicate footers
     * @return Updated commit message
     */
    fun addFooter(
        commitMessage: String,
        footerKey: String,
        footerValue: String,
        allowDuplicates: Boolean = true
    ): String {
        val lines = commitMessage.split("\n").toMutableList()
        val footerLine = "$footerKey: $footerValue"
        
        // Check if footer already exists
        if (!allowDuplicates) {
            val existingPattern = Pattern.compile("^$footerKey:\\s*(.+)\\s*$", Pattern.MULTILINE)
            if (existingPattern.matcher(commitMessage).find()) {
                return commitMessage // Footer already exists, don't add duplicate
            }
        }
        
        // Find insertion point (before Change-Id if it exists, otherwise at the end)
        var insertIndex = lines.size
        for (i in lines.indices.reversed()) {
            val line = lines[i].trim()
            if (line.startsWith("Change-Id:")) {
                insertIndex = i
                break
            }
        }
        
        // Ensure there's a blank line before footers if there's a body
        if (insertIndex > 1 && lines[insertIndex - 1].trim().isNotEmpty() && !isFooterLine(lines[insertIndex - 1])) {
            lines.add(insertIndex, "")
            insertIndex++
        }
        
        lines.add(insertIndex, footerLine)
        return lines.joinToString("\n")
    }
    
    /**
     * Remove a footer from a commit message.
     * 
     * @param commitMessage The original commit message
     * @param footerKey The footer key to remove
     * @param footerValue Optional specific value to remove (removes all if null)
     * @return Updated commit message
     */
    fun removeFooter(
        commitMessage: String,
        footerKey: String,
        footerValue: String? = null
    ): String {
        val lines = commitMessage.split("\n")
        val filteredLines = lines.filter { line ->
            val trimmedLine = line.trim()
            if (!trimmedLine.startsWith("$footerKey:")) {
                true // Keep non-matching lines
            } else if (footerValue == null) {
                false // Remove all footers with this key
            } else {
                // Only remove if value matches
                !trimmedLine.equals("$footerKey: $footerValue", ignoreCase = true)
            }
        }
        
        return filteredLines.joinToString("\n")
    }
    
    /**
     * Extract commit messages from a range of commits.
     * 
     * @param repository The Git repository
     * @param fromCommit Starting commit (exclusive)
     * @param toCommit Ending commit (inclusive)
     * @return List of commit messages
     */
    fun extractCommitMessages(
        repository: Repository,
        fromCommit: ObjectId?,
        toCommit: ObjectId
    ): List<String> {
        val messages = mutableListOf<String>()
        
        RevWalk(repository).use { revWalk ->
            val endCommit = revWalk.parseCommit(toCommit)
            revWalk.markStart(endCommit)
            
            if (fromCommit != null) {
                val startCommit = revWalk.parseCommit(fromCommit)
                revWalk.markUninteresting(startCommit)
            }
            
            for (commit in revWalk) {
                messages.add(commit.fullMessage)
            }
        }
        
        return messages
    }
    
    /**
     * Generate a standardized commit message for automated operations.
     * 
     * @param operation The operation being performed (e.g., "Rebase", "Cherry-pick")
     * @param originalSubject The original commit subject
     * @param changeId The Change-Id to include
     * @param additionalFooters Additional footers to include
     * @return Generated commit message
     */
    fun generateAutomatedCommitMessage(
        operation: String,
        originalSubject: String,
        changeId: String? = null,
        additionalFooters: Map<String, String> = emptyMap()
    ): String {
        val subject = if (originalSubject.startsWith(operation)) {
            originalSubject
        } else {
            "$operation: $originalSubject"
        }
        
        val lines = mutableListOf<String>()
        lines.add(subject)
        lines.add("")
        lines.add("This commit was generated automatically by Gerrit.")
        lines.add("")
        
        // Add additional footers
        additionalFooters.forEach { (key, value) ->
            lines.add("$key: $value")
        }
        
        // Add Change-Id if provided
        changeId?.let {
            lines.add("Change-Id: $it")
        }
        
        return lines.joinToString("\n")
    }
    
    /**
     * Check if a line is a footer line.
     */
    private fun isFooterLine(line: String): Boolean {
        val trimmed = line.trim()
        return FOOTER_PATTERN.matcher(trimmed).matches()
    }
    
    /**
     * Format a commit message for display in logs or UI.
     * 
     * @param commitMessage The raw commit message
     * @param maxLength Maximum length for truncation
     * @param includeFooters Whether to include footers in the formatted output
     * @return Formatted commit message
     */
    fun formatForDisplay(
        commitMessage: String,
        maxLength: Int = 100,
        includeFooters: Boolean = false
    ): String {
        val parsed = parseCommitMessage(commitMessage)
        
        var formatted = parsed.subject
        
        if (parsed.body != null && includeFooters) {
            formatted += "\n\n${parsed.body}"
        }
        
        if (includeFooters && parsed.footers.isNotEmpty()) {
            formatted += "\n\n"
            parsed.footers.forEach { (key, values) ->
                values.forEach { value ->
                    formatted += "$key: $value\n"
                }
            }
        }
        
        // Truncate if necessary
        if (formatted.length > maxLength) {
            formatted = formatted.take(maxLength - 3) + "..."
        }
        
        return formatted.trim()
    }
    
    /**
     * Check if two commit messages are semantically equivalent.
     * This ignores whitespace differences and footer ordering.
     * 
     * @param message1 First commit message
     * @param message2 Second commit message
     * @return True if messages are equivalent
     */
    fun areEquivalent(message1: String, message2: String): Boolean {
        val parsed1 = parseCommitMessage(sanitizeCommitMessage(message1))
        val parsed2 = parseCommitMessage(sanitizeCommitMessage(message2))
        
        return parsed1.subject == parsed2.subject &&
               parsed1.body == parsed2.body &&
               parsed1.changeId == parsed2.changeId &&
               parsed1.footers == parsed2.footers
    }
    
    /**
     * Merge commit messages from multiple commits into a single message.
     * Useful for squash operations.
     * 
     * @param messages List of commit messages to merge
     * @param preserveChangeIds Whether to preserve all Change-Ids
     * @return Merged commit message
     */
    fun mergeCommitMessages(
        messages: List<String>,
        preserveChangeIds: Boolean = false
    ): String {
        if (messages.isEmpty()) return ""
        if (messages.size == 1) return messages[0]
        
        val parsedMessages = messages.map { parseCommitMessage(it) }
        
        // Use the first message's subject as the primary subject
        val primarySubject = parsedMessages[0].subject
        
        // Combine all bodies
        val combinedBodies = parsedMessages
            .mapNotNull { it.body }
            .filter { it.isNotEmpty() }
        
        // Combine all footers
        val allFooters = mutableMapOf<String, MutableSet<String>>()
        parsedMessages.forEach { parsed ->
            parsed.footers.forEach { (key, values) ->
                if (key != "Change-Id" || preserveChangeIds) {
                    allFooters.getOrPut(key) { mutableSetOf() }.addAll(values)
                }
            }
        }
        
        // Build merged message
        val lines = mutableListOf<String>()
        lines.add(primarySubject)
        
        if (combinedBodies.isNotEmpty()) {
            lines.add("")
            lines.addAll(combinedBodies)
        }
        
        if (allFooters.isNotEmpty()) {
            lines.add("")
            allFooters.forEach { (key, values) ->
                values.forEach { value ->
                    lines.add("$key: $value")
                }
            }
        }
        
        return lines.joinToString("\n")
    }
}
