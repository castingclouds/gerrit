package ai.fluxuate.gerrit.api.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

/**
 * The CommentInfo entity contains information about an inline comment.
 * Based on legacy Gerrit CommentInfo structure.
 */
data class CommentInfo(
    val id: String,
    val updated: Instant,
    
    @JsonProperty("patch_set")
    val patchSet: Int? = null,
    
    val path: String? = null,
    val side: CommentSide? = null,
    val parent: Int? = null,
    val line: Int? = null,
    val range: CommentRange? = null,
    
    @JsonProperty("in_reply_to")
    val inReplyTo: String? = null,
    
    val message: String? = null,
    val author: AccountInfo? = null,
    val tag: String? = null,
    val unresolved: Boolean? = null,
    
    @JsonProperty("change_message_id")
    val changeMessageId: String? = null,
    
    @JsonProperty("commit_id")
    val commitId: String? = null,
    
    @JsonProperty("context_lines")
    val contextLines: List<ContextLineInfo>? = null,
    
    @JsonProperty("source_content_type")
    val sourceContentType: String? = null,
    
    @JsonProperty("fix_suggestions")
    val fixSuggestions: List<FixSuggestionInfo>? = null
)

/**
 * Comment side enumeration
 */
enum class CommentSide {
    REVISION,
    PARENT
}

/**
 * Comment range for inline comments
 */
data class CommentRange(
    @JsonProperty("start_line")
    val startLine: Int,
    
    @JsonProperty("start_character")
    val startCharacter: Int,
    
    @JsonProperty("end_line")
    val endLine: Int,
    
    @JsonProperty("end_character")
    val endCharacter: Int
) {
    fun isValid(): Boolean {
        return startLine > 0 &&
                startCharacter >= 0 &&
                endLine > 0 &&
                endCharacter >= 0 &&
                startLine <= endLine &&
                (startLine != endLine || startCharacter <= endCharacter)
    }
}

/**
 * Context line information for comments
 */
data class ContextLineInfo(
    @JsonProperty("line_number")
    val lineNumber: Int,
    
    @JsonProperty("context_line")
    val contextLine: String
)

/**
 * Fix suggestion information
 */
data class FixSuggestionInfo(
    @JsonProperty("fix_id")
    val fixId: String,
    
    val description: String,
    val replacements: List<FixReplacementInfo>
)

/**
 * Fix replacement information
 */
data class FixReplacementInfo(
    val path: String,
    val range: CommentRange,
    val replacement: String
)
