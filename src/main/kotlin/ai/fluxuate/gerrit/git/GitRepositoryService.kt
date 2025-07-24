package ai.fluxuate.gerrit.git

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
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
                .call()
                .repository
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
     * Creates initial branches with an empty commit to establish the branch.
     */
    fun createInitialBranches(projectName: String, branches: List<String> = listOf("main"), initialCommit: Boolean = true) {
        if (!initialCommit) return
        
        val repository = openRepository(projectName)
        try {
            val firstBranch = branches.firstOrNull() ?: "main"
            
            // Create an empty tree
            val treeId = repository.newObjectInserter().use { inserter ->
                val treeFormatter = org.eclipse.jgit.lib.TreeFormatter()
                inserter.insert(treeFormatter)
            }
            
            // Create initial commit
            val commitId = repository.newObjectInserter().use { inserter ->
                val commit = org.eclipse.jgit.lib.CommitBuilder()
                commit.setTreeId(treeId)
                commit.setMessage("Initial commit")
                commit.setAuthor(org.eclipse.jgit.lib.PersonIdent("Gerrit System", "system@gerrit.local"))
                commit.setCommitter(org.eclipse.jgit.lib.PersonIdent("Gerrit System", "system@gerrit.local"))
                inserter.insert(commit)
            }
            
            // Create the main branch pointing to the initial commit
            val refUpdate = repository.updateRef("refs/heads/$firstBranch")
            refUpdate.setNewObjectId(commitId)
            refUpdate.update()
            
            // Set HEAD to point to the main branch
            val headUpdate = repository.updateRef(Constants.HEAD)
            headUpdate.link("refs/heads/$firstBranch")
            headUpdate.update()
            
        } catch (e: Exception) {
            throw GitRepositoryException("Failed to create initial branches for repository '$projectName'", e)
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
