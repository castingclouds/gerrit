package ai.fluxuate.gerrit.api.controller

import ai.fluxuate.gerrit.api.dto.*
import ai.fluxuate.gerrit.config.TestSecurityConfig
import ai.fluxuate.gerrit.model.UserEntity
import ai.fluxuate.gerrit.repository.AccountRepository
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.*
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import java.time.Instant

/**
 * Integration tests for AccountsController.
 * Uses live PostgreSQL database and full Spring Boot context with real HTTP calls.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestSecurityConfig::class)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AccountsControllerIntegrationTest {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var accountRepository: AccountRepository

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private lateinit var testUser: UserEntity
    private val baseUrl get() = "http://localhost:$port/a/accounts"

    @BeforeEach
    fun setUp() {
        // Clean up any existing test data
        accountRepository.deleteAll()
        
        // Create test user
        testUser = UserEntity(
            username = "testuser",
            fullName = "Test User",
            preferredEmail = "test@example.com",
            active = true,
            registeredOn = Instant.now(),
            preferences = mapOf(
                "changesPerPage" to 25,
                "theme" to "dark",
                "timezone" to "UTC"
            ),
            contactInfo = mapOf(
                "emails" to listOf(
                    mapOf("email" to "test@example.com", "preferred" to true),
                    mapOf("email" to "secondary@example.com", "preferred" to false)
                ),
                "sshKeys" to listOf(
                    mapOf(
                        "publicKey" to "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQ...",
                        "encodedKey" to "AAAAB3NzaC1yc2EAAAADAQABAAABAQ...",
                        "algorithm" to "ssh-rsa",
                        "comment" to "test@laptop",
                        "valid" to true
                    )
                )
            )
        )
        testUser = accountRepository.save(testUser)
    }

    @AfterEach
    fun tearDown() {
        accountRepository.deleteAll()
    }

    @Test
    fun `test query accounts`() {
        val response = restTemplate.exchange(
            "$baseUrl/",
            HttpMethod.GET,
            null,
            object : ParameterizedTypeReference<Map<String, AccountInfo>>() {}
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val accounts = response.body!!
        assertTrue(accounts.containsKey("testuser"))
        
        val account = accounts["testuser"]!!
        assertEquals(testUser.id.toLong(), account.accountId)
        assertEquals("Test User", account.name)
        assertEquals("test@example.com", account.email)
        assertEquals("testuser", account.username)
    }

    @Test
    fun `test query accounts with filters`() {
        val response = restTemplate.exchange(
            "$baseUrl/?q=test&active=true&n=10&S=0",
            HttpMethod.GET,
            null,
            object : ParameterizedTypeReference<Map<String, AccountInfo>>() {}
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val accounts = response.body!!
        assertTrue(accounts.containsKey("testuser"))
    }

    @Test
    fun `test get account by username`() {
        val response = restTemplate.getForEntity(
            "$baseUrl/testuser",
            AccountInfo::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val account = response.body!!
        assertEquals(testUser.id.toLong(), account.accountId)
        assertEquals("Test User", account.name)
        assertEquals("test@example.com", account.email)
        assertEquals("testuser", account.username)
    }

    @Test
    fun `test get account by email`() {
        val response = restTemplate.getForEntity(
            "$baseUrl/test@example.com",
            AccountInfo::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val account = response.body!!
        assertEquals(testUser.id.toLong(), account.accountId)
        assertEquals("testuser", account.username)
    }

    @Test
    fun `test get account by id`() {
        val response = restTemplate.getForEntity(
            "$baseUrl/${testUser.id}",
            AccountInfo::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val account = response.body!!
        assertEquals(testUser.id.toLong(), account.accountId)
        assertEquals("testuser", account.username)
    }

    @Test
    fun `test get account detail`() {
        val response = restTemplate.getForEntity(
            "$baseUrl/testuser/detail",
            AccountDetailInfo::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val account = response.body!!
        assertEquals(testUser.id, account.accountId)
        assertEquals("Test User", account.name)
        assertEquals("testuser", account.username)
        assertNotNull(account.registeredOn)
    }

    @Test
    fun `test create account`() {
        val input = AccountInput(
            name = "New User",
            email = "newuser@example.com"
        )

        val response = restTemplate.exchange(
            "$baseUrl/newuser",
            HttpMethod.PUT,
            HttpEntity(input),
            AccountInfo::class.java
        )

        assertEquals(HttpStatus.CREATED, response.statusCode)
        val account = response.body!!
        assertEquals("New User", account.name)
        assertEquals("newuser@example.com", account.email)
        assertEquals("newuser", account.username)

        // Verify in database
        val savedUser = accountRepository.findByUsername("newuser")
        assertTrue(savedUser.isPresent)
        assertEquals("New User", savedUser.get().fullName)
    }

    @Test
    fun `test create account with duplicate username returns conflict`() {
        val input = AccountInput(
            name = "Duplicate User",
            email = "duplicate@example.com"
        )

        val response = restTemplate.exchange(
            "$baseUrl/testuser", // existing username
            HttpMethod.PUT,
            HttpEntity(input),
            String::class.java
        )

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
    }

    @Test
    fun `test update account`() {
        val input = AccountInput(
            name = "Updated User",
            email = "updated@example.com"
        )

        val response = restTemplate.exchange(
            "$baseUrl/testuser",
            HttpMethod.POST,
            HttpEntity(input),
            AccountInfo::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val account = response.body!!
        assertEquals("Updated User", account.name)
        assertEquals("updated@example.com", account.email)
    }

    @Test
    fun `test get account name`() {
        val response = restTemplate.getForEntity(
            "$baseUrl/testuser/name",
            String::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("\"Test User\"", response.body)
    }

    @Test
    fun `test set account name`() {
        val input = AccountNameInput(name = "New Name")

        val response = restTemplate.exchange(
            "$baseUrl/testuser/name",
            HttpMethod.PUT,
            HttpEntity(input),
            String::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("\"New Name\"", response.body)
    }

    @Test
    fun `test delete account name`() {
        val response = restTemplate.exchange(
            "$baseUrl/testuser/name",
            HttpMethod.DELETE,
            null,
            Void::class.java
        )

        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)

        // Verify name is deleted
        val getResponse = restTemplate.getForEntity(
            "$baseUrl/testuser/name",
            String::class.java
        )
        assertEquals(HttpStatus.NO_CONTENT, getResponse.statusCode)
    }

    @Test
    fun `test get account status`() {
        // First set a status
        val statusInput = AccountStatusInput(status = "Away")
        restTemplate.exchange(
            "$baseUrl/testuser/status",
            HttpMethod.PUT,
            HttpEntity(statusInput),
            String::class.java
        )

        val response = restTemplate.getForEntity(
            "$baseUrl/testuser/status",
            String::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("\"Away\"", response.body)
    }

    @Test
    fun `test set account status`() {
        val input = AccountStatusInput(status = "Busy")

        val response = restTemplate.exchange(
            "$baseUrl/testuser/status",
            HttpMethod.PUT,
            HttpEntity(input),
            String::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("\"Busy\"", response.body)
    }

    @Test
    fun `test get account active state`() {
        val response = restTemplate.getForEntity(
            "$baseUrl/testuser/active",
            String::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("\"ok\"", response.body)
    }

    @Test
    fun `test set account active`() {
        val response = restTemplate.exchange(
            "$baseUrl/testuser/active",
            HttpMethod.PUT,
            null,
            String::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("\"ok\"", response.body)
    }

    @Test
    fun `test set account inactive`() {
        val response = restTemplate.exchange(
            "$baseUrl/testuser/active",
            HttpMethod.DELETE,
            null,
            Void::class.java
        )

        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)

        // Verify account is inactive
        val getResponse = restTemplate.getForEntity(
            "$baseUrl/testuser/active",
            String::class.java
        )
        assertEquals(HttpStatus.NO_CONTENT, getResponse.statusCode)
    }

    @Test
    fun `test get username`() {
        val response = restTemplate.getForEntity(
            "$baseUrl/testuser/username",
            String::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("\"testuser\"", response.body)
    }

    @Test
    fun `test set username returns method not allowed`() {
        val response = restTemplate.exchange(
            "$baseUrl/testuser/username",
            HttpMethod.PUT,
            HttpEntity("newusername"),
            String::class.java
        )

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.statusCode)
        assertEquals("Username cannot be changed.", response.body)
    }

    @Test
    fun `test get account emails`() {
        val response = restTemplate.exchange(
            "$baseUrl/testuser/emails/",
            HttpMethod.GET,
            null,
            object : ParameterizedTypeReference<List<EmailInfo>>() {}
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val emails = response.body!!
        assertTrue(emails.any { it.email == "test@example.com" && it.preferred == true })
        assertTrue(emails.any { it.email == "secondary@example.com" && it.preferred == false })
    }

    @Test
    fun `test add account email`() {
        val input = EmailInput(email = "new@example.com", preferred = false)

        val response = restTemplate.exchange(
            "$baseUrl/testuser/emails/new@example.com",
            HttpMethod.PUT,
            HttpEntity(input),
            EmailInfo::class.java
        )

        assertEquals(HttpStatus.CREATED, response.statusCode)
        val email = response.body!!
        assertEquals("new@example.com", email.email)
        assertEquals(false, email.preferred)
    }

    @Test
    fun `test get account ssh keys`() {
        val response = restTemplate.exchange(
            "$baseUrl/testuser/sshkeys/",
            HttpMethod.GET,
            null,
            object : ParameterizedTypeReference<List<SshKeyInfo>>() {}
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val sshKeys = response.body!!
        assertEquals(1, sshKeys.size)
        assertEquals("ssh-rsa", sshKeys[0].algorithm)
        assertEquals("test@laptop", sshKeys[0].comment)
    }

    @Test
    fun `test add account ssh key`() {
        val input = AddSshKeyInput(
            sshPublicKey = "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABgQDNew... newkey@example.com"
        )

        val response = restTemplate.exchange(
            "$baseUrl/testuser/sshkeys/",
            HttpMethod.POST,
            HttpEntity(input),
            SshKeyInfo::class.java
        )

        assertEquals(HttpStatus.CREATED, response.statusCode)
        val sshKey = response.body!!
        assertEquals("ssh-rsa", sshKey.algorithm)
        assertEquals("newkey@example.com", sshKey.comment)
    }

    @Test
    fun `test get account preferences`() {
        val response = restTemplate.getForEntity(
            "$baseUrl/testuser/preferences",
            PreferencesInfo::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val preferences = response.body!!
        assertEquals(25, preferences.changesPerPage)
        assertEquals(Theme.DARK, preferences.theme)
    }

    @Test
    fun `test set account preferences`() {
        val input = PreferencesInfo(
            changesPerPage = 50,
            theme = Theme.DARK,
            emailStrategy = EmailStrategy.ENABLED
        )

        val response = restTemplate.exchange(
            "$baseUrl/testuser/preferences",
            HttpMethod.PUT,
            HttpEntity(input),
            PreferencesInfo::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val preferences = response.body!!
        assertEquals(50, preferences.changesPerPage)
        assertEquals(Theme.DARK, preferences.theme)
        assertEquals(EmailStrategy.ENABLED, preferences.emailStrategy)
    }

    @Test
    fun `test get account capabilities`() {
        val response = restTemplate.getForEntity(
            "$baseUrl/testuser/capabilities",
            CapabilityInfo::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val capabilities = response.body!!
        assertEquals(true, capabilities.createProject)
        assertEquals(false, capabilities.createAccount)
        assertEquals(false, capabilities.administrateServer)
    }

    @Test
    fun `test check specific capability`() {
        val response = restTemplate.getForEntity(
            "$baseUrl/testuser/capabilities/createProject",
            String::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("\"ok\"", response.body)
    }

    @Test
    fun `test check missing capability`() {
        val response = restTemplate.getForEntity(
            "$baseUrl/testuser/capabilities/administrateServer",
            String::class.java
        )

        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
    }

    @Test
    fun `test get account groups returns empty list`() {
        val response = restTemplate.exchange(
            "$baseUrl/testuser/groups",
            HttpMethod.GET,
            null,
            object : ParameterizedTypeReference<List<Map<String, Any>>>() {}
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val groups = response.body!!
        assertTrue(groups.isEmpty())
    }

    @Test
    fun `test avatar endpoints return not found`() {
        val avatarResponse = restTemplate.getForEntity(
            "$baseUrl/testuser/avatar",
            String::class.java
        )
        assertEquals(HttpStatus.NOT_FOUND, avatarResponse.statusCode)

        val avatarUrlResponse = restTemplate.getForEntity(
            "$baseUrl/testuser/avatar.change.url",
            String::class.java
        )
        assertEquals(HttpStatus.NOT_FOUND, avatarUrlResponse.statusCode)
    }

    @Test
    fun `test oauth token endpoint returns not found`() {
        val response = restTemplate.getForEntity(
            "$baseUrl/testuser/oauthtoken",
            String::class.java
        )
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `test get account not found`() {
        val response = restTemplate.getForEntity(
            "$baseUrl/nonexistent",
            String::class.java
        )
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `test delete account`() {
        val response = restTemplate.exchange(
            "$baseUrl/testuser",
            HttpMethod.DELETE,
            null,
            Void::class.java
        )

        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)

        // Verify account is deleted
        val getResponse = restTemplate.getForEntity(
            "$baseUrl/testuser",
            String::class.java
        )
        assertEquals(HttpStatus.NOT_FOUND, getResponse.statusCode)
    }
}
