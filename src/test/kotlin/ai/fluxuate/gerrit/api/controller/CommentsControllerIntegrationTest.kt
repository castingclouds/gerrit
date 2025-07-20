package ai.fluxuate.gerrit.api.controller

import ai.fluxuate.gerrit.api.dto.*
import ai.fluxuate.gerrit.model.ChangeEntity
import ai.fluxuate.gerrit.model.ChangeStatus
import ai.fluxuate.gerrit.repository.ChangeEntityRepository
import ai.fluxuate.gerrit.config.TestSecurityConfig
import ai.fluxuate.gerrit.service.AccountService
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import java.time.Instant

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestSecurityConfig::class)
class CommentsControllerIntegrationTest {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var changeRepository: ChangeEntityRepository

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var accountService: AccountService

    private lateinit var baseUrl: String
    private lateinit var testChange: ChangeEntity
    private var testAccountId: Long = 0L

    @BeforeEach
    fun setUp() {
        baseUrl = "http://localhost:$port/a"
        
        // Clean up any existing test data
        changeRepository.deleteAll()
        
        // Create a test user account for the authenticated user
        val testUser = try {
            accountService.createAccount("testuser", AccountInput(
                name = "Test User",
                email = "testuser@example.com"
            ))
        } catch (e: Exception) {
            // User might already exist, get the existing one
            accountService.getAccount("testuser")
        }
        testAccountId = testUser._account_id
        
        // Create a test change with all required parameters
        testChange = changeRepository.save(
            ChangeEntity(
                changeKey = "I1234567890123456789012345678901234567890",
                ownerId = testAccountId.toInt(), // Use real account ID
                projectName = "test-project",
                destBranch = "main",
                subject = "Test change for comments",
                status = ChangeStatus.NEW,
                currentPatchSetId = 1,
                createdOn = Instant.now(),
                lastUpdatedOn = Instant.now(),
                patchSets = listOf(
                    mapOf(
                        "number" to 1,
                        "revision" to "abc123",
                        "ref" to "refs/changes/01/${testAccountId.toInt()}/1",
                        "uploader" to mapOf("_account_id" to testAccountId.toInt()),
                        "created" to Instant.now().toString(),
                        "kind" to "REWORK",
                        "description" to "Initial patch set"
                    )
                ),
                metadata = mapOf(
                    "comments" to emptyMap<String, List<Map<String, Any>>>(),
                    "drafts" to emptyMap<String, List<Map<String, Any>>>()
                )
            )
        )
        
        // Configure TestRestTemplate with Basic Auth
        restTemplate = restTemplate.withBasicAuth("testuser", "password")
    }

