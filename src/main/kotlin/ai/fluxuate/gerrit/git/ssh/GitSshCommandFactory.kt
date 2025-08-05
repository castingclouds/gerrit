package ai.fluxuate.gerrit.git.ssh

import ai.fluxuate.gerrit.git.GitConfiguration
import ai.fluxuate.gerrit.git.GitReceivePackService
import ai.fluxuate.gerrit.git.GitRepositoryService
import ai.fluxuate.gerrit.service.ChangeService

import org.apache.sshd.server.channel.ChannelSession
import org.apache.sshd.server.command.Command
import org.apache.sshd.server.command.CommandFactory
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * SSH command factory for Git operations.
 * Routes SSH commands to appropriate handlers.
 */
@Component
class GitSshCommandFactory(
    private val gitConfiguration: GitConfiguration,
    private val repositoryService: GitRepositoryService,
    private val changeService: ChangeService,
    private val gitReceivePackService: GitReceivePackService
) : CommandFactory {
    
    private val logger = LoggerFactory.getLogger(GitSshCommandFactory::class.java)
    
    override fun createCommand(session: ChannelSession, command: String): Command {
        logger.debug("Creating SSH command: $command")
        
        return when {
            command.startsWith("git-receive-pack") -> {
                logger.info("Creating git-receive-pack command")
                GitSshReceivePackCommand(gitConfiguration, repositoryService, changeService, gitReceivePackService)
            }
            command.startsWith("git-upload-pack") -> {
                logger.info("Creating git-upload-pack command")
                GitSshUploadPackCommand(gitConfiguration, repositoryService, changeService)
            }
            else -> {
                logger.warn("Unknown command: $command")
                UnknownSshCommand(command)
            }
        }
    }
    
    /**
     * Extract repository path from Git command.
     * Commands are in format: "git-receive-pack '/path/to/repo.git'"
     */
    private fun extractRepositoryPath(command: String): String {
        val parts = command.split(" ")
        if (parts.size >= 2) {
            // Remove quotes if present
            return parts[1].trim('\'', '"')
        }
        throw IllegalArgumentException("Invalid Git command format: $command")
    }
}
