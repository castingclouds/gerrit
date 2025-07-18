package ai.fluxuate.gerrit.git

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import java.nio.file.Path
import java.nio.file.Files
import kotlin.io.path.exists

/**
 * Comprehensive tests for GitRepositoryService.
 * Tests repository creation, cloning, management, and error handling.
 */
@SpringBootTest
@TestPropertySource(properties = [
    "gerrit.git.repository-base-path=\${java.io.tmpdir}/gerrit-test-repos",
    "gerrit.git.max-cached-repositories=10",
    "gerrit.git.repository-cache-ttl-seconds=60"
])
class GitRepositoryServiceTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var gitConfig: GitConfiguration
    private lateinit var gitRepositoryService: GitRepositoryService

    @BeforeEach
    fun setUp() {
        gitConfig = GitConfiguration().apply {
            repositoryBasePath = tempDir.toString()
            maxCachedRepositories = 10
            repositoryCacheTtlSeconds = 60
            httpEnabled = true
            httpPort = 8080
            anonymousReadEnabled = true
            sshHostKeyPath = "./test-ssh-key"
            sshPort = 29418
            sshEnabled = false
            maxConcurrentOperations = 10
            operationTimeoutSeconds = 60
            lfsEnabled = false
            lfsStoragePath = "./test-lfs"
            virtualBranchesEnabled = true
            maxPatchSetsPerChange = 50
            changeIdValidationEnabled = true
            changeIdRequired = true
            autoGenerateChangeId = false
            changeIdGenerationEnabled = true
            refAdvertisementEnabled = true
            pushHookEnabled = true
            gcEnabled = false
            gcIntervalHours = 24
            packRefsEnabled = false
            packRefsIntervalHours = 168
        }
        gitRepositoryService = GitRepositoryService(gitConfig)
    }

    @AfterEach
    fun tearDown() {
        // Cleanup is handled by @TempDir
    }

    @Test
    fun `should create new bare repository successfully`() {
        // Given
        val projectName = "test-project"

        // When
        val repository = gitRepositoryService.createRepository(projectName, bare = true)

        // Then
        assertNotNull(repository)
        assertTrue(repository.isBare)
        assertTrue(gitRepositoryService.repositoryExists(projectName))
        
        // Verify Gerrit-specific configuration
        val config = repository.config
        assertTrue(config.getBoolean("http", "receivepack", false))
        assertEquals("ignore", config.getString("receive", null, "denyCurrentBranch"))
        assertTrue(config.getBoolean("uploadpack", "allowReachableSHA1InWant", false))
        assertTrue(config.getBoolean("uploadpack", "allowTipSHA1InWant", false))
        
        repository.close()
    }

    @Test
    fun `should create new non-bare repository successfully`() {
        // Given
        val projectName = "test-project-non-bare"

        // When
        val repository = gitRepositoryService.createRepository(projectName, bare = false)

        // Then
        assertNotNull(repository)
        assertFalse(repository.isBare)
        assertTrue(gitRepositoryService.repositoryExists(projectName))
        
        repository.close()
    }

    @Test
    fun `should throw exception when creating repository that already exists`() {
        // Given
        val projectName = "existing-project"
        gitRepositoryService.createRepository(projectName).close()

        // When & Then
        val exception = assertThrows(GitRepositoryException::class.java) {
            gitRepositoryService.createRepository(projectName)
        }
        
        assertTrue(exception.message!!.contains("already exists"))
    }

    @Test
    fun `should open existing repository successfully`() {
        // Given
        val projectName = "existing-project"
        val originalRepo = gitRepositoryService.createRepository(projectName)
        originalRepo.close()

        // When
        val repository = gitRepositoryService.openRepository(projectName)

        // Then
        assertNotNull(repository)
        assertTrue(repository.isBare)
        
        repository.close()
    }

    @Test
    fun `should throw exception when opening non-existent repository`() {
        // Given
        val projectName = "non-existent-project"

        // When & Then
        val exception = assertThrows(GitRepositoryException::class.java) {
            gitRepositoryService.openRepository(projectName)
        }
        
        assertTrue(exception.message!!.contains("does not exist"))
    }

    @Test
    fun `should delete repository successfully`() {
        // Given
        val projectName = "project-to-delete"
        gitRepositoryService.createRepository(projectName).close()
        assertTrue(gitRepositoryService.repositoryExists(projectName))

        // When
        gitRepositoryService.deleteRepository(projectName)

        // Then
        assertFalse(gitRepositoryService.repositoryExists(projectName))
    }

    @Test
    fun `should throw exception when deleting non-existent repository`() {
        // Given
        val projectName = "non-existent-project"

        // When & Then
        val exception = assertThrows(GitRepositoryException::class.java) {
            gitRepositoryService.deleteRepository(projectName)
        }
        
        assertTrue(exception.message!!.contains("does not exist"))
    }

    @Test
    fun `should list repositories correctly`() {
        // Given
        val project1 = "project-1"
        val project2 = "project-2"
        val project3 = "project-3"
        
        gitRepositoryService.createRepository(project1).close()
        gitRepositoryService.createRepository(project2).close()
        gitRepositoryService.createRepository(project3).close()

        // When
        val repositories = gitRepositoryService.listRepositories()

        // Then
        assertEquals(3, repositories.size)
        assertTrue(repositories.contains(project1))
        assertTrue(repositories.contains(project2))
        assertTrue(repositories.contains(project3))
    }

    @Test
    fun `should return empty list when no repositories exist`() {
        // When
        val repositories = gitRepositoryService.listRepositories()

        // Then
        assertTrue(repositories.isEmpty())
    }

    @Test
    fun `should validate project names correctly`() {
        // Test invalid project names
        val invalidNames = listOf(
            "",           // blank
            "   ",        // whitespace only
            "project/../hack", // path traversal
            "project/subdir",  // contains slash
            "project\\subdir", // contains backslash
            "a".repeat(256)    // too long
        )

        invalidNames.forEach { invalidName ->
            val exception = assertThrows(GitRepositoryException::class.java) {
                gitRepositoryService.createRepository(invalidName)
            }
            assertTrue(exception.message!!.contains("invalid") || 
                      exception.message!!.contains("blank") || 
                      exception.message!!.contains("long"))
        }
    }

    @Test
    fun `should handle valid project names correctly`() {
        // Test valid project names
        val validNames = listOf(
            "simple-project",
            "project_with_underscores",
            "project.with.dots",
            "project123",
            "UPPERCASE-PROJECT",
            "mixed-Case_Project.123"
        )

        validNames.forEach { validName ->
            assertDoesNotThrow {
                val repo = gitRepositoryService.createRepository(validName)
                repo.close()
                assertTrue(gitRepositoryService.repositoryExists(validName))
            }
        }
    }

    @Test
    fun `should check repository existence correctly`() {
        // Given
        val existingProject = "existing-project"
        val nonExistentProject = "non-existent-project"
        
        gitRepositoryService.createRepository(existingProject).close()

        // When & Then
        assertTrue(gitRepositoryService.repositoryExists(existingProject))
        assertFalse(gitRepositoryService.repositoryExists(nonExistentProject))
    }
}