    @Test
    fun `test list revision comments - empty`() {
        val response = restTemplate.getForEntity(
            "$baseUrl/changes/${testChange.id}/revisions/1/comments",
            String::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val comments: Map<String, List<CommentInfo>> = objectMapper.readValue(response.body!!)
        assertTrue(comments.isEmpty())
    }

    @Test
    fun `test list revision drafts - empty`() {
        val response = restTemplate.getForEntity(
            "$baseUrl/changes/${testChange.id}/revisions/1/drafts",
            String::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val drafts: Map<String, List<CommentInfo>> = objectMapper.readValue(response.body!!)
        assertTrue(drafts.isEmpty())
    }

    @Test
    fun `test create revision drafts`() {
        val commentsInput = CommentsInput(
            comments = mapOf(
                "src/main/java/Example.java" to listOf(
                    CommentInput(
                        path = "src/main/java/Example.java",
                        side = CommentSide.REVISION,
                        line = 10,
                        message = "This needs improvement"
                    ),
                    CommentInput(
                        path = "src/main/java/Example.java",
                        side = CommentSide.REVISION,
                        line = 15,
                        message = "Consider refactoring this method",
                        unresolved = true
                    )
                ),
                "README.md" to listOf(
                    CommentInput(
                        path = "README.md",
                        line = 5,
                        message = "Update documentation"
                    )
                )
            )
        )

        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        val request = HttpEntity(commentsInput, headers)

        val response = restTemplate.exchange(
            "$baseUrl/changes/${testChange.id}/revisions/1/drafts",
            HttpMethod.PUT,
            request,
            String::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val drafts: Map<String, List<CommentInfo>> = objectMapper.readValue(response.body!!)
        
        assertEquals(2, drafts.size)
        assertTrue(drafts.containsKey("src/main/java/Example.java"))
        assertTrue(drafts.containsKey("README.md"))
        
        val javaDrafts = drafts["src/main/java/Example.java"]!!
        assertEquals(2, javaDrafts.size)
        assertEquals("This needs improvement", javaDrafts[0].message)
        assertEquals(10, javaDrafts[0].line)
        
        val readmeDrafts = drafts["README.md"]!!
        assertEquals(1, readmeDrafts.size)
        assertEquals("Update documentation", readmeDrafts[0].message)
    }

    @Test
    fun `test list revision drafts after creation`() {
        // First create some drafts
        val commentsInput = CommentsInput(
            comments = mapOf(
                "test.txt" to listOf(
                    CommentInput(
                        path = "test.txt",
                        line = 1,
                        message = "Test draft comment"
                    )
                )
            )
        )

        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        val request = HttpEntity(commentsInput, headers)

        restTemplate.exchange(
            "$baseUrl/changes/${testChange.id}/revisions/1/drafts",
            HttpMethod.PUT,
            request,
            String::class.java
        )

        // Now list the drafts
        val response = restTemplate.getForEntity(
            "$baseUrl/changes/${testChange.id}/revisions/1/drafts",
            String::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val drafts: Map<String, List<CommentInfo>> = objectMapper.readValue(response.body!!)
        
        assertEquals(1, drafts.size)
        assertTrue(drafts.containsKey("test.txt"))
        val testDrafts = drafts["test.txt"]!!
        assertEquals(1, testDrafts.size)
        assertEquals("Test draft comment", testDrafts[0].message)
    }

    @Test
    fun `test get specific draft comment`() {
        // First create a draft
        val commentsInput = CommentsInput(
            comments = mapOf(
                "test.txt" to listOf(
                    CommentInput(
                        path = "test.txt",
                        line = 5,
                        message = "Specific draft comment"
                    )
                )
            )
        )

        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        val request = HttpEntity(commentsInput, headers)

        val createResponse = restTemplate.exchange(
            "$baseUrl/changes/${testChange.id}/revisions/1/drafts",
            HttpMethod.PUT,
            request,
            String::class.java
        )

        val drafts: Map<String, List<CommentInfo>> = objectMapper.readValue(createResponse.body!!)
        val commentId = drafts["test.txt"]!![0].id!!

        // Now get the specific draft
        val response = restTemplate.getForEntity(
            "$baseUrl/changes/${testChange.id}/revisions/1/drafts/$commentId",
            String::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val comment: CommentInfo = objectMapper.readValue(response.body!!)
        assertEquals(commentId, comment.id)
        assertEquals("Specific draft comment", comment.message)
        assertEquals(5, comment.line)
    }

    @Test
    fun `test update specific draft comment`() {
        // First create a draft
        val commentsInput = CommentsInput(
            comments = mapOf(
                "test.txt" to listOf(
                    CommentInput(
                        path = "test.txt",
                        line = 10,
                        message = "Original message"
                    )
                )
            )
        )

        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        val request = HttpEntity(commentsInput, headers)

        val createResponse = restTemplate.exchange(
            "$baseUrl/changes/${testChange.id}/revisions/1/drafts",
            HttpMethod.PUT,
            request,
            String::class.java
        )

        val drafts: Map<String, List<CommentInfo>> = objectMapper.readValue(createResponse.body!!)
        val commentId = drafts["test.txt"]!![0].id!!

        // Now update the draft
        val updateInput = CommentInput(
            path = "test.txt",
            line = 15,
            message = "Updated message",
            unresolved = true
        )

        val updateRequest = HttpEntity(updateInput, headers)
        val updateResponse = restTemplate.exchange(
            "$baseUrl/changes/${testChange.id}/revisions/1/drafts/$commentId",
            HttpMethod.PUT,
            updateRequest,
            String::class.java
        )

        assertEquals(HttpStatus.OK, updateResponse.statusCode)
        val updatedComment: CommentInfo = objectMapper.readValue(updateResponse.body!!)
        assertEquals(commentId, updatedComment.id)
        assertEquals("Updated message", updatedComment.message)
        assertEquals(15, updatedComment.line)
        assertEquals(true, updatedComment.unresolved)
    }

    @Test
    fun `test delete draft comment`() {
        // First create a draft
        val commentsInput = CommentsInput(
            comments = mapOf(
                "test.txt" to listOf(
                    CommentInput(
                        path = "test.txt",
                        line = 20,
                        message = "Draft to be deleted"
                    )
                )
            )
        )

        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        val request = HttpEntity(commentsInput, headers)

        val createResponse = restTemplate.exchange(
            "$baseUrl/changes/${testChange.id}/revisions/1/drafts",
            HttpMethod.PUT,
            request,
            String::class.java
        )

        val drafts: Map<String, List<CommentInfo>> = objectMapper.readValue(createResponse.body!!)
        val commentId = drafts["test.txt"]!![0].id!!

        // Now delete the draft
        val deleteResponse = restTemplate.exchange(
            "$baseUrl/changes/${testChange.id}/revisions/1/drafts/$commentId",
            HttpMethod.DELETE,
            null,
            String::class.java
        )

        assertEquals(HttpStatus.NO_CONTENT, deleteResponse.statusCode)

        // Verify the draft is gone
        val listResponse = restTemplate.getForEntity(
            "$baseUrl/changes/${testChange.id}/revisions/1/drafts",
            String::class.java
        )

        val remainingDrafts: Map<String, List<CommentInfo>> = objectMapper.readValue(listResponse.body!!)
        assertTrue(remainingDrafts.isEmpty() || remainingDrafts["test.txt"]?.isEmpty() == true)
    }

    @Test
    fun `test get non-existent draft comment returns 404`() {
        val response = restTemplate.getForEntity(
            "$baseUrl/changes/${testChange.id}/revisions/1/drafts/non-existent-comment",
            String::class.java
        )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `test update non-existent draft comment returns 404`() {
        val updateInput = CommentInput(
            path = "test.txt",
            message = "This should fail"
        )

        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        val request = HttpEntity(updateInput, headers)

        val response = restTemplate.exchange(
            "$baseUrl/changes/${testChange.id}/revisions/1/drafts/non-existent-comment",
            HttpMethod.PUT,
            request,
            String::class.java
        )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `test delete non-existent draft comment returns 404`() {
        val response = restTemplate.exchange(
            "$baseUrl/changes/${testChange.id}/revisions/1/drafts/non-existent-comment",
            HttpMethod.DELETE,
            null,
            String::class.java
        )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `test get non-existent published comment returns 404`() {
        val response = restTemplate.getForEntity(
            "$baseUrl/changes/${testChange.id}/revisions/1/comments/non-existent-comment",
            String::class.java
        )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `test comments with different revisions`() {
        // Create drafts for revision 1
        val commentsInput1 = CommentsInput(
            comments = mapOf(
                "file1.txt" to listOf(
                    CommentInput(
                        path = "file1.txt",
                        line = 1,
                        message = "Comment on revision 1"
                    )
                )
            )
        )

        // Create drafts for revision 2
        val commentsInput2 = CommentsInput(
            comments = mapOf(
                "file2.txt" to listOf(
                    CommentInput(
                        path = "file2.txt",
                        line = 2,
                        message = "Comment on revision 2"
                    )
                )
            )
        )

        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON

        // Create drafts for both revisions
        restTemplate.exchange(
            "$baseUrl/changes/${testChange.id}/revisions/1/drafts",
            HttpMethod.PUT,
            HttpEntity(commentsInput1, headers),
            String::class.java
        )

        restTemplate.exchange(
            "$baseUrl/changes/${testChange.id}/revisions/2/drafts",
            HttpMethod.PUT,
            HttpEntity(commentsInput2, headers),
            String::class.java
        )

        // Verify revision 1 drafts
        val response1 = restTemplate.getForEntity(
            "$baseUrl/changes/${testChange.id}/revisions/1/drafts",
            String::class.java
        )
        val drafts1: Map<String, List<CommentInfo>> = objectMapper.readValue(response1.body!!)
        assertTrue(drafts1.containsKey("file1.txt"))
        assertEquals("Comment on revision 1", drafts1["file1.txt"]!![0].message)

        // Verify revision 2 drafts
        val response2 = restTemplate.getForEntity(
            "$baseUrl/changes/${testChange.id}/revisions/2/drafts",
            String::class.java
        )
        val drafts2: Map<String, List<CommentInfo>> = objectMapper.readValue(response2.body!!)
        assertTrue(drafts2.containsKey("file2.txt"))
        assertEquals("Comment on revision 2", drafts2["file2.txt"]!![0].message)
    }

    @Test
    fun `test comments with range selection`() {
        val commentsInput = CommentsInput(
            comments = mapOf(
                "test.java" to listOf(
                    CommentInput(
                        path = "test.java",
                        line = 10,
                        range = CommentRange(
                            startLine = 10,
                            startCharacter = 5,
                            endLine = 12,
                            endCharacter = 15
                        ),
                        message = "This range needs attention"
                    )
                )
            )
        )

        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        val request = HttpEntity(commentsInput, headers)

        val response = restTemplate.exchange(
            "$baseUrl/changes/${testChange.id}/revisions/1/drafts",
            HttpMethod.PUT,
            request,
            String::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val drafts: Map<String, List<CommentInfo>> = objectMapper.readValue(response.body!!)
        
        val comment = drafts["test.java"]!![0]
        assertNotNull(comment.range)
        assertEquals(10, comment.range!!.startLine)
        assertEquals(5, comment.range!!.startCharacter)
        assertEquals(12, comment.range!!.endLine)
        assertEquals(15, comment.range!!.endCharacter)
    }
}
