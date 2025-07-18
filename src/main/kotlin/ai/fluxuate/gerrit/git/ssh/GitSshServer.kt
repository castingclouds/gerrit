package ai.fluxuate.gerrit.git.ssh

import ai.fluxuate.gerrit.git.GitConfiguration
import org.apache.sshd.common.config.keys.KeyUtils
import org.apache.sshd.common.keyprovider.KeyPairProvider
import org.apache.sshd.common.session.Session
import org.apache.sshd.common.util.security.SecurityUtils
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.auth.password.PasswordAuthenticator
import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.apache.sshd.server.shell.ShellFactory
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Duration
import jakarta.annotation.PreDestroy
import java.security.PublicKey
import org.apache.sshd.common.AttributeRepository

/**
 * SSH server for Git operations.
 * Uses JGit's SSH support with Apache SSHD for Git protocol handling.
 */
@Service
class GitSshServer(
    private val gitConfig: GitConfiguration,
    private val commandFactory: GitSshCommandFactory
) {

    private val logger = LoggerFactory.getLogger(GitSshServer::class.java)
    private var sshServer: SshServer? = null
    
    // Define attribute keys for session data
    companion object {
        private val USERNAME_KEY = AttributeRepository.AttributeKey<String>()
        private val AUTHENTICATED_KEY = AttributeRepository.AttributeKey<Boolean>()
    }

    @EventListener(ApplicationReadyEvent::class)
    fun startServer() {
        if (!gitConfig.sshEnabled) {
            logger.info("SSH server is disabled")
            return
        }

        try {
            logger.info("Starting SSH server on port ${gitConfig.sshPort}")
            
            sshServer = SshServer.setUpDefaultServer().apply {
                port = gitConfig.sshPort
                host = gitConfig.sshHost
                
                // Set up host key
                keyPairProvider = createHostKeyProvider()
                
                // Set up authentication
                passwordAuthenticator = createPasswordAuthenticator()
                publickeyAuthenticator = createPublicKeyAuthenticator()
                
                // Set up command factory for Git operations
                commandFactory = this@GitSshServer.commandFactory
                
                // Configure SSH server properties
                properties.put("idle-timeout", Duration.ofSeconds(gitConfig.sshIdleTimeoutSeconds))
                properties.put("nio2-read-timeout", Duration.ofSeconds(gitConfig.sshReadTimeoutSeconds))
            }
            
            sshServer?.start()
            logger.info("SSH server started successfully on ${gitConfig.sshHost}:${gitConfig.sshPort}")
            
        } catch (e: Exception) {
            logger.error("Failed to start SSH server", e)
            throw RuntimeException("SSH server startup failed", e)
        }
    }

    @PreDestroy
    fun stopServer() {
        sshServer?.let { server ->
            try {
                logger.info("Stopping SSH server")
                server.stop()
                logger.info("SSH server stopped")
            } catch (e: Exception) {
                logger.error("Error stopping SSH server", e)
            }
        }
    }

    /**
     * Create host key provider for SSH server.
     */
    private fun createHostKeyProvider(): KeyPairProvider {
        val hostKeyPath = Paths.get(gitConfig.sshHostKeyPath)
        return SimpleGeneratorHostKeyProvider(hostKeyPath).apply {
            algorithm = KeyUtils.RSA_ALGORITHM
            keySize = 2048
        }
    }

    /**
     * Create password authenticator.
     * For now, this is a placeholder - in production, this would integrate with
     * Gerrit's user authentication system.
     */
    private fun createPasswordAuthenticator(): PasswordAuthenticator {
        return PasswordAuthenticator { username, password, session ->
            logger.debug("Password authentication attempt for user: $username")
            
            // Allow anonymous access if enabled
            if (gitConfig.anonymousReadEnabled && username == "anonymous") {
                logger.info("Anonymous SSH access granted for user: $username")
                session.setAttribute(USERNAME_KEY, username)
                session.setAttribute(AUTHENTICATED_KEY, true)
                return@PasswordAuthenticator true
            }
            
            // Basic password authentication
            // In production, this would integrate with Gerrit's user management system
            if (authenticateUser(username, password)) {
                logger.info("Password authentication successful for user: $username")
                session.setAttribute(USERNAME_KEY, username)
                session.setAttribute(AUTHENTICATED_KEY, true)
                return@PasswordAuthenticator true
            }
            
            logger.warn("Password authentication failed for user: $username")
            false
        }
    }

    /**
     * Create public key authenticator.
     */
    private fun createPublicKeyAuthenticator(): PublickeyAuthenticator {
        return PublickeyAuthenticator { username, key, session ->
            logger.debug("Public key authentication attempt for user: $username, key type: ${key.algorithm}")
            
            // Allow anonymous access if enabled
            if (gitConfig.anonymousReadEnabled && username == "anonymous") {
                logger.info("Anonymous SSH key access granted for user: $username")
                session.setAttribute(USERNAME_KEY, username)
                session.setAttribute(AUTHENTICATED_KEY, true)
                return@PublickeyAuthenticator true
            }
            
            // Public key authentication
            if (authenticateUserWithPublicKey(username, key)) {
                logger.info("Public key authentication successful for user: $username")
                session.setAttribute(USERNAME_KEY, username)
                session.setAttribute(AUTHENTICATED_KEY, true)
                return@PublickeyAuthenticator true
            }
            
            logger.warn("Public key authentication failed for user: $username")
            false
        }
    }
    
    /**
     * Authenticate user with username and password.
     * This is a basic implementation that can be extended with real user management.
     */
    private fun authenticateUser(username: String, password: String): Boolean {
        // Basic validation
        if (username.isBlank() || password.isBlank()) {
            return false
        }
        
        // For development/testing, accept some basic credentials
        // In production, this would query a user database or LDAP
        return when {
            username == "admin" && password == "admin" -> true
            username == "user" && password == "password" -> true
            username == "gerrit" && password == "gerrit" -> true
            else -> {
                logger.debug("No matching credentials found for user: $username")
                false
            }
        }
    }
    
    /**
     * Authenticate user with public key.
     * This is a basic implementation that can be extended with real key management.
     */
    private fun authenticateUserWithPublicKey(username: String, key: PublicKey): Boolean {
        // Basic validation
        if (username.isBlank()) {
            return false
        }
        
        // For development/testing, accept any valid key for known users
        // In production, this would:
        // 1. Look up user's registered public keys from database
        // 2. Compare the provided key with stored keys
        // 3. Verify key signatures and validity
        
        val knownUsers = setOf("admin", "user", "gerrit", "developer", "reviewer")
        
        if (username in knownUsers && isValidPublicKey(key)) {
            logger.debug("Public key accepted for known user: $username")
            return true
        }
        
        logger.debug("Public key authentication failed - unknown user or invalid key: $username")
        return false
    }
    
    /**
     * Basic public key validation.
     */
    private fun isValidPublicKey(key: PublicKey): Boolean {
        return try {
            // Basic checks for key validity
            when (key.algorithm) {
                "RSA" -> {
                    // RSA key should have reasonable key size
                    val keySize = (key as? java.security.interfaces.RSAPublicKey)?.modulus?.bitLength() ?: 0
                    keySize >= 2048 // Minimum 2048 bits for RSA
                }
                "DSA" -> {
                    // DSA key validation
                    val keySize = (key as? java.security.interfaces.DSAPublicKey)?.params?.p?.bitLength() ?: 0
                    keySize >= 1024 // Minimum 1024 bits for DSA
                }
                "EC" -> {
                    // EC key validation - generally acceptable
                    true
                }
                "Ed25519", "Ed448" -> {
                    // EdDSA keys - modern and secure
                    true
                }
                else -> {
                    logger.warn("Unknown key algorithm: ${key.algorithm}")
                    false
                }
            }
        } catch (e: Exception) {
            logger.error("Error validating public key", e)
            false
        }
    }
}
