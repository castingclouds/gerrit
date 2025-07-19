package ai.fluxuate.gerrit.api.controller

import ai.fluxuate.gerrit.api.dto.*
import ai.fluxuate.gerrit.service.ChangeService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import jakarta.validation.Valid
import jakarta.servlet.http.HttpServletRequest

/**
 * REST controller for Changes API endpoints.
 * Implements Gerrit's Changes REST API for compatibility.
 */
@RestController
@RequestMapping("/a/changes")
class ChangesController(
    private val changeService: ChangeService
) {

    /**
     * Query changes.
     * GET /a/changes/
     */
    @GetMapping
    fun queryChanges(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) n: Int?,
        @RequestParam(required = false) S: Int?,
        @RequestParam(required = false) o: List<String>?
    ): ResponseEntity<List<ChangeInfo>> {
        val changes = changeService.queryChanges(q, n, S, o)
        return ResponseEntity.ok(changes)
    }

    /**
     * Create a new change.
     * POST /a/changes/
     */
    @PostMapping
    fun createChange(@Valid @RequestBody input: ChangeInput): ResponseEntity<ChangeInfo> {
        val change = changeService.createChange(input)
        return ResponseEntity.status(HttpStatus.CREATED).body(change)
    }

    /**
     * Get change details.
     * GET /a/changes/{change-id}
     */
    @GetMapping("/{changeId}")
    fun getChange(
        @PathVariable changeId: String,
        @RequestParam(required = false) o: List<String>?
    ): ResponseEntity<ChangeInfo> {
        val change = changeService.getChange(changeId, o)
        return ResponseEntity.ok(change)
    }

    /**
     * Update change details.
     * PUT /a/changes/{change-id}
     */
    @PutMapping("/{changeId}")
    fun updateChange(
        @PathVariable changeId: String,
        @Valid @RequestBody input: ChangeInput
    ): ResponseEntity<ChangeInfo> {
        val change = changeService.updateChange(changeId, input)
        return ResponseEntity.ok(change)
    }

    /**
     * Delete change.
     * DELETE /a/changes/{change-id}
     */
    @DeleteMapping("/{changeId}")
    fun deleteChange(@PathVariable changeId: String): ResponseEntity<Void> {
        changeService.deleteChange(changeId)
        return ResponseEntity.noContent().build()
    }

    /**
     * Abandon a change.
     * POST /a/changes/{change-id}/abandon
     */
    @PostMapping("/{changeId}/abandon")
    fun abandonChange(
        @PathVariable changeId: String,
        @Valid @RequestBody input: AbandonInput
    ): ResponseEntity<ChangeInfo> {
        val change = changeService.abandonChange(changeId, input)
        return ResponseEntity.ok(change)
    }

    /**
     * Restore a change.
     * POST /a/changes/{change-id}/restore
     */
    @PostMapping("/{changeId}/restore")
    fun restoreChange(
        @PathVariable changeId: String,
        @Valid @RequestBody input: RestoreInput
    ): ResponseEntity<ChangeInfo> {
        val change = changeService.restoreChange(changeId, input)
        return ResponseEntity.ok(change)
    }

    /**
     * Submit a change.
     * POST /a/changes/{change-id}/submit
     */
    @PostMapping("/{changeId}/submit")
    fun submitChange(
        @PathVariable changeId: String,
        @Valid @RequestBody input: SubmitInput
    ): ResponseEntity<ChangeInfo> {
        val change = changeService.submitChange(changeId, input)
        return ResponseEntity.ok(change)
    }

    /**
     * Rebase a change.
     * POST /a/changes/{change-id}/rebase
     */
    @PostMapping("/{changeId}/rebase")
    fun rebaseChange(
        @PathVariable changeId: String,
        @Valid @RequestBody input: RebaseInput
    ): ResponseEntity<ChangeInfo> {
        val change = changeService.rebaseChange(changeId, input)
        return ResponseEntity.ok(change)
    }

    /**
     * Cherry-pick a change.
     * POST /a/changes/{change-id}/revisions/{revision-id}/cherrypick
     */
    @PostMapping("/{changeId}/revisions/{revisionId}/cherrypick")
    fun cherryPickChange(
        @PathVariable changeId: String,
        @PathVariable revisionId: String,
        @Valid @RequestBody input: CherryPickInput
    ): ResponseEntity<ChangeInfo> {
        val change = changeService.cherryPickChange(changeId, revisionId, input)
        return ResponseEntity.ok(change)
    }

    /**
     * Move a change.
     * POST /a/changes/{change-id}/move
     */
    @PostMapping("/{changeId}/move")
    fun moveChange(
        @PathVariable changeId: String,
        @Valid @RequestBody input: MoveInput
    ): ResponseEntity<ChangeInfo> {
        val change = changeService.moveChange(changeId, input)
        return ResponseEntity.ok(change)
    }

    /**
     * Revert a change.
     * POST /a/changes/{change-id}/revert
     */
    @PostMapping("/{changeId}/revert")
    fun revertChange(
        @PathVariable changeId: String,
        @Valid @RequestBody input: RevertInput
    ): ResponseEntity<ChangeInfo> {
        val change = changeService.revertChange(changeId, input)
        return ResponseEntity.ok(change)
    }

    /**
     * Get topic.
     * GET /a/changes/{change-id}/topic
     */
    @GetMapping("/{changeId}/topic")
    fun getTopic(@PathVariable changeId: String): ResponseEntity<String> {
        val topic = changeService.getTopic(changeId)
        return ResponseEntity.ok(topic)
    }

    /**
     * Set topic.
     * PUT /a/changes/{change-id}/topic
     */
    @PutMapping("/{changeId}/topic")
    fun setTopic(
        @PathVariable changeId: String,
        @Valid @RequestBody input: TopicInput
    ): ResponseEntity<String> {
        val topic = changeService.setTopic(changeId, input)
        return ResponseEntity.ok(topic)
    }

    /**
     * Delete topic.
     * DELETE /a/changes/{change-id}/topic
     */
    @DeleteMapping("/{changeId}/topic")
    fun deleteTopic(@PathVariable changeId: String): ResponseEntity<Void> {
        changeService.deleteTopic(changeId)
        return ResponseEntity.noContent().build()
    }

    /**
     * Set private.
     * POST /a/changes/{change-id}/private
     */
    @PostMapping("/{changeId}/private")
    fun setPrivate(
        @PathVariable changeId: String,
        @Valid @RequestBody input: PrivateInput
    ): ResponseEntity<String> {
        val result = changeService.setPrivate(changeId, input)
        return ResponseEntity.ok(result)
    }

    /**
     * Unset private.
     * DELETE /a/changes/{change-id}/private
     */
    @DeleteMapping("/{changeId}/private")
    fun unsetPrivate(@PathVariable changeId: String): ResponseEntity<String> {
        val result = changeService.unsetPrivate(changeId)
        return ResponseEntity.ok(result)
    }

    /**
     * Set work in progress.
     * POST /a/changes/{change-id}/wip
     */
    @PostMapping("/{changeId}/wip")
    fun setWorkInProgress(
        @PathVariable changeId: String,
        @Valid @RequestBody input: WorkInProgressInput
    ): ResponseEntity<String> {
        val result = changeService.setWorkInProgress(changeId, input)
        return ResponseEntity.ok(result)
    }

    /**
     * Set ready for review.
     * POST /a/changes/{change-id}/ready
     */
    @PostMapping("/{changeId}/ready")
    fun setReadyForReview(
        @PathVariable changeId: String,
        @Valid @RequestBody input: ReadyForReviewInput
    ): ResponseEntity<String> {
        val result = changeService.setReadyForReview(changeId, input)
        return ResponseEntity.ok(result)
    }

    // ================================
    // REVIEWERS MANAGEMENT ENDPOINTS
    // ================================

    /**
     * List reviewers for a change.
     * GET /a/changes/{change-id}/reviewers
     */
    @GetMapping("/{changeId}/reviewers")
    fun getReviewers(@PathVariable changeId: String): ResponseEntity<List<AccountInfo>> {
        val reviewers = changeService.getReviewers(changeId)
        return ResponseEntity.ok(reviewers)
    }

    /**
     * Add reviewer to a change.
     * POST /a/changes/{change-id}/reviewers
     */
    @PostMapping("/{changeId}/reviewers")
    fun addReviewer(
        @PathVariable changeId: String,
        @Valid @RequestBody input: ReviewerInput
    ): ResponseEntity<AddReviewerResult> {
        val result = changeService.addReviewer(changeId, input)
        return ResponseEntity.ok(result)
    }

    /**
     * Get specific reviewer.
     * GET /a/changes/{change-id}/reviewers/{reviewer-id}
     */
    @GetMapping("/{changeId}/reviewers/{reviewerId}")
    fun getReviewer(
        @PathVariable changeId: String,
        @PathVariable reviewerId: String
    ): ResponseEntity<AccountInfo> {
        val reviewer = changeService.getReviewer(changeId, reviewerId)
        return ResponseEntity.ok(reviewer)
    }

    /**
     * Remove reviewer from a change.
     * DELETE /a/changes/{change-id}/reviewers/{reviewer-id}
     */
    @DeleteMapping("/{changeId}/reviewers/{reviewerId}")
    fun removeReviewer(
        @PathVariable changeId: String,
        @PathVariable reviewerId: String,
        @Valid @RequestBody input: DeleteReviewerInput = DeleteReviewerInput()
    ): ResponseEntity<Void> {
        changeService.removeReviewer(changeId, reviewerId, input)
        return ResponseEntity.noContent().build()
    }

    /**
     * Suggest reviewers for a change.
     * GET /a/changes/{change-id}/suggest_reviewers
     */
    @GetMapping("/{changeId}/suggest_reviewers")
    fun suggestReviewers(
        @PathVariable changeId: String,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) n: Int?
    ): ResponseEntity<List<SuggestedReviewerInfo>> {
        val suggestions = changeService.suggestReviewers(changeId, q, n)
        return ResponseEntity.ok(suggestions)
    }

    // ================================
    // REVISIONS ENDPOINTS
    // ================================

    /**
     * GET /a/changes/{change-id}/revisions/
     * List all revisions for a change.
     */
    @GetMapping("/{changeId}/revisions")
    fun getRevisions(
        @PathVariable changeId: String
    ): ResponseEntity<Map<String, RevisionInfo>> {
        val revisions = changeService.getRevisions(changeId)
        return ResponseEntity.ok(revisions)
    }

    /**
     * GET /a/changes/{change-id}/revisions/{revision-id}
     * Get a specific revision.
     */
    @GetMapping("/{changeId}/revisions/{revisionId}")
    fun getRevision(
        @PathVariable changeId: String,
        @PathVariable revisionId: String
    ): ResponseEntity<RevisionInfo> {
        val revision = changeService.getRevision(changeId, revisionId)
        return ResponseEntity.ok(revision)
    }

    /**
     * POST /a/changes/{change-id}/revisions/{revision-id}/submit
     * Submit a revision.
     */
    @PostMapping("/{changeId}/revisions/{revisionId}/submit")
    fun submitRevision(
        @PathVariable changeId: String,
        @PathVariable revisionId: String,
        @RequestBody input: SubmitInput
    ): ResponseEntity<ChangeInfo> {
        val change = changeService.submitRevision(changeId, revisionId, input)
        return ResponseEntity.ok(change)
    }

    /**
     * GET /a/changes/{change-id}/revisions/{revision-id}/commit
     * Get commit info for a revision.
     */
    @GetMapping("/{changeId}/revisions/{revisionId}/commit")
    fun getRevisionCommit(
        @PathVariable changeId: String,
        @PathVariable revisionId: String
    ): ResponseEntity<CommitInfo> {
        val commit = changeService.getRevisionCommit(changeId, revisionId)
        return ResponseEntity.ok(commit)
    }

    /**
     * POST /a/changes/{change-id}/revisions/{revision-id}/rebase
     * Rebase a revision.
     */
    @PostMapping("/{changeId}/revisions/{revisionId}/rebase")
    fun rebaseRevision(
        @PathVariable changeId: String,
        @PathVariable revisionId: String,
        @RequestBody input: RebaseInput
    ): ResponseEntity<ChangeInfo> {
        val change = changeService.rebaseRevision(changeId, revisionId, input)
        return ResponseEntity.ok(change)
    }

    /**
     * POST /a/changes/{change-id}/revisions/{revision-id}/review
     * Review a revision.
     */
    @PostMapping("/{changeId}/revisions/{revisionId}/review")
    fun reviewRevision(
        @PathVariable changeId: String,
        @PathVariable revisionId: String,
        @RequestBody input: ReviewInput
    ): ResponseEntity<ReviewResult> {
        val result = changeService.reviewRevision(changeId, revisionId, input)
        return ResponseEntity.ok(result)
    }

    // ===== COMMENT ENDPOINTS =====

    /**
     * GET /a/changes/{change-id}/revisions/{revision-id}/comments
     * List comments for a revision.
     */
    @GetMapping("/{changeId}/revisions/{revisionId}/comments")
    fun listRevisionComments(
        @PathVariable changeId: String,
        @PathVariable revisionId: String
    ): ResponseEntity<Map<String, List<CommentInfo>>> {
        val comments = changeService.listRevisionComments(changeId, revisionId)
        return ResponseEntity.ok(comments)
    }

    /**
     * GET /a/changes/{change-id}/revisions/{revision-id}/drafts
     * List draft comments for a revision.
     */
    @GetMapping("/{changeId}/revisions/{revisionId}/drafts")
    fun listRevisionDrafts(
        @PathVariable changeId: String,
        @PathVariable revisionId: String
    ): ResponseEntity<Map<String, List<CommentInfo>>> {
        val drafts = changeService.listRevisionDrafts(changeId, revisionId)
        return ResponseEntity.ok(drafts)
    }

    /**
     * PUT /a/changes/{change-id}/revisions/{revision-id}/drafts
     * Create/update draft comments for a revision.
     */
    @PutMapping("/{changeId}/revisions/{revisionId}/drafts")
    fun createRevisionDrafts(
        @PathVariable changeId: String,
        @PathVariable revisionId: String,
        @RequestBody input: CommentsInput
    ): ResponseEntity<Map<String, List<CommentInfo>>> {
        val drafts = changeService.createRevisionDrafts(changeId, revisionId, input)
        return ResponseEntity.ok(drafts)
    }

    /**
     * GET /a/changes/{change-id}/revisions/{revision-id}/comments/{comment-id}
     * Get a specific comment.
     */
    @GetMapping("/{changeId}/revisions/{revisionId}/comments/{commentId}")
    fun getRevisionComment(
        @PathVariable changeId: String,
        @PathVariable revisionId: String,
        @PathVariable commentId: String
    ): ResponseEntity<CommentInfo> {
        val comment = changeService.getRevisionComment(changeId, revisionId, commentId)
        return ResponseEntity.ok(comment)
    }

    /**
     * GET /a/changes/{change-id}/revisions/{revision-id}/drafts/{comment-id}
     * Get a specific draft comment.
     */
    @GetMapping("/{changeId}/revisions/{revisionId}/drafts/{commentId}")
    fun getRevisionDraft(
        @PathVariable changeId: String,
        @PathVariable revisionId: String,
        @PathVariable commentId: String
    ): ResponseEntity<CommentInfo> {
        val draft = changeService.getRevisionDraft(changeId, revisionId, commentId)
        return ResponseEntity.ok(draft)
    }

    /**
     * PUT /a/changes/{change-id}/revisions/{revision-id}/drafts/{comment-id}
     * Update a specific draft comment.
     */
    @PutMapping("/{changeId}/revisions/{revisionId}/drafts/{commentId}")
    fun updateRevisionDraft(
        @PathVariable changeId: String,
        @PathVariable revisionId: String,
        @PathVariable commentId: String,
        @RequestBody input: CommentInput
    ): ResponseEntity<CommentInfo> {
        val draft = changeService.updateRevisionDraft(changeId, revisionId, commentId, input)
        return ResponseEntity.ok(draft)
    }

    /**
     * DELETE /a/changes/{change-id}/revisions/{revision-id}/drafts/{comment-id}
     * Delete a specific draft comment.
     */
    @DeleteMapping("/{changeId}/revisions/{revisionId}/drafts/{commentId}")
    fun deleteRevisionDraft(
        @PathVariable changeId: String,
        @PathVariable revisionId: String,
        @PathVariable commentId: String
    ): ResponseEntity<Void> {
        changeService.deleteRevisionDraft(changeId, revisionId, commentId)
        return ResponseEntity.noContent().build()
    }

    /**
     * DELETE /a/changes/{change-id}/revisions/{revision-id}/comments/{comment-id}
     * Delete a published comment.
     */
    @DeleteMapping("/{changeId}/revisions/{revisionId}/comments/{commentId}")
    fun deleteRevisionComment(
        @PathVariable changeId: String,
        @PathVariable revisionId: String,
        @PathVariable commentId: String,
        @RequestBody input: DeleteCommentInput
    ): ResponseEntity<CommentInfo> {
        val comment = changeService.deleteRevisionComment(changeId, revisionId, commentId, input)
        return ResponseEntity.ok(comment)
    }

    /**
     * POST /a/changes/{change-id}/revisions/{revision-id}/comments/{comment-id}/delete
     * Delete a published comment (alternative endpoint).
     */
    @PostMapping("/{changeId}/revisions/{revisionId}/comments/{commentId}/delete")
    fun deleteRevisionCommentPost(
        @PathVariable changeId: String,
        @PathVariable revisionId: String,
        @PathVariable commentId: String,
        @RequestBody input: DeleteCommentInput
    ): ResponseEntity<CommentInfo> {
        val comment = changeService.deleteRevisionComment(changeId, revisionId, commentId, input)
        return ResponseEntity.ok(comment)
    }

    // ===== FILES AND DIFFS ENDPOINTS =====

    /**
     * GET /a/changes/{change-id}/revisions/{revision-id}/files
     * List files in a revision.
     */
    @GetMapping("/{changeId}/revisions/{revisionId}/files")
    fun listRevisionFiles(
        @PathVariable changeId: String,
        @PathVariable revisionId: String,
        @RequestParam(required = false) base: String?,
        @RequestParam(required = false) parent: Int?,
        @RequestParam(required = false) reviewed: Boolean?,
        @RequestParam(required = false, name = "q") query: String?
    ): ResponseEntity<Map<String, FileInfo>> {
        val files = changeService.listRevisionFiles(changeId, revisionId, base, parent, reviewed, query)
        return ResponseEntity.ok(files)
    }

    /**
     * GET /a/changes/{change-id}/revisions/{revision-id}/files/content?path={file-path}
     * Get file content.
     */
    @GetMapping("/{changeId}/revisions/{revisionId}/files/content")
    fun getFileContent(
        @PathVariable changeId: String,
        @PathVariable revisionId: String,
        @RequestParam path: String,
        @RequestParam(required = false) parent: Int?
    ): ResponseEntity<String> {
        try {
            println("DEBUG: getFileContent called with changeId=$changeId, revisionId=$revisionId, path=$path, parent=$parent")
            val content = changeService.getFileContent(changeId, revisionId, path, parent)
            return ResponseEntity.ok(content)
        } catch (e: Exception) {
            println("ERROR in getFileContent: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    /**
     * GET /a/changes/{change-id}/revisions/{revision-id}/files/diff?path={file-path}
     * Get file diff.
     */
    @GetMapping("/{changeId}/revisions/{revisionId}/files/diff")
    fun getFileDiff(
        @PathVariable changeId: String,
        @PathVariable revisionId: String,
        @RequestParam path: String,
        @RequestParam(required = false) base: String?,
        @RequestParam(required = false) parent: Int?,
        @RequestParam(required = false) context: Int?,
        @RequestParam(required = false) intraline: Boolean?,
        @RequestParam(required = false) whitespace: String?
    ): ResponseEntity<DiffInfo> {
        try {
            println("DEBUG: getFileDiff called with changeId=$changeId, revisionId=$revisionId, path=$path, base=$base, parent=$parent")
            val diff = changeService.getFileDiff(changeId, revisionId, path, base, parent, context, intraline, whitespace)
            return ResponseEntity.ok(diff)
        } catch (e: Exception) {
            println("ERROR in getFileDiff: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    /**
     * GET /a/changes/{change-id}/revisions/{revision-id}/patch
     * Get revision patch.
     */
    @GetMapping("/{changeId}/revisions/{revisionId}/patch")
    fun getRevisionPatch(
        @PathVariable changeId: String,
        @PathVariable revisionId: String,
        @RequestParam(required = false) zip: Boolean?
    ): ResponseEntity<String> {
        val patch = changeService.getRevisionPatch(changeId, revisionId, zip ?: false)
        return ResponseEntity.ok(patch)
    }
}
