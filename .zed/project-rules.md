# Gerrit-Too Project Rules & Best Practices

## Technology Stack Requirements

### Core Technologies
- **Language**: Kotlin (required) - no Java except for JGit integration
- **Build System**: Gradle with Kotlin DSL
- **Framework**: Spring Boot 3.x with native compilation support
- **Web**: Spring WebMVC (no WebFlux unless specifically needed)
- **Security**: Spring Security 6.x
- **Database**: Spring Data JPA with NoteDb compatibility
- **Testing**: Spring Boot Test with TestContainers

### Dependencies Management
- Use Gradle version catalogs for dependency management
- Prefer Spring Boot starters over individual libraries
- Keep JGit as the only legacy Java dependency
- Use Kotlin coroutines for async operations where needed
- Use the gerrit folder as a reference point when implementing new features

## Code Structure & Organization

### Package Structure
```
ai.fluxuate.gerrit/
├── api/           # REST controllers only
├── service/       # Business logic services
├── repository/    # Data access layer
├── security/      # Authentication & authorization
├── git/          # Git operations (JGit wrapper)
├── model/        # Domain models and DTOs
├── config/       # Spring configuration classes
└── common/       # Shared utilities and extensions
```

### File Naming Conventions
- Controllers: `*Controller.kt`
- Services: `*Service.kt`
- Repositories: `*Repository.kt`
- Models: Domain entities without suffix
- DTOs: `*Dto.kt` or `*Request.kt`/`*Response.kt`
- Configuration: `*Configuration.kt`

## Kotlin Best Practices

### Code Style
- Use data classes for immutable models
- Prefer `val` over `var` whenever possible
- Use nullable types (`?`) appropriately
- Leverage Kotlin's null safety features
- Use extension functions for utility methods
- Apply `@JvmStatic` only when needed for Java interop

### Spring Integration
- Use constructor injection (avoid `@Autowired` on fields)
- Prefer `@Component`, `@Service`, `@Repository` annotations
- Use `@ConfigurationProperties` for configuration binding
- Apply `@Transactional` at service layer, not repository

## Spring Boot Implementation Patterns

### Controllers
```kotlin
@RestController
@RequestMapping("/api/v1/changes")
@PreAuthorize("hasRole('USER')")
class ChangeController(
    private val changeService: ChangeService
) {
    // Implementation
}
```

### Services
```kotlin
@Service
@Transactional
class ChangeService(
    private val changeRepository: ChangeRepository,
    private val gitService: GitService
) {
    // Implementation
}
```

### Configuration
```kotlin
@Configuration
@EnableJpaRepositories
@EnableCaching
class DatabaseConfiguration {
    // Configuration beans
}
```

## Security Implementation

### Authentication
- Use Spring Security's built-in providers where possible
- Implement custom `AuthenticationProvider` for Gerrit-specific logic
- Use JWT for stateless API authentication
- Support multiple auth providers (LDAP, OAuth2, OIDC)

### Authorization
- Use method-level security with `@PreAuthorize`
- Implement custom `PermissionEvaluator` for complex permissions
- Cache permission evaluations for performance
- Follow principle of least privilege

## Git Operations

### JGit Integration
- Wrap all JGit operations in Spring services
- Use Spring's `@Transactional` for git operations
- Implement proper error handling and resource cleanup
- Cache git operations where appropriate

### Performance Considerations
- Use connection pooling for git operations
- Implement caching for frequently accessed repositories
- Monitor memory usage for large repositories
- Use pagination for large result sets

## API Design Principles

### REST Endpoints
- Follow REST conventions (GET, POST, PUT, DELETE)
- Use proper HTTP status codes
- Implement consistent error responses
- Support content negotiation (JSON preferred)
- Use HATEOAS for discoverability

### Request/Response Format
- Use DTOs for API contracts
- Implement proper validation with `@Valid`
- Follow consistent naming conventions
- Include proper error messages and codes

## Testing Strategy

### Unit Tests
- Use `@SpringBootTest` for integration tests
- Mock external dependencies with `@MockBean`
- Test security configurations with `@WithMockUser`
- Use `@DataJpaTest` for repository tests

### Integration Tests
- Use TestContainers for database testing
- Test complete API workflows
- Verify security configurations
- Test git operations with real repositories

## Performance & Monitoring

### Caching Strategy
- Use Spring Cache abstraction
- Cache expensive operations (git, permissions)
- Implement cache invalidation strategies
- Monitor cache hit rates

### Metrics & Observability
- Use Micrometer for custom metrics
- Implement custom health indicators
- Add distributed tracing with Spring Cloud Sleuth
- Monitor git operation performance

## Migration Considerations

### Data Compatibility
- Maintain NoteDb format compatibility
- Implement data migration utilities
- Test with existing Gerrit data
- Plan for rollback scenarios

### API Compatibility
- Maintain existing API contracts
- Implement versioning strategy
- Document breaking changes
- Provide migration guides

## Code Review Guidelines

### Pull Request Requirements
- All code must be in Kotlin
- Must include tests for new functionality
- Security changes require additional review
- Performance impact must be documented

### Documentation Requirements
- Update API documentation for endpoint changes
- Document configuration changes
- Include migration notes for breaking changes
- Update this rules file for new patterns

## Common Anti-Patterns to Avoid

### Technology Choices
- Don't mix Java and Kotlin in business logic
- Avoid custom frameworks when Spring provides solutions
- Don't bypass Spring Security for authentication
- Avoid direct JGit usage outside service layer

### Code Patterns
- Don't use `@Autowired` on fields
- Avoid nullable types where not needed
- Don't ignore transaction boundaries
- Avoid tight coupling between layers

## Development Workflow

### Setup Requirements
- Use Gradle wrapper for builds
- Configure native compilation for development
- Set up code formatting with ktlint
- Use Git hooks for code quality

### CI/CD Pipeline
- Run tests on all PRs
- Build native image for deployment
- Run security scans
- Performance regression testing

## Decision Log

### Architecture Decisions
- Chosen Kotlin over Java for type safety and modern features
- Selected Gradle over Maven for flexibility and Kotlin DSL
- Spring Boot Native for performance and cloud deployment
- Retained JGit for Git operations (mature, battle-tested)

### Trade-offs
- Native compilation increases build time but improves runtime
- Kotlin adoption requires team training but improves maintainability
- Spring Security complexity justified by enterprise requirements
- NoteDb compatibility constrains some database optimizations

## Future Considerations

### Potential Enhancements
- Consider reactive programming for high-throughput scenarios
- Evaluate GraphQL for complex API queries
- Explore advanced caching strategies
- Consider microservices decomposition for scale

### Technology Evolution
- Monitor Spring Boot updates and migration paths
- Track Kotlin language evolution
- Evaluate GraalVM native image improvements
- Consider cloud-native deployment patterns

---

- Always give the user the command to run and ask them to run the command in the terminal instead of running commands in the prompts.

**Note**: This rules file should be updated as the project evolves and new patterns emerge. All team members should contribute to maintaining these best practices.
