# Future of Stacked Diffs: AI-Native SCM Architecture

## Executive Summary

This document outlines a comprehensive approach to building stacked diff capability with poly repo support for trunk-based development, designed specifically for the future of AI coding and SCM. The architecture leverages the existing Gerrit workflow while introducing modern AI-native features that enable atomic change sets across multiple repositories.

## Current State Analysis

### Existing Gerrit Architecture

The current Gerrit implementation provides a solid foundation with:

1. **Change Management**: `ChangeEntity` with JSONB storage for flexibility
2. **Patch Set System**: `PatchSetEntity` with virtual branch refs (`refs/changes/XX/YYYY/Z`)
3. **Trunk-Based Workflow**: Direct pushes to trunk with Change-Id validation
4. **REST API**: Comprehensive Changes API with full CRUD operations
5. **Git Integration**: JGit-based repository management with hooks

### Key Strengths

- **Virtual Branch System**: Changes are stored as virtual refs, not actual branches
- **Change-Id Persistence**: Stable identifiers across patch set updates
- **JSONB Flexibility**: Extensible metadata storage for future enhancements
- **Hook Integration**: Pre/post receive hooks for workflow enforcement
- **Multi-Protocol Support**: HTTP and SSH access patterns

## Stacked Diff Architecture

### Core Concepts

#### 1. Change-Id and Virtual Branch Relationship
A **Change-Id** is a stable identifier that persists across commit amendments and maps to a virtual branch on the server side:

```kotlin
// Client-side: Change-Id in commit message
// Server-side: Virtual branch refs/changes/XX/YYYY/Z
data class ChangeInfo(
    val changeId: String,                   // Stable Change-Id (e.g., I1234567890abcdef...)
    val virtualBranchRef: String,          // refs/changes/XX/YYYY/Z
    val projectName: String,               // Target project
    val branch: String = "trunk",          // Target branch
    val status: ChangeStatus,
    val currentPatchSet: PatchSetInfo,
    val owner: AccountInfo
)
```

#### 2. ChangeRequest: Atomic Grouping of Change-Ids
A **ChangeRequest** groups multiple Change-Ids that should be submitted atomically, supporting cross-project scenarios:

```kotlin
data class ChangeRequest(
    val requestId: String,                  // Unique request identifier
    val title: String,                       // Human-readable title
    val description: String?,               // Optional description
    val owner: AccountInfo,                 // Request owner
    val changes: List<ChangeRequestItem>,   // Ordered list of changes
    val status: ChangeRequestStatus,        // Overall request status
    val priority: Priority = Priority.NORMAL,
    val createdAt: Instant,
    val updatedAt: Instant,
    val metadata: Map<String, Any> = emptyMap()
)

data class ChangeRequestItem(
    val changeId: String,                   // Change-Id from existing system
    val position: Int,                     // Position in request (0-based)
    val projectName: String,               // Target project
    val branch: String = "trunk",          // Target branch
    val dependencies: List<String> = emptyList(), // Dependencies on other changes
    val status: ChangeStatus,
    val submissionOrder: Int? = null       // For cross-project ordering
)

enum class ChangeRequestStatus {
    DRAFT,      // Work in progress
    REVIEW,     // Under review
    APPROVED,   // Approved but not submitted
    QUEUED,     // In submission queue
    SUBMITTED,  // Successfully submitted
    FAILED,     // Submission failed
    ABANDONED   // Abandoned
}

enum class Priority {
    LOW,
    NORMAL,
    HIGH,
    URGENT,
    BLOCKING
}
```

#### 3. ChangeQueue: Prioritization and Ordering
A **ChangeQueue** allows team leads and managers to reorder ChangeRequests based on priorities and dependencies:

