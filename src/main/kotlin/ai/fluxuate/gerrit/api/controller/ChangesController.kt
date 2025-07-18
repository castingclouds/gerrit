package ai.fluxuate.gerrit.api.controller

import ai.fluxuate.gerrit.api.dto.*
import ai.fluxuate.gerrit.service.ChangeService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import jakarta.validation.Valid

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
}
