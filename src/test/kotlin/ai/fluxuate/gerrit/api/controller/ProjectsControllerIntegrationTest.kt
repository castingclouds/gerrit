package ai.fluxuate.gerrit.api.controller

import ai.fluxuate.gerrit.api.dto.*
import ai.fluxuate.gerrit.config.TestSecurityConfig
import ai.fluxuate.gerrit.model.ProjectEntity
import ai.fluxuate.gerrit.model.ProjectState
import ai.fluxuate.gerrit.repository.ProjectEntityRepository
import ai.fluxuate.gerrit.service.AccountService
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.AfterEach
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

/**
 * Integration test for Projects REST API endpoints.
 * Uses live PostgreSQL database and full Spring Boot context with real HTTP calls.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestSecurityConfig::class)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ProjectsControllerIntegrationTest {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var projectRepository: ProjectEntityRepository

    @Autowired
    private lateinit var accountService: AccountService

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private var testAccountId: Long = 0

    private fun baseUrl() = "http://localhost:$port/a/projects"

    @BeforeEach
    fun setUp() {
        // Clean up any existing test data
        projectRepository.deleteAll()
        
        // Create test user with dynamic account creation for authentication
        val accountInfo = accountService.createAccount("testuser", AccountInput(
            name = "Test User",
            email = "test@example.com"
        ))
        testAccountId = accountInfo._account_id
        
        // Configure TestRestTemplate with Basic Auth
        restTemplate = restTemplate.withBasicAuth("testuser", "password")
    }

    @AfterEach
    fun cleanUp() {
        // Clean up any test data created during the test
        projectRepository.deleteAll()
    }

    @Test
    fun `should query projects with empty result`() {
        // When
        val response = restTemplate.getForEntity("${baseUrl()}/", Map::class.java)

        // Then
        assert(response.statusCode == HttpStatus.OK) { "Expected OK but got ${response.statusCode}" }
        assert(response.body is Map<*, *>) { "Expected Map but got ${response.body?.javaClass}" }
        assert((response.body as Map<*, *>).isEmpty()) { "Expected empty map but got ${response.body}" }
    }

    @Test
    fun `should query projects with existing data`() {
        // Given
        val project1 = ProjectEntity(
            name = "test-project-1",
            description = "Test Project 1",
            state = ProjectState.ACTIVE,
            config = mapOf("submit_type" to "MERGE_IF_NECESSARY"),
            metadata = mapOf("created_by" to "admin")
        )
        val project2 = ProjectEntity(
            name = "test-project-2",
            description = "Test Project 2",
            state = ProjectState.READ_ONLY,
            parentName = "test-project-1",
            config = mapOf("submit_type" to "REBASE_IF_NECESSARY"),
            metadata = mapOf("created_by" to "user")
        )
        projectRepository.saveAll(listOf(project1, project2))

        // When
        val response = restTemplate.getForEntity("${baseUrl()}/", Map::class.java)

        // Then
        assert(response.statusCode == HttpStatus.OK)
        val projects = response.body as Map<String, Any>
        assert(projects.containsKey("test-project-1"))
        assert(projects.containsKey("test-project-2"))
        
        val project1Data = projects["test-project-1"] as Map<String, Any>
        assert(project1Data["name"] == "test-project-1")
        assert(project1Data["description"] == "Test Project 1")
        assert(project1Data["state"] == "ACTIVE")
        
        val project2Data = projects["test-project-2"] as Map<String, Any>
        assert(project2Data["name"] == "test-project-2")
        assert(project2Data["parent"] == "test-project-1")
        assert(project2Data["state"] == "READ_ONLY")
    }

    @Test
    fun `should get specific project`() {
        // Given
        val project = ProjectEntity(
            name = "test-project",
            description = "Test Project Description",
            state = ProjectState.ACTIVE,
            config = mapOf("submit_type" to "MERGE_IF_NECESSARY"),
            metadata = mapOf("created_by" to "admin")
        )
        projectRepository.save(project)

        // When
        val response = restTemplate.getForEntity("${baseUrl()}/test-project", ProjectInfo::class.java)

        // Then
        assert(response.statusCode == HttpStatus.OK)
        val projectInfo = response.body!!
        assert(projectInfo.name == "test-project")
        assert(projectInfo.description == "Test Project Description")
        assert(projectInfo.state == ai.fluxuate.gerrit.api.dto.ProjectState.ACTIVE)
    }

    @Test
    fun `should return 404 for non-existent project`() {
        // When
        val response = restTemplate.getForEntity("${baseUrl()}/non-existent", String::class.java)

        // Then
        assert(response.statusCode == HttpStatus.NOT_FOUND)
    }

    @Test
    fun `should create new project`() {
        // Given
        val timestamp = System.currentTimeMillis()
        val projectInput = ProjectInput(
            name = "new-project-$timestamp",
            description = "New Project Description",
            createEmptyCommit = true,
            state = ai.fluxuate.gerrit.api.dto.ProjectState.ACTIVE
        )

        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON

        // First, make a raw request to see what we get
        val rawResponse = restTemplate.exchange(
            "${baseUrl()}/new-project-$timestamp",
            HttpMethod.PUT,
            HttpEntity(projectInput, headers),
            String::class.java
        )
        
        println("Raw response status: ${rawResponse.statusCode}")
        println("Raw response headers: ${rawResponse.headers}")
        println("Raw response body: ${rawResponse.body}")
        
        // If we get here, the String response worked, now try ProjectInfo
        val response = restTemplate.exchange(
            "${baseUrl()}/new-project-$timestamp-2",
            HttpMethod.PUT,
            HttpEntity(projectInput.copy(name = "new-project-$timestamp-2"), headers),
            ProjectInfo::class.java
        )

        // Then
        assert(response.statusCode == HttpStatus.CREATED)
        val projectInfo = response.body!!
        assert(projectInfo.name == "new-project-$timestamp-2")
        assert(projectInfo.description == "New Project Description")
        assert(projectInfo.state == ai.fluxuate.gerrit.api.dto.ProjectState.ACTIVE)
    }

    @Test
    fun `should create project with parent`() {
        // Given - Create parent project first
        val timestamp = System.currentTimeMillis()
        val parentInput = ProjectInput(
            name = "parent-project-$timestamp",
            description = "Parent Project",
            createEmptyCommit = true,
            state = ai.fluxuate.gerrit.api.dto.ProjectState.ACTIVE
        )

        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON

        // Create parent project
        val parentResponse = restTemplate.exchange(
            "${baseUrl()}/parent-project-$timestamp",
            HttpMethod.PUT,
            HttpEntity(parentInput, headers),
            ProjectInfo::class.java
        )
        assert(parentResponse.statusCode == HttpStatus.CREATED)

        // Create child project
        val projectInput = ProjectInput(
            name = "child-project-$timestamp",
            parent = "parent-project-$timestamp",
            description = "Child Project",
            state = ai.fluxuate.gerrit.api.dto.ProjectState.ACTIVE
        )

        val request = HttpEntity(projectInput, headers)

        // When
        val response = restTemplate.exchange(
            "${baseUrl()}/child-project-$timestamp",
            HttpMethod.PUT,
            request,
            ProjectInfo::class.java
        )

        // Then
        assert(response.statusCode == HttpStatus.CREATED)
        val projectInfo = response.body!!
        assert(projectInfo.name == "child-project-$timestamp")
        assert(projectInfo.parent == "parent-project-$timestamp")
        assert(projectInfo.description == "Child Project")
        assert(projectInfo.state == ai.fluxuate.gerrit.api.dto.ProjectState.ACTIVE)
    }

    @Test
    fun `should update project configuration`() {
        // Given
        val project = ProjectEntity(
            name = "test-project",
            description = "Original Description",
            state = ProjectState.ACTIVE,
            config = mapOf("submit_type" to "MERGE_IF_NECESSARY"),
            metadata = mapOf()
        )
        projectRepository.save(project)

        val configInput = ConfigInput(
            description = "Updated Description",
            submitType = SubmitType.REBASE_IF_NECESSARY,
            state = ai.fluxuate.gerrit.api.dto.ProjectState.READ_ONLY
        )

        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        val request = HttpEntity(configInput, headers)

        // When
        val response = restTemplate.exchange(
            "${baseUrl()}/test-project/config",
            HttpMethod.PUT,
            request,
            ProjectInfo::class.java
        )

        // Then
        assert(response.statusCode == HttpStatus.OK)
        val projectInfo = response.body!!
        assert(projectInfo.description == "Updated Description")
        assert(projectInfo.state == ai.fluxuate.gerrit.api.dto.ProjectState.READ_ONLY)

        // Verify changes were saved
        val updatedProject = projectRepository.findByName("test-project")
        assert(updatedProject?.description == "Updated Description")
        assert(updatedProject?.state == ProjectState.READ_ONLY)
    }

    @Test
    fun `should delete project`() {
        // Given
        val project = ProjectEntity(
            name = "project-to-delete",
            description = "Project to be deleted",
            state = ProjectState.ACTIVE,
            config = mapOf(),
            metadata = mapOf()
        )
        projectRepository.save(project)

        // When
        val response = restTemplate.exchange(
            "${baseUrl()}/project-to-delete",
            HttpMethod.DELETE,
            null,
            Void::class.java
        )

        // Then
        assert(response.statusCode == HttpStatus.NO_CONTENT)

        // Verify project was deleted
        val deletedProject = projectRepository.findByName("project-to-delete")
        assert(deletedProject == null)
    }

    @Test
    fun `should prevent deletion of project with children`() {
        // Given
        val parentProject = ProjectEntity(
            name = "parent-project",
            state = ProjectState.ACTIVE,
            config = mapOf(),
            metadata = mapOf()
        )
        val childProject = ProjectEntity(
            name = "child-project",
            parentName = "parent-project",
            state = ProjectState.ACTIVE,
            config = mapOf(),
            metadata = mapOf()
        )
        projectRepository.saveAll(listOf(parentProject, childProject))

        // When
        val response = restTemplate.exchange(
            "${baseUrl()}/parent-project",
            HttpMethod.DELETE,
            null,
            String::class.java
        )

        // Then
        assert(response.statusCode == HttpStatus.CONFLICT)

        // Verify parent project still exists
        val parentStillExists = projectRepository.findByName("parent-project")
        assert(parentStillExists != null)
    }

    @Test
    fun `should get and set project description`() {
        // Given
        val project = ProjectEntity(
            name = "test-project",
            description = "Original Description",
            state = ProjectState.ACTIVE,
            config = mapOf(),
            metadata = mapOf()
        )
        projectRepository.save(project)

        // When & Then - Get description
        val getResponse = restTemplate.getForEntity("${baseUrl()}/test-project/description", String::class.java)
        assert(getResponse.statusCode == HttpStatus.OK)
        assert(getResponse.body == "Original Description")

        // When & Then - Set description
        val descriptionInput = DescriptionInput(description = "New Description")
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        val request = HttpEntity(descriptionInput, headers)

        val putResponse = restTemplate.exchange(
            "${baseUrl()}/test-project/description",
            HttpMethod.PUT,
            request,
            String::class.java
        )
        assert(putResponse.statusCode == HttpStatus.OK)
        assert(putResponse.body == "New Description")

        // Verify description was updated
        val updatedProject = projectRepository.findByName("test-project")
        assert(updatedProject?.description == "New Description")
    }

    @Test
    fun `should get child projects`() {
        // Given
        val parentProject = ProjectEntity(
            name = "parent-project",
            description = "Parent project",
            parentName = null,
            state = ProjectState.ACTIVE,
            config = mapOf(),
            metadata = mapOf()
        )
        val child1 = ProjectEntity(
            name = "child-1",
            description = "Child 1",
            parentName = "parent-project",
            state = ProjectState.ACTIVE,
            config = mapOf(),
            metadata = mapOf()
        )
        val child2 = ProjectEntity(
            name = "child-2",
            description = "Child 2",
            parentName = "parent-project",
            state = ProjectState.ACTIVE,
            config = mapOf(),
            metadata = mapOf()
        )
        projectRepository.saveAll(listOf(parentProject, child1, child2))

        // When
        val response = restTemplate.getForEntity("${baseUrl()}/parent-project/children", String::class.java)

        // Then
        assert(response.statusCode == HttpStatus.OK)
        val responseBody = response.body!!
        
        // Verify the response is a JSON array containing the expected child projects
        assert(responseBody.contains("\"name\":\"child-1\""))
        assert(responseBody.contains("\"name\":\"child-2\""))
        assert(responseBody.contains("\"parent\":\"parent-project\""))
        assert(responseBody.startsWith("[") && responseBody.endsWith("]")) // JSON array format
    }

    @Test
    fun `should handle validation errors`() {
        // Given - invalid project input
        val invalidInput = ProjectInput(
            name = "", // Invalid empty name
            description = "Test Project"
        )

        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        val request = HttpEntity(invalidInput, headers)

        // When
        val response = restTemplate.exchange(
            "${baseUrl()}/invalid-project",
            HttpMethod.PUT,
            request,
            String::class.java
        )

        // Then
        assert(response.statusCode == HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `debug create project response`() {
        try {
            // Given - use timestamp to ensure unique project name
            val timestamp = System.currentTimeMillis()
            val projectInput = ProjectInput(
                name = "debug-project-$timestamp",
                description = "Debug Project",
                createEmptyCommit = true,
                state = ai.fluxuate.gerrit.api.dto.ProjectState.ACTIVE
            )

            val headers = HttpHeaders()
            headers.contentType = MediaType.APPLICATION_JSON
            val request = HttpEntity(projectInput, headers)

            println("=== DEBUG TEST START ===")
            println("Making request to: ${baseUrl()}/debug-project-$timestamp")
            println("Request body: $projectInput")
            
            // Make request as String first to see raw response
            val rawResponse = restTemplate.exchange(
                "${baseUrl()}/debug-project-$timestamp",
                HttpMethod.PUT,
                request,
                String::class.java
            )
            
            println("Raw response status: ${rawResponse.statusCode}")
            println("Raw response headers: ${rawResponse.headers}")
            println("Raw response body: '${rawResponse.body}'")
            println("Response body length: ${rawResponse.body?.length}")
            println("=== DEBUG TEST END ===")
            
            // Basic assertions with detailed error messages
            assert(rawResponse.statusCode == HttpStatus.CREATED) { 
                "Expected CREATED (201) but got ${rawResponse.statusCode}. Response body: ${rawResponse.body}" 
            }
            assert(rawResponse.body != null) { 
                "Response body is null" 
            }
            assert(rawResponse.body!!.isNotEmpty()) { 
                "Response body is empty" 
            }
        } catch (e: Exception) {
            println("=== EXCEPTION IN DEBUG TEST ===")
            println("Exception type: ${e.javaClass.simpleName}")
            println("Exception message: ${e.message}")
            if (e.cause != null) {
                println("Cause: ${e.cause!!.javaClass.simpleName}: ${e.cause!!.message}")
            }
            e.printStackTrace()
            throw e
        }
    }
}
