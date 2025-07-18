package ai.fluxuate.gerrit.repository

import ai.fluxuate.gerrit.model.ApprovalEntity
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
class ApprovalRepositoryTest {

    @Autowired
    private lateinit var approvalRepository: ApprovalRepository

    private lateinit var codeReviewPlus2: ApprovalEntity
    private lateinit var codeReviewMinus1: ApprovalEntity
    private lateinit var verifiedPlus1: ApprovalEntity
    private lateinit var copiedApproval: ApprovalEntity
    private lateinit var impersonatedApproval: ApprovalEntity

    @BeforeEach
    fun setUp() {
        approvalRepository.deleteAll()

        val now = Instant.now()
        
        codeReviewPlus2 = ApprovalEntity(
            changeId = 1001L,
            patchSetId = 2001L,
            userId = 100L,
            label = "Code-Review",
            value = 2,
            granted = now.minusSeconds(3600),
            tag = "manual"
        )

        codeReviewMinus1 = ApprovalEntity(
            changeId = 1001L,
            patchSetId = 2001L,
            userId = 101L,
            label = "Code-Review",
            value = -1,
            granted = now.minusSeconds(2700),
            tag = "manual"
        )

        verifiedPlus1 = ApprovalEntity(
            changeId = 1001L,
            patchSetId = 2001L,
            userId = 102L,
            label = "Verified",
            value = 1,
            granted = now.minusSeconds(1800),
            tag = "ci"
        )

        copiedApproval = ApprovalEntity(
            changeId = 1001L,
            patchSetId = 2002L,
            userId = 100L,
            label = "Code-Review",
            value = 2,
            granted = now.minusSeconds(900),
            copied = true,
            tag = "copied"
        )

        impersonatedApproval = ApprovalEntity(
            changeId = 1002L,
            patchSetId = 2003L,
            userId = 103L,
            realUserId = 104L,
            label = "Code-Review",
            value = 1,
            granted = now.minusSeconds(600),
            tag = "impersonated"
        )

        codeReviewPlus2 = approvalRepository.save(codeReviewPlus2)
        codeReviewMinus1 = approvalRepository.save(codeReviewMinus1)
        verifiedPlus1 = approvalRepository.save(verifiedPlus1)
        copiedApproval = approvalRepository.save(copiedApproval)
        impersonatedApproval = approvalRepository.save(impersonatedApproval)
    }

    @Test
    fun `should find approvals by change ID ordered by granted date`() {
        val approvals = approvalRepository.findByChangeIdOrderByGrantedAsc(1001L)
        
        assertEquals(4, approvals.size)
        assertEquals(codeReviewPlus2.id, approvals[0].id)
        assertEquals(copiedApproval.id, approvals[3].id)
    }

    @Test
    fun `should find approvals by patch set ID`() {
        val approvals = approvalRepository.findByPatchSetIdOrderByGrantedAsc(2001L)
        
        assertEquals(3, approvals.size)
    }

    @Test
    fun `should find approvals by user`() {
        val pageable = PageRequest.of(0, 10)
        val approvals = approvalRepository.findByUserIdOrderByGrantedDesc(100L, pageable)
        
        assertEquals(2, approvals.totalElements)
        assertEquals(copiedApproval.id, approvals.content[0].id) // Most recent first
    }

    @Test
    fun `should find approvals for specific label on change`() {
        val approvals = approvalRepository.findByChangeIdAndLabelOrderByGrantedDesc(1001L, "Code-Review")
        
        assertEquals(3, approvals.size)
        assertEquals("Code-Review", approvals[0].label)
    }

    @Test
    fun `should find latest approval for user and label`() {
        val approval = approvalRepository.findTopByChangeIdAndUserIdAndLabelOrderByGrantedDesc(
            1001L, 100L, "Code-Review"
        )
        
        assertNotNull(approval)
        assertEquals(copiedApproval.id, approval!!.id) // Latest one
    }

    @Test
    fun `should find positive approvals`() {
        val approvals = approvalRepository.findPositiveApprovals(1001L)
        
        assertEquals(3, approvals.size) // +2, +1, copied +2
        assertTrue(approvals.all { it.value > 0 })
    }

    @Test
    fun `should find negative approvals`() {
        val approvals = approvalRepository.findNegativeApprovals(1001L)
        
        assertEquals(1, approvals.size)
        assertEquals(codeReviewMinus1.id, approvals[0].id)
    }

    @Test
    fun `should find approvals by value range`() {
        val approvals = approvalRepository.findByValueRange(1001L, 1, 2)
        
        assertEquals(3, approvals.size) // +2, +1, copied +2
        assertTrue(approvals.all { it.value >= 1 && it.value <= 2 })
    }

    @Test
    fun `should find copied approvals`() {
        val approvals = approvalRepository.findByChangeIdAndCopiedTrueOrderByGrantedDesc(1001L)
        
        assertEquals(1, approvals.size)
        assertEquals(copiedApproval.id, approvals[0].id)
    }

    @Test
    fun `should find impersonated approvals`() {
        val approvals = approvalRepository.findImpersonatedApprovals(1002L)
        
        assertEquals(1, approvals.size)
        assertEquals(impersonatedApproval.id, approvals[0].id)
    }

    @Test
    fun `should count approvals for change`() {
        val count = approvalRepository.countByChangeId(1001L)
        assertEquals(4, count)
    }

    @Test
    fun `should count approvals by label`() {
        val count = approvalRepository.countByChangeIdAndLabel(1001L, "Code-Review")
        assertEquals(3, count)
    }

    @Test
    fun `should check if change has required approvals`() {
        val hasCodeReviewPlus2 = approvalRepository.hasRequiredApprovals(1001L, "Code-Review", 2)
        val hasVerifiedPlus2 = approvalRepository.hasRequiredApprovals(1001L, "Verified", 2)
        
        assertTrue(hasCodeReviewPlus2)
        assertTrue(!hasVerifiedPlus2)
    }

    @Test
    fun `should find approvals by tag`() {
        val pageable = PageRequest.of(0, 10)
        val approvals = approvalRepository.findByTagOrderByGrantedDesc("manual", pageable)
        
        assertEquals(2, approvals.totalElements)
    }

    @Test
    fun `should identify approval types correctly`() {
        assertTrue(codeReviewPlus2.isPositive)
        assertTrue(!codeReviewPlus2.isNegative)
        assertTrue(!codeReviewPlus2.isNeutral)
        
        assertTrue(!codeReviewMinus1.isPositive)
        assertTrue(codeReviewMinus1.isNegative)
        assertTrue(!codeReviewMinus1.isNeutral)
        
        assertTrue(impersonatedApproval.isImpersonated) // realUserId (104) != userId (103)
        assertEquals(104L, impersonatedApproval.effectiveUserId)
    }

    @Test
    fun `should find approvals by date range`() {
        val now = Instant.now()
        val startDate = now.minusSeconds(7200)
        val endDate = now.minusSeconds(300)
        val pageable = PageRequest.of(0, 10)
        
        val approvals = approvalRepository.findByGrantedBetween(startDate, endDate, pageable)
        
        assertEquals(5, approvals.totalElements)
    }

    @Test
    fun `should find latest approvals by user`() {
        val approvals = approvalRepository.findLatestApprovalsByUser(1001L)
        
        // Should find latest approval for each user-label combination
        assertTrue(approvals.isNotEmpty())
        // Each user should appear only once per label
        val userLabelPairs = approvals.map { "${it.userId}-${it.label}" }
        assertEquals(userLabelPairs.size, userLabelPairs.toSet().size)
    }
}
