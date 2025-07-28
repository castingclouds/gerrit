package ai.fluxuate.gerrit.util

import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.PersonIdent
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.Date
import java.util.TimeZone

/**
 * Tests for Change-ID generation and validation functionality.
 * These tests ensure that Change-IDs are generated consistently and
 * that the validation logic works correctly.
 */
class ChangeIdGenerationTest {

    @Test
    fun `test Change-Id generation produces valid format`() {
        // Create test data
        val treeId = ObjectId.fromString("4b825dc642cb6eb9a060e54bf8d69288fbee4904") // Empty tree
        val parentIds = listOf(ObjectId.fromString("0000000000000000000000000000000000000000"))
        val author = PersonIdent("Test Author", "author@example.com", Date(1625097600000), TimeZone.getTimeZone("UTC"))
        val committer = PersonIdent("Test Committer", "committer@example.com", Date(1625097600000), TimeZone.getTimeZone("UTC"))
        val commitMessage = "Test commit message\n\nThis is a test commit message for Change-Id generation."

        // Generate Change-Id
        val changeId = ChangeIdUtil.generateChangeId(treeId, parentIds, author, committer, commitMessage)

        // Verify format
        assertTrue(changeId.startsWith("I"), "Change-Id should start with 'I'")
        assertEquals(41, changeId.length, "Change-Id should be 41 characters long (I + 40 hex chars)")
        assertTrue(changeId.substring(1).matches("[0-9a-f]{40}".toRegex()), "Change-Id should contain 40 hex characters after 'I'")
    }

    @Test
    fun `test Change-Id generation is deterministic`() {
        // Create test data
        val treeId = ObjectId.fromString("4b825dc642cb6eb9a060e54bf8d69288fbee4904") // Empty tree
        val parentIds = listOf(ObjectId.fromString("0000000000000000000000000000000000000000"))
        val author = PersonIdent("Test Author", "author@example.com", Date(1625097600000), TimeZone.getTimeZone("UTC"))
        val committer = PersonIdent("Test Committer", "committer@example.com", Date(1625097600000), TimeZone.getTimeZone("UTC"))
        val commitMessage = "Test commit message\n\nThis is a test commit message for Change-Id generation."

        // Generate Change-Id twice with the same inputs
        val changeId1 = ChangeIdUtil.generateChangeId(treeId, parentIds, author, committer, commitMessage)
        val changeId2 = ChangeIdUtil.generateChangeId(treeId, parentIds, author, committer, commitMessage)

        // Verify they are the same
        assertEquals(changeId1, changeId2, "Change-Id generation should be deterministic for the same inputs")
    }

    @Test
    fun `test Change-Id validation`() {
        // Valid Change-Id
        val validChangeId = "I1234567890123456789012345678901234567890"
        assertTrue(ChangeIdUtil.isValidChangeId(validChangeId), "Valid Change-Id should be accepted")

        // Invalid Change-Ids
        val invalidChangeIds = listOf(
            "1234567890123456789012345678901234567890", // Missing 'I' prefix
            "I123456789012345678901234567890123456789", // Too short
            "I12345678901234567890123456789012345678901", // Too long
            "Iabcdefghijklmnopqrstuvwxyz1234567890123", // Invalid characters
            "I 234567890123456789012345678901234567890", // Contains space
            "i1234567890123456789012345678901234567890"  // Lowercase 'i'
        )

        for (invalidChangeId in invalidChangeIds) {
            assertFalse(ChangeIdUtil.isValidChangeId(invalidChangeId), "Invalid Change-Id should be rejected: $invalidChangeId")
        }
    }

    @Test
    fun `test extracting Change-Id from commit message`() {
        // Commit message with Change-Id
        val commitMessage = """
            Test commit message
            
            This is a test commit message with a Change-Id.
            
            Change-Id: I1234567890123456789012345678901234567890
        """.trimIndent()

        val extractedChangeId = ChangeIdUtil.extractChangeId(commitMessage)
        assertEquals("I1234567890123456789012345678901234567890", extractedChangeId, "Should extract the correct Change-Id")

        // Commit message without Change-Id
        val commitMessageWithoutChangeId = """
            Test commit message
            
            This is a test commit message without a Change-Id.
        """.trimIndent()

        val extractedChangeIdFromMessageWithoutChangeId = ChangeIdUtil.extractChangeId(commitMessageWithoutChangeId)
        assertNull(extractedChangeIdFromMessageWithoutChangeId, "Should return null when no Change-Id is present")
    }

    @Test
    fun `test adding Change-Id to commit message`() {
        // Commit message without Change-Id
        val commitMessage = """
            Test commit message
            
            This is a test commit message without a Change-Id.
        """.trimIndent()

        // Create test data
        val treeId = ObjectId.fromString("4b825dc642cb6eb9a060e54bf8d69288fbee4904") // Empty tree
        val parentIds = listOf(ObjectId.fromString("0000000000000000000000000000000000000000"))
        val author = PersonIdent("Test Author", "author@example.com", Date(1625097600000), TimeZone.getTimeZone("UTC"))
        val committer = PersonIdent("Test Committer", "committer@example.com", Date(1625097600000), TimeZone.getTimeZone("UTC"))

        // Add Change-Id to commit message
        val updatedMessage = ChangeIdUtil.addOrUpdateChangeId(commitMessage, treeId, parentIds, author, committer)

        // Verify Change-Id was added
        val extractedChangeId = ChangeIdUtil.extractChangeId(updatedMessage)
        assertNotNull(extractedChangeId, "Change-Id should be added to the commit message")
        assertTrue(ChangeIdUtil.isValidChangeId(extractedChangeId!!), "Added Change-Id should be valid")
    }

    @Test
    fun `test preserving existing Change-Id when updating commit message`() {
        // Commit message with existing Change-Id
        val existingChangeId = "I1234567890123456789012345678901234567890"
        val commitMessage = """
            Test commit message
            
            This is a test commit message with an existing Change-Id.
            
            Change-Id: $existingChangeId
        """.trimIndent()

        // Create test data
        val treeId = ObjectId.fromString("4b825dc642cb6eb9a060e54bf8d69288fbee4904") // Empty tree
        val parentIds = listOf(ObjectId.fromString("0000000000000000000000000000000000000000"))
        val author = PersonIdent("Test Author", "author@example.com", Date(1625097600000), TimeZone.getTimeZone("UTC"))
        val committer = PersonIdent("Test Committer", "committer@example.com", Date(1625097600000), TimeZone.getTimeZone("UTC"))

        // Update commit message
        val updatedMessage = ChangeIdUtil.addOrUpdateChangeId(commitMessage, treeId, parentIds, author, committer)

        // Verify existing Change-Id was preserved
        val extractedChangeId = ChangeIdUtil.extractChangeId(updatedMessage)
        assertEquals(existingChangeId, extractedChangeId, "Existing Change-Id should be preserved")
    }
}