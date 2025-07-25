package ai.fluxuate.gerrit.git

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.createDirectories

/**
 * Simple Git repository service using pure JGit calls.
 */
@Service
class GitRepositoryService(
    private val gitConfig: GitConfiguration
) {
    private val logger = LoggerFactory.getLogger(GitRepositoryService::class.java)

    /**
     * Creates a new bare Git repository.
     */
    fun createRepository(projectName: String, bare: Boolean = true): Repository {
        val repositoryPath = getRepositoryPath(projectName)
        
        if (repositoryPath.exists()) {
            throw GitRepositoryException("Repository '$projectName' already exists")
        }
        
        repositoryPath.createDirectories()
        
        return try {
            Git.init()
                .setDirectory(repositoryPath.toFile())
                .setBare(bare)
                .call().repository

        } catch (e: Exception) {
            throw GitRepositoryException("Failed to create repository '$projectName'", e)
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
                "refs/heads/main"
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
}

/**
 * Exception thrown when Git repository operations fail.
 */
class GitRepositoryException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
