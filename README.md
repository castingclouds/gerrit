# Gerrit-Too: Modern Gerrit Code Review System

A modernized version of the Gerrit Code Review system built with Spring Boot 3.x, Kotlin, and PostgreSQL. This project aims to provide a more maintainable, scalable, and developer-friendly alternative to the traditional Gerrit implementation.

## 🎯 Project Overview

Gerrit-Too is a complete modernization of the Gerrit Code Review system, transitioning from the legacy Java/Bazel/Guice stack to modern technologies while maintaining full compatibility with existing Gerrit workflows and data formats.

### Key Objectives

- **Modernize Technology Stack**: Migrate from Java to Kotlin, Bazel to Gradle, and Guice to Spring Boot
- **Improve Developer Experience**: Provide better tooling, faster development cycles, and modern IDE support
- **Enhance Performance**: Leverage Spring Boot Native, PostgreSQL JSONB, and optimized caching
- **Maintain Compatibility**: Ensure seamless migration from existing Gerrit installations
- **Simplify Operations**: Reduce complexity in deployment, monitoring, and maintenance

## 🚀 Technology Stack

### Current (Legacy Gerrit) vs Target (Gerrit-Too)

| Component | Current | Target |
|-----------|---------|--------|
| **Language** | Java | Kotlin |
| **Build System** | Bazel | Gradle with Kotlin DSL |
| **DI Framework** | Google Guice | Spring Boot 3.x |
| **Web Container** | Jetty (embedded) | Spring Boot Native |
| **Database** | NoteDb (Git-based) | PostgreSQL with JSONB |
| **Authentication** | Custom | Spring Security |
| **API Framework** | Custom REST | Spring WebMVC |
| **Git Operations** | JGit | JGit (retained) |
| **Testing** | JUnit | Spring Boot Test + TestContainers |
| **Monitoring** | Custom | Micrometer + Prometheus |

## 🏗️ Architecture Overview

```
gerrit/
├── api/           # REST controllers and API endpoints
├── service/       # Business logic and domain services
├── repository/    # Data access layer with Spring Data JPA
├── security/      # Authentication and authorization
├── git/          # Git operations and JGit integration
├── model/        # Domain models and DTOs
├── config/       # Spring configuration classes
└── common/       # Shared utilities and extensions
```

### Key Components

- **PostgreSQL + JSONB**: Hybrid relational-document storage for optimal performance
- **Spring Security**: Multi-provider authentication (LDAP, OAuth2, OIDC)
- **Spring Data JPA**: Type-safe repository pattern with custom JSONB queries
- **Spring Boot Native**: Fast startup and low memory footprint
- **Gradle**: Modern build system with Kotlin DSL
- **TestContainers**: Integration testing with real PostgreSQL instances

## 🚦 Quick Start

### Prerequisites

- Java 17+
- Docker (for PostgreSQL)
- Git

### Setup Instructions

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd gerrit
   ```

2. **Start PostgreSQL**
   ```bash
   chmod +x start-postgres.sh
   ./start-postgres.sh
   ```

3. **Build and run the application**
   ```bash
   ./gradlew bootRun
   ```

4. **Access the application**
   - Main application: http://localhost:8080
   - Swagger UI: http://localhost:8080/swagger-ui.html
   - Health check: http://localhost:8080/actuator/health

### Development Setup

```bash
# Run tests
./gradlew test

# Run with specific profile
./gradlew bootRun --args='--spring.profiles.active=dev'

# Build native image
./gradlew bootBuildImage

# Check code quality
./gradlew check
```

## 📊 Current Status

### ✅ Completed Features

- [x] **Foundation Setup**
  - [x] Gradle multi-module project with Kotlin DSL
  - [x] Spring Boot 3.x with native compilation support
  - [x] PostgreSQL + JSONB database configuration
  - [x] Core domain models (User, Project, Change, PatchSet, Comment)
  - [x] Spring Data JPA repositories with JSONB support

- [x] **Database Layer**
  - [x] JPA entity models with PostgreSQL JSONB support
  - [x] Repository interfaces with complex JSONB queries
  - [x] Database migration and schema management
  - [x] Integration tests with TestContainers

### 🔄 In Progress

- [ ] **Core Services**
  - [ ] GitRepositoryService with Spring integration
  - [ ] ChangeService with lifecycle management
  - [ ] CommentService with threading support
  - [ ] PatchSetService for version control

- [ ] **Security Implementation**
  - [ ] Multi-provider authentication
  - [ ] JWT token service
  - [ ] Permission evaluation system
  - [ ] Role-based access control

### 📋 Planned Features

- [ ] **API Layer**
  - [ ] REST controllers for all entities
  - [ ] OpenAPI/Swagger documentation
  - [ ] API versioning strategy
  - [ ] Rate limiting and throttling

- [ ] **Advanced Features**
  - [ ] Real-time notifications
  - [ ] Advanced search capabilities
  - [ ] Performance monitoring
  - [ ] Caching strategies

## 🔧 Configuration

### Database Configuration

The application uses PostgreSQL with JSONB for optimal performance:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/gerrit
    username: gerrit
    password: gerrit_password
  jpa:
    hibernate:
      ddl-auto: create  # Auto-creates tables from JPA annotations
```

