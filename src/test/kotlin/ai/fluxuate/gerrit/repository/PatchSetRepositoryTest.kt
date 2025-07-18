package ai.fluxuate.gerrit.repository

import ai.fluxuate.gerrit.model.PatchSetEntity
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import org.springframework.data.domain.PageRequest
import java.time.Instant

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PatchSetRepositoryTest {

    @Autowired
    private lateinit var patchSetRepository: PatchSetRepository

    private lateinit var testPatchSet1: PatchSetEntity
    private lateinit var testPatchSet2: PatchSetEntity
    private lateinit var testPatchSet3: PatchSetEntity

    @BeforeEach
    fun setUp() {
        patchSetRepository.deleteAll()

        val now = Instant.now()
        
        testPatchSet1 = PatchSetEntity(
            changeId = 1001L,
            patchSetNumber = 1,
            commitId = "abc123def456",
            uploaderId = 100L,
            realUploaderId = 100L,
            createdOn = now.minusSeconds(3600),
            description = "Initial patch set",
            branch = "main",
            groups = listOf("group1", "group2"),
            fileModifications = listOf(
                mapOf("path" to "src/main/Main.java", "type" to "ADDED"),
                mapOf("path" to "README.md", "type" to "MODIFIED")
            )
        )

        testPatchSet2 = PatchSetEntity(
            changeId = 1001L,
            patchSetNumber = 2,
            commitId = "def456ghi789",
            uploaderId = 101L,
            realUploaderId = 101L,
            createdOn = now.minusSeconds(1800),
            description = "Updated patch set",
            branch = "main",
            groups = listOf("group1", "group3"),
            fileModifications = listOf(
                mapOf("path" to "src/main/Main.java", "type" to "MODIFIED")
            )
        )

        testPatchSet3 = PatchSetEntity(
            changeId = 1002L,
            patchSetNumber = 1,
            commitId = "ghi789jkl012",
            uploaderId = 102L,
            realUploaderId = 102L,
            createdOn = now.minusSeconds(900),
            description = "Different change patch set",
            branch = "feature",
            groups = listOf("group2", "group4")
        )

        testPatchSet1 = patchSetRepository.save(testPatchSet1)
        testPatchSet2 = patchSetRepository.save(testPatchSet2)
        testPatchSet3 = patchSetRepository.save(testPatchSet3)
    }

    @Test
    fun `should find patch sets by change ID ordered by patch set number`() {
        val patchSets = patchSetRepository.findByChangeIdOrderByPatchSetNumberAsc(1001L)
        
        assertEquals(2, patchSets.size)
        assertEquals(1, patchSets[0].patchSetNumber)
        assertEquals(2, patchSets[1].patchSetNumber)
    }

    @Test
    fun `should find latest patch set for change`() {
        val latestPatchSet = patchSetRepository.findTopByChangeIdOrderByPatchSetNumberDesc(1001L)
        
        assertNotNull(latestPatchSet)
        assertEquals(2, latestPatchSet!!.patchSetNumber)
        assertEquals("def456ghi789", latestPatchSet.commitId)
    }

    @Test
    fun `should find specific patch set by change ID and patch set number`() {
        val patchSet = patchSetRepository.findByChangeIdAndPatchSetNumber(1001L, 1)
        
        assertNotNull(patchSet)
        assertEquals("abc123def456", patchSet!!.commitId)
        assertEquals("Initial patch set", patchSet.description)
    }

    @Test
    fun `should find patch sets by uploader`() {
        val pageable = PageRequest.of(0, 10)
        val patchSets = patchSetRepository.findByUploaderIdOrderByCreatedOnDesc(100L, pageable)
        
        assertEquals(1, patchSets.totalElements)
        assertEquals(testPatchSet1.id, patchSets.content[0].id)
    }

    @Test
    fun `should find patch sets by commit ID`() {
        val patchSets = patchSetRepository.findByCommitId("abc123def456")
        
        assertEquals(1, patchSets.size)
        assertEquals(testPatchSet1.id, patchSets[0].id)
    }

    @Test
    fun `should count patch sets for change`() {
        val count = patchSetRepository.countByChangeId(1001L)
        assertEquals(2, count)
    }

    @Test
    fun `should generate correct ref name`() {
        val refName = testPatchSet1.refName
        assertEquals("refs/changes/01/1001/1", refName)
    }

    @Test
    fun `should identify initial patch set`() {
        assertTrue(testPatchSet1.isInitial)
        assertTrue(!testPatchSet2.isInitial)
    }

    @Test
    fun `should find patch sets by date range`() {
        val now = Instant.now()
        val startDate = now.minusSeconds(7200)
        val endDate = now.minusSeconds(600)
        val pageable = PageRequest.of(0, 10)
        
        val patchSets = patchSetRepository.findByCreatedOnBetween(startDate, endDate, pageable)
        
        assertEquals(3, patchSets.totalElements)
    }
}
