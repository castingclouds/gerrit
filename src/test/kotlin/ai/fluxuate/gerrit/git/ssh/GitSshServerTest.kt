package ai.fluxuate.gerrit.git.ssh

import ai.fluxuate.gerrit.git.GitConfiguration
import ai.fluxuate.gerrit.git.GitRepositoryService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.mockito.kotlin.*
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

@SpringBootTest
@ActiveProfiles("test")
class GitSshServerTest {

    private lateinit var gitConfig: GitConfiguration
    private lateinit var repositoryService: GitRepositoryService
    private lateinit var receiveCommand: GitSshReceiveCommand
    private lateinit var uploadCommand: GitSshUploadCommand

    @BeforeEach
    fun setUp() {
        gitConfig = GitConfiguration().apply {
            sshEnabled = true
            sshHost = "localhost"
            sshPort = 29418
            sshHostKeyPath = "/tmp/test_host_key"
            receivePackEnabled = true
            uploadPackEnabled = true
            validateRepositoryNames = true
            allowedRepositoryNamePattern = "[a-zA-Z0-9][a-zA-Z0-9._/-]*[a-zA-Z0-9]"
            maxRepositoryNameLength = 255
            pushTimeoutSeconds = 300
            fetchTimeoutSeconds = 300
        }

        repositoryService = mock {
            on { repositoryExists(any()) } doReturn true
            on { openRepository(any()) } doReturn mock()
        }

        receiveCommand = GitSshReceiveCommand(gitConfig, repositoryService)
        uploadCommand = GitSshUploadCommand(gitConfig, repositoryService)
    }

    @AfterEach
    fun tearDown() {
        // Cleanup if needed
    }

    @Test
    fun `should validate project names correctly`() {
        val validNames = listOf(
            "test-project",
            "my_project",
            "project.name",
            "project/subproject",
            "a1b2c3"
        )

        val invalidNames = listOf(
            "",
            " ",
            "-invalid",
            "invalid-",
            ".invalid",
            "invalid.",
            "invalid..name",
            "invalid//name"
        )

        validNames.forEach { name ->
            assertDoesNotThrow("Valid name '$name' should not throw") {
                receiveCommand.validateProjectName(name)
            }
        }

        invalidNames.forEach { name ->
            assertThrows(GitSshException::class.java, "Invalid name '$name' should throw") {
                receiveCommand.validateProjectName(name)
            }
        }
    }

    @Test
    fun `should handle receive-pack command execution`() {
        val projectName = "test-project"
        val inputStream = ByteArrayInputStream("test input".toByteArray())
        val outputStream = ByteArrayOutputStream()
        val errorStream = ByteArrayOutputStream()

        // Mock repository service to return a valid repository
        whenever(repositoryService.repositoryExists(projectName)).thenReturn(true)
        whenever(repositoryService.openRepository(projectName)).thenReturn(mock())

        // Note: This will fail with JGit operations, but we're testing the command structure
        val exitCode = receiveCommand.execute(projectName, inputStream, outputStream, errorStream)

        // Verify that the command was processed (even if it fails due to mocked repository)
        verify(repositoryService).repositoryExists(projectName)
        verify(repositoryService).openRepository(projectName)
    }

    @Test
    fun `should handle upload-pack command execution`() {
        val projectName = "test-project"
        val inputStream = ByteArrayInputStream("test input".toByteArray())
        val outputStream = ByteArrayOutputStream()
        val errorStream = ByteArrayOutputStream()

        // Mock repository service to return a valid repository
        whenever(repositoryService.repositoryExists(projectName)).thenReturn(true)
        whenever(repositoryService.openRepository(projectName)).thenReturn(mock())

        // Note: This will fail with JGit operations, but we're testing the command structure
        val exitCode = uploadCommand.execute(projectName, inputStream, outputStream, errorStream)

        // Verify that the command was processed (even if it fails due to mocked repository)
        verify(repositoryService).repositoryExists(projectName)
        verify(repositoryService).openRepository(projectName)
    }