```kotlin
data class ChangeQueue(
    val queueId: String,                    // Unique queue identifier
    val name: String,                       // Queue name (e.g., "main", "hotfixes")
    val description: String?,
    val owner: AccountInfo,                 // Queue owner/manager
    val scope: QueueScope,                  // Global or project-specific
    val requests: List<QueuedRequest>,      // Ordered list of requests
    val maxConcurrentSubmissions: Int = 1,  // Limit concurrent submissions
    val createdAt: Instant,
    val updatedAt: Instant
)

data class QueuedRequest(
    val requestId: String,                  // Reference to ChangeRequest
    val position: Int,                     // Position in queue
    val priority: Priority,
    val addedBy: AccountInfo,
    val addedAt: Instant,
    val estimatedDuration: Duration?,       // Estimated submission time
    val dependencies: List<String> = emptyList() // Queue dependencies
)

enum class QueueScope {
    GLOBAL,     // Across all projects
    PROJECT,    // Project-specific
    TEAM        // Team-specific
}
```

#### 4. Atomic Submission Guarantees
ChangeRequests provide atomic submission guarantees across multiple projects:

```kotlin
data class AtomicSubmissionConfig(
    val mode: AtomicMode = AtomicMode.STRICT,
    val rollbackOnFailure: Boolean = true,
    val maxRetries: Int = 3,
    val timeout: Duration = Duration.ofMinutes(30)
)

enum class AtomicMode {
    STRICT,     // All changes must succeed or all fail
    BEST_EFFORT, // Try to submit as many as possible
    ORDERED     // Submit in dependency order, stop on failure
}

data class SubmissionResult(
    val requestId: String,
    val success: Boolean,
    val submittedChanges: List<String>,     // Successfully submitted Change-Ids
    val failedChanges: List<String>,        // Failed Change-Ids
    val errors: List<SubmissionError>,
    val duration: Duration,
    val rollbackPerformed: Boolean = false
)
```

### Enhanced Data Model

#### Extended ChangeEntity
```kotlin
data class ChangeEntity(
    // ... existing fields ...
    
    // ChangeRequest-related fields
    val changeRequestId: String? = null,    // Associated ChangeRequest
    val requestPosition: Int? = null,       // Position within request
    val requestDependencies: List<String> = emptyList(), // Dependencies within request
    
    // Queue-related fields
    val queueId: String? = null,            // Associated queue
    val queuePosition: Int? = null,         // Position in queue
    val priority: Priority = Priority.NORMAL,
    
    // Atomic submission fields
    val atomicGroupId: String? = null,      // For atomic submission groups
    val submissionOrder: Int? = null,       // For ordered submission
    
    // AI-specific fields
    val aiGenerated: Boolean = false,
    val aiContext: Map<String, Any> = emptyMap(), // AI generation context
    val aiSuggestions: List<AISuggestion> = emptyList()
)
```

#### New ChangeRequest Entity
```kotlin
@Entity
@Table(name = "change_requests")
data class ChangeRequestEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    
    @Column(name = "request_id", nullable = false, unique = true)
    val requestId: String,
    
    @Column(name = "name", nullable = false)
    val name: String,
    
    @Column(name = "description")
    val description: String? = null,
    
    @Column(name = "owner_id", nullable = false)
    val ownerId: Int,
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    val status: ChangeRequestStatus = ChangeRequestStatus.DRAFT,
    
    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false)
    val priority: Priority = Priority.NORMAL,
    
    @Column(name = "created_on", nullable = false)
    val createdAt: Instant = Instant.now(),
    
    @Column(name = "updated_on", nullable = false)
    val updatedAt: Instant = Instant.now(),
    
    // Request configuration
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config", columnDefinition = "jsonb")
    val config: ChangeRequestConfig = ChangeRequestConfig(),
    
    // AI-specific metadata
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ai_metadata", columnDefinition = "jsonb")
    val aiMetadata: Map<String, Any> = emptyMap()
)

data class ChangeRequestConfig(
    val atomicSubmission: Boolean = true,
    val allowPartialSubmission: Boolean = false,
    val autoRebase: Boolean = true,
    val crossProjectValidation: Boolean = true,
    val aiAssistedReview: Boolean = false,
    val maxRetries: Int = 3,
    val timeout: Duration = Duration.ofMinutes(30)
)
```

