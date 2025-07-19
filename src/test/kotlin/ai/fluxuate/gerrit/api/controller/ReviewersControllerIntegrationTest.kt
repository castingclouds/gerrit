package ai.fluxuate.gerrit.api.controller

import ai.fluxuate.gerrit.api.dto.*
import ai.fluxuate.gerrit.config.TestSecurityConfig
import ai.fluxuate.gerrit.model.ChangeEntity
import ai.fluxuate.gerrit.model.ChangeStatus
import ai.fluxuate.gerrit.repository.ChangeEntityRepository
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.http.*
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import java.time.Instant

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Import(TestSecurityConfig::class)
class ReviewersControllerIntegrationTest {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var changeRepository: ChangeEntityRepository

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private lateinit var baseUrl: String
    private lateinit var testChange: ChangeEntity

    @BeforeEach
    fun setUp() {
        baseUrl = "http://localhost:$port/a/changes"
        
        // Create test change
        testChange = ChangeEntity(
            id = 1,
            changeKey = "I1234567890123456789012345678901234567890",
            ownerId = 1,
            projectName = "test-project",
            destBranch = "main",
            subject = "Test change for reviewers",
            status = ChangeStatus.NEW,
            createdOn = Instant.now(),
            lastUpdatedOn = Instant.now(),
            metadata = mutableMapOf(
                "reviewers" to mutableMapOf(
                    "REVIEWER" to mutableListOf<Map<String, Any>>(),
                    "CC" to mutableListOf<Map<String, Any>>()
                )
            )
        )
        changeRepository.save(testChange)
    }

    @AfterEach
    fun tearDown() {
        changeRepository.deleteAll()
    }

