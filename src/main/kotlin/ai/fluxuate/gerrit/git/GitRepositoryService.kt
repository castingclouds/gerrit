package ai.fluxuate.gerrit.git

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.ObjectId
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
import org.eclipse.jgit.lib.Ref
import org.eclipse.jgit.api.CreateBranchCommand

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

    /**
     * Gets the current HEAD reference of a repository.
     * 
     * @param projectName The name of the project/repository
     * @return The current HEAD reference (e.g., "refs/heads/main")
     */
    fun getHead(projectName: String): String {
        val repository = openRepository(projectName)
        return try {
            val head = repository.exactRef(Constants.HEAD)
            if (head?.isSymbolic == true) {
                head.target.name
            } else {
                // If HEAD is detached, return the default branch
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
     * 
     * @param projectName The name of the project/repository
     * @param ref The reference to set as HEAD (e.g., "refs/heads/main")
     */
    fun setHead(projectName: String, ref: String) {
        val repository = openRepository(projectName)
        try {
            // Validate that the reference exists
            val targetRef = repository.exactRef(ref)
            if (targetRef == null) {
                throw GitRepositoryException("Reference '$ref' does not exist in repository '$projectName'")
            }
            
            // Update HEAD to point to the new reference
            val refUpdate = repository.updateRef(Constants.HEAD)
            refUpdate.link(ref)
            val result = refUpdate.update()
            
            if (result != org.eclipse.jgit.lib.RefUpdate.Result.FORCED && 
                result != org.eclipse.jgit.lib.RefUpdate.Result.NEW && 
                result != org.eclipse.jgit.lib.RefUpdate.Result.NO_CHANGE) {
                throw GitRepositoryException("Failed to update HEAD to '$ref' in repository '$projectName': $result")
            }
        } catch (e: GitRepositoryException) {
            throw e
        } catch (e: Exception) {
            throw GitRepositoryException("Failed to set HEAD for repository '$projectName'", e)
        } finally {
            repository.close()
        }
    }

    /**
     * Lists all branches in a repository.
     * 
     * @param projectName The name of the project/repository
     * @return List of branch names (without refs/heads/ prefix)
     */
    fun listBranches(projectName: String): List<String> {
        val repository = openRepository(projectName)
        return try {
            val git = Git(repository)
            git.branchList().call()
                .map { ref -> Repository.shortenRefName(ref.name) }
        } catch (e: Exception) {
            throw GitRepositoryException("Failed to list branches for repository '$projectName'", e)
        } finally {
            repository.close()
        }
    }

    /**
     * Creates initial branches in a repository.
     * 
     * @param projectName The name of the project/repository
     * @param branches List of branch names to create (default: ["main"])
     * @param initialCommit Whether to create an initial commit (default: true)
     */
    fun createInitialBranches(projectName: String, branches: List<String> = listOf("main"), initialCommit: Boolean = true) {
        val repository = openRepository(projectName)
        try {
            val git = Git(repository)
            
            // Create initial commit if requested and repository is empty
            if (initialCommit && repository.resolve(Constants.HEAD) == null) {
                // Create initial commit on the first branch
                val firstBranch = branches.firstOrNull() ?: "main"
                
                try {
                    // Create and checkout the first branch
                    git.checkout()
                        .setCreateBranch(true)
                        .setName(firstBranch)
                        .call()
                    
                    // Create initial commit
                    git.commit()
                        .setMessage("Initial commit")
                        .setAuthor("Gerrit System", "system@gerrit.local")
                        .setCommitter("Gerrit System", "system@gerrit.local")
                        .setAllowEmpty(true)
                        .call()
                    
                    // Create additional branches from the initial commit
                    branches.drop(1).forEach { branchName ->
                        try {
                            git.branchCreate()
                                .setName(branchName)
                                .setStartPoint(firstBranch)
                                .call()
                        } catch (e: Exception) {
                            // Log warning but continue - branch creation is not critical
                            println("Warning: Failed to create branch '$branchName' in repository '$projectName': ${e.message}")
                        }
                    }
                    
                    // Set HEAD to the first branch
                    setHead(projectName, "refs/heads/$firstBranch")
                } catch (e: Exception) {
                    // If initial commit fails, try creating branches without commits
                    println("Warning: Failed to create initial commit for repository '$projectName': ${e.message}")
                    println("Attempting to create branches without initial commit...")
                    
                    branches.forEach { branchName ->
                        try {
                            // Create empty branch
                            val ref = repository.updateRef("refs/heads/$branchName")
                            ref.setNewObjectId(repository.resolve("refs/heads/main") ?: ObjectId.zeroId())
                            ref.update()
                        } catch (branchException: Exception) {
                            println("Warning: Failed to create branch '$branchName': ${branchException.message}")
                        }
                    }
                    
                    // Set HEAD to the first branch if possible
                    try {
                        setHead(projectName, "refs/heads/${branches.first()}")
                    } catch (headException: Exception) {
                        println("Warning: Failed to set HEAD for repository '$projectName': ${headException.message}")
                    }
                }
            } else {
                // Just create branches without initial commit
                branches.forEach { branchName ->
                    try {
                        git.branchCreate()
                            .setName(branchName)
                            .call()
                    } catch (e: Exception) {
                        // Branch might already exist, continue with others
                        println("Warning: Failed to create branch '$branchName': ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            throw GitRepositoryException("Failed to create initial branches for repository '$projectName'", e)
        } finally {
            repository.close()
        }
    }

    /**
     * Validates that a reference exists in a repository.
     * 
     * @param projectName The name of the project/repository
     * @param ref The reference to validate
     * @return true if the reference exists, false otherwise
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
     * Cleans up repository references and related data.
     * This is called when a project is being deleted.
     * 
     * @param projectName The name of the project/repository
     */
    fun cleanupReferences(projectName: String) {
        try {
            // Clear any cached repository data
            // The @CacheEvict annotation on deleteRepository already handles this
            
            // Additional cleanup could include:
            // - Removing any Gerrit-specific refs (refs/changes/*)
            // - Cleaning up any temporary files
            // - Notifying other services of repository deletion
            
            // For now, the basic file deletion in deleteRepository is sufficient
        } catch (e: Exception) {
            throw GitRepositoryException("Failed to cleanup references for repository '$projectName'", e)
        }
    }
}

/**
 * Exception thrown when Git repository operations fail.
 */
class GitRepositoryException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