#### New ChangeQueue Entity
```kotlin
@Entity
@Table(name = "change_queues")
data class ChangeQueueEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    
    @Column(name = "queue_id", nullable = false, unique = true)
    val queueId: String,
    
    @Column(name = "name", nullable = false)
    val name: String,
    
    @Column(name = "description")
    val description: String? = null,
    
    @Column(name = "owner_id", nullable = false)
    val ownerId: Int,
    
    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false)
    val scope: QueueScope = QueueScope.GLOBAL,
    
    @Column(name = "max_concurrent_submissions", nullable = false)
    val maxConcurrentSubmissions: Int = 1,
    
    @Column(name = "created_on", nullable = false)
    val createdAt: Instant = Instant.now(),
    
    @Column(name = "updated_on", nullable = false)
    val updatedAt: Instant = Instant.now(),
    
    // Queue configuration
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config", columnDefinition = "jsonb")
    val config: QueueConfig = QueueConfig()
)

data class QueueConfig(
    val autoProcess: Boolean = true,
    val requireApproval: Boolean = false,
    val allowPriorityOverrides: Boolean = true,
    val maxQueueSize: Int? = null,
    val estimatedProcessingTime: Duration = Duration.ofMinutes(10)
)
```

### Service Layer Enhancements

#### ChangeRequestService
```kotlin
@Service
class ChangeRequestService(
    private val changeService: ChangeService,
    private val changeRepository: ChangeEntityRepository,
    private val changeRequestRepository: ChangeRequestRepository,
    private val gitRepositoryService: GitRepositoryService
) {
    
    /**
     * Create a new change request
     */
    fun createChangeRequest(input: CreateChangeRequestInput): ChangeRequestInfo {
        // Validate request configuration
        validateChangeRequestInput(input)
        
        // Create change request entity
        val changeRequest = ChangeRequestEntity(
            requestId = generateRequestId(),
            name = input.name,
            description = input.description,
            ownerId = getCurrentUser().accountId.toInt(),
            priority = input.priority,
            config = input.config
        )
        
        val savedRequest = changeRequestRepository.save(changeRequest)
        
        // Add changes to request if provided
        if (input.changes.isNotEmpty()) {
            addChangesToRequest(savedRequest.requestId, input.changes)
        }
        
        return mapToChangeRequestInfo(savedRequest)
    }
    
    /**
     * Add changes to an existing change request
     */
    fun addChangesToRequest(requestId: String, changes: List<AddChangeToRequestInput>): ChangeRequestInfo {
        val request = changeRequestRepository.findByRequestId(requestId)
            ?: throw NotFoundException("Change request not found: $requestId")
        
        // Validate changes can be added to request
        validateChangesForRequest(request, changes)
        
        // Update changes with request information
        changes.forEachIndexed { index, changeInput ->
            val change = changeRepository.findByChangeKey(changeInput.changeId)
                ?: throw NotFoundException("Change not found: ${changeInput.changeId}")
            
            val updatedChange = change.copy(
                changeRequestId = requestId,
                requestPosition = request.changes.size + index,
                requestDependencies = changeInput.dependencies,
                submissionOrder = changeInput.submissionOrder
            )
            
            changeRepository.save(updatedChange)
        }
        
        return mapToChangeRequestInfo(request)
    }
    
    /**
     * Submit entire change request atomically
     */
    fun submitChangeRequest(requestId: String, input: SubmitChangeRequestInput): SubmitChangeRequestResult {
        val request = changeRequestRepository.findByRequestId(requestId)
            ?: throw NotFoundException("Change request not found: $requestId")
        
        // Validate request can be submitted
        validateRequestForSubmission(request)
        
        // For atomic submission, ensure all changes succeed or fail together
        if (request.config.atomicSubmission) {
            return submitRequestAtomically(request, input)
        } else {
            return submitRequestIndividually(request, input)
        }
    }
    
    /**
     * AI-assisted change request creation
     */
    fun createChangeRequestWithAI(input: CreateChangeRequestWithAIInput): ChangeRequestInfo {
        // Use AI to analyze changes and suggest request structure
        val aiAnalysis = analyzeChangesWithAI(input.changes)
        
        // Create optimized request based on AI analysis
        val requestInput = CreateChangeRequestInput(
            name = input.name,
            description = input.description,
            changes = aiAnalysis.suggestedChanges,
            config = aiAnalysis.suggestedConfig,
            priority = aiAnalysis.suggestedPriority
        )
        
        return createChangeRequest(requestInput)
    }
    
    /**
     * Validate cross-project dependencies
     */
    fun validateCrossProjectDependencies(requestId: String): ValidationResult {
        val request = getChangeRequest(requestId)
        val issues = mutableListOf<ValidationIssue>()
        
        // Check for circular dependencies
        val circularDeps = detectCircularDependencies(request.changes)
        if (circularDeps.isNotEmpty()) {
            issues.add(ValidationIssue(
                type = ValidationIssueType.CIRCULAR_DEPENDENCY,
                message = "Circular dependencies detected",
                details = circularDeps
            ))
        }
        
        // Check for missing dependencies
        val missingDeps = findMissingDependencies(request.changes)
        if (missingDeps.isNotEmpty()) {
            issues.add(ValidationIssue(
                type = ValidationIssueType.MISSING_DEPENDENCY,
                message = "Missing dependencies detected",
                details = missingDeps
            ))
        }
        
        return ValidationResult(
            isValid = issues.isEmpty(),
            issues = issues
        )
    }
    
    private fun submitRequestAtomically(request: ChangeRequestEntity, input: SubmitChangeRequestInput): SubmitChangeRequestResult {
        val changes = request.changes.sortedBy { it.submissionOrder ?: it.position }
        val results = mutableListOf<ChangeSubmissionResult>()
        val submittedChanges = mutableListOf<String>()
        val failedChanges = mutableListOf<String>()
        
        try {
            // Submit changes in order
            changes.forEach { change ->
                val result = submitChange(change.changeId, input)
                results.add(result)
                
                if (result.success) {
                    submittedChanges.add(change.changeId)
                } else {
                    failedChanges.add(change.changeId)
                    // If atomic submission is strict, stop here
                    if (request.config.atomicSubmission && !request.config.allowPartialSubmission) {
                        throw AtomicSubmissionException("Atomic submission failed for change: ${change.changeId}")
                    }
                }
            }
            
            // Update request status
            val finalStatus = if (failedChanges.isEmpty()) ChangeRequestStatus.SUBMITTED else ChangeRequestStatus.FAILED
            updateRequestStatus(request.requestId, finalStatus)
            
            return SubmitChangeRequestResult(
                requestId = request.requestId,
                success = failedChanges.isEmpty(),
                submittedChanges = submittedChanges,
                failedChanges = failedChanges,
                results = results,
                duration = Duration.between(input.startTime, Instant.now())
            )
            
        } catch (e: AtomicSubmissionException) {
            // Rollback successful submissions if needed
            if (request.config.rollbackOnFailure && submittedChanges.isNotEmpty()) {
                rollbackSubmittedChanges(submittedChanges)
            }
            
            updateRequestStatus(request.requestId, ChangeRequestStatus.FAILED)
            
            return SubmitChangeRequestResult(
                requestId = request.requestId,
                success = false,
                submittedChanges = emptyList(),
                failedChanges = request.changes.map { it.changeId },
                results = results,
                duration = Duration.between(input.startTime, Instant.now()),
                rollbackPerformed = true
            )
        }
    }
}
```

