package ai.fluxuate.gerrit.controller

import ai.fluxuate.gerrit.api.dto.ReviewInput
import ai.fluxuate.gerrit.api.dto.ReviewResult
import ai.fluxuate.gerrit.api.dto.AccountInfo
import ai.fluxuate.gerrit.api.dto.AccountInput
import ai.fluxuate.gerrit.model.ChangeEntity
import ai.fluxuate.gerrit.model.ChangeStatus
import ai.fluxuate.gerrit.repository.ChangeEntityRepository
import ai.fluxuate.gerrit.service.AccountService
import ai.fluxuate.gerrit.config.TestSecurityConfig
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.ActiveProfiles
import java.time.Instant

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestSecurityConfig::class)
class ReviewVotingIntegrationTest {

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

    private lateinit var testChange: ChangeEntity
    private var testAccountId: Long = 0L

    @BeforeEach
    fun setUp() {
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
        
        // Create a test change
        testChange = ChangeEntity(
            id = 0, // Will be auto-generated
            changeKey = "I1234567890123456789012345678901234567890",
            ownerId = testAccountId.toInt(), // Use the actual account ID
            projectName = "test-project",
            destBranch = "main",
            subject = "Test change for voting",
            status = ChangeStatus.NEW,
            createdOn = Instant.now(),
            lastUpdatedOn = Instant.now(),
            patchSets = listOf(
                mapOf(
                    "patchSetNumber" to 1,
                    "revision" to "abc123",
                    "commitId" to "abc123456789",
                    "uploader" to mapOf(
                        "_account_id" to testAccountId,
                        "name" to "Test User",
                        "email" to "testuser@example.com"
                    ),
                    "createdOn" to Instant.now().toString()
                )
            ),
            approvals = emptyList()
        )
        testChange = changeRepository.save(testChange)
        
        // Configure TestRestTemplate with Basic Auth
        restTemplate = restTemplate.withBasicAuth("testuser", "password")
    }

    @Test
    fun `should submit Code-Review vote successfully`() {
        val reviewInput = ReviewInput(
            labels = mapOf("Code-Review" to 2)
        )

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
        }
        val request = HttpEntity(reviewInput, headers)

        val response = restTemplate.exchange(
            "http://localhost:$port/a/changes/${testChange.id}/revisions/abc123/review",
            HttpMethod.POST,
            request,
            ReviewResult::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val result = response.body!!
        assertEquals(2, result.labels?.get("Code-Review"))

        // Verify the vote was stored in the database
        val updatedChange = changeRepository.findById(testChange.id).orElse(null)
        assertNotNull(updatedChange)
        assertEquals(1, updatedChange.approvals.size)
        
        val approval = updatedChange.approvals[0]
        assertEquals("Code-Review", approval["label"])
        assertEquals(2, approval["value"])
        assertEquals("abc123", approval["revision_id"])
        
        val user = approval["user"] as Map<String, Any>
        assertEquals(testAccountId.toInt(), user["_account_id"])
    }

    @Test
    fun `should submit Verified vote successfully`() {
        val reviewInput = ReviewInput(
            labels = mapOf("Verified" to 1)
        )

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
        }
        val request = HttpEntity(reviewInput, headers)

        val response = restTemplate.exchange(
            "http://localhost:$port/a/changes/${testChange.id}/revisions/abc123/review",
            HttpMethod.POST,
            request,
            ReviewResult::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val result = response.body!!
        assertEquals(1, result.labels?.get("Verified"))

        // Verify the vote was stored
        val updatedChange = changeRepository.findById(testChange.id).orElse(null)
        assertNotNull(updatedChange)
        assertEquals(1, updatedChange.approvals.size)
        
        val approval = updatedChange.approvals[0]
        assertEquals("Verified", approval["label"])
        assertEquals(1, approval["value"])
    }

    @Test
    fun `should submit multiple votes in single review`() {
        val reviewInput = ReviewInput(
            labels = mapOf(
                "Code-Review" to 1,
                "Verified" to -1
            )
        )

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
        }
        val request = HttpEntity(reviewInput, headers)

        val response = restTemplate.exchange(
            "http://localhost:$port/a/changes/${testChange.id}/revisions/abc123/review",
            HttpMethod.POST,
            request,
            ReviewResult::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val result = response.body!!
        assertEquals(1, result.labels?.get("Code-Review"))
        assertEquals(-1, result.labels?.get("Verified"))

        // Verify both votes were stored
        val updatedChange = changeRepository.findById(testChange.id).orElse(null)
        assertNotNull(updatedChange)
        assertEquals(2, updatedChange.approvals.size)
        
        val codeReviewVote = updatedChange.approvals.find { it["label"] == "Code-Review" }!!
        assertEquals(1, codeReviewVote["value"])
        
        val verifiedVote = updatedChange.approvals.find { it["label"] == "Verified" }!!
        assertEquals(-1, verifiedVote["value"])
    }

    @Test
    fun `should replace existing vote when user votes again`() {
        // First vote
        val firstReview = ReviewInput(
            labels = mapOf("Code-Review" to 1)
        )

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
        }
        var request = HttpEntity(firstReview, headers)

        restTemplate.exchange(
            "http://localhost:$port/a/changes/${testChange.id}/revisions/abc123/review",
            HttpMethod.POST,
            request,
            ReviewResult::class.java
        )

        // Second vote (should replace the first)
        val secondReview = ReviewInput(
            labels = mapOf("Code-Review" to -2)
        )

        request = HttpEntity(secondReview, headers)

