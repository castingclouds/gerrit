package ai.fluxuate.gerrit.api.dto

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Information about a file in a revision.
 * Based on legacy Gerrit FileInfo DTO.
 */
data class FileInfo(
    /** Status of the file (A=added, M=modified, D=deleted, R=renamed, C=copied) */
    val status: Char? = null,
    
    /** File mode in the old revision */
    @JsonProperty("old_mode")
    val oldMode: Int? = null,
    
    /** File mode in the new revision */
    @JsonProperty("new_mode") 
    val newMode: Int? = null,
    
    /** SHA-1 of the file in the old revision */
    @JsonProperty("old_sha")
    val oldSha: String? = null,
    
    /** SHA-1 of the file in the new revision */
    @JsonProperty("new_sha")
    val newSha: String? = null,
    
    /** Whether the file is binary */
    val binary: Boolean? = null,
    
    /** Path of the file in the old revision (for renames/copies) */
    @JsonProperty("old_path")
    val oldPath: String? = null,
    
    /** Number of lines inserted */
    @JsonProperty("lines_inserted")
    val linesInserted: Int? = null,
    
    /** Number of lines deleted */
    @JsonProperty("lines_deleted")
    val linesDeleted: Int? = null,
    
    /** Size delta in bytes */
    @JsonProperty("size_delta")
    val sizeDelta: Long = 0,
    
    /** File size in bytes */
    val size: Long = 0
)

/**
 * Information about the diff of a file in a revision.
 * Based on legacy Gerrit DiffInfo DTO.
 */
data class DiffInfo(
    /** Meta information about the file on side A */
    @JsonProperty("meta_a")
    val metaA: FileMeta? = null,
    
    /** Meta information about the file on side B */
    @JsonProperty("meta_b")
    val metaB: FileMeta? = null,
    
    /** Intraline status */
    @JsonProperty("intraline_status")
    val intralineStatus: IntraLineStatus? = null,
    
    /** The type of change */
    @JsonProperty("change_type")
    val changeType: ChangeType? = null,
    
    /** A list of strings representing the patch set diff header */
    @JsonProperty("diff_header")
    val diffHeader: List<String>? = null,
    
    /** The content differences in the file as a list of entities */
    @JsonProperty("content")
    val content: List<ContentEntry>? = null,
    
    /** Links to the file diff in external sites */
    @JsonProperty("web_links")
    val webLinks: List<DiffWebLinkInfo>? = null,
    
    /** Links to edit the file in external sites */
    @JsonProperty("edit_web_links")
    val editWebLinks: List<WebLinkInfo>? = null,
    
    /** Binary file */
    val binary: Boolean? = null
)

/**
 * Meta information about a file.
 */
data class FileMeta(
    /** The ID of the commit containing the file */
    @JsonProperty("commit_id")
    val commitId: String? = null,
    
    /** The name of the file */
    val name: String? = null,
    
    /** The content type of the file */
    @JsonProperty("content_type")
    val contentType: String? = null,
    
    /** The total number of lines in the file */
    val lines: Int? = null,
    
    /** Links to the file in external sites */
    @JsonProperty("web_links")
    val webLinks: List<WebLinkInfo>? = null
)

/**
 * Content entry representing a section of diff content.
 */
data class ContentEntry(
    /** Common lines to both sides */
    val ab: List<String>? = null,
    
    /** Lines of side A */
    val a: List<String>? = null,
    
    /** Lines of side B */
    val b: List<String>? = null,
    
    /** Changed sections in side A (character offset, length pairs) */
    @JsonProperty("edit_a")
    val editA: List<List<Int>>? = null,
    
    /** Changed sections in side B (character offset, length pairs) */
    @JsonProperty("edit_b")
    val editB: List<List<Int>>? = null,
    
    /** Indicates entry exists only because of rebase */
    @JsonProperty("due_to_rebase")
    val dueToRebase: Boolean? = null,
    
    /** Lines are actually common with whitespace ignore setting */
    val common: Boolean? = null,
    
    /** Number of lines to skip on both sides */
    val skip: Int? = null
)

/**
 * Intraline status enumeration.
 */
enum class IntraLineStatus {
    OK,
    TIMEOUT,
    FAILURE
}

/**
 * Change type enumeration.
 */
enum class ChangeType {
    ADDED,
    MODIFIED,
    DELETED,
    RENAMED,
    COPIED,
    REWRITE
}

/**
 * Web link information for diffs.
 */
data class DiffWebLinkInfo(
    /** The link name */
    val name: String,
    
    /** The link URL */
    val url: String,
    
    /** The link image URL */
    @JsonProperty("image_url")
    val imageUrl: String? = null
)

/**
 * General web link information.
 */
data class WebLinkInfo(
    /** The link name */
    val name: String,
    
    /** The link URL */
    val url: String,
    
    /** The link image URL */
    @JsonProperty("image_url")
    val imageUrl: String? = null
)