#### ChangeQueueService
```kotlin
@Service
class ChangeQueueService(
    private val changeRequestService: ChangeRequestService,
    private val changeQueueRepository: ChangeQueueRepository,
    private val projectService: ProjectService
) {
    
    /**
     * Create a new change queue
     */
    fun createQueue(input: CreateQueueInput): ChangeQueueInfo {
        // Validate queue configuration
        validateQueueInput(input)
        
        // Create queue entity
        val queue = ChangeQueueEntity(
            queueId = generateQueueId(),
            name = input.name,
            description = input.description,
            ownerId = getCurrentUser().accountId.toInt(),
            scope = input.scope,
            maxConcurrentSubmissions = input.maxConcurrentSubmissions,
            config = input.config
        )
        
        val savedQueue = changeQueueRepository.save(queue)
        return mapToQueueInfo(savedQueue)
    }
    
    /**
     * Add a change request to a queue
     */
    fun addRequestToQueue(queueId: String, requestId: String, input: AddRequestToQueueInput): ChangeQueueInfo {
        val queue = changeQueueRepository.findByQueueId(queueId)
            ?: throw NotFoundException("Queue not found: $queueId")
        
        val request = changeRequestService.getChangeRequest(requestId)
            ?: throw NotFoundException("Change request not found: $requestId")
        
        // Validate request can be added to queue
        validateRequestForQueue(queue, request)
        
        // Add request to queue
        val queuedRequest = QueuedRequest(
            requestId = requestId,
            position = queue.requests.size,
            priority = input.priority ?: request.priority,
            addedBy = getCurrentUser(),
            addedAt = Instant.now(),
            estimatedDuration = input.estimatedDuration,
            dependencies = input.dependencies
        )
        
        val updatedQueue = queue.copy(
            requests = queue.requests + queuedRequest
        )
        
        changeQueueRepository.save(updatedQueue)
        return mapToQueueInfo(updatedQueue)
    }
    
    /**
     * Reorder requests in a queue
     */
    fun reorderQueue(queueId: String, input: ReorderQueueInput): ChangeQueueInfo {
        val queue = changeQueueRepository.findByQueueId(queueId)
            ?: throw NotFoundException("Queue not found: $queueId")
        
        // Validate reorder request
        validateReorderInput(queue, input)
        
        // Apply reordering
        val reorderedRequests = applyReorder(queue.requests, input.reorderOperations)
        
        val updatedQueue = queue.copy(
            requests = reorderedRequests,
            updatedAt = Instant.now()
        )
        
        changeQueueRepository.save(updatedQueue)
        return mapToQueueInfo(updatedQueue)
    }
    
    /**
     * Process next request in queue
     */
    fun processNextRequest(queueId: String): ProcessQueueResult {
        val queue = changeQueueRepository.findByQueueId(queueId)
            ?: throw NotFoundException("Queue not found: $queueId")
        
        // Find next request to process
        val nextRequest = findNextRequest(queue)
            ?: return ProcessQueueResult(queueId, false, "No requests ready for processing")
        
        // Check if we can process (respecting max concurrent submissions)
        if (!canProcessRequest(queue, nextRequest)) {
            return ProcessQueueResult(queueId, false, "Maximum concurrent submissions reached")
        }
        
        // Submit the request
        val submitInput = SubmitChangeRequestInput(
            startTime = Instant.now(),
            submitter = getCurrentUser(),
            force = false
        )
        
        val result = changeRequestService.submitChangeRequest(nextRequest.requestId, submitInput)
        
        // Remove from queue if successful
        if (result.success) {
            removeRequestFromQueue(queueId, nextRequest.requestId)
        }
        
        return ProcessQueueResult(
            queueId = queueId,
            success = result.success,
            processedRequestId = nextRequest.requestId,
            result = result
        )
    }
    
    /**
     * Auto-process queue (for background processing)
     */
    @Scheduled(fixedDelay = 30000) // Every 30 seconds
    fun autoProcessQueues() {
        val queues = changeQueueRepository.findByConfigAutoProcessTrue()
        
        queues.forEach { queue ->
            if (queue.requests.isNotEmpty()) {
                try {
                    processNextRequest(queue.queueId)
                } catch (e: Exception) {
                    logger.error("Failed to auto-process queue: ${queue.queueId}", e)
                }
            }
        }
    }
    
    private fun findNextRequest(queue: ChangeQueueEntity): QueuedRequest? {
        return queue.requests
            .filter { request -> isRequestReady(request, queue) }
            .minByOrNull { it.priority.ordinal }
    }
    
    private fun isRequestReady(request: QueuedRequest, queue: ChangeQueueEntity): Boolean {
        // Check if all dependencies are satisfied
        val dependencies = request.dependencies
        val satisfiedDependencies = dependencies.all { depRequestId ->
            // Check if dependency is already submitted or not in this queue
            val depRequest = queue.requests.find { it.requestId == depRequestId }
            depRequest == null || depRequest.position < request.position
        }
        
        return satisfiedDependencies
    }
    
    private fun canProcessRequest(queue: ChangeQueueEntity, request: QueuedRequest): Boolean {
        val currentlyProcessing = queue.requests.count { it.status == QueuedRequestStatus.PROCESSING }
        return currentlyProcessing < queue.maxConcurrentSubmissions
    }
}
```

