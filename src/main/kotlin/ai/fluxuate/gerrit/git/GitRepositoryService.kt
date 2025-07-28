package ai.fluxuate.gerrit.git

import ai.fluxuate.gerrit.service.AccountService
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.RefUpdate
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.security.core.context.SecurityContextHolder
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.util.TimeZone
import kotlin.io.path.exists
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import java.util.Date

/**
 * Simple Git repository service using pure JGit calls.
 */
@Service
class GitRepositoryService(
    private val gitConfig: GitConfiguration,
    private val accountService: AccountService
) {
    private val logger = LoggerFactory.getLogger(GitRepositoryService::class.java)

    /**
     * Creates a new bare Git repository with an initial branch.
     */
    fun createRepository(projectName: String, bare: Boolean = true, createEmptyCommit: Boolean = true): Repository {
        val repositoryPath = getRepositoryPath(projectName)
        
        if (repositoryPath.exists()) {
            throw GitRepositoryException("Repository '$projectName' already exists")
        }
        
        repositoryPath.createDirectories()
        
        val gitRepo = try {
            Git.init()
                .setDirectory(repositoryPath.toFile())
                .setBare(bare)
                .setInitialBranch("trunk")
                .call()
        } catch (e: Exception) {
            throw GitRepositoryException("Failed to create repository '$projectName'", e)
        }

        // Install Git hooks
        installGitHooks(repositoryPath)
        
        // Create an initial empty commit to establish the trunk branch only if requested
        if (createEmptyCommit) {
            createInitialCommitForBareRepository(gitRepo)
        }
        
        return gitRepo.repository
    }

    /**
     * Creates an initial empty commit for a bare repository
     */
    private fun createInitialCommitForBareRepository(git: Git) {
        try {
            val repository = git.repository
            
            // Create a new empty tree
            val treeId = repository.newObjectInserter().use { inserter ->
                val treeFormatter = org.eclipse.jgit.lib.TreeFormatter()
                val treeId = inserter.insert(treeFormatter)
                inserter.flush()
                treeId
            }
            
            // Get current authenticated user
            val authentication = SecurityContextHolder.getContext().authentication
            val username = authentication?.name ?: "unknown"
            val userService = accountService.getAccount(username)

            // Create the commit with authenticated user info
            val person = PersonIdent(userService.name, userService.email, Date(), TimeZone.getDefault())
            val commitBuilder = org.eclipse.jgit.lib.CommitBuilder()
            commitBuilder.setTreeId(treeId)
            commitBuilder.setAuthor(person)
            commitBuilder.setCommitter(person)
            commitBuilder.setMessage("Initial commit")
            
            val commitId = repository.newObjectInserter().use { inserter ->
                val commitId = inserter.insert(commitBuilder)
                inserter.flush()
                commitId
            }
            
            // Update the trunk branch to point to the new commit
            val refUpdate = repository.updateRef("refs/heads/trunk")
            refUpdate.setNewObjectId(commitId)
            refUpdate.update()
            
        } catch (e: Exception) {
            logger.error("Failed to create initial commit for bare repository", e)
            throw GitRepositoryException("Failed to create initial commit: ${e.message}", e)
        }
    }

    /**
     * Opens an existing Git repository.
     */
    fun openRepository(projectName: String): Repository {
        val repositoryPath = getRepositoryPath(projectName)
        
        if (!repositoryPath.exists()) {
            throw GitRepositoryException("Repository '$projectName' does not exist")
        }
        
        return try {
            FileRepositoryBuilder()
                .setGitDir(repositoryPath.toFile())
                .readEnvironment()
                .findGitDir()
                .build()
        } catch (e: Exception) {
            throw GitRepositoryException("Failed to open repository '$projectName'", e)
        }
    }

    /**
     * Gets a repository by path (used by Git HTTP operations).
     */
    fun getRepository(repoPath: String): Repository {
        val projectName = repoPath.removeSuffix(".git")
        return openRepository(projectName)
    }

    /**
     * Checks if a repository exists.
     */
    fun repositoryExists(projectName: String): Boolean {
        val repositoryPath = getRepositoryPath(projectName)
        if (!repositoryPath.exists()) {
            return false
        }
        
        val refsDir = repositoryPath.resolve("refs")
        val objectsDir = repositoryPath.resolve("objects")
        val headFile = repositoryPath.resolve("HEAD")
        
        return refsDir.exists() && objectsDir.exists() && headFile.exists()
    }

    /**
     * Deletes a Git repository.
     */
    fun deleteRepository(projectName: String) {
        val repositoryPath = getRepositoryPath(projectName)
        
        if (!repositoryPath.exists()) {
            throw GitRepositoryException("Repository '$projectName' does not exist")
        }
        
        try {
            repositoryPath.toFile().deleteRecursively()
        } catch (e: Exception) {
            throw GitRepositoryException("Failed to delete repository '$projectName'", e)
        }
    }

    /**
     * Gets the current HEAD reference of a repository.
     */
    fun getHead(projectName: String): String {
        val repository = openRepository(projectName)
        return try {
            val head = repository.exactRef(Constants.HEAD)
            if (head?.isSymbolic == true) {
                head.target.name
            } else {
                "refs/heads/trunk"
            }
        } catch (e: Exception) {
            throw GitRepositoryException("Failed to get HEAD for repository '$projectName'", e)
        } finally {
            repository.close()
        }
    }

    /**
     * Sets the HEAD reference of a repository.
     */
    fun setHead(projectName: String, ref: String) {
        val repository = openRepository(projectName)
        try {
            val refUpdate = repository.updateRef(Constants.HEAD)
            refUpdate.link(ref)
            refUpdate.update()
        } catch (e: Exception) {
            throw GitRepositoryException("Failed to set HEAD for repository '$projectName'", e)
        } finally {
            repository.close()
        }
    }

    /**
     * Lists all branches in a repository.
     */
    fun listBranches(projectName: String): List<String> {
        val repository = openRepository(projectName)
        return try {
            Git(repository).branchList().call()
                .map { ref -> Repository.shortenRefName(ref.name) }
        } catch (e: Exception) {
            throw GitRepositoryException("Failed to list branches for repository '$projectName'", e)
        } finally {
            repository.close()
        }
    }

    /**
     * Validates that a reference exists in a repository.
     */
    fun validateRef(projectName: String, ref: String): Boolean {
        val repository = openRepository(projectName)
        return try {
            repository.exactRef(ref) != null
        } catch (e: Exception) {
            false
        } finally {
            repository.close()
        }
    }

    /**
     * Ensures repository has at least one commit and proper refs.
     * For empty repositories, we don't create any initial commit.
     */
    fun ensureRepositoryInitialized(projectName: String) {
        // Do nothing - allow empty repositories
        logger.debug("Repository $projectName is intentionally left empty")
    }

    /**
     * Cleans up repository references - no-op for simple implementation.
     */
    fun cleanupReferences(projectName: String) {
        // For simple implementation, no additional cleanup needed
    }

    /**
     * Lists all repositories in the base path.
     */
    fun listRepositories(): List<String> {
        val basePath = Paths.get(gitConfig.repositoryBasePath)
        if (!basePath.exists()) {
            return emptyList()
        }
        
        return basePath.toFile().listFiles()
            ?.filter { it.isDirectory && repositoryExists(it.name) }
            ?.map { it.name }
            ?: emptyList()
    }

    private fun getRepositoryPath(projectName: String): Path {
        validateProjectName(projectName)
        return Paths.get(gitConfig.repositoryBasePath, projectName)
    }

    private fun validateProjectName(projectName: String) {
        if (projectName.isBlank()) {
            throw GitRepositoryException("Project name cannot be blank")
        }
        
        if (projectName.contains("..") || projectName.contains("/") || projectName.contains("\\")) {
            throw GitRepositoryException("Project name contains invalid characters")
        }
        
        if (projectName.length > 255) {
            throw GitRepositoryException("Project name is too long")
        }
    }

    /**
     * Installs necessary Git hooks for the repository.
     */
    private fun installGitHooks(repositoryPath: Path) {
        try {
            val hooksDir = repositoryPath.resolve("hooks")
            if (!hooksDir.exists()) {
                hooksDir.createDirectories()
            }

            // Install pre-receive hook
            installPreReceiveHook(hooksDir)

            logger.debug("Successfully installed Git hooks for repository at: $repositoryPath")
        } catch (e: Exception) {
            logger.error("Failed to install Git hooks for repository at: $repositoryPath", e)
            throw GitRepositoryException("Failed to install Git hooks: ${e.message}", e)
        }
    }

    /**
     * Installs the pre-receive hook for Change-Id validation.
     */
    private fun installPreReceiveHook(hooksDir: Path) {
        val preReceiveHookPath = hooksDir.resolve("pre-receive")
        val preReceiveHookContent = getPreReceiveHookContent()

        preReceiveHookPath.writeText(preReceiveHookContent)

        // Make the hook executable
        val hookFile = preReceiveHookPath.toFile()
        hookFile.setExecutable(true)

        logger.debug("Pre-receive hook installed at: $preReceiveHookPath")
    }

    /**
     * Returns the content of the pre-receive hook script from classpath resource.
     */
    private fun getPreReceiveHookContent(): String {
        val resourceStream = javaClass.classLoader.getResourceAsStream("git-hooks/pre-receive")
        return resourceStream?.bufferedReader()?.use { it.readText() }
            ?: throw GitRepositoryException("Failed to load pre-receive hook from resources")
    }
}

/**
 * Exception thrown when Git repository operations fail.
 */
class GitRepositoryException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
