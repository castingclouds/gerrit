package ai.fluxuate.gerrit.git.ssh

import ai.fluxuate.gerrit.git.GitConfiguration
import ai.fluxuate.gerrit.git.GitRepositoryService
import org.apache.sshd.server.channel.ChannelSession
import org.apache.sshd.server.command.Command
import org.apache.sshd.server.ExitCallback
import org.apache.sshd.server.Environment
import org.eclipse.jgit.lib.Repository
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.io.OutputStream
import kotlin.concurrent.thread

/**
 * Abstract base class for Git SSH commands.
 * Provides common functionality for git-receive-pack and git-upload-pack operations.
 */
abstract class AbstractGitSshCommand(
    protected val gitConfiguration: GitConfiguration,
    protected val repositoryService: GitRepositoryService
) : Command {
    
    protected val logger = LoggerFactory.getLogger(this::class.java)
    
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var errorStream: OutputStream? = null
    private var exitCallback: ExitCallback? = null
    
    protected var repository: Repository? = null
    protected var projectName: String? = null

    override fun setInputStream(inputStream: InputStream) {
        this.inputStream = inputStream
    }

    override fun setOutputStream(outputStream: OutputStream) {
        this.outputStream = outputStream
    }

    override fun setErrorStream(errorStream: OutputStream) {
        this.errorStream = errorStream
    }

    override fun setExitCallback(callback: ExitCallback) {
        this.exitCallback = callback
    }

    override fun start(session: ChannelSession, env: Environment) {
        thread {
            try {
                // Extract project name from SSH_ORIGINAL_COMMAND
                projectName = extractProjectName(env)
                
                if (projectName == null) {
                    sendError("Unable to determine project name from command")
                    exitCallback?.onExit(1)
                    return@thread
                }
                
                // Validate project name - simple validation for now
                if (projectName!!.isBlank() || projectName!!.contains("..")) {
                    sendError("Invalid repository name: $projectName")
                    exitCallback?.onExit(1)
                    return@thread
                }
                
                // Open repository
                repository = repositoryService.openRepository(projectName!!)
                
                // Execute the Git command
                runImpl()
                
                exitCallback?.onExit(0)
                
            } catch (e: Exception) {
                logger.error("Error executing Git SSH command", e)
                sendError("Internal server error: ${e.message}")
                exitCallback?.onExit(1)
            } finally {
                // Clean up repository
                repository?.close()
            }
        }
    }

    override fun destroy(session: ChannelSession) {
        repository?.close()
    }
    
    /**
     * Abstract method for subclasses to implement their specific Git command logic.
     */
    protected abstract fun runImpl()
    
    /**
     * Extract project name from SSH command environment.
     */
    private fun extractProjectName(env: Environment): String? {
        val originalCommand = env.env["SSH_ORIGINAL_COMMAND"] ?: return null
        
        // Match git-receive-pack or git-upload-pack commands
        val regex = """git-(receive|upload)-pack\s+'?([^']+?)'?(?:\.git)?$""".toRegex()
        val match = regex.find(originalCommand) ?: return null
        
        return match.groupValues[2]
    }
    
    /**
     * Send error message to client.
     */
    private fun sendError(message: String) {
        try {
            errorStream?.write("fatal: $message\n".toByteArray())
            errorStream?.flush()
        } catch (e: Exception) {
            logger.error("Failed to send error message", e)
        }
    }
    
    // Utility methods for subclasses
    protected fun getInputStream(): InputStream? = inputStream
    protected fun getOutputStream(): OutputStream? = outputStream
    protected fun getErrorStream(): OutputStream? = errorStream
}
