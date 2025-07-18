package ai.fluxuate.gerrit.git

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.cache.annotation.Cacheable
import org.springframework.cache.annotation.CacheEvict
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.createDirectories

/**
 * Service for managing Git repositories with Spring integration.
 * Wraps JGit operations with Spring transaction management, caching, and proper resource handling.
 */
@Service
@Transactional
class GitRepositoryService(
    private val gitConfig: GitConfiguration
) {

    /**
     * Creates a new Git repository at the specified path.
     * 
     * @param projectName The name of the project/repository
     * @param bare Whether to create a bare repository (default: true for Gerrit)
     * @return The created Git repository
     */
    @CacheEvict(value = ["repositories"], key = "#projectName")
    fun createRepository(projectName: String, bare: Boolean = true): Repository {
        val repositoryPath = getRepositoryPath(projectName)
        
        if (repositoryPath.exists()) {
            throw GitRepositoryException("Repository '$projectName' already exists")
        }
        
        repositoryPath.createDirectories()
        
        return try {
            val git = Git.init()
                .setDirectory(repositoryPath.toFile())
                .setBare(bare)
                .call()
            
            val repository = git.repository
            
            // Configure repository settings for Gerrit
            configureGerritRepository(repository)
            
            repository
        } catch (e: Exception) {
            throw GitRepositoryException("Failed to create repository '$projectName'", e)
        }
    }

    /**
     * Opens an existing Git repository.
     * 
     * @param projectName The name of the project/repository
     * @return The opened Git repository
     */
    @Cacheable(value = ["repositories"], key = "#projectName")
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
     * Clones a repository from a remote URL.
     * 
     * @param projectName The name for the local repository
     * @param remoteUrl The URL of the remote repository
     * @param bare Whether to create a bare clone (default: true for Gerrit)
     * @return The cloned Git repository
     */
    @CacheEvict(value = ["repositories"], key = "#projectName")
    fun cloneRepository(projectName: String, remoteUrl: String, bare: Boolean = true): Repository {
        val repositoryPath = getRepositoryPath(projectName)
        
        if (repositoryPath.exists()) {
            throw GitRepositoryException("Repository '$projectName' already exists")
        }
        
        repositoryPath.createDirectories()
        
        return try {
            val git = Git.cloneRepository()
                .setURI(remoteUrl)
                .setDirectory(repositoryPath.toFile())
                .setBare(bare)
                .call()
            
            val repository = git.repository
            
            // Configure repository settings for Gerrit
            configureGerritRepository(repository)
            
            repository
        } catch (e: Exception) {
            throw GitRepositoryException("Failed to clone repository '$projectName' from '$remoteUrl'", e)
        }
    }

    /**
     * Deletes a Git repository.
     * 
     * @param projectName The name of the project/repository to delete
     */
    @CacheEvict(value = ["repositories"], key = "#projectName")
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
     * Checks if a repository exists.
     * 
     * @param projectName The name of the project/repository
     * @return true if the repository exists, false otherwise
     */
    fun repositoryExists(projectName: String): Boolean {
        val repositoryPath = getRepositoryPath(projectName)
        if (!repositoryPath.exists()) {
            return false
        }
        
        // For bare repositories, check for refs, objects, and HEAD
        val refsDir = repositoryPath.resolve("refs")
        val objectsDir = repositoryPath.resolve("objects")
        val headFile = repositoryPath.resolve("HEAD")
        
        // For non-bare repositories, check for .git directory
        val gitDir = repositoryPath.resolve(".git")
        
        return (refsDir.exists() && objectsDir.exists() && headFile.exists()) || gitDir.exists()
    }

    /**
     * Lists all available repositories.
     * 
     * @return List of repository names
     */
    fun listRepositories(): List<String> {
        val baseDir = Paths.get(gitConfig.repositoryBasePath)
        
        if (!baseDir.exists()) {
            return emptyList()
        }
        
        return baseDir.toFile()
            .listFiles { file -> 
                if (!file.isDirectory) return@listFiles false
                
                val path = file.toPath()
                // Check for bare repository structure
                val refsDir = path.resolve("refs")
                val objectsDir = path.resolve("objects")
                val headFile = path.resolve("HEAD")
                
                // Check for non-bare repository structure
                val gitDir = path.resolve(".git")
                
                (refsDir.exists() && objectsDir.exists() && headFile.exists()) || gitDir.exists()
            }
            ?.map { it.name }
            ?: emptyList()
    }

    /**
     * Gets a repository by path (used by SSH commands).
     * 
     * @param repoPath The repository path (project name)
     * @return The opened Git repository
     */
    fun getRepository(repoPath: String): Repository {
        // Extract project name from path (remove .git suffix if present)
        val projectName = repoPath.removeSuffix(".git")
        return openRepository(projectName)
    }

    /**
     * Gets the file system path for a repository.
     * 
     * @param projectName The name of the project/repository
     * @return The path to the repository
     */
    private fun getRepositoryPath(projectName: String): Path {
        validateProjectName(projectName)
        return Paths.get(gitConfig.repositoryBasePath, projectName)
    }

    /**
     * Validates that a project name is safe for file system use.
     * 
     * @param projectName The project name to validate
     */
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
     * Configures a repository with Gerrit-specific settings.
     * 
     * @param repository The repository to configure
     */
    private fun configureGerritRepository(repository: Repository) {
        val config = repository.config
        
        // Enable receive pack for push operations
        config.setBoolean("http", null, "receivepack", true)
        
        // Configure for Gerrit's change-based workflow
        config.setString("receive", null, "denyCurrentBranch", "ignore")
        
        // Enable ref advertisement for virtual branches
        config.setBoolean("uploadpack", null, "allowReachableSHA1InWant", true)
        config.setBoolean("uploadpack", null, "allowTipSHA1InWant", true)
        
        config.save()
    }
}

/**
 * Exception thrown when Git repository operations fail.
 */
class GitRepositoryException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