### API Layer Extensions

#### ChangeRequestController
```kotlin
@RestController
@RequestMapping("/a/changerequests")
class ChangeRequestController(
    private val changeRequestService: ChangeRequestService,
    private val changeQueueService: ChangeQueueService
) {
    
    /**
     * Create a new change request
     */
    @PostMapping
    fun createChangeRequest(@Valid @RequestBody input: CreateChangeRequestInput): ResponseEntity<ChangeRequestInfo> {
        val request = changeRequestService.createChangeRequest(input)
        return ResponseEntity.status(HttpStatus.CREATED).body(request)
    }
    
    /**
     * Get change request details
     */
    @GetMapping("/{requestId}")
    fun getChangeRequest(@PathVariable requestId: String): ResponseEntity<ChangeRequestInfo> {
        val request = changeRequestService.getChangeRequest(requestId)
        return ResponseEntity.ok(request)
    }
    
    /**
     * Submit a change request
     */
    @PostMapping("/{requestId}/submit")
    fun submitChangeRequest(
        @PathVariable requestId: String,
        @Valid @RequestBody input: SubmitChangeRequestInput
    ): ResponseEntity<SubmitChangeRequestResult> {
        val result = changeRequestService.submitChangeRequest(requestId, input)
        return ResponseEntity.ok(result)
    }
    
    /**
     * Add changes to a change request
     */
    @PostMapping("/{requestId}/changes")
    fun addChangesToRequest(
        @PathVariable requestId: String,
        @Valid @RequestBody input: List<AddChangeToRequestInput>
    ): ResponseEntity<ChangeRequestInfo> {
        val request = changeRequestService.addChangesToRequest(requestId, input)
        return ResponseEntity.ok(request)
    }
    
    /**
     * Validate cross-project dependencies
     */
    @PostMapping("/{requestId}/validate")
    fun validateChangeRequest(@PathVariable requestId: String): ResponseEntity<ValidationResult> {
        val result = changeRequestService.validateCrossProjectDependencies(requestId)
        return ResponseEntity.ok(result)
    }
}
```