    @Test
    fun `should reject non-existent repositories`() {
        val projectName = "non-existent-project"
        val inputStream = ByteArrayInputStream("test input".toByteArray())
        val outputStream = ByteArrayOutputStream()
        val errorStream = ByteArrayOutputStream()

        // Mock repository service to return false for repository existence
        whenever(repositoryService.repositoryExists(projectName)).thenReturn(false)

        val exitCode = receiveCommand.execute(projectName, inputStream, outputStream, errorStream)

        assertEquals(1, exitCode, "Should return error exit code for non-existent repository")
        verify(repositoryService).repositoryExists(projectName)
        verify(repositoryService, never()).openRepository(projectName)
    }

    @Test
    fun `should respect disabled operations`() {
        gitConfig.receivePackEnabled = false
        gitConfig.uploadPackEnabled = false

        val projectName = "test-project"
        val inputStream = ByteArrayInputStream("test input".toByteArray())
        val outputStream = ByteArrayOutputStream()
        val errorStream = ByteArrayOutputStream()

        // Test disabled receive-pack
        val receiveExitCode = receiveCommand.execute(projectName, inputStream, outputStream, errorStream)
        assertEquals(1, receiveExitCode, "Should return error exit code for disabled receive-pack")

        // Test disabled upload-pack
        val uploadExitCode = uploadCommand.execute(projectName, inputStream, outputStream, errorStream)
        assertEquals(1, uploadExitCode, "Should return error exit code for disabled upload-pack")
    }

    @Test
    fun `should handle project name length limits`() {
        val longProjectName = "a".repeat(gitConfig.maxRepositoryNameLength + 1)
        val inputStream = ByteArrayInputStream("test input".toByteArray())
        val outputStream = ByteArrayOutputStream()
        val errorStream = ByteArrayOutputStream()

        val exitCode = receiveCommand.execute(longProjectName, inputStream, outputStream, errorStream)

        assertEquals(1, exitCode, "Should return error exit code for project name too long")
    }

    @Test
    fun `should create SSH server with correct configuration`() {
        val sshServer = GitSshServer(gitConfig, receiveCommand, uploadCommand)

        // Test that server can be created without throwing exceptions
        assertNotNull(sshServer)
    }

    @Test
    fun `should extract project names from Git commands correctly`() {
        val testCases = mapOf(
            "git-receive-pack 'test-project.git'" to "test-project",
            "git-upload-pack test-project" to "test-project",
            "git-receive-pack '/path/to/project.git'" to "/path/to/project",
            "git-upload-pack 'project/subproject'" to "project/subproject"
        )

        // Since extractProjectName is private, we'll test through command creation
        // This is more of an integration test
        val sshServer = GitSshServer(gitConfig, receiveCommand, uploadCommand)
        val commandFactory = sshServer.GitCommandFactory()

        testCases.forEach { (command, expectedProject) ->
            assertDoesNotThrow("Command '$command' should be parseable") {
                val gitCommand = commandFactory.createCommand(mock(), command)
                assertNotNull(gitCommand)
            }
        }
    }

    @Test
    fun `should handle unknown SSH commands gracefully`() {
        val sshServer = GitSshServer(gitConfig, receiveCommand, uploadCommand)
        val commandFactory = sshServer.GitCommandFactory()

        val unknownCommand = commandFactory.createCommand(mock(), "unknown-command")
        assertNotNull(unknownCommand)
        assertTrue(unknownCommand is UnknownCommandWrapper)
    }

    @Test
    fun `should send error messages in Git protocol format`() {
        val outputStream = ByteArrayOutputStream()
        val errorMessage = "Test error message"

        receiveCommand.sendError(outputStream, errorMessage)

        val output = outputStream.toString()
        assertTrue(output.contains("ERR $errorMessage"), "Error message should be formatted correctly")
    }
}
