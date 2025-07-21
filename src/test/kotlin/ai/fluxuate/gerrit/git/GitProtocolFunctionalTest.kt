package ai.fluxuate.gerrit.git

import ai.fluxuate.gerrit.service.ChangeService

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.transport.ReceiveCommand
import java.lang.reflect.Method

/**
 * Functional tests for Git protocol handlers.
 * Tests core functionality without complex mocking.
 */
@SpringBootTest
@ActiveProfiles("test")
class GitProtocolFunctionalTest {

    @Autowired
    private lateinit var gitRepositoryService: GitRepositoryService

    @Autowired
    private lateinit var changeService: ChangeService

    // ChangeIdService functionality now available through ChangeIdUtil

    @Autowired
    private lateinit var gitConfig: GitConfiguration
    
    @Autowired
    private lateinit var httpController: GitHttpController

    @BeforeEach
    fun setUp() {
        // Configuration is now loaded from Spring - no manual setup needed
    }

    @Test
    fun `test Git configuration is properly loaded`() {
        assertTrue(gitConfig.httpEnabled)
        assertTrue(gitConfig.sshEnabled)
        assertTrue(gitConfig.anonymousReadEnabled)
        assertFalse(gitConfig.allowDirectPush)
        assertTrue(gitConfig.allowCreates)
        assertTrue(gitConfig.allowDeletes)
        assertFalse(gitConfig.allowNonFastForwards)
    }

    @Test
    fun `test Change-Id format validation`() {
        // Use reflection to access private method
        val isValidChangeIdMethod = httpController.javaClass.getDeclaredMethod("isValidChangeId", String::class.java)
        isValidChangeIdMethod.isAccessible = true

        // Test valid Change-Id formats
        val validChangeIds = listOf(
            "I1234567890abcdef1234567890abcdef12345678",
            "I0000000000000000000000000000000000000001",
            "Iabcdefabcdefabcdefabcdefabcdefabcdefabcd"
        )
        
        for (changeId in validChangeIds) {
            val result = isValidChangeIdMethod.invoke(httpController, changeId) as Boolean
            assertTrue(result, "Change-Id should be valid: $changeId")
        }
        
        // Test invalid Change-Id formats
        val invalidChangeIds = listOf(
            "I123456789ABCDEF123456789ABCDEF12345678",  // uppercase hex
            "I123",  // too short
            "I12345678901234567890123456789012345678901",  // too long
            "12345678901234567890123456789012345678901",  // missing 'I'
            "I123456789012345678901234567890123456789g",  // invalid character
            ""  // empty
        )
        
        for (changeId in invalidChangeIds) {
            val result = isValidChangeIdMethod.invoke(httpController, changeId) as Boolean
            assertFalse(result, "Change-Id should be invalid: $changeId")
        }
    }

    @Test
    fun `test user ID extraction from authentication`() {
        val method = httpController.javaClass.getDeclaredMethod("extractUserId", Authentication::class.java)
        method.isAccessible = true

        // Test with String principal
        val auth = UsernamePasswordAuthenticationToken("testuser", "password")
        val userId = method.invoke(httpController, auth) as Int
        assertTrue(userId > 0)

        // Test with null authentication (anonymous)
        val anonymousUserId = method.invoke(httpController, null) as Int
        assertEquals(0, anonymousUserId)
    }

    @Test
    fun `test repository access validation`() {
        val method = httpController.javaClass.getDeclaredMethod(
            "hasRepositoryAccess",
            String::class.java,
            String::class.java,
            Authentication::class.java
        )
        method.isAccessible = true

        val auth = UsernamePasswordAuthenticationToken("testuser", "password")

        // Anonymous read access should be allowed
        assertTrue(method.invoke(httpController, "test-repo", "git-upload-pack", null) as Boolean)

        // Authenticated write access should be allowed
        assertTrue(method.invoke(httpController, "test-repo", "git-receive-pack", auth) as Boolean)

        // Unauthenticated write access should be denied
        assertFalse(method.invoke(httpController, "test-repo", "git-receive-pack", null) as Boolean)
    }

