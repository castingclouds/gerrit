package ai.fluxuate.gerrit.repository

import ai.fluxuate.gerrit.model.ChangeEntity
import ai.fluxuate.gerrit.model.ChangeStatus
import ai.fluxuate.gerrit.model.ProjectEntity
import ai.fluxuate.gerrit.model.ProjectState
import ai.fluxuate.gerrit.model.UserEntity
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Integration test for clean JPA entities without embedded type conflicts.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CleanEntityIntegrationTest {

    @Autowired
    private lateinit var userRepository: UserEntityRepository

    @Autowired
    private lateinit var projectRepository: ProjectEntityRepository

    @Autowired
    private lateinit var changeRepository: ChangeEntityRepository

    @Test
    fun `should create and save user entity without conflicts`() {
        // Given
        val user = UserEntity(
            username = "testuser",
            fullName = "Test User",
            preferredEmail = "test@example.com",
            preferences = mapOf(
                "timezone" to "America/New_York",
                "dateFormat" to "MMM dd, yyyy"
            )
        )

        // When
        val savedUser = userRepository.save(user)

        // Then
        assert(savedUser.id > 0)
        assert(savedUser.username == "testuser")
        assert(savedUser.preferences["timezone"] == "America/New_York")
    }

    @Test
    fun `should create and save project entity without conflicts`() {
        // Given
        val project = ProjectEntity(
            name = "test-project",
            description = "Test project",
            state = ProjectState.ACTIVE,
            config = mapOf(
                "submit_type" to "MERGE_IF_NECESSARY",
                "require_change_id" to true
            )
        )

        // When
        val savedProject = projectRepository.save(project)

        // Then
        assert(savedProject.id > 0)
        assert(savedProject.name == "test-project")
        assert(savedProject.config["submit_type"] == "MERGE_IF_NECESSARY")
    }

    @Test
    fun `should create and save change entity with JSONB data without conflicts`() {
        // Given
        val change = ChangeEntity(
            changeKey = "test-change-001",
            ownerId = 1,
            projectName = "test-project",
            destBranch = "main",
            subject = "Test change",
            status = ChangeStatus.NEW,
            patchSets = listOf(
                mapOf(
                    "id" to 1,
                    "commitId" to "abc123def456",
                    "uploader_id" to 1,
                    "created_on" to Instant.now().toString()
                )
            ),
            comments = listOf(
                mapOf(
                    "id" to "comment-1",
                    "author_id" to 1,
                    "message" to "LGTM",
                    "line" to 42,
                    "file" to "src/main.kt"
                )
            ),
            approvals = listOf(
                mapOf(
                    "label" to "Code-Review",
                    "value" to 2,
                    "user_id" to 1,
                    "granted" to Instant.now().toString()
                )
            )
        )

        // When
        val savedChange = changeRepository.save(change)

        // Then
        assert(savedChange.id > 0)
        assert(savedChange.changeKey == "test-change-001")
        assert(savedChange.patchSets.isNotEmpty())
        assert(savedChange.comments.isNotEmpty())
        assert(savedChange.approvals.isNotEmpty())
    }

    @Test
    fun `should query changes with JSONB operations`() {
        // Given - create a change with approvals
        val change = ChangeEntity(
            changeKey = "test-change-002",
            ownerId = 1,
            projectName = "test-project",
            destBranch = "main",
            subject = "Test change with approval",
            status = ChangeStatus.NEW,
            approvals = listOf(
                mapOf(
                    "label" to "Code-Review",
                    "value" to 2,
                    "user_id" to 1
                )
            )
        )
        changeRepository.save(change)

        // When - query for changes with Code-Review +2
        val changesWithApproval = changeRepository.findChangesWithApproval("Code-Review", 2, org.springframework.data.domain.Pageable.unpaged())

        // Then
        assert(changesWithApproval.content.isNotEmpty())
        assert(changesWithApproval.content.any { it.changeKey == "test-change-002" })
    }
}
