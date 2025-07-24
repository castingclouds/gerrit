package ai.fluxuate.gerrit.util

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName

class CommitMessagesUtilTest {

    @Test
    @DisplayName("Should parse commit message with all components")
    fun testParseCommitMessage() {
        val commitMessage = """
            Add new feature for user authentication
            
            This commit adds a new authentication system that supports
            OAuth2 and traditional username/password authentication.
            
            The implementation includes:
            - OAuth2 integration
            - JWT token management
            - User session handling
            
            Bug: AUTH-123
            Change-Id: I1234567890abcdef1234567890abcdef12345678
            Signed-off-by: John Doe <john.doe@example.com>
            Reviewed-by: Jane Smith <jane.smith@example.com>
        """.trimIndent()

        val parsed = CommitMessagesUtil.parseCommitMessage(commitMessage)

        assertEquals("Add new feature for user authentication", parsed.subject)
        assertNotNull(parsed.body)
        assertTrue(parsed.body!!.contains("OAuth2 integration"))
        assertEquals("I1234567890abcdef1234567890abcdef12345678", parsed.changeId)
        assertEquals(1, parsed.bugs.size)
        assertEquals("AUTH-123", parsed.bugs[0])
        assertEquals(1, parsed.signedOffBy.size)
        assertEquals("John Doe <john.doe@example.com>", parsed.signedOffBy[0])
        assertEquals(1, parsed.reviewedBy.size)
        assertEquals("Jane Smith <jane.smith@example.com>", parsed.reviewedBy[0])
    }

    @Test
    @DisplayName("Should validate commit message successfully")
    fun testValidateCommitMessageSuccess() {
        val commitMessage = """
            Add new feature for user authentication
            
            This commit adds a new authentication system.
            
            Change-Id: I1234567890abcdef1234567890abcdef12345678
            Signed-off-by: John Doe <john.doe@example.com>
        """.trimIndent()

        val result = CommitMessagesUtil.validateCommitMessage(commitMessage, requireChangeId = true, requireSignedOffBy = true)

        assertTrue(result.valid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    @DisplayName("Should detect missing Change-Id")
    fun testValidateMissingChangeId() {
        val commitMessage = """
            Add new feature for user authentication
            
            This commit adds a new authentication system.
        """.trimIndent()

        val result = CommitMessagesUtil.validateCommitMessage(commitMessage, requireChangeId = true)

        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("Missing Change-Id") })
    }

    @Test
    @DisplayName("Should add footer to commit message")
    fun testAddFooter() {
        val originalMessage = """
            Add new feature
            
            This is the body.
            
            Change-Id: I1234567890abcdef1234567890abcdef12345678
        """.trimIndent()

        val updated = CommitMessagesUtil.addFooter(originalMessage, "Reviewed-by", "Jane Smith <jane@example.com>")

        assertTrue(updated.contains("Reviewed-by: Jane Smith <jane@example.com>"))
        assertTrue(updated.indexOf("Reviewed-by:") < updated.indexOf("Change-Id:"))
    }

    @Test
    @DisplayName("Should sanitize commit message")
    fun testSanitizeCommitMessage() {
        val messyMessage = "  Add new feature  \n\n  \tThis is the body.  \n  \nChange-Id: I1234567890abcdef1234567890abcdef12345678  "
        
        val sanitized = CommitMessagesUtil.sanitizeCommitMessage(messyMessage)

        assertFalse(sanitized.contains("  "))
        assertTrue(sanitized.endsWith("Change-Id: I1234567890abcdef1234567890abcdef12345678"))
    }

    @Test
    @DisplayName("Should format commit message for display")
    fun testFormatForDisplay() {
        val commitMessage = """
            Add new feature for user authentication with OAuth2 integration
            
            This is a very long commit message that contains detailed information about
            the implementation of the new authentication system. It includes OAuth2
            integration, JWT token management, and user session handling.
            
            Change-Id: I1234567890abcdef1234567890abcdef12345678
        """.trimIndent()

        val formatted = CommitMessagesUtil.formatForDisplay(commitMessage, maxLength = 50)

        assertTrue(formatted.length <= 53) // 50 + "..."
        assertTrue(formatted.endsWith("..."))
    }

    @Test
    @DisplayName("Should generate automated commit message")
    fun testGenerateAutomatedCommitMessage() {
        val message = CommitMessagesUtil.generateAutomatedCommitMessage(
            "Rebase",
            "Add new feature",
            "I1234567890abcdef1234567890abcdef12345678",
            mapOf("Original-Change" to "12345")
        )

        assertTrue(message.startsWith("Rebase: Add new feature"))
        assertTrue(message.contains("This commit was generated automatically by Gerrit"))
        assertTrue(message.contains("Change-Id: I1234567890abcdef1234567890abcdef12345678"))
        assertTrue(message.contains("Original-Change: 12345"))
    }

    @Test
    @DisplayName("Should merge commit messages")
    fun testMergeCommitMessages() {
        val messages = listOf(
            "First commit\n\nBody 1\n\nChange-Id: I1111111111111111111111111111111111111111",
            "Second commit\n\nBody 2\n\nChange-Id: I2222222222222222222222222222222222222222"
        )

        val merged = CommitMessagesUtil.mergeCommitMessages(messages, preserveChangeIds = true)

        assertTrue(merged.startsWith("First commit"))
        assertTrue(merged.contains("Body 1"))
        assertTrue(merged.contains("Body 2"))
        assertTrue(merged.contains("Change-Id: I1111111111111111111111111111111111111111"))
        assertTrue(merged.contains("Change-Id: I2222222222222222222222222222222222222222"))
    }
}