#### ChangeQueueController
```kotlin
@RestController
@RequestMapping("/a/queues")
class ChangeQueueController(
    private val changeQueueService: ChangeQueueService,
    private val changeRequestService: ChangeRequestService
) {
    
    /**
     * Create a new change queue
     */
    @PostMapping
    fun createQueue(@Valid @RequestBody input: CreateQueueInput): ResponseEntity<ChangeQueueInfo> {
        val queue = changeQueueService.createQueue(input)
        return ResponseEntity.status(HttpStatus.CREATED).body(queue)
    }
    
    /**
     * Get queue details
     */
    @GetMapping("/{queueId}")
    fun getQueue(@PathVariable queueId: String): ResponseEntity<ChangeQueueInfo> {
        val queue = changeQueueService.getQueue(queueId)
        return ResponseEntity.ok(queue)
    }
    
    /**
     * Add a change request to a queue
     */
    @PostMapping("/{queueId}/requests")
    fun addRequestToQueue(
        @PathVariable queueId: String,
        @RequestParam requestId: String,
        @Valid @RequestBody input: AddRequestToQueueInput
    ): ResponseEntity<ChangeQueueInfo> {
        val queue = changeQueueService.addRequestToQueue(queueId, requestId, input)
        return ResponseEntity.ok(queue)
    }
    
    /**
     * Reorder requests in a queue
     */
    @PutMapping("/{queueId}/reorder")
    fun reorderQueue(
        @PathVariable queueId: String,
        @Valid @RequestBody input: ReorderQueueInput
    ): ResponseEntity<ChangeQueueInfo> {
        val queue = changeQueueService.reorderQueue(queueId, input)
        return ResponseEntity.ok(queue)
    }
    
    /**
     * Process next request in queue
     */
    @PostMapping("/{queueId}/process")
    fun processNextRequest(@PathVariable queueId: String): ResponseEntity<ProcessQueueResult> {
        val result = changeQueueService.processNextRequest(queueId)
        return ResponseEntity.ok(result)
    }
    
    /**
     * Get all queues
     */
    @GetMapping
    fun getQueues(
        @RequestParam(required = false) scope: QueueScope?,
        @RequestParam(required = false) ownerId: Int?
    ): ResponseEntity<List<ChangeQueueInfo>> {
        val queues = changeQueueService.getQueues(scope, ownerId)
        return ResponseEntity.ok(queues)
    }
}
```

