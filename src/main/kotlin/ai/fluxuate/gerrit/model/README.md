# Gerrit Domain Models

This directory contains the core domain models for the Gerrit code review system, implemented as Kotlin data classes. These models represent the fundamental entities used throughout the system.

## Overview

The domain models are designed to closely mirror the original Gerrit Java entities while taking advantage of Kotlin's modern language features like data classes, null safety, and expression-based syntax.

## Core Models

### User

Represents a user account in the Gerrit system.

```kotlin
data class User(
    val id: Id,
    val registeredOn: Instant,
    val fullName: String? = null,
    val displayName: String? = null,
    val preferredEmail: String? = null,
    val inactive: Boolean = false,
    val status: String? = null,
    val metaId: String? = null,
    val uniqueTag: String? = null
)
```

**Key Features:**
- Immutable data class with nullable fields for optional information
- Nested `Id` class for type-safe user identification
- Helper methods for name formatting and display
- Active/inactive status management

**Usage Example:**
```kotlin
val user = User(
    id = User.Id(123),
    registeredOn = Instant.now(),
    fullName = "John Doe",
    preferredEmail = "john.doe@example.com"
)
println(user.getNameEmail()) // "John Doe <john.doe@example.com>"
```

### Project

Represents a source code repository project managed by Gerrit.

```kotlin
data class Project(
    val nameKey: NameKey,
    val description: String? = null,
    val booleanConfigs: Map<BooleanProjectConfig, InheritableBoolean> = ...,
    val submitType: SubmitType = SubmitType.MERGE_IF_NECESSARY,
    val state: ProjectState = ProjectState.ACTIVE,
    val parent: NameKey? = null,
    // ... other fields
)
```

**Key Features:**
- Type-safe project name handling with `NameKey`
- Comprehensive configuration management
- Support for project inheritance hierarchy
- Enum-based configuration for submit types and states

**Configuration Options:**
- `BooleanProjectConfig`: Various boolean settings like requiring Change-Id
- `SubmitType`: How changes are merged (merge, rebase, cherry-pick, etc.)
- `ProjectState`: Active, read-only, or hidden

### Change

Represents a change (code review request) in the Gerrit system.

```kotlin
data class Change(
    val id: Id,
    val serverId: String? = null,
    val key: Key,
    val createdOn: Instant,
    val lastUpdatedOn: Instant,
    val owner: User.Id,
    val dest: BranchNameKey,
    val status: Status,
    val currentPatchSetId: Int,
    val subject: String,
    // ... other fields
)
```

**Key Features:**
- Dual identification: numeric `Id` and globally unique `Key`
- Branch targeting with `BranchNameKey`
- Status tracking (NEW, MERGED, ABANDONED)
- Support for private changes and work-in-progress
- Relationship tracking (reverts, cherry-picks)

**Status Management:**
```kotlin
val change = Change(...)
if (change.isNew) {
    // Handle new change
} else if (change.isMerged) {
    // Handle merged change
}
```

### PatchSet

Represents a single revision of a Change.

```kotlin
data class PatchSet(
    val id: Id,
    val commitId: String,
    val branch: String? = null,
    val uploader: User.Id,
    val realUploader: User.Id = uploader,
    val createdOn: Instant,
    val groups: List<String> = emptyList(),
    // ... other fields
)
```

**Key Features:**
- Composite `Id` linking to parent change
- Git commit SHA tracking
- Uploader vs. real uploader distinction (for impersonation)
- Group membership for related changes
- Conflict information for merge results

**Ref Name Generation:**
```kotlin
val patchSet = PatchSet(...)
val refName = patchSet.refName // "refs/changes/23/1234/1"
```

### Comment

Represents a comment on a change in the Gerrit system.

```kotlin
data class Comment(
    val key: Key,
    val lineNumber: Int = 0, // 0 for file comments
    val author: User.Id,
    val realAuthor: User.Id = author,
    val writtenOn: Instant,
    val side: Short,
    val message: String,
    val range: Range? = null,
    val fixSuggestions: List<FixSuggestion> = emptyList(),
    // ... other fields
)
```

**Key Features:**
- Line-specific or file-level comments
- Character range selection support
- Fix suggestions for automated resolution
- Draft vs. published status
- Threading support with parent UUIDs

**Comment Types:**
- **File Comments**: `lineNumber == 0`
- **Line Comments**: `lineNumber > 0`
- **Range Comments**: Include `Range` for character selection

## Relationships

The domain models form a hierarchical structure:

```
User ──────────────────────────────────┐
  │                                    │
  │ (owner)                            │ (author)
  ▼                                    ▼
Project ──► Change ──► PatchSet ──► Comment
  │           │
  │           │ (1:many)
  │           ▼
  │         Comment
  │
  │ (parent/child)
  ▼
Project
```

## Key Design Decisions

1. **Immutability**: All models are immutable data classes, promoting functional programming patterns
2. **Null Safety**: Optional fields use nullable types rather than wrapper classes
3. **Type Safety**: Strong typing with nested ID classes prevents mixing different entity types
4. **Enum Usage**: Enums for status and configuration values provide compile-time safety
5. **Kotlin Idioms**: Extension properties and methods for computed values

## Usage Patterns

### Creating a New Change
```kotlin
val change = Change(
    id = Change.Id(1234),
    key = Change.Key("I1234567890abcdef"),
    createdOn = Instant.now(),
    lastUpdatedOn = Instant.now(),
    owner = User.Id(42),
    dest = Change.BranchNameKey.create("my-project", "main"),
    status = Change.Status.NEW,
    currentPatchSetId = 1,
    subject = "Fix critical bug in authentication"
)
```

### Querying Change Status
```kotlin
when {
    change.isNew -> handleNewChange(change)
    change.isMerged -> handleMergedChange(change)
    change.isAbandoned -> handleAbandonedChange(change)
}
```

### Working with Comments
```kotlin
val comment = Comment.create(
    uuid = UUID.randomUUID().toString(),
    filename = "src/main/java/Example.java",
    patchSetId = 1,
    author = User.Id(42),
    writtenOn = Instant.now(),
    side = 1,
    message = "Consider using a more descriptive variable name"
)
```

## Migration Notes

These Kotlin models are designed to be compatible with the existing Gerrit Java entities for easier migration. The main differences are:

1. **Constructor Parameters**: Kotlin data classes use primary constructors
2. **Null Safety**: Explicit nullable types instead of null checks
3. **Property Access**: Direct property access instead of getter/setter methods
4. **Immutability**: No setter methods, use `copy()` for modifications

## Future Enhancements

Potential improvements to consider:

1. **Validation**: Add field validation in constructors
2. **Serialization**: Add JSON serialization annotations
3. **Builder Pattern**: Add builder classes for complex object creation
4. **Value Objects**: Extract common value objects (email, SHA, etc.)
5. **Events**: Add domain events for change tracking