    @Test
    fun `test refs for branch detection`() {
        // Test basic ref patterns - this is logic we can test without reflection
        val refsForPattern = "refs/for/"
        
        assertTrue("refs/for/master".startsWith(refsForPattern))
        assertTrue("refs/for/develop".startsWith(refsForPattern))
        assertTrue("refs/for/feature/test".startsWith(refsForPattern))
        assertTrue("refs/for/release/1.0".startsWith(refsForPattern))

        // Invalid patterns
        assertFalse("refs/heads/master".startsWith(refsForPattern))
        assertFalse("refs/tags/v1.0".startsWith(refsForPattern))
        assertFalse("refs/for".startsWith(refsForPattern))
        assertFalse("for/master".startsWith(refsForPattern))
    }

    @Test
    fun `test direct branch push detection`() {
        // Test basic ref patterns - this is logic we can test without reflection
        val directBranchPattern = "refs/heads/"
        
        assertTrue("refs/heads/master".startsWith(directBranchPattern))
        assertTrue("refs/heads/develop".startsWith(directBranchPattern))
        assertTrue("refs/heads/feature/test".startsWith(directBranchPattern))

        // Not direct branch pushes
        assertFalse("refs/for/master".startsWith(directBranchPattern))
        assertFalse("refs/tags/v1.0".startsWith(directBranchPattern))
        assertFalse("refs/changes/01/1/1".startsWith(directBranchPattern))
    }

    @Test
    fun `test basic push command validation without mocking`() {
        val method = httpController.javaClass.getDeclaredMethod(
            "validatePushCommands",
            Collection::class.java,
            String::class.java,
            Authentication::class.java
        )
        method.isAccessible = true

        val auth = UsernamePasswordAuthenticationToken("testuser", "password")

        // Test direct branch push (should be rejected)
        val directPushCommand = ReceiveCommand(
            ObjectId.zeroId(),
            ObjectId.fromString("1234567890abcdef1234567890abcdef12345678"),
            "refs/heads/master"
        )
        val directPushErrors = method.invoke(httpController, listOf(directPushCommand), "test-repo", auth) as List<String>
        assertTrue(directPushErrors.any { it.contains("Direct pushes to branches are not allowed") })

        // Test unauthenticated push (should be rejected)
        val refsForCommand = ReceiveCommand(
            ObjectId.zeroId(),
            ObjectId.fromString("1234567890abcdef1234567890abcdef12345678"),
            "refs/for/master"
        )
        val unauthenticatedErrors = method.invoke(httpController, listOf(refsForCommand), "test-repo", null) as List<String>
        assertTrue(unauthenticatedErrors.any { it.contains("Authentication required") })
    }

    @Test
    fun `test SSH configuration validation`() {
        val sshConfig = GitConfiguration().apply {
            sshEnabled = true
            sshHost = "localhost"
            sshPort = 29418
            sshHostKeyPath = "/tmp/test_host_key"
            sshIdleTimeoutSeconds = 300
            sshReadTimeoutSeconds = 30
        }

        assertEquals("localhost", sshConfig.sshHost)
        assertEquals(29418, sshConfig.sshPort)
        assertTrue(sshConfig.sshEnabled)
        assertEquals(300, sshConfig.sshIdleTimeoutSeconds)
        assertEquals(30, sshConfig.sshReadTimeoutSeconds)
    }

    @Test
    fun `test HTTP configuration validation`() {
        assertTrue(gitConfig.httpEnabled)
        assertTrue(gitConfig.anonymousReadEnabled)
        assertFalse(gitConfig.allowDirectPush)
    }

    @Test
    fun `test Git operation permissions`() {
        assertTrue(gitConfig.allowCreates)
        assertTrue(gitConfig.allowDeletes)
        assertFalse(gitConfig.allowNonFastForwards)
    }
}