### Git Integration Enhancements

#### Enhanced Git Hooks
```bash
#!/bin/bash
# Enhanced commit-msg hook for stacked diffs

# Extract Change-Id
CHANGE_ID=$(grep -o "Change-Id: I[0-9a-f]\{40\}" "$1" | head -1)

# Check for stack information
STACK_ID=$(grep -o "Stack-Id: [a-zA-Z0-9_-]\+" "$1" | head -1 | cut -d' ' -f2)
STACK_POSITION=$(grep -o "Stack-Position: [0-9]\+" "$1" | head -1 | cut -d' ' -f2)

if [ -n "$STACK_ID" ]; then
    echo "Stack-Id: $STACK_ID" >> "$1"
    if [ -n "$STACK_POSITION" ]; then
        echo "Stack-Position: $STACK_POSITION" >> "$1"
    fi
fi

# Add AI context if present
if [ -n "$GERRIT_AI_CONTEXT" ]; then
    echo "AI-Context: $GERRIT_AI_CONTEXT" >> "$1"
fi
```

#### Enhanced Pre-receive Hook
```kotlin
class EnhancedPreReceiveHook(
    private val stackService: StackService,
    private val polyRepoService: PolyRepoService
) {
    
    fun processPush(
        repository: Repository,
        refName: String,
        oldObjectId: ObjectId?,
        newObjectId: ObjectId
    ): ProcessResult {
        
        // Extract stack information from commit
        val commit = RevWalk(repository).use { it.parseCommit(newObjectId) }
        val stackId = extractStackId(commit.fullMessage)
        val stackPosition = extractStackPosition(commit.fullMessage)
        
        if (stackId != null) {
            // Validate stack constraints
            val validation = validateStackConstraints(stackId, stackPosition, commit)
            if (!validation.isValid) {
                return ProcessResult.error("Stack validation failed: ${validation.message}")
            }
            
            // Update stack metadata
            updateStackMetadata(stackId, commit)
        }
        
        // Continue with normal change processing
        return processNormalChange(repository, refName, oldObjectId, newObjectId)
    }
    
    private fun validateStackConstraints(
        stackId: String,
        position: Int?,
        commit: RevCommit
    ): ValidationResult {
        val stack = stackService.getStack(stackId)
        
        // Check if position is valid for stack
        if (position != null && position > stack.changes.size) {
            return ValidationResult(false, "Invalid stack position: $position")
        }
        
        // Check dependencies
        val dependencies = extractDependencies(commit.fullMessage)
        val missingDeps = findMissingDependencies(stack, dependencies)
        if (missingDeps.isNotEmpty()) {
            return ValidationResult(false, "Missing dependencies: $missingDeps")
        }
        
        return ValidationResult(true, null)
    }
}
```

### AI Integration Features

#### AI-Assisted Stack Creation
```kotlin
@Service
class AIStackService(
    private val openAIClient: OpenAIClient,
    private val gitRepositoryService: GitRepositoryService
) {
    
    /**
     * Analyze changes and suggest optimal stack structure
     */
    fun analyzeChangesForStack(changes: List<ChangeInfo>): AIStackAnalysis {
        val changeContexts = changes.map { change ->
            buildChangeContext(change)
        }
        
        val prompt = buildAnalysisPrompt(changeContexts)
        val response = openAIClient.analyze(prompt)
        
        return parseAIResponse(response)
    }
    
    /**
     * Generate commit messages for stack changes
     */
    fun generateCommitMessages(stack: ChangeStack): List<String> {
        val stackContext = buildStackContext(stack)
        val prompt = buildCommitMessagePrompt(stackContext)
        val response = openAIClient.generate(prompt)
        
        return parseCommitMessages(response)
    }
    
    /**
     * Suggest reviewers for stack
     */
    fun suggestReviewers(stack: ChangeStack): List<AccountInfo> {
        val stackContext = buildStackContext(stack)
        val prompt = buildReviewerSuggestionPrompt(stackContext)
        val response = openAIClient.analyze(prompt)
        
        return parseReviewerSuggestions(response)
    }
    
    /**
     * Detect potential conflicts in stack
     */
    fun detectPotentialConflicts(stack: ChangeStack): List<ConflictPrediction> {
        val stackContext = buildStackContext(stack)
        val prompt = buildConflictDetectionPrompt(stackContext)
        val response = openAIClient.analyze(prompt)
        
        return parseConflictPredictions(response)
    }
}
```

