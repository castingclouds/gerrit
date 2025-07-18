package ai.fluxuate.gerrit.git.ssh

import org.apache.sshd.server.channel.ChannelSession
import org.apache.sshd.server.Environment
import org.apache.sshd.server.command.Command
import org.apache.sshd.server.ExitCallback
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.io.OutputStream
import kotlin.concurrent.thread

/**
 * SSH command handler for unknown/unsupported commands.
 * Returns appropriate error messages to clients.
 */
class UnknownSshCommand(private val commandName: String) : Command {
    
    private val logger = LoggerFactory.getLogger(this::class.java)
    
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var errorStream: OutputStream? = null
    private var exitCallback: ExitCallback? = null

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
                val errorMessage = "fatal: '$commandName' is not a valid Git command\n"
                errorStream?.write(errorMessage.toByteArray())
                errorStream?.flush()
                
                logger.warn("Unknown SSH command attempted: $commandName")
                exitCallback?.onExit(1)
            } catch (e: Exception) {
                logger.error("Error handling unknown SSH command: $commandName", e)
                exitCallback?.onExit(1)
            }
        }
    }

    override fun destroy(session: ChannelSession) {
        // No cleanup needed for unknown commands
    }
}
