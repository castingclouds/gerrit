# Repository Layer Documentation

This document describes the repository layer for the Gerrit code review system, implemented using Spring Data JPA with Kotlin. The repository layer provides data access operations for all core domain entities.

## Overview

The repository layer follows Spring Data JPA conventions and provides:
- **CRUD Operations**: Basic create, read, update, delete operations
- **Query Methods**: Derived query methods from method names
- **Custom Queries**: Complex queries using JPQL and native SQL
- **Pagination**: Built-in support for paginated results
- **Type Safety**: Kotlin-specific features and null safety

## Repository Interfaces

### UserRepository

Manages user account data access operations.

```kotlin
@Repository
interface UserRepository : JpaRepository<User, User.Id>
```

**Key Features:**
- User search by email, name, and display name
- Active/inactive user filtering
- User registration date queries
- Advanced search with multiple criteria
- User suggestion queries for autocomplete

**Common Usage:**
```kotlin
// Find by email
val user = userRepository.findByPreferredEmail("john.doe@example.com")

// Search users
val users = userRepository.searchUsers("john", null, pageable)

// Find active users
val activeUsers = userRepository.findByInactiveFalse(pageable)
```

### ProjectRepository

Handles project management data access.

```kotlin
@Repository
interface ProjectRepository : JpaRepository<Project, Project.NameKey>
```

**Key Features:**
- Project hierarchy navigation (parent/child relationships)
- Project state filtering (active, read-only, hidden)
- Submit type queries
- Project search and suggestions
- Recursive hierarchy queries

**Common Usage:**
```kotlin
// Find by name
val project = projectRepository.findByNameKeyName("my-project")

// Find child projects
val children = projectRepository.findByParent(parentProject.nameKey)

// Search projects
val projects = projectRepository.searchProjects("android", ProjectState.ACTIVE, null, pageable)
```

### ChangeRepository

Manages change (code review) data access operations.

```kotlin
@Repository
interface ChangeRepository : JpaRepository<Change, Change.Id>
```

**Key Features:**
- Change lifecycle management (NEW, MERGED, ABANDONED)
- Owner and project-based queries
- Status and topic-based filtering
- Dashboard queries for review workflows
- Advanced search with multiple criteria

**Common Usage:**
```kotlin
// Find by change key
val change = changeRepository.findByKey(changeKey)

// Find user's changes
val myChanges = changeRepository.findByOwner(userId, pageable)

// Find changes needing attention
val needsAttention = changeRepository.findChangesNeedingAttention(userId, pageable)
```

### PatchSetRepository

Handles patch set version control data access.

```kotlin
@Repository
interface PatchSetRepository : JpaRepository<PatchSet, PatchSet.Id>
```

**Key Features:**
- Patch set versioning within changes
- Git commit SHA tracking
- Uploader and creation date queries
- Group-based relationship queries
- Conflict and edit patch set handling

**Common Usage:**
```kotlin
// Find all patch sets for a change
val patchSets = patchSetRepository.findByIdChangeIdOrderByIdPatchSetNumberAsc(changeId)

// Find latest patch set
val latest = patchSetRepository.findTopByIdChangeIdOrderByIdPatchSetNumberDesc(changeId)

// Find related patch sets
val related = patchSetRepository.findRelatedPatchSets(patchSetId, pageable)
```

### CommentRepository

Manages comment data access for code reviews.

```kotlin
@Repository
interface CommentRepository : JpaRepository<Comment, Comment.Key>
```

**Key Features:**
- Inline and file-level comments
- Comment threading (parent/child relationships)
- Line-specific and range-based comments
- Fix suggestion queries
- Comment search across multiple criteria

**Common Usage:**
```kotlin
// Find comments for a patch set
val comments = commentRepository.findByKeyPatchSetId(patchSetId)

// Find comment thread
val thread = commentRepository.findCommentThread(rootUuid)

// Find comments with fix suggestions
val withFixes = commentRepository.findCommentsWithFixSuggestions(pageable)
```

## Spring Data JPA Features

### Derived Query Methods

Spring Data JPA automatically implements queries based on method names:

```kotlin
// Automatically implemented by Spring Data JPA
fun findByPreferredEmail(email: String): User?
fun findByStatusAndOwner(status: Change.Status, owner: User.Id): List<Change>
fun findByCreatedOnAfter(date: Instant): List<PatchSet>
```

### Custom Queries

Complex queries using `@Query` annotation:

```kotlin
@Query("""
    SELECT u FROM User u
    WHERE (:searchText IS NULL OR
           LOWER(u.fullName) LIKE LOWER(CONCAT('%', :searchText, '%')))
    AND (:isActive IS NULL OR u.inactive = :isActive)
    ORDER BY u.fullName ASC
""")
fun searchUsers(
    @Param("searchText") searchText: String?,
    @Param("isActive") isActive: Boolean?,
    pageable: Pageable
): Page<User>
```

### Pagination Support

Built-in pagination with `Pageable` parameter:

```kotlin
fun findByStatus(status: Change.Status, pageable: Pageable): Page<Change>

// Usage
val pageable = PageRequest.of(0, 20, Sort.by("lastUpdatedOn").descending())
val changes = changeRepository.findByStatus(Change.Status.NEW, pageable)
```

## Query Patterns

### 1. Basic Queries

```kotlin
// Single result
fun findByEmail(email: String): User?

// Multiple results
fun findByStatus(status: Change.Status): List<Change>

// Existence check
fun existsByEmail(email: String): Boolean

// Count
fun countByStatus(status: Change.Status): Long
```

