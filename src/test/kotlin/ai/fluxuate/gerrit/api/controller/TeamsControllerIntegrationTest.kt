package ai.fluxuate.gerrit.api.controller

import ai.fluxuate.gerrit.api.dto.AccountInput
import ai.fluxuate.gerrit.api.dto.TeamInfo
import ai.fluxuate.gerrit.api.dto.TeamInput
import ai.fluxuate.gerrit.api.dto.MembersInput
import ai.fluxuate.gerrit.api.dto.AccountInfo
import ai.fluxuate.gerrit.config.TestSecurityConfig
import ai.fluxuate.gerrit.model.TeamEntity
import ai.fluxuate.gerrit.model.UserEntity
import ai.fluxuate.gerrit.repository.TeamRepository
import ai.fluxuate.gerrit.repository.UserEntityRepository
import ai.fluxuate.gerrit.service.AccountService
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import java.time.Instant

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestSecurityConfig::class)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class TeamsControllerIntegrationTest {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var teamRepository: TeamRepository

    @Autowired
    private lateinit var userRepository: UserEntityRepository

    @Autowired
    private lateinit var accountService: AccountService

    private lateinit var baseUrl: String
    private lateinit var testUser1: UserEntity
    private lateinit var testUser2: UserEntity
    private lateinit var testTeam: TeamEntity
    private var testAccountId: Long = 0

    @BeforeEach
    fun setUp() {
        baseUrl = "http://localhost:$port/a/teams"
        
        // Clean up any existing test data
        teamRepository.deleteAll()
        userRepository.deleteAll()
        
        // Create test user with dynamic account creation for authentication
        val accountInfo = accountService.createAccount("testuser", AccountInput(
            name = "Test User",
            email = "test@example.com"
        ))
        testAccountId = accountInfo._account_id
        
        // Configure TestRestTemplate with Basic Auth
        restTemplate = restTemplate.withBasicAuth("testuser", "password")
        
        // Create test users for team operations
        testUser1 = UserEntity(
            username = "testuser1",
            fullName = "Test User 1",
            preferredEmail = "test1@example.com",
            active = true,
            registeredOn = Instant.now()
        )
        testUser1 = userRepository.save(testUser1)

        testUser2 = UserEntity(
            username = "testuser2",
            fullName = "Test User 2",
            preferredEmail = "test2@example.com",
            active = true,
            registeredOn = Instant.now()
        )
        testUser2 = userRepository.save(testUser2)

        // Create test team
        testTeam = TeamEntity(
            name = "test-team",
            description = "Test team for integration tests",
            visibleToAll = true,
            members = mutableListOf(testUser1.id.toString())
        )
        testTeam = teamRepository.save(testTeam)
    }

    @AfterEach
    fun tearDown() {
        teamRepository.deleteAll()
        userRepository.deleteAll()
    }

    @Test
    fun `test query teams`() {
        val response = restTemplate.exchange(
            "$baseUrl/",
            HttpMethod.GET,
            null,
            object : ParameterizedTypeReference<Map<String, TeamInfo>>() {}
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val teams = response.body!!
        assertTrue(teams.containsKey("test-team"))
        assertEquals("test-team", teams["test-team"]?.name)
    }

    @Test
    fun `test get team by name`() {
        val response = restTemplate.getForEntity(
            "$baseUrl/test-team",
            TeamInfo::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val team = response.body!!
        assertEquals("test-team", team.name)
        assertEquals("Test team for integration tests", team.description)
        assertTrue(team.options?.visibleToAll == true)
    }

    @Test
    fun `test create team`() {
        val teamInput = TeamInput(
            description = "New test team",
            visibleToAll = true,
            members = listOf(testUser1.id.toString(), testUser2.id.toString())
        )

        val response = restTemplate.postForEntity(
            "$baseUrl/new-team",
            teamInput,
            TeamInfo::class.java
        )

        assertEquals(HttpStatus.CREATED, response.statusCode)
        val team = response.body!!
        assertEquals("new-team", team.name)
        assertEquals("New test team", team.description)
        assertTrue(team.options?.visibleToAll == true)
    }

    @Test
    fun `test create team with duplicate name returns conflict`() {
        val teamInput = TeamInput(
            description = "Duplicate team"
        )

        val response = restTemplate.postForEntity(
            "$baseUrl/test-team",
            teamInput,
            String::class.java
        )

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
    }

    @Test
    fun `test update team`() {
        val teamInput = TeamInput(
            description = "Updated description",
            visibleToAll = false
        )

        val response = restTemplate.exchange(
            "$baseUrl/${testTeam.id}",
            HttpMethod.PUT,
            HttpEntity(teamInput),
            TeamInfo::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val team = response.body!!
        assertEquals("Updated description", team.description)
        assertEquals(false, team.options?.visibleToAll)
    }

    @Test
    fun `test delete team`() {
        val response = restTemplate.exchange(
            "$baseUrl/${testTeam.id}",
            HttpMethod.DELETE,
            null,
            Void::class.java
        )

        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
        
        // Verify team is deleted
        val getResponse = restTemplate.getForEntity(
            "$baseUrl/${testTeam.id}",
            String::class.java
        )
        assertEquals(HttpStatus.NOT_FOUND, getResponse.statusCode)
    }

    @Test
    fun `test get team detail`() {
        val response = restTemplate.getForEntity(
            "$baseUrl/${testTeam.id}/detail",
            TeamInfo::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val team = response.body!!
        assertEquals("test-team", team.name)
        assertNotNull(team.members)
        assertEquals(1, team.members?.size)
    }

    @Test
    fun `test get team members`() {
        val response = restTemplate.exchange(
            "$baseUrl/${testTeam.id}/members/",
            HttpMethod.GET,
            null,
            object : ParameterizedTypeReference<List<AccountInfo>>() {}
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val members = response.body!!
        assertEquals(1, members.size)
        assertEquals("testuser1", members[0].username)
    }

    @Test
    fun `test add team member`() {
        val response = restTemplate.exchange(
            "$baseUrl/${testTeam.id}/members/${testUser2.id}",
            HttpMethod.PUT,
            null,
            AccountInfo::class.java
        )

        assertEquals(HttpStatus.CREATED, response.statusCode)
        val member = response.body!!
        assertEquals("testuser2", member.username)
    }

    @Test
    fun `test add team members`() {
        val membersInput = MembersInput(
            members = listOf(testUser2.id.toString())
        )

        val response = restTemplate.exchange(
            "$baseUrl/${testTeam.id}/members",
            HttpMethod.POST,
            HttpEntity(membersInput),
            object : ParameterizedTypeReference<List<AccountInfo>>() {}
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val members = response.body!!
        assertEquals(1, members.size)
        assertEquals("testuser2", members[0].username)
    }

    @Test
    fun `test remove team member`() {
        val response = restTemplate.exchange(
            "$baseUrl/${testTeam.id}/members/${testUser1.id}",
            HttpMethod.DELETE,
            null,
            Void::class.java
        )

        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
    }

    @Test
    fun `test get team subteams`() {
        val response = restTemplate.exchange(
            "$baseUrl/${testTeam.id}/teams/",
            HttpMethod.GET,
            null,
            object : ParameterizedTypeReference<List<TeamInfo>>() {}
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val subteams = response.body!!
        assertEquals(0, subteams.size)
    }

    @Test
    fun `test team not found`() {
        val response = restTemplate.getForEntity(
            "$baseUrl/nonexistent-team",
            String::class.java
        )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }
}