    @Test
    fun `test get reviewers for change with no reviewers`() {
        val response = restTemplate.getForEntity(
            "$baseUrl/${testChange.id}/reviewers",
            Array<AccountInfo>::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val reviewers = response.body!!
        assertTrue(reviewers.isEmpty())
    }

    @Test
    fun `test add reviewer to change`() {
        val reviewerInput = ReviewerInput(
            reviewer = "test@example.com",
            state = ReviewerState.REVIEWER
        )

        val response = restTemplate.postForEntity(
            "$baseUrl/${testChange.id}/reviewers",
            reviewerInput,
            AddReviewerResult::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val result = response.body!!
        assertEquals("test@example.com", result.input)
        assertEquals(1, result.reviewers.size)
        assertEquals("test@example.com", result.reviewers[0].email)
        assertNull(result.error)
    }

    @Test
    fun `test add CC to change`() {
        val reviewerInput = ReviewerInput(
            reviewer = "cc@example.com",
            state = ReviewerState.CC
        )

        val response = restTemplate.postForEntity(
            "$baseUrl/${testChange.id}/reviewers",
            reviewerInput,
            AddReviewerResult::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val result = response.body!!
        assertEquals("cc@example.com", result.input)
        assertEquals(0, result.reviewers.size)
        assertEquals(1, result.ccs.size)
        assertEquals("cc@example.com", result.ccs[0].email)
        assertNull(result.error)
    }

    @Test
    fun `test add reviewer by account ID`() {
        val reviewerInput = ReviewerInput(
            reviewer = "123",
            state = ReviewerState.REVIEWER
        )

        val response = restTemplate.postForEntity(
            "$baseUrl/${testChange.id}/reviewers",
            reviewerInput,
            AddReviewerResult::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val result = response.body!!
        assertEquals("123", result.input)
        assertEquals(1, result.reviewers.size)
        assertEquals(123, result.reviewers[0]._account_id)
        assertEquals("user123@example.com", result.reviewers[0].email)
    }

    @Test
    fun `test get reviewers after adding some`() {
        // Add a reviewer and a CC
        val reviewer = ReviewerInput(reviewer = "reviewer@example.com", state = ReviewerState.REVIEWER)
        val cc = ReviewerInput(reviewer = "cc@example.com", state = ReviewerState.CC)

        restTemplate.postForEntity("$baseUrl/${testChange.id}/reviewers", reviewer, AddReviewerResult::class.java)
        restTemplate.postForEntity("$baseUrl/${testChange.id}/reviewers", cc, AddReviewerResult::class.java)

        // Get all reviewers
        val response = restTemplate.getForEntity(
            "$baseUrl/${testChange.id}/reviewers",
            Array<AccountInfo>::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val reviewers = response.body!!
        assertEquals(2, reviewers.size)
        
        val emails = reviewers.map { it.email }.toSet()
        assertTrue(emails.contains("reviewer@example.com"))
        assertTrue(emails.contains("cc@example.com"))
    }

    @Test
    fun `test get specific reviewer`() {
        // Add a reviewer first
        val reviewerInput = ReviewerInput(reviewer = "specific@example.com", state = ReviewerState.REVIEWER)
        val addResult = restTemplate.postForEntity(
            "$baseUrl/${testChange.id}/reviewers",
            reviewerInput,
            AddReviewerResult::class.java
        ).body!!

        val accountId = addResult.reviewers[0]._account_id

        // Get specific reviewer by account ID
        val response = restTemplate.getForEntity(
            "$baseUrl/${testChange.id}/reviewers/$accountId",
            AccountInfo::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val reviewer = response.body!!
        assertEquals(accountId, reviewer._account_id)
        assertEquals("specific@example.com", reviewer.email)
    }

    @Test
    fun `test get specific reviewer by email`() {
        // Add a reviewer first
        val reviewerInput = ReviewerInput(reviewer = "email-lookup@example.com", state = ReviewerState.REVIEWER)
        restTemplate.postForEntity("$baseUrl/${testChange.id}/reviewers", reviewerInput, AddReviewerResult::class.java)

        // Get specific reviewer by email
        val response = restTemplate.getForEntity(
            "$baseUrl/${testChange.id}/reviewers/email-lookup@example.com",
            AccountInfo::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val reviewer = response.body!!
        assertEquals("email-lookup@example.com", reviewer.email)
    }

    @Test
    fun `test get non-existent reviewer returns 404`() {
        val response = restTemplate.getForEntity(
            "$baseUrl/${testChange.id}/reviewers/nonexistent@example.com",
            String::class.java
        )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `test remove reviewer`() {
        // Add a reviewer first
        val reviewerInput = ReviewerInput(reviewer = "remove-me@example.com", state = ReviewerState.REVIEWER)
        val addResult = restTemplate.postForEntity(
            "$baseUrl/${testChange.id}/reviewers",
            reviewerInput,
            AddReviewerResult::class.java
        ).body!!

        val accountId = addResult.reviewers[0]._account_id

        // Remove the reviewer
        val deleteInput = DeleteReviewerInput()
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        val entity = HttpEntity(deleteInput, headers)

        val response = restTemplate.exchange(
            "$baseUrl/${testChange.id}/reviewers/$accountId",
            HttpMethod.DELETE,
            entity,
            Void::class.java
        )

        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)

        // Verify reviewer is removed
        val getResponse = restTemplate.getForEntity(
            "$baseUrl/${testChange.id}/reviewers",
            Array<AccountInfo>::class.java
        )
        val reviewers = getResponse.body!!
        assertTrue(reviewers.none { it._account_id == accountId })
    }

    @Test
    fun `test remove reviewer by email`() {
        // Add a reviewer first
        val reviewerInput = ReviewerInput(reviewer = "remove-by-email@example.com", state = ReviewerState.REVIEWER)
        restTemplate.postForEntity("$baseUrl/${testChange.id}/reviewers", reviewerInput, AddReviewerResult::class.java)

        // Remove the reviewer by email
        val deleteInput = DeleteReviewerInput()
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        val entity = HttpEntity(deleteInput, headers)

        val response = restTemplate.exchange(
            "$baseUrl/${testChange.id}/reviewers/remove-by-email@example.com",
            HttpMethod.DELETE,
            entity,
            Void::class.java
        )

        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)

        // Verify reviewer is removed
        val getResponse = restTemplate.getForEntity(
            "$baseUrl/${testChange.id}/reviewers",
            Array<AccountInfo>::class.java
        )
        val reviewers = getResponse.body!!
        assertTrue(reviewers.none { it.email == "remove-by-email@example.com" })
    }

    @Test
    fun `test remove non-existent reviewer returns 404`() {
        val deleteInput = DeleteReviewerInput()
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        val entity = HttpEntity(deleteInput, headers)

        val response = restTemplate.exchange(
            "$baseUrl/${testChange.id}/reviewers/nonexistent@example.com",
            HttpMethod.DELETE,
            entity,
            String::class.java
        )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `test add duplicate reviewer returns error`() {
        val reviewerInput = ReviewerInput(reviewer = "duplicate@example.com", state = ReviewerState.REVIEWER)

        // Add reviewer first time
        val firstResponse = restTemplate.postForEntity(
            "$baseUrl/${testChange.id}/reviewers",
            reviewerInput,
            AddReviewerResult::class.java
        )
        assertEquals(HttpStatus.OK, firstResponse.statusCode)
        assertNull(firstResponse.body!!.error)

        // Try to add same reviewer again
        val secondResponse = restTemplate.postForEntity(
            "$baseUrl/${testChange.id}/reviewers",
            reviewerInput,
            AddReviewerResult::class.java
        )
        assertEquals(HttpStatus.OK, secondResponse.statusCode)
        assertEquals("Reviewer already added", secondResponse.body!!.error)
    }

    @Test
    fun `test suggest reviewers endpoint`() {
        val response = restTemplate.getForEntity(
            "$baseUrl/${testChange.id}/suggest_reviewers?q=test&n=5",
            Array<SuggestedReviewerInfo>::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val suggestions = response.body!!
        // Currently returns empty list as it's a placeholder implementation
        assertTrue(suggestions.isEmpty())
    }

    @Test
    fun `test suggest reviewers without query parameters`() {
        val response = restTemplate.getForEntity(
            "$baseUrl/${testChange.id}/suggest_reviewers",
            Array<SuggestedReviewerInfo>::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val suggestions = response.body!!
        assertTrue(suggestions.isEmpty())
    }

    @Test
    fun `test reviewer operations on non-existent change returns 404`() {
        val nonExistentChangeId = "999999"

        // Test get reviewers
        val getResponse = restTemplate.getForEntity(
            "$baseUrl/$nonExistentChangeId/reviewers",
            String::class.java
        )
        assertEquals(HttpStatus.NOT_FOUND, getResponse.statusCode)

        // Test add reviewer
        val reviewerInput = ReviewerInput(reviewer = "test@example.com")
        val addResponse = restTemplate.postForEntity(
            "$baseUrl/$nonExistentChangeId/reviewers",
            reviewerInput,
            String::class.java
        )
        assertEquals(HttpStatus.NOT_FOUND, addResponse.statusCode)

        // Test remove reviewer
        val deleteInput = DeleteReviewerInput()
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        val entity = HttpEntity(deleteInput, headers)

        val deleteResponse = restTemplate.exchange(
            "$baseUrl/$nonExistentChangeId/reviewers/test@example.com",
            HttpMethod.DELETE,
            entity,
            String::class.java
        )
        assertEquals(HttpStatus.NOT_FOUND, deleteResponse.statusCode)
    }
}
