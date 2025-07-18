package ai.fluxuate.gerrit.api.dto

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * The PreferencesInfo entity contains information about the general preferences of an account.
 */
data class PreferencesInfo(
    @JsonProperty("changes_per_page")
    val changesPerPage: Int? = null,
    
    @JsonProperty("theme")
    val theme: Theme? = null,
    
    @JsonProperty("date_format")
    val dateFormat: DateFormat? = null,
    
    @JsonProperty("time_format")
    val timeFormat: TimeFormat? = null,
    
    @JsonProperty("relative_date_in_change_table")
    val relativeDateInChangeTable: Boolean? = null,
    
    @JsonProperty("diff_view")
    val diffView: DiffView? = null,
    
    @JsonProperty("size_bar_in_change_table")
    val sizeBarInChangeTable: Boolean? = null,
    
    @JsonProperty("legacycid_in_change_table")
    val legacyCidInChangeTable: Boolean? = null,
    
    @JsonProperty("mute_common_path_prefixes")
    val muteCommonPathPrefixes: Boolean? = null,
    
    @JsonProperty("signed_off_by")
    val signedOffBy: Boolean? = null,
    
    @JsonProperty("email_strategy")
    val emailStrategy: EmailStrategy? = null,
    
    @JsonProperty("email_format")
    val emailFormat: EmailFormat? = null,
    
    @JsonProperty("default_base_for_merges")
    val defaultBaseForMerges: DefaultBase? = null,
    
    @JsonProperty("publish_comments_on_push")
    val publishCommentsOnPush: Boolean? = null,
    
    @JsonProperty("work_in_progress_by_default")
    val workInProgressByDefault: Boolean? = null,
    
    @JsonProperty("my")
    val my: List<TopMenuItem>? = null,
    
    @JsonProperty("change_table")
    val changeTable: List<String>? = null
)

/**
 * The DiffPreferencesInfo entity contains information about the diff preferences of an account.
 */
data class DiffPreferencesInfo(
    @JsonProperty("context")
    val context: Int? = null,
    
    @JsonProperty("expand_all_comments")
    val expandAllComments: Boolean? = null,
    
    @JsonProperty("ignore_whitespace")
    val ignoreWhitespace: Whitespace? = null,
    
    @JsonProperty("line_length")
    val lineLength: Int? = null,
    
    @JsonProperty("manual_review")
    val manualReview: Boolean? = null,
    
    @JsonProperty("retain_header")
    val retainHeader: Boolean? = null,
    
    @JsonProperty("show_line_endings")
    val showLineEndings: Boolean? = null,
    
    @JsonProperty("show_tabs")
    val showTabs: Boolean? = null,
    
    @JsonProperty("show_whitespace_errors")
    val showWhitespaceErrors: Boolean? = null,
    
    @JsonProperty("skip_deleted")
    val skipDeleted: Boolean? = null,
    
    @JsonProperty("skip_uncommented")
    val skipUncommented: Boolean? = null,
    
    @JsonProperty("syntax_highlighting")
    val syntaxHighlighting: Boolean? = null,
    
    @JsonProperty("hide_top_menu")
    val hideTopMenu: Boolean? = null,
    
    @JsonProperty("auto_hide_diff_table_header")
    val autoHideDiffTableHeader: Boolean? = null,
    
    @JsonProperty("hide_line_numbers")
    val hideLineNumbers: Boolean? = null,
    
    @JsonProperty("tab_size")
    val tabSize: Int? = null,
    
    @JsonProperty("font_size")
    val fontSize: Int? = null,
    
    @JsonProperty("line_wrapping")
    val lineWrapping: Boolean? = null,
    
    @JsonProperty("indent_with_tabs")
    val indentWithTabs: Boolean? = null
)

/**
 * The EditPreferencesInfo entity contains information about the edit preferences of an account.
 */
data class EditPreferencesInfo(
    @JsonProperty("tab_size")
    val tabSize: Int? = null,
    
    @JsonProperty("line_length")
    val lineLength: Int? = null,
    
    @JsonProperty("indent_unit")
    val indentUnit: Int? = null,
    
    @JsonProperty("cursor_blink_rate")
    val cursorBlinkRate: Int? = null,
    
    @JsonProperty("hide_top_menu")
    val hideTopMenu: Boolean? = null,
    
    @JsonProperty("show_tabs")
    val showTabs: Boolean? = null,
    
    @JsonProperty("show_whitespace_errors")
    val showWhitespaceErrors: Boolean? = null,
    
    @JsonProperty("syntax_highlighting")
    val syntaxHighlighting: Boolean? = null,
    
    @JsonProperty("hide_line_numbers")
    val hideLineNumbers: Boolean? = null,
    
    @JsonProperty("match_brackets")
    val matchBrackets: Boolean? = null,
    
    @JsonProperty("line_wrapping")
    val lineWrapping: Boolean? = null,
    
    @JsonProperty("indent_with_tabs")
    val indentWithTabs: Boolean? = null,
    
    @JsonProperty("auto_close_brackets")
    val autoCloseBrackets: Boolean? = null,
    
    @JsonProperty("theme")
    val theme: String? = null,
    
    @JsonProperty("key_map_type")
    val keyMapType: KeyMapType? = null
)

// Enums for preferences
enum class Theme { AUTO, DARK, LIGHT }
enum class DateFormat { STD, US, ISO, EURO, UK }
enum class TimeFormat { HHMM_12, HHMM_24 }
enum class DiffView { SIDE_BY_SIDE, UNIFIED_DIFF }
enum class EmailStrategy { ENABLED, CC_ON_OWN_COMMENTS, DISABLED }
enum class EmailFormat { PLAINTEXT, HTML_PLAINTEXT }
enum class DefaultBase { FIRST_PARENT, AUTO_MERGE }
enum class Whitespace { IGNORE_NONE, IGNORE_TRAILING, IGNORE_LEADING_AND_TRAILING, IGNORE_ALL }
enum class KeyMapType { DEFAULT, EMACS, VIM }

data class TopMenuItem(
    @JsonProperty("name")
    val name: String,
    
    @JsonProperty("url")
    val url: String
)