### 2. Conditional Queries

```kotlin
// Optional parameters
fun findByNameAndStatus(
    name: String?,
    status: Change.Status?
): List<Change>

// Null handling
fun findByParentIsNull(): List<Project>
fun findByDescriptionIsNotNull(): List<Project>
```

### 3. Comparison Queries

```kotlin
// Date comparisons
fun findByCreatedOnAfter(date: Instant): List<Change>
fun findByCreatedOnBetween(start: Instant, end: Instant): List<Change>

// Numeric comparisons
fun findByLineNumberGreaterThan(line: Int): List<Comment>
```

### 4. Text Search

```kotlin
// Case-insensitive search
fun findBySubjectContainingIgnoreCase(text: String): List<Change>

// Like queries
fun findByEmailStartingWith(prefix: String): List<User>
```

### 5. Complex Joins

```kotlin
@Query("""
    SELECT c FROM Comment c
    WHERE c.key.patchSetId IN (
        SELECT ps.id.patchSetNumber FROM PatchSet ps
        WHERE ps.id.changeId = :changeId
    )
""")
fun findCommentsForChange(@Param("changeId") changeId: Change.Id): List<Comment>
```

## Best Practices

### 1. Repository Design

- **Single Responsibility**: Each repository manages one entity type
- **Consistent Naming**: Use clear, descriptive method names
- **Return Types**: Use `Optional` for single results, `List` for multiple
- **Null Safety**: Leverage Kotlin's null safety features

### 2. Query Optimization

- **Pagination**: Always use pagination for large result sets
- **Indexing**: Ensure database indexes for frequently queried fields
- **Lazy Loading**: Use `@EntityGraph` for controlled eager loading
- **Batch Operations**: Use batch queries for multiple operations

### 3. Performance Considerations

```kotlin
// Good: Paginated query
fun findByStatus(status: Change.Status, pageable: Pageable): Page<Change>

// Bad: Unpaginated query that could return millions of results
fun findByStatus(status: Change.Status): List<Change>
```

### 4. Error Handling

```kotlin
// Repository method
fun findByEmail(email: String): User?

// Service layer usage
fun getUserByEmail(email: String): User {
    return userRepository.findByEmail(email)
        ?: throw UserNotFoundException("User not found: $email")
}
```

## Integration Examples

### Service Layer Integration

```kotlin
@Service
class ChangeService(
    private val changeRepository: ChangeRepository,
    private val patchSetRepository: PatchSetRepository
) {
    
    fun getChangeWithLatestPatchSet(changeId: Change.Id): ChangeWithPatchSet {
        val change = changeRepository.findById(changeId)
            .orElseThrow { ChangeNotFoundException("Change not found: $changeId") }
        
        val latestPatchSet = patchSetRepository
            .findTopByIdChangeIdOrderByIdPatchSetNumberDesc(changeId)
            ?: throw PatchSetNotFoundException("No patch sets found for change: $changeId")
        
        return ChangeWithPatchSet(change, latestPatchSet)
    }
}
```

### Transaction Management

```kotlin
@Service
@Transactional
class CommentService(
    private val commentRepository: CommentRepository,
    private val changeRepository: ChangeRepository
) {
    
    fun addComment(commentRequest: CommentRequest): Comment {
        val comment = Comment.create(...)
        
        // Save comment
        val savedComment = commentRepository.save(comment)
        
        // Update change last modified time
        val change = changeRepository.findById(commentRequest.changeId)
            .orElseThrow { ChangeNotFoundException("Change not found") }
        
        changeRepository.save(change.copy(lastUpdatedOn = Instant.now()))
        
        return savedComment
    }
}
```

## Testing

### Repository Testing

```kotlin
@DataJpaTest
class UserRepositoryTest {
    
    @Autowired
    private lateinit var userRepository: UserRepository
    
    @Test
    fun `should find user by email`() {
        // Given
        val user = User(...)
        userRepository.save(user)
        
        // When
        val found = userRepository.findByPreferredEmail("test@example.com")
        
        // Then
        assertThat(found).isNotNull
        assertThat(found!!.fullName).isEqualTo("Test User")
    }
}
```

### Custom Query Testing

```kotlin
@Test
fun `should search users with multiple criteria`() {
    // Given
    val users = listOf(
        User(fullName = "John Doe", email = "john@example.com"),
        User(fullName = "Jane Smith", email = "jane@example.com")
    )
    userRepository.saveAll(users)
    
    // When
    val result = userRepository.searchUsers("john", null, PageRequest.of(0, 10))
    
    // Then
    assertThat(result.content).hasSize(1)
    assertThat(result.content[0].fullName).isEqualTo("John Doe")
}
```

## Migration Notes

When migrating from the existing Gerrit codebase:

1. **Query Translation**: Convert existing SQL queries to JPQL
2. **Batch Operations**: Identify and optimize batch operations
3. **Caching**: Implement appropriate caching strategies
4. **Performance**: Monitor and optimize query performance
5. **Compatibility**: Ensure NoteDb format compatibility

## Future Enhancements

1. **Reactive Repositories**: Consider R2DBC for reactive streams
2. **Custom Repository Implementations**: Add complex business logic
3. **Audit Logging**: Implement automatic audit trail
4. **Query Optimization**: Add query hints and optimization
5. **Metrics**: Add repository-level metrics and monitoring