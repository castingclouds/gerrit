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
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import java.time.Instant

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestSecurityConfig::class)
class FilesControllerIntegrationTest {

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

        testChange = ChangeEntity(
            id = 0, // Will be auto-generated
            changeKey = "I1234567890123456789012345678901234567890",
            ownerId = testAccountId.toInt(), // Use real account ID
            projectName = "test-project",
            destBranch = "main",
            subject = "Test change for files",
            status = ChangeStatus.NEW,
            createdOn = Instant.now(),
            lastUpdatedOn = Instant.now(),
            patchSets = listOf(
                mapOf(
                    "patchSetNumber" to 1,
                    "revision" to "abc123",
                    "commitId" to "abc123456789",
                    "uploader" to mapOf("_account_id" to testAccountId.toInt()),
                    "createdOn" to Instant.now().toString()
                )
            ),
            currentPatchSetId = 1
        )
        testChange = changeRepository.save(testChange)
        
        // Configure TestRestTemplate with Basic Auth
        restTemplate = restTemplate.withBasicAuth("testuser", "password")
    }

    @Test
    fun `test list revision files - default`() {
        val url = "http://localhost:$port/a/changes/${testChange.id}/revisions/abc123/files"
        val response = restTemplate.getForEntity(url, String::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val files: Map<String, FileInfo> = objectMapper.readValue(response.body!!)
        
        assertNotNull(files)
        assertTrue(files.isNotEmpty())
        assertTrue(files.containsKey("src/main/kotlin/Example.kt"))
        assertTrue(files.containsKey("README.md"))
        
        val exampleFile = files["src/main/kotlin/Example.kt"]!!
        assertEquals('M', exampleFile.status)
        assertEquals(5, exampleFile.linesInserted)
        assertEquals(2, exampleFile.linesDeleted)
    }

    @Test
    fun `test list revision files - with base`() {
        val url = "http://localhost:$port/a/changes/${testChange.id}/revisions/abc123/files?base=abc123"
        val response = restTemplate.getForEntity(url, String::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val files: Map<String, FileInfo> = objectMapper.readValue(response.body!!)
        assertNotNull(files)
    }

    @Test
    fun `test list revision files - with parent`() {
        val url = "http://localhost:$port/a/changes/${testChange.id}/revisions/abc123/files?parent=1"
        val response = restTemplate.getForEntity(url, String::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val files: Map<String, FileInfo> = objectMapper.readValue(response.body!!)
        assertNotNull(files)
    }

    @Test
    fun `test list revision files - reviewed flag`() {
        val url = "http://localhost:$port/a/changes/${testChange.id}/revisions/abc123/files?reviewed=true"
        val response = restTemplate.getForEntity(url, String::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val files: Map<String, FileInfo> = objectMapper.readValue(response.body!!)
        assertNotNull(files)
        assertTrue(files.isEmpty()) // Placeholder returns empty for reviewed files
    }

    @Test
    fun `test list revision files - with query`() {
        val url = "http://localhost:$port/a/changes/${testChange.id}/revisions/abc123/files?q=kotlin"
        val response = restTemplate.getForEntity(url, String::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val files: Map<String, FileInfo> = objectMapper.readValue(response.body!!)
        assertNotNull(files)
        assertTrue(files.isEmpty()) // Placeholder returns empty for query
    }

    @Test
    fun `test list revision files - conflicting options`() {
        val url = "http://localhost:$port/a/changes/${testChange.id}/revisions/abc123/files?base=def456&parent=1"
        val response = restTemplate.getForEntity(url, String::class.java)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `test get file content`() {
        val filePath = "src/main/kotlin/Example.kt"
        val url = "http://localhost:$port/a/changes/${testChange.id}/revisions/abc123/files/content?path=$filePath"
        val response = restTemplate.getForEntity(url, String::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertTrue(response.body!!.contains("TODO: Implement file content retrieval"))
    }

    @Test
    fun `test get file content - with parent`() {
        val filePath = "src/main/kotlin/Example.kt"
        val url = "http://localhost:$port/a/changes/${testChange.id}/revisions/abc123/files/content?path=$filePath&parent=1"
        val response = restTemplate.getForEntity(url, String::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertTrue(response.body!!.contains("Parent: 1"))
    }

    @Test
    fun `test get file diff`() {
        val filePath = "src/main/kotlin/Example.kt"
        val url = "http://localhost:$port/a/changes/${testChange.id}/revisions/abc123/files/diff?path=$filePath"
        val response = restTemplate.getForEntity(url, DiffInfo::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertEquals(ChangeType.MODIFIED, response.body!!.changeType)
    }

    @Test
    fun `test get file diff - with base`() {
        val filePath = "src/main/kotlin/Example.kt"
        val url = "http://localhost:$port/a/changes/${testChange.id}/revisions/abc123/files/diff?path=$filePath&base=abc123"
        val response = restTemplate.getForEntity(url, DiffInfo::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertEquals(ChangeType.MODIFIED, response.body!!.changeType)
    }

    @Test
    fun `test get file diff - with parent`() {
        val filePath = "src/main/kotlin/Example.kt"
        val url = "http://localhost:$port/a/changes/${testChange.id}/revisions/abc123/files/diff?path=$filePath&parent=1"
        val response = restTemplate.getForEntity(url, DiffInfo::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertEquals(ChangeType.MODIFIED, response.body!!.changeType)
    }

    @Test
    fun `test get file diff - with options`() {
        val filePath = "src/main/kotlin/Example.kt"
        val url = "http://localhost:$port/a/changes/${testChange.id}/revisions/abc123/files/diff?path=$filePath&context=5&intraline=true&whitespace=IGNORE_ALL"
        val response = restTemplate.getForEntity(url, DiffInfo::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertEquals(ChangeType.MODIFIED, response.body!!.changeType)
    }

    @Test
    fun `test get revision patch`() {
        val url = "http://localhost:$port/a/changes/${testChange.id}/revisions/abc123/patch"
        val response = restTemplate.getForEntity(url, String::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertTrue(response.body!!.contains("From abc123def456"))
        assertTrue(response.body!!.contains("Subject: [PATCH] Test change for files"))
        assertTrue(response.body!!.contains("Change-Id: ${testChange.changeKey}"))
    }

    @Test
    fun `test get revision patch - with zip`() {
        val url = "http://localhost:$port/a/changes/${testChange.id}/revisions/abc123/patch?zip=true"
        val response = restTemplate.getForEntity(url, String::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
    }

    @Test
    fun `test files endpoints - non-existent change`() {
        val url = "http://localhost:$port/a/changes/999999/revisions/abc123/files"
        val response = restTemplate.getForEntity(url, String::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `test files endpoints - non-existent revision`() {
        val url = "http://localhost:$port/a/changes/${testChange.id}/revisions/nonexistent/files"
        val response = restTemplate.getForEntity(url, String::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `test file content - non-existent change`() {
        val url = "http://localhost:$port/a/changes/999999/revisions/abc123/files/content?path=src/main/kotlin/Example.kt"
        val response = restTemplate.getForEntity(url, String::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `test file diff - non-existent change`() {
        val url = "http://localhost:$port/a/changes/999999/revisions/abc123/files/diff?path=src/main/kotlin/Example.kt"
        val response = restTemplate.getForEntity(url, String::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `test revision patch - non-existent change`() {
        val url = "http://localhost:$port/a/changes/999999/revisions/abc123/patch"
        val response = restTemplate.getForEntity(url, String::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }
}