        val response = restTemplate.exchange(
            "http://localhost:$port/a/changes/${testChange.id}/revisions/abc123/review",
            HttpMethod.POST,
            request,
            ReviewResult::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val result = response.body!!
        assertEquals(-2, result.labels?.get("Code-Review"))

        // Verify only one vote exists (the latest one)
        val updatedChange = changeRepository.findById(testChange.id).orElse(null)
        assertNotNull(updatedChange)
        assertEquals(1, updatedChange.approvals.size)
        
        val approval = updatedChange.approvals[0]
        assertEquals("Code-Review", approval["label"])
        assertEquals(-2, approval["value"])
    }

    @Test
    fun `should reject invalid Code-Review vote value`() {
        val reviewInput = ReviewInput(
            labels = mapOf("Code-Review" to 3) // Invalid: outside -2..2 range
        )

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
        }
        val request = HttpEntity(reviewInput, headers)

        val response = restTemplate.exchange(
            "http://localhost:$port/a/changes/${testChange.id}/revisions/abc123/review",
            HttpMethod.POST,
            request,
            String::class.java
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertTrue(response.body!!.contains("Invalid vote value 3 for label Code-Review"))

        // Verify no vote was stored
        val updatedChange = changeRepository.findById(testChange.id).orElse(null)
        assertNotNull(updatedChange)
        assertEquals(0, updatedChange.approvals.size)
    }

    @Test
    fun `should reject invalid Verified vote value`() {
        val reviewInput = ReviewInput(
            labels = mapOf("Verified" to 2) // Invalid: outside -1..1 range
        )

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
        }
        val request = HttpEntity(reviewInput, headers)

        val response = restTemplate.exchange(
            "http://localhost:$port/a/changes/${testChange.id}/revisions/abc123/review",
            HttpMethod.POST,
            request,
            String::class.java
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertTrue(response.body!!.contains("Invalid vote value 2 for label Verified"))

        // Verify no vote was stored
        val updatedChange = changeRepository.findById(testChange.id).orElse(null)
        assertNotNull(updatedChange)
        assertEquals(0, updatedChange.approvals.size)
    }

    @Test
    fun `should accept custom label with default range`() {
        val reviewInput = ReviewInput(
            labels = mapOf("Custom-Label" to 1)
        )

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
        }
        val request = HttpEntity(reviewInput, headers)

        val response = restTemplate.exchange(
            "http://localhost:$port/a/changes/${testChange.id}/revisions/abc123/review",
            HttpMethod.POST,
            request,
            ReviewResult::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val result = response.body!!
        assertEquals(1, result.labels?.get("Custom-Label"))

        // Verify the vote was stored
        val updatedChange = changeRepository.findById(testChange.id).orElse(null)
        assertNotNull(updatedChange)
        assertEquals(1, updatedChange.approvals.size)
        
        val approval = updatedChange.approvals[0]
        assertEquals("Custom-Label", approval["label"])
        assertEquals(1, approval["value"])
    }

    @Test
    fun `should handle zero votes (removing vote)`() {
        // First, add a vote
        val firstReview = ReviewInput(
            labels = mapOf("Code-Review" to 2)
        )

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
        }
        var request = HttpEntity(firstReview, headers)

        restTemplate.exchange(
            "http://localhost:$port/a/changes/${testChange.id}/revisions/abc123/review",
            HttpMethod.POST,
            request,
            ReviewResult::class.java
        )

        // Then vote 0 (should replace with 0 vote, not remove)
        val zeroReview = ReviewInput(
            labels = mapOf("Code-Review" to 0)
        )

        request = HttpEntity(zeroReview, headers)

        val response = restTemplate.exchange(
            "http://localhost:$port/a/changes/${testChange.id}/revisions/abc123/review",
            HttpMethod.POST,
            request,
            ReviewResult::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val result = response.body!!
        assertEquals(0, result.labels?.get("Code-Review"))

        // Verify the zero vote is stored
        val updatedChange = changeRepository.findById(testChange.id).orElse(null)
        assertNotNull(updatedChange)
        assertEquals(1, updatedChange.approvals.size)
        
        val approval = updatedChange.approvals[0]
        assertEquals("Code-Review", approval["label"])
        assertEquals(0, approval["value"])
    }

    @Test
    fun `should handle review with no labels`() {
        val reviewInput = ReviewInput(
            labels = emptyMap()
        )

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
        }
        val request = HttpEntity(reviewInput, headers)

        val response = restTemplate.exchange(
            "http://localhost:$port/a/changes/${testChange.id}/revisions/abc123/review",
            HttpMethod.POST,
            request,
            ReviewResult::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val result = response.body!!
        assertTrue(result.labels.isNullOrEmpty())

        // Verify no votes were stored
        val updatedChange = changeRepository.findById(testChange.id).orElse(null)
        assertNotNull(updatedChange)
        assertEquals(0, updatedChange.approvals.size)
    }

    @Test
    fun `should return 404 for non-existent change`() {
        val reviewInput = ReviewInput(
            labels = mapOf("Code-Review" to 1)
        )

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
        }
        val request = HttpEntity(reviewInput, headers)

        val response = restTemplate.exchange(
            "http://localhost:$port/a/changes/99999/revisions/abc123/review",
            HttpMethod.POST,
            request,
            String::class.java
        )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `should return 404 for non-existent revision`() {
        val reviewInput = ReviewInput(
            labels = mapOf("Code-Review" to 1)
        )

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
        }
        val request = HttpEntity(reviewInput, headers)

        val response = restTemplate.exchange(
            "http://localhost:$port/a/changes/${testChange.id}/revisions/nonexistent/review",
            HttpMethod.POST,
            request,
            String::class.java
        )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }
}
