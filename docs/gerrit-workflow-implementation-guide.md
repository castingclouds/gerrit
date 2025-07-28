# Gerrit Workflow Implementation Guide

This document provides a comprehensive guide for implementing the Gerrit code review workflow in our modernized Gerrit project. It outlines the architecture, components, and implementation details required to support trunk-based development with Gerrit's change-based review system.

## Table of Contents

1. [Overview](#overview)
2. [Core Concepts](#core-concepts)
3. [Architecture](#architecture)
4. [Implementation Details](#implementation-details)
5. [Workflow Examples](#workflow-examples)
6. [Testing Strategy](#testing-strategy)

## Overview

Our modernized Gerrit project is transitioning from the legacy "gerrit-old" codebase to a modern Spring Boot and Gradle-based implementation. We currently have a working Git server that supports repository creation, cloning, and pushing. The next step is to implement the Gerrit code review workflow, which includes:

- Trunk-based development in the primary branch
- Change-ID based tracking of changes
- Virtual branches using refs/for
- Patchset management
- Support for pull, rebase, cherry-pick, and merge operations

This guide outlines how to implement these features in our codebase.

## Core Concepts

### Trunk-Based Development

Trunk-based development is a source control branching model where developers collaborate on code in a single branch called "trunk" (in our case, named "trunk" rather than the traditional "master" or "main"). The key principles are:

- The trunk branch is always in a releasable state
- Developers create short-lived feature branches for development
- Changes are frequently integrated into the trunk
- Continuous integration ensures trunk stability

### Change-IDs

Change-IDs are unique identifiers that track a logical change through its lifecycle, regardless of how many times the commit is amended or rebased. Key characteristics:

- Format: `I` followed by 40 hexadecimal characters (e.g., `I1a2b3c4d5e6f7g8h9i0j1k2l3m4n5o6p7q8r9s0t`)
- Embedded in commit messages as a footer: `Change-Id: I1a2b3c4d5e6f7g8h9i0j1k2l3m4n5o6p7q8r9s0t`
- Generated based on the content of the commit
- Preserved across amends and rebases

### Virtual Branches (refs/for)

Virtual branches are not actual Git branches but references used to submit changes for review:

- Format: `refs/for/<target-branch>` (e.g., `refs/for/trunk`)
- When pushing to a virtual branch, Gerrit creates or updates a change for review
- The actual commits are stored in `refs/changes/XX/YYYY/Z` where:
  - XX is the last two digits of the change number
  - YYYY is the change number
  - Z is the patchset number

### Patchsets

Patchsets represent revisions of a change:

- Each time a change is updated, a new patchset is created
- Patchsets are numbered sequentially starting from 1
- Each patchset has its own commit and can be accessed via `refs/changes/XX/YYYY/Z`
- Comments, reviews, and approvals are associated with specific patchsets

## Architecture

The Gerrit workflow implementation consists of several components:

### 1. Git Repository Service

The `GitRepositoryService` manages Git repositories and provides basic Git operations:

- Repository creation and management
- Branch operations
- Reference management

### 2. Change Service

The `ChangeService` manages changes and implements the core Gerrit workflow:

- Processing pushes to refs/for virtual branches
- Creating and updating changes based on commits
- Managing patchsets
- Handling change lifecycle (submit, abandon, restore)

### 3. Utilities

Various utility classes support the Gerrit workflow:

- `ChangeIdUtil`: Generates and validates Change-IDs
- `GitUtil`: Provides Git operations for file content, diffs, etc.
- `RebaseUtil`: Handles rebasing changes
- `PatchUtil`: Manages patchset operations

### 4. Data Model

The data model includes entities for tracking changes and related information:

- `ChangeEntity`: Represents a change with its metadata
- `PatchSetEntity`: Represents a revision of a change

### 5. REST API

The REST API provides endpoints for interacting with changes:

- `ChangesController`: Exposes operations for managing changes
- DTOs for request/response data

## Implementation Details

### 1. Change-ID Generation and Tracking

Change-IDs are generated using the `ChangeIdUtil` class, which follows the same algorithm as Gerrit's commit-msg hook:

```kotlin
// Generate a Change-Id based on commit content
fun generateChangeId(
    treeId: ObjectId,
    parentIds: List<ObjectId>,
    author: PersonIdent,
    committer: PersonIdent,
    commitMessage: String
): String {
    // Implementation details in ChangeIdUtil.kt
}

// Add or update Change-Id in commit message
fun addOrUpdateChangeId(
    commitMessage: String,
    treeId: ObjectId,
    parentIds: List<ObjectId>,
    author: PersonIdent,
    committer: PersonIdent
): String {
    // Implementation details in ChangeIdUtil.kt
}
```

To ensure all commits have Change-IDs, we need to:

1. Implement a server-side hook that adds Change-IDs to commits if missing
2. Provide a client-side commit-msg hook for developers to install

### 2. Virtual Branch Implementation (refs/for)

The virtual branch implementation is handled in the `ChangeService.processRefsForPush` method:

```kotlin
fun processRefsForPush(
    repository: Repository,
    refName: String,
    oldObjectId: ObjectId?,
    newObjectId: ObjectId,
    projectName: String,
    ownerId: Int
): ProcessResult {
    // Extract target branch from refs/for/branch
    val targetBranch = extractTargetBranch(refName)
        ?: return ProcessResult.error("Invalid refs/for/ format: $refName")
    
    // Get the commit being pushed
    RevWalk(repository).use { revWalk ->
        val commit = revWalk.parseCommit(newObjectId)
        
        // Extract and validate Change-Id from commit message
        val changeId = extractChangeId(commit.fullMessage)
            ?: return ProcessResult.error("Missing Change-Id in commit message")
        
        // Check if this is a new change or update to existing change
        val existingChange = changeRepository.findByChangeKey(changeId)
        
        return if (existingChange != null) {
            updateExistingChange(existingChange, commit, newObjectId, targetBranch, ownerId)
        } else {
            createNewChange(changeId, commit, newObjectId, projectName, targetBranch, ownerId)
        }
    }
}
```

To enhance this implementation:

1. Add support for additional options in the refs/for reference (e.g., `refs/for/trunk%topic=feature-x,r=reviewer@example.com`)
2. Implement proper access control for pushing to refs/for
3. Add validation for the target branch

### 3. Patchset Management

Patchsets are managed in the `ChangeService` through the `createNewChange` and `updateExistingChange` methods:

```kotlin
private fun createNewChange(
    changeId: String,
    commit: RevCommit,
    commitObjectId: ObjectId,
    projectName: String,
    targetBranch: String,
    ownerId: Int
): ProcessResult {
    // Create initial patch set
    val patchSet = mapOf(
        "id" to 1,
        "commitId" to commitObjectId.name,
        "uploader_id" to ownerId,
        "createdOn" to now.toString(),
        "description" to "Initial patch set",
        "isDraft" to false
    )
    
    val change = ChangeEntity(
        changeKey = changeId,
        ownerId = ownerId,
        projectName = projectName,
        destBranch = targetBranch,
        subject = subject,
        status = ChangeStatus.NEW,
        currentPatchSetId = 1,
        createdOn = now,
        lastUpdatedOn = now,
        patchSets = listOf(patchSet)
    )
    
    // Save change and return result
}

private fun updateExistingChange(
    existingChange: ChangeEntity,
    commit: RevCommit,
    commitObjectId: ObjectId,
    targetBranch: String,
    ownerId: Int
): ProcessResult {
    // Create new patch set
    val newPatchSetId = existingChange.currentPatchSetId + 1
    val newPatchSet = mapOf(
        "id" to newPatchSetId,
        "commitId" to commitObjectId.name,
        "uploader_id" to ownerId,
        "createdOn" to now.toString(),
        "description" to "Patch Set $newPatchSetId",
        "isDraft" to false
    )
    
    // Update change with new patch set
    val updatedPatchSets = existingChange.patchSets + newPatchSet
    val updatedChange = existingChange.copy(
        currentPatchSetId = newPatchSetId,
        lastUpdatedOn = now,
        patchSets = updatedPatchSets
    )
    
    // Save change and return result
}
```

To enhance patchset management:

1. Store patchsets in Git using the `refs/changes/XX/YYYY/Z` format
2. Implement proper cleanup of abandoned changes
3. Add support for draft patchsets

### 4. Workflow Operations

#### Submit (Merge to Trunk)

The submit operation merges a change into the trunk branch:

```kotlin
@Transactional
fun submitChange(changeId: String, input: SubmitInput): ChangeInfo {
    val change = findChangeByIdentifier(changeId)
    
    // 1. Get the latest patchset
    val patchSet = change.patchSets.last()
    val commitId = patchSet["commitId"] as String
    
    // 2. Open the repository
    val repository = getRepository(change.projectName)
    
    try {
        // 3. Create a new Git instance
        val git = Git(repository)
        
        // 4. Check if fast-forward merge is possible
        val revWalk = RevWalk(repository)
        val commit = revWalk.parseCommit(ObjectId.fromString(commitId))
        val targetRef = repository.exactRef("refs/heads/${change.destBranch}")
        
        if (targetRef != null) {
            val targetCommit = revWalk.parseCommit(targetRef.objectId)
            
            // 5. Perform the merge
            val refUpdate = repository.updateRef("refs/heads/${change.destBranch}")
            refUpdate.setNewObjectId(commit.id)
            refUpdate.setExpectedOldObjectId(targetRef.objectId)
            refUpdate.setRefLogMessage("Submit change ${change.id}", false)
            
            val result = refUpdate.update()
            
            if (result != RefUpdate.Result.FAST_FORWARD && result != RefUpdate.Result.NEW) {
                throw ConflictException("Cannot submit change: ${result.name}")
            }
        }
        
        // 6. Update change status
        val updatedChange = change.copy(
            status = ChangeStatus.MERGED,
            lastUpdatedOn = Instant.now()
        )
        
        return convertToChangeInfo(changeRepository.save(updatedChange))
    } finally {
        repository.close()
    }
}
```

#### Rebase

The rebase operation updates a change to be based on the latest trunk:

```kotlin
@Transactional
fun rebaseChange(changeId: String, input: RebaseInput): ChangeInfo {
    val change = findChangeByIdentifier(changeId)
    
    // 1. Get the latest patchset
    val patchSet = change.patchSets.last()
    val commitId = patchSet["commitId"] as String
    
    // 2. Open the repository
    val repository = getRepository(change.projectName)
    
    try {
        // 3. Create a new Git instance
        val git = Git(repository)
        
        // 4. Get the current commit
        val revWalk = RevWalk(repository)
        val commit = revWalk.parseCommit(ObjectId.fromString(commitId))
        
        // 5. Get the target branch head
        val targetRef = repository.exactRef("refs/heads/${change.destBranch}")
        if (targetRef == null) {
            throw NotFoundException("Target branch ${change.destBranch} not found")
        }
        
        val targetCommit = revWalk.parseCommit(targetRef.objectId)
        
        // 6. Check if rebase is needed
        if (revWalk.isMergedInto(targetCommit, commit)) {
            // Already up to date
            return convertToChangeInfo(change)
        }
        
        // 7. Perform rebase
        val cherryPick = git.cherryPick()
            .setNoCommit(true)
            .include(commit)
            .call()
        
        // 8. Create new commit with same author but new committer
        val newCommit = git.commit()
            .setMessage(commit.fullMessage)
            .setAuthor(commit.authorIdent)
            .call()
        
        // 9. Create new patchset
        val newPatchSetId = change.currentPatchSetId + 1
        val newPatchSet = mapOf(
            "id" to newPatchSetId,
            "commitId" to newCommit.name,
            "uploader_id" to getCurrentUser()._account_id.toInt(),
            "createdOn" to Instant.now().toString(),
            "description" to "Rebased patch set",
            "isDraft" to false
        )
        
        // 10. Update change with new patchset
        val updatedPatchSets = change.patchSets + newPatchSet
        val updatedChange = change.copy(
            currentPatchSetId = newPatchSetId,
            lastUpdatedOn = Instant.now(),
            patchSets = updatedPatchSets
        )
        
        return convertToChangeInfo(changeRepository.save(updatedChange))
    } finally {
        repository.close()
    }
}
```

#### Cherry-Pick

The cherry-pick operation creates a new change based on an existing one but targeting a different branch:

```kotlin
@Transactional
fun cherryPickChange(changeId: String, revisionId: String, input: CherryPickInput): ChangeInfo {
    val change = findChangeByIdentifier(changeId)
    
    // 1. Get the specified patchset
    val patchSet = findPatchSetByRevisionId(change, revisionId)
        ?: throw NotFoundException("Revision $revisionId not found")
    
    val commitId = patchSet["commitId"] as String
    
    // 2. Open the repository
    val repository = getRepository(change.projectName)
    
    try {
        // 3. Create a new Git instance
        val git = Git(repository)
        
        // 4. Get the commit to cherry-pick
        val revWalk = RevWalk(repository)
        val commit = revWalk.parseCommit(ObjectId.fromString(commitId))
        
        // 5. Get the target branch head
        val targetRef = repository.exactRef("refs/heads/${input.destination}")
        if (targetRef == null) {
            throw NotFoundException("Target branch ${input.destination} not found")
        }
        
        // 6. Checkout the target branch
        git.checkout()
            .setName(input.destination)
            .call()
        
        // 7. Perform cherry-pick
        val cherryPickResult = git.cherryPick()
            .include(commit)
            .setMessage(input.message ?: commit.fullMessage)
            .call()
        
        // 8. Generate new Change-Id
        val newCommit = revWalk.parseCommit(cherryPickResult)
        val newChangeId = ChangeIdUtil.generateChangeId(
            newCommit.tree.id,
            listOf(targetRef.objectId),
            newCommit.authorIdent,
            newCommit.committerIdent,
            newCommit.fullMessage
        )
        
        // 9. Create new change
        val newChange = ChangeEntity(
            changeKey = newChangeId,
            ownerId = getCurrentUser()._account_id.toInt(),
            projectName = change.projectName,
            destBranch = input.destination,
            subject = input.message ?: commit.shortMessage,
            status = ChangeStatus.NEW,
            currentPatchSetId = 1,
            createdOn = Instant.now(),
            lastUpdatedOn = Instant.now(),
            cherryPickOf = change.id,
            patchSets = listOf(
                mapOf(
                    "id" to 1,
                    "commitId" to newCommit.name,
                    "uploader_id" to getCurrentUser()._account_id.toInt(),
                    "createdOn" to Instant.now().toString(),
                    "description" to "Cherry-picked from change ${change.id} patchset ${patchSet["id"]}",
                    "isDraft" to false
                )
            )
        )
        
        return convertToChangeInfo(changeRepository.save(newChange))
    } finally {
        repository.close()
    }
}
```

## Workflow Examples

### Example 1: Creating a New Change

1. Developer creates a local branch:
   ```bash
   git checkout -b feature-x trunk
   ```

2. Developer makes changes and commits:
   ```bash
   git add .
   git commit -m "Implement feature X"
   ```

3. The commit-msg hook adds a Change-Id:
   ```
   Implement feature X
   
   Change-Id: I1a2b3c4d5e6f7g8h9i0j1k2l3m4n5o6p7q8r9s0t
   ```

4. Developer pushes to refs/for/trunk:
   ```bash
   git push origin HEAD:refs/for/trunk
   ```

5. Gerrit creates a new change with patchset 1

### Example 2: Updating an Existing Change

1. Developer makes additional changes:
   ```bash
   git add .
   git commit --amend
   ```

2. The Change-Id is preserved in the commit message

3. Developer pushes to refs/for/trunk:
   ```bash
   git push origin HEAD:refs/for/trunk
   ```

4. Gerrit updates the existing change with a new patchset

### Example 3: Rebasing a Change

1. Developer rebases on latest trunk:
   ```bash
   git fetch origin
   git rebase origin/trunk
   ```

2. The Change-Id is preserved in the commit message

3. Developer pushes to refs/for/trunk:
   ```bash
   git push origin HEAD:refs/for/trunk
   ```

4. Gerrit updates the existing change with a new patchset

### Example 4: Submitting a Change

1. Reviewer approves the change in the UI

2. Submitter clicks "Submit" in the UI

3. Gerrit merges the change into the trunk branch

4. The change status is updated to MERGED

## Testing Strategy

To ensure the Gerrit workflow implementation works correctly, we should implement the following tests:

### Unit Tests

1. Test Change-ID generation and validation
2. Test commit message parsing and manipulation
3. Test virtual branch reference parsing

### Integration Tests

1. Test creating a new change via refs/for
2. Test updating an existing change
3. Test submitting a change to trunk
4. Test rebasing a change
5. Test cherry-picking a change

### End-to-End Tests

1. Test the complete workflow from change creation to submission
2. Test handling of conflicts during rebase and submit
3. Test multiple changes with dependencies

## Conclusion

This guide provides a comprehensive approach to implementing the Gerrit workflow in our modernized Gerrit project. By following these guidelines, we can create a robust code review system that supports trunk-based development with all the features of Gerrit's change-based review process.

The implementation leverages our existing codebase and extends it with the necessary components to support the full Gerrit workflow. The key additions are:

1. Enhanced support for Change-IDs
2. Complete implementation of refs/for virtual branches
3. Proper patchset management
4. Support for workflow operations (submit, rebase, cherry-pick)

With these components in place, our system will provide a modern, efficient code review workflow that maintains compatibility with Gerrit's established patterns.