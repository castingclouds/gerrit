package ai.fluxuate.gerrit.api.controller

import ai.fluxuate.gerrit.model.ChangeEntity
import ai.fluxuate.gerrit.model.ChangeStatus
import ai.fluxuate.gerrit.repository.ChangeEntityRepository
import ai.fluxuate.gerrit.api.dto.*
import ai.fluxuate.gerrit.config.TestSecurityConfig
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
import org.springframework.test.context.ActiveProfiles
import java.time.Instant

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestSecurityConfig::class)
class RevisionsControllerIntegrationTest {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var changeRepository: ChangeEntityRepository

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private lateinit var testChange: ChangeEntity

    @BeforeEach
    fun setUp() {
        changeRepository.deleteAll()
        
        // Create a test change with patch sets
        testChange = ChangeEntity(
            changeKey = "I1234567890abcdef1234567890abcdef12345678",
            ownerId = 1001,
            projectName = "test-project",
            destBranch = "main",
            subject = "Test change with revisions",
            status = ChangeStatus.NEW,
            createdOn = Instant.now(),
            lastUpdatedOn = Instant.now(),
            patchSets = listOf(
                mapOf(
                    "commitId" to "abc123def456",
                    "subject" to "Initial patch set",
                    "message" to "Initial patch set\n\nChange-Id: I1234567890abcdef1234567890abcdef12345678",
                    "createdOn" to Instant.now().toString(),
                    "uploader" to mapOf(
                        "_account_id" to 1001,
                        "name" to "Test User",
                        "email" to "test@example.com",
                        "username" to "testuser"
                    ),
                    "author" to mapOf(
                        "name" to "Test Author",
                        "email" to "author@example.com"
                    ),
                    "committer" to mapOf(
                        "name" to "Test Committer", 
                        "email" to "committer@example.com"
                    )
                ),
                mapOf(
                    "commitId" to "def789ghi012",
                    "subject" to "Updated patch set",
                    "message" to "Updated patch set\n\nChange-Id: I1234567890abcdef1234567890abcdef12345678",
                    "createdOn" to Instant.now().toString(),
                    "uploader" to mapOf(
                        "_account_id" to 1001,
                        "name" to "Test User",
                        "email" to "test@example.com",
                        "username" to "testuser"
                    ),
                    "author" to mapOf(
                        "name" to "Test Author",
                        "email" to "author@example.com"
                    ),
                    "committer" to mapOf(
                        "name" to "Test Committer",
                        "email" to "committer@example.com"
                    ),
                    "description" to "Addressed review comments"
                )
            )
        )
        
        testChange = changeRepository.save(testChange)
    }

    @Test
    fun `should list all revisions for a change`() {
        val url = "http://localhost:$port/a/changes/${testChange.id}/revisions"
        
        val response = restTemplate.getForEntity(url, String::class.java)
        
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        
        val revisions: Map<String, Any> = objectMapper.readValue(response.body!!)
        assertEquals(2, revisions.size)
        
        // Check that both revision IDs are present
        assertTrue(revisions.containsKey("abc123def456"))
        assertTrue(revisions.containsKey("def789ghi012"))
    }

    @Test
    fun `should get specific revision by commit ID`() {
        val url = "http://localhost:$port/a/changes/${testChange.id}/revisions/abc123def456"
        
        val response = restTemplate.getForEntity(url, String::class.java)
        
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        
        val revision: Map<String, Any> = objectMapper.readValue(response.body!!)
        assertEquals("REWORK", revision["kind"])
        assertEquals(1, revision["_number"])
        assertNotNull(revision["uploader"])
        assertNotNull(revision["commit"])
        assertTrue((revision["ref"] as String).contains("refs/changes/"))
    }

    @Test
    fun `should get specific revision by patch set number`() {
        val url = "http://localhost:$port/a/changes/${testChange.id}/revisions/2"
        
        val response = restTemplate.getForEntity(url, String::class.java)
        
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        
        val revision: Map<String, Any> = objectMapper.readValue(response.body!!)
        assertEquals("REWORK", revision["kind"])
        assertEquals(2, revision["_number"])
        assertEquals("Addressed review comments", revision["description"])
    }

    @Test
    fun `should get current revision`() {
        val url = "http://localhost:$port/a/changes/${testChange.id}/revisions/current"
        
        val response = restTemplate.getForEntity(url, String::class.java)
        
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        
        val revision: Map<String, Any> = objectMapper.readValue(response.body!!)
        assertEquals("REWORK", revision["kind"])
        assertEquals(2, revision["_number"]) // Should be the latest patch set
    }

    @Test
    fun `should return 404 for non-existent revision`() {
        val url = "http://localhost:$port/a/changes/${testChange.id}/revisions/nonexistent"
        
        val response = restTemplate.getForEntity(url, String::class.java)
        
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `should get commit info for revision`() {
        val url = "http://localhost:$port/a/changes/${testChange.id}/revisions/abc123def456/commit"
        
        val response = restTemplate.getForEntity(url, String::class.java)
        
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        
        val commit: Map<String, Any> = objectMapper.readValue(response.body!!)
        assertEquals("abc123def456", commit["commit"])
        assertEquals("Initial patch set", commit["subject"])
        assertNotNull(commit["author"])
        assertNotNull(commit["committer"])
        
        val author = commit["author"] as Map<String, Any>
        assertEquals("Test Author", author["name"])
        assertEquals("author@example.com", author["email"])
    }

    @Test
    fun `should submit revision successfully`() {
        val url = "http://localhost:$port/a/changes/${testChange.id}/revisions/current/submit"
        val submitInput = SubmitInput()
        
        val headers = HttpHeaders()
        headers.set("Content-Type", "application/json")
        val request = HttpEntity(submitInput, headers)
        
        val response = restTemplate.postForEntity(url, request, String::class.java)
        
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        
        val changeInfo: Map<String, Any> = objectMapper.readValue(response.body!!)
        assertEquals("MERGED", changeInfo["status"])
    }

    @Test
    fun `should fail to submit already merged change`() {
        // First, submit the change
        testChange = testChange.copy(status = ChangeStatus.MERGED)
        changeRepository.save(testChange)
        
        val url = "http://localhost:$port/a/changes/${testChange.id}/revisions/current/submit"
        val submitInput = SubmitInput()
        
        val headers = HttpHeaders()
        headers.set("Content-Type", "application/json")
        val request = HttpEntity(submitInput, headers)
        
        val response = restTemplate.postForEntity(url, request, String::class.java)
        
        assertEquals(HttpStatus.CONFLICT, response.statusCode)
    }

    @Test
    fun `should rebase revision successfully`() {
        val url = "http://localhost:$port/a/changes/${testChange.id}/revisions/current/rebase"
        val rebaseInput = RebaseInput(base = "main")
        
        val headers = HttpHeaders()
        headers.set("Content-Type", "application/json")
        val request = HttpEntity(rebaseInput, headers)
        
        val response = restTemplate.postForEntity(url, request, String::class.java)
        
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        
        val changeInfo: Map<String, Any> = objectMapper.readValue(response.body!!)
        assertEquals("NEW", changeInfo["status"])
    }

    @Test
    fun `should fail to rebase merged change`() {
        // First, mark the change as merged
        testChange = testChange.copy(status = ChangeStatus.MERGED)
        changeRepository.save(testChange)
        
        val url = "http://localhost:$port/a/changes/${testChange.id}/revisions/current/rebase"
        val rebaseInput = RebaseInput(base = "main")
        
        val headers = HttpHeaders()
        headers.set("Content-Type", "application/json")
        val request = HttpEntity(rebaseInput, headers)
        
        val response = restTemplate.postForEntity(url, request, String::class.java)
        
        assertEquals(HttpStatus.CONFLICT, response.statusCode)
    }

    @Test
    fun `should review revision successfully`() {
        val url = "http://localhost:$port/a/changes/${testChange.id}/revisions/current/review"
        val reviewInput = ReviewInput(
            message = "Looks good to me!",
            labels = mapOf("Code-Review" to 2)
        )
        
        val headers = HttpHeaders()
        headers.set("Content-Type", "application/json")
        val request = HttpEntity(reviewInput, headers)
        
        val response = restTemplate.postForEntity(url, request, String::class.java)
        
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        
        val reviewResult: Map<String, Any> = objectMapper.readValue(response.body!!)
        assertNotNull(reviewResult["labels"])
        
        val labels = reviewResult["labels"] as Map<String, Any>
        assertEquals(2, labels["Code-Review"])
    }

    @Test
    fun `should return 404 for non-existent change in revision endpoints`() {
        val url = "http://localhost:$port/a/changes/999999/revisions"
        
        val response = restTemplate.getForEntity(url, String::class.java)
        
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `should handle partial commit ID matching`() {
        val url = "http://localhost:$port/a/changes/${testChange.id}/revisions/abc123"
        
        val response = restTemplate.getForEntity(url, String::class.java)
        
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        
        val revision: Map<String, Any> = objectMapper.readValue(response.body!!)
        assertEquals(1, revision["_number"])
    }

    @Test
    fun `should validate revision ref format`() {
        val url = "http://localhost:$port/a/changes/${testChange.id}/revisions/1"
        
        val response = restTemplate.getForEntity(url, String::class.java)
        
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        
        val revision: Map<String, Any> = objectMapper.readValue(response.body!!)
        val ref = revision["ref"] as String
        
        // Verify ref format: refs/changes/XX/CHANGEID/PATCHSET
        val expectedPattern = "refs/changes/\\d{2}/${testChange.id}/1"
        assertTrue(ref.matches(Regex(expectedPattern)), "Ref format should match pattern: $expectedPattern, got: $ref")
    }

    @Test
    fun `should include fetch info in revision response`() {
        val url = "http://localhost:$port/a/changes/${testChange.id}/revisions/current"
        
        val response = restTemplate.getForEntity(url, String::class.java)
        
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        
        val revision: Map<String, Any> = objectMapper.readValue(response.body!!)
        assertNotNull(revision["fetch"])
        
        val fetch = revision["fetch"] as Map<String, Any>
        assertTrue(fetch.containsKey("http"))
        
        val httpFetch = fetch["http"] as Map<String, Any>
        assertNotNull(httpFetch["url"])
        assertNotNull(httpFetch["ref"])
    }
}