### Workflow Enhancements

#### ChangeRequest-Based Workflow
1. **ChangeRequest Creation**: Developer creates a ChangeRequest with multiple related Change-Ids
2. **AI Assistance**: AI suggests optimal structure and commit messages
3. **Dependency Management**: System tracks cross-change dependencies within the request
4. **Atomic Review**: Entire ChangeRequest reviewed as a unit
5. **Atomic Submission**: All changes in the request submitted together or not at all

#### ChangeQueue-Based Workflow
1. **Queue Management**: Team leads create and manage queues for different priorities
2. **Request Prioritization**: ChangeRequests are added to queues with priorities
3. **Dependency Tracking**: Queue respects dependencies between requests
4. **Ordered Processing**: Requests processed in priority order
5. **Concurrent Control**: Limit concurrent submissions to prevent conflicts

#### Cross-Project Workflow
1. **Cross-Project Analysis**: AI analyzes changes across multiple projects
2. **Dependency Mapping**: System maps dependencies between projects
3. **Atomic Validation**: Ensures all projects can be updated atomically
4. **Ordered Submission**: Submits projects in dependency order
5. **Rollback Support**: Automatic rollback if any project fails

### Implementation Roadmap

#### Phase 1: Core ChangeRequest Support (Months 1-2)
- [ ] Extend data model with ChangeRequest entities
- [ ] Implement basic ChangeRequestService
- [ ] Add ChangeRequest-aware Git hooks
- [ ] Create ChangeRequest REST API endpoints
- [ ] Basic ChangeRequest validation

#### Phase 2: ChangeQueue Support (Months 3-4)
- [ ] Implement ChangeQueueService
- [ ] Queue prioritization and ordering
- [ ] Dependency tracking within queues
- [ ] Queue validation and conflict detection
- [ ] Background queue processing

#### Phase 3: Cross-Project Support (Months 5-6)
- [ ] Cross-project dependency tracking
- [ ] Atomic submission across projects
- [ ] Cross-project validation
- [ ] Project-specific queue management
- [ ] Cross-project conflict detection

#### Phase 4: AI Integration (Months 7-8)
- [ ] AI-assisted ChangeRequest creation
- [ ] Intelligent commit message generation
- [ ] Reviewer suggestions
- [ ] Conflict prediction
- [ ] Code review assistance

#### Phase 5: Advanced Features (Months 9-10)
- [ ] ChangeRequest templates and patterns
- [ ] Advanced dependency visualization
- [ ] Performance optimizations
- [ ] Integration with CI/CD
- [ ] Advanced AI features

### Benefits for AI Coding/SCM

1. **Atomic Change Sets**: Enable AI to create complex features across multiple commits
2. **Dependency Management**: AI can manage complex change dependencies
3. **Cross-Project Coordination**: Support for AI-generated changes across projects
4. **Intelligent Prioritization**: AI can suggest optimal queue ordering
5. **Predictive Analysis**: AI can predict potential issues before submission

### Conclusion

This architecture provides a solid foundation for the future of AI-native SCM, combining the proven Gerrit workflow with modern ChangeRequest capabilities and ChangeQueue prioritization. The AI integration features enable more intelligent and efficient development workflows, while the atomic submission guarantees ensure codebase consistency.

The implementation leverages existing Gerrit strengths while adding the flexibility needed for AI-driven development, making it an ideal platform for the future of collaborative software development. 