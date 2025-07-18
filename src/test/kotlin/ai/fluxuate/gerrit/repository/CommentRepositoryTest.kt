package ai.fluxuate.gerrit.repository

import ai.fluxuate.gerrit.model.CommentEntity
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import org.springframework.test.annotation.DirtiesContext
import org.springframework.data.domain.PageRequest
import java.time.Instant
import java.util.*

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class CommentRepositoryTest {

    @Autowired
    private lateinit var commentRepository: CommentRepository

    private lateinit var fileComment: CommentEntity
    private lateinit var lineComment: CommentEntity
    private lateinit var rangeComment: CommentEntity
    private lateinit var replyComment: CommentEntity
    private lateinit var unresolvedComment: CommentEntity

    @BeforeEach
    fun setUp() {
        commentRepository.deleteAll()

        val now = Instant.now()
        val uuid1 = UUID.randomUUID().toString()
        val uuid2 = UUID.randomUUID().toString()
        val uuid3 = UUID.randomUUID().toString()
        val uuid4 = UUID.randomUUID().toString()
        val uuid5 = UUID.randomUUID().toString()
        
        fileComment = CommentEntity(
            changeId = 1001L,
            patchSetId = 2001L,
            authorId = 100L,
            message = "This is a file-level comment",
            writtenOn = now.minusSeconds(3600),
            filePath = "src/main/Main.java",
            uuid = uuid1
        )

        lineComment = CommentEntity(
            changeId = 1001L,
            patchSetId = 2001L,
            authorId = 101L,
            message = "This line needs improvement",
            writtenOn = now.minusSeconds(2700),
            filePath = "src/main/Main.java",
            lineNumber = 42,
            side = CommentEntity.Side.REVISION,
            uuid = uuid2
        )

        rangeComment = CommentEntity(
            changeId = 1001L,
            patchSetId = 2001L,
            authorId = 102L,
            message = "This range has issues",
            writtenOn = now.minusSeconds(1800),
            filePath = "src/main/Main.java",
            lineNumber = 50,
            rangeStartLine = 50,
            rangeStartChar = 10,
            rangeEndLine = 55,
            rangeEndChar = 20,
            side = CommentEntity.Side.REVISION,
            uuid = uuid3
        )

        replyComment = CommentEntity(
            changeId = 1001L,
            patchSetId = 2001L,
            authorId = 103L,
            message = "I agree with this comment",
            writtenOn = now.minusSeconds(900),
            filePath = "src/main/Main.java",
            lineNumber = 50,
            side = CommentEntity.Side.REVISION,
            parentUuid = uuid2,
            uuid = uuid4
        )

        unresolvedComment = CommentEntity(
            changeId = 1002L,
            patchSetId = 2002L,
            authorId = 104L,
            message = "This needs to be fixed",
            writtenOn = now.minusSeconds(600),
            filePath = "src/test/Test.java",
            lineNumber = 10,
            side = CommentEntity.Side.REVISION,
            unresolved = true,
            uuid = uuid5
        )

        fileComment = commentRepository.save(fileComment)
        lineComment = commentRepository.save(lineComment)
        rangeComment = commentRepository.save(rangeComment)
        replyComment = commentRepository.save(replyComment)
        unresolvedComment = commentRepository.save(unresolvedComment)
    }

    @Test
    fun `should find comments by change ID ordered by written date`() {
        val comments = commentRepository.findByChangeIdOrderByWrittenOnAsc(1001L)
        
        assertEquals(4, comments.size)
        assertEquals(fileComment.id, comments[0].id)
        assertEquals(replyComment.id, comments[3].id)
    }

    @Test
    fun `should find comments by patch set ID`() {
        val comments = commentRepository.findByPatchSetIdOrderByWrittenOnAsc(2001L)
        
        assertEquals(4, comments.size)
    }

    @Test
    fun `should find comments by author`() {
        val pageable = PageRequest.of(0, 10)
        val comments = commentRepository.findByAuthorIdOrderByWrittenOnDesc(101L, pageable)
        
        assertEquals(1, comments.totalElements)
        assertEquals(lineComment.id, comments.content[0].id)
    }

    @Test
    fun `should find comments on specific file`() {
        val comments = commentRepository.findByChangeIdAndFilePathOrderByLineNumberAsc(1001L, "src/main/Main.java")
        
        println("Found ${comments.size} comments:")
        comments.forEach { comment ->
            println("  Comment ID: ${comment.id}, Line: ${comment.lineNumber}, Message: ${comment.message}")
        }
        
        assertEquals(4, comments.size)
        // File comment should be last (null line number sorts last)
        assertEquals(fileComment.id, comments[3].id)
    }

    @Test
    fun `should find comment by UUID`() {
        val comment = commentRepository.findByUuid(lineComment.uuid)
        
        assertNotNull(comment)
        assertEquals(lineComment.id, comment!!.id)
    }

    @Test
    fun `should find replies to comment`() {
        val replies = commentRepository.findByParentUuidOrderByWrittenOnAsc(lineComment.uuid)
        
        assertEquals(1, replies.size)
        assertEquals(replyComment.id, replies[0].id)
    }

    @Test
    fun `should find unresolved comments`() {
        val unresolvedComments = commentRepository.findByChangeIdAndUnresolvedTrueOrderByWrittenOnAsc(1002L)
        
        assertEquals(1, unresolvedComments.size)
        assertEquals(unresolvedComment.id, unresolvedComments[0].id)
    }

    @Test
    fun `should find file-level comments`() {
        val fileComments = commentRepository.findByChangeIdAndLineNumberIsNullOrderByWrittenOnAsc(1001L)
        
        assertEquals(1, fileComments.size)
        assertEquals(fileComment.id, fileComments[0].id)
    }

    @Test
    fun `should find comments by line range`() {
        val comments = commentRepository.findByFileAndLineRange(1001L, "src/main/Main.java", 40, 45)
        
        assertEquals(1, comments.size)
        assertEquals(lineComment.id, comments[0].id)
    }

    @Test
    fun `should find range comments overlapping with line range`() {
        val comments = commentRepository.findRangeCommentsOverlapping(1001L, "src/main/Main.java", 48, 52)
        
        assertEquals(1, comments.size)
        assertEquals(rangeComment.id, comments[0].id)
    }

    @Test
    fun `should count comments for change`() {
        val count = commentRepository.countByChangeId(1001L)
        assertEquals(4, count)
    }

    @Test
    fun `should count unresolved comments for change`() {
        val count = commentRepository.countByChangeIdAndUnresolvedTrue(1002L)
        assertEquals(1, count)
    }

    @Test
    fun `should find comments containing text`() {
        val pageable = PageRequest.of(0, 10)
        val comments = commentRepository.findByMessageContaining(1001L, "comment", pageable)
        
        assertEquals(2, comments.totalElements) // fileComment and replyComment
    }

    @Test
    fun `should identify comment types correctly`() {
        assertTrue(fileComment.isFileComment)
        assertTrue(!lineComment.isFileComment)
        
        assertTrue(!fileComment.isRangeComment)
        assertTrue(rangeComment.isRangeComment)
        
        assertTrue(!fileComment.isReply)
        assertTrue(replyComment.isReply)
    }

    @Test
    fun `should find comments by date range`() {
        val now = Instant.now()
        val startDate = now.minusSeconds(7200)
        val endDate = now.minusSeconds(300)
        val pageable = PageRequest.of(0, 10)
        
        val comments = commentRepository.findByWrittenOnBetween(startDate, endDate, pageable)
        
        assertEquals(5, comments.totalElements)
    }
}