### Application Profiles

- **dev**: Development profile with debug logging
- **test**: Testing profile with H2 in-memory database
- **prod**: Production profile with optimized settings

## 🎨 Key Features

### Modern Domain Models

```kotlin
@Entity
data class Change(
    @Id val id: Int,
    val projectName: String,
    val ownerId: Int,
    val status: ChangeStatus,

    // JSONB fields for complex nested data
    @Type(type = "io.hypersistence.utils.hibernate.type.json.JsonType")
    @Column(columnDefinition = "jsonb")
    val patchSets: List<PatchSetData> = emptyList(),

    @Type(type = "io.hypersistence.utils.hibernate.type.json.JsonType")
    @Column(columnDefinition = "jsonb")
    val comments: List<CommentData> = emptyList()
)
```

### Powerful JSONB Queries

```kotlin
@Query("""
    SELECT c FROM Change c
    WHERE jsonb_path_exists(c.comments, '$[*] ? (@.author_id == :userId)')
    AND c.status = 'NEW'
""")
fun findChangesWithCommentsByUser(userId: Int): List<Change>
```

### Type-Safe Repository Pattern

```kotlin
interface ChangeRepository : JpaRepository<Change, Int> {
    fun findByProjectNameAndStatus(project: String, status: ChangeStatus): List<Change>
    fun findByOwnerIdAndWorkInProgress(ownerId: Int, wip: Boolean): List<Change>
}
```

## 📚 Documentation

- **[Project Rules & Best Practices](.zed/project-rules.md)** - Development guidelines and patterns
- **[PostgreSQL JSONB Setup](POSTGRES_JSONB_SETUP.md)** - Database configuration and usage
- **[Domain Models](src/main/kotlin/ai/fluxuate/gerrit/model/README.md)** - Core entity documentation
- **[Repository Layer](src/main/kotlin/ai/fluxuate/gerrit/repository/README.md)** - Data access patterns
- **[Modernization PRD](GERRIT_MODERNIZATION_PRD.md)** - Detailed project roadmap

## 🧪 Testing

### Unit Tests
```bash
./gradlew test
```

### Integration Tests with TestContainers
```bash
./gradlew integrationTest
```

### Test Coverage
```bash
./gradlew jacocoTestReport
```

## 🔍 Monitoring & Observability

- **Health Checks**: `/actuator/health`
- **Metrics**: `/actuator/metrics`
- **Prometheus**: `/actuator/prometheus`
- **OpenAPI**: `/api-docs`

## 🐳 Docker Support

### PostgreSQL Container
```bash
./start-postgres.sh
->
PostgreSQL container started!
Connection details:
  Host: localhost
  Port: 5432
  Database: gerrit
  User: gerrit
  Password: gerrit_password

Connection URL: jdbc:postgresql://localhost:5432/gerrit

To connect with psql:
  docker exec -it gerrit-postgres psql -U gerrit -d gerrit

To stop the container:
  docker stop gerrit-postgres
```

### Application Container
```bash
./gradlew bootBuildImage
docker run -p 8080:8080 gerrit:latest
```

## 🤝 Contributing

### Development Workflow

1. **Fork and clone** the repository
2. **Create a feature branch** from `main`
3. **Follow the coding standards** in [project-rules.md](.zed/project-rules.md)
4. **Write tests** for new functionality
5. **Submit a pull request** with detailed description

### Code Style

- **Language**: Kotlin (required) - no Java except for JGit integration
- **Architecture**: Follow Spring Boot best practices
- **Testing**: Comprehensive unit and integration tests
- **Documentation**: Update relevant documentation

### Pull Request Requirements

- All code must be in Kotlin
- Must include tests for new functionality
- Security changes require additional review
- Performance impact must be documented

## 📈 Performance Benefits

- **Faster Startup**: Spring Boot Native compilation
- **Better Queries**: PostgreSQL JSONB with GIN indexes
- **Reduced Memory**: Optimized for cloud deployment
- **Improved Caching**: Spring Cache abstraction
- **Better Monitoring**: Micrometer integration

## 🔒 Security

- **Multi-Provider Auth**: LDAP, OAuth2, OIDC support
- **JWT Tokens**: Stateless API authentication
- **Method-Level Security**: `@PreAuthorize` annotations
- **Permission Caching**: High-performance authorization

## 📄 License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- Original Gerrit project and community
- Spring Boot and Spring Security teams
- PostgreSQL and JSONB capabilities
- JGit for Git operations
- TestContainers for integration testing

---

**Note**: This is a modernization project and is not affiliated with the official Gerrit project. It aims to provide a more maintainable and modern alternative while maintaining compatibility with existing Gerrit workflows.
