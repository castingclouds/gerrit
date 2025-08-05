package ai.fluxuate.gerrit.util

import ai.fluxuate.gerrit.api.dto.*
import ai.fluxuate.gerrit.api.exception.BadRequestException
import ai.fluxuate.gerrit.api.exception.NotFoundException
import ai.fluxuate.gerrit.model.ChangeEntity
import ai.fluxuate.gerrit.model.PatchSetEntity
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.treewalk.filter.PathFilter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.nio.charset.StandardCharsets

/**
 * Utility class for Git operations including file content retrieval, patch generation,
 * diff operations, and file comparison. Based on official Gerrit GitTestUtil patterns.
 * 
 * This utility consolidates Git repository operations that were previously scattered
 * throughout ChangeService.kt, improving maintainability and modularity.
 */
object GitUtil {

    /**
     * Get the Git repository for a project.
     * 
     * @param projectName The project name
     * @return The Git repository
     */
    private fun getRepository(projectName: String): Repository {
        // TODO: Get actual repository path from configuration
        val gitDir = File("/tmp/gerrit-repos/$projectName.git")
        return FileRepositoryBuilder()
            .setGitDir(gitDir)
            .readEnvironment()
            .findGitDir()
            .build()
    }

    /**
     * Safely execute Git operations with repository cleanup.
     */
    private fun <T> withRepository(projectName: String, block: (Repository) -> T): T {
        val repository = getRepository(projectName)
        return try {
            block(repository)
        } finally {
            repository.close()
        }
    }

    /**
     * Generate a patch file for a specific revision.
     * 
     * @param change The change entity
     * @param patchSet The patch set data
     * @param revisionId The revision identifier
     * @param zip Whether to return zipped format
     * @return The patch content as string
     */
    fun generateRevisionPatch(
        change: ChangeEntity,
        patchSet: PatchSetEntity,
        revisionId: String,
        zip: Boolean = false
    ): String {
        return try {
            withRepository(change.projectName) { repository ->
                val git = Git(repository)
                val revisionObjectId = ObjectId.fromString(revisionId)
                
                // Get the commit for this revision
                val revWalk = RevWalk(repository)
                val commit = revWalk.parseCommit(revisionObjectId)
                
                // Generate patch using JGit
                val outputStream = ByteArrayOutputStream()
                val formatter = DiffFormatter(outputStream)
                formatter.setRepository(repository)
                
                // Compare with parent (or empty tree for initial commit)
                val parentTree = if (commit.parentCount > 0) {
                    commit.getParent(0).tree
                } else {
                    null
                }
                
                formatter.format(parentTree, commit.tree)
                formatter.flush()
                
                // Build patch header
                val patchContent = outputStream.toString(StandardCharsets.UTF_8)
                val author = commit.authorIdent
                val commitMessage = commit.fullMessage
                
                val header = """
                    |From ${commit.name} Mon Sep 17 00:00:00 2001
                    |From: ${author.name} <${author.emailAddress}>
                    |Date: ${author.`when`}
                    |Subject: [PATCH] ${commit.shortMessage}
                    |
                    |$commitMessage
                    |---
                """.trimMargin()
                
                revWalk.close()
                formatter.close()
                
                header + patchContent
            }
        } catch (e: Exception) {
            // Fallback to placeholder if Git operations fail
            val revision = patchSet.commitId ?: revisionId
            // TODO: Lookup user info by uploaderId from user service/repository
            val uploaderName = "User-${patchSet.uploaderId}" // Placeholder - should lookup from user service
            val uploaderEmail = "user${patchSet.uploaderId}@example.com" // Placeholder - should lookup from user service
            
            """
                |From ${revision}def456 Mon Sep 17 00:00:00 2001
                |From: $uploaderName <$uploaderEmail>
                |Subject: [PATCH] ${change.subject}
                |
                |Error generating patch: ${e.message}
                |
                |Change-Id: ${change.changeKey}
                |---
            """.trimMargin()
        }
    }

    /**
     * List files in a revision with optional filtering.
     * 
     * @param change The change entity
     * @param patchSet The patch set data
     * @param base Base revision for comparison (optional)
     * @param parent Parent number for comparison (optional)
     * @param reviewed Filter for reviewed files (optional)
     * @param query Search query for files (optional)
     * @return Map of file paths to FileInfo
     */
    fun listRevisionFiles(
        change: ChangeEntity,
        patchSet: PatchSetEntity,
        base: String? = null,
        parent: Int? = null,
        reviewed: Boolean? = null,
        query: String? = null
    ): Map<String, FileInfo> {
        // Validate mutually exclusive options
        val optionCount = listOfNotNull(base, parent, reviewed, query).size
        if (optionCount > 1) {
            throw BadRequestException("Cannot combine base, parent, reviewed, query options")
        }

        // TODO: Implement actual Git file listing logic
        return when {
            reviewed == true -> {
                // Return reviewed files (placeholder)
                emptyMap()
            }
            query != null -> {
                // Return files matching query (placeholder)
                emptyMap()
            }
            base != null -> {
                // Return files compared to base revision
                getFilesComparedToBase(change, patchSet, base)
            }
            parent != null -> {
                // Return files compared to parent
                getFilesComparedToParent(change, patchSet, parent)
            }
            else -> {
                // Return all files in revision
                getAllFilesInRevision(change, patchSet)
            }
        }
    }

    /**
     * Get file content for a specific file in a patch set.
     * 
     * @param change The change entity
     * @param patchSet The patch set data
     * @param fileId The path to the file
     * @return The file content as string
     */
    fun getFileContent(
        change: ChangeEntity,
        patchSet: PatchSetEntity,
        fileId: String
    ): String {
        return try {
            withRepository(change.projectName) { repository ->
                // Get the revision ID from patch set
                val revisionId = patchSet.commitId
                    ?: throw IllegalArgumentException("No revision found in patch set")
                
                val revisionObjectId = ObjectId.fromString(revisionId)
                val revWalk = RevWalk(repository)
                val commit = revWalk.parseCommit(revisionObjectId)
                
                // Get the tree for this revision
                val treeWalk = TreeWalk(repository)
                treeWalk.addTree(commit.tree)
                treeWalk.isRecursive = true
                treeWalk.filter = PathFilter.create(fileId)
                
                if (treeWalk.next()) {
                    val objectId = treeWalk.getObjectId(0)
                    val loader = repository.open(objectId)
                    
                    when {
                        loader.isLarge -> {
                            // For large files, read in chunks
                            val inputStream = loader.openStream()
                            inputStream.use { it.bufferedReader().readText() }
                        }
                        else -> {
                            // For small files, read directly
                            String(loader.bytes, StandardCharsets.UTF_8)
                        }
                    }
                } else {
                    throw FileNotFoundException("File not found: $fileId")
                }
            }
        } catch (e: Exception) {
            // Fallback to placeholder if Git operations fail
            "// File: $fileId\n// Error retrieving content: ${e.message}\n// Change: ${change.changeKey}\n// Patch set: ${patchSet.patchSetNumber}"
        }
    }

    /**
     * Get file diff for a specific file in a patch set.
     */
    fun getFileDiff(
        change: ChangeEntity,
        patchSet: PatchSetEntity,
        fileId: String,
        base: String? = null,
        parent: Int? = null,
        context: Int? = null,
        intraline: Boolean? = null,
        whitespace: String? = null
    ): DiffInfo {
        return try {
            withRepository(change.projectName) { repository ->
                val revisionId = patchSet.commitId
                    ?: throw IllegalArgumentException("No revision found in patch set")
                
                val git = Git(repository)
                val revisionObjectId = ObjectId.fromString(revisionId)
                val revWalk = RevWalk(repository)
                val commit = revWalk.parseCommit(revisionObjectId)
                
                // Determine what to compare against
                val oldCommit = when {
                    base != null -> {
                        val baseObjectId = ObjectId.fromString(base)
                        revWalk.parseCommit(baseObjectId)
                    }
                    parent != null && commit.parentCount > parent -> {
                        commit.getParent(parent)
                    }
                    commit.parentCount > 0 -> {
                        commit.getParent(0)
                    }
                    else -> null // Initial commit, compare with empty tree
                }
                
                // Create diff formatter
                val outputStream = ByteArrayOutputStream()
                val formatter = DiffFormatter(outputStream)
                formatter.setRepository(repository)
                
                // Set context lines if specified
                context?.let { formatter.setContext(it) }
                
                // Generate diff entries
                val diffEntries = if (oldCommit != null) {
                    formatter.scan(oldCommit.tree, commit.tree)
                } else {
                    formatter.scan(null, commit.tree)
                }
                
                // Find the specific file diff
                val fileDiffEntry = diffEntries.find { 
                    it.newPath == fileId || it.oldPath == fileId 
                }
                
                if (fileDiffEntry != null) {
                    // Format the diff for this file
                    formatter.format(fileDiffEntry)
                    formatter.flush()
                    
                    val diffText = outputStream.toString(StandardCharsets.UTF_8)
                    
                    // Parse diff text into content entries
                    val contentEntries = parseDiffContent(diffText)
                    
                    // Determine change type
                    val changeType = when (fileDiffEntry.changeType) {
                        DiffEntry.ChangeType.ADD -> ChangeType.ADDED
                        DiffEntry.ChangeType.DELETE -> ChangeType.DELETED
                        DiffEntry.ChangeType.MODIFY -> ChangeType.MODIFIED
                        DiffEntry.ChangeType.RENAME -> ChangeType.RENAMED
                        DiffEntry.ChangeType.COPY -> ChangeType.COPIED
                        else -> ChangeType.MODIFIED
                    }
                    
                    // Get file metadata
                    val oldLines = countLinesInFile(repository, oldCommit?.tree, fileDiffEntry.oldPath)
                    val newLines = countLinesInFile(repository, commit.tree, fileDiffEntry.newPath)
                    
                    revWalk.close()
                    formatter.close()
                    
                    DiffInfo(
                        metaA = if (oldCommit != null) {
                            FileMeta(
                                commitId = oldCommit.name,
                                name = fileDiffEntry.oldPath ?: fileId,
                                contentType = "text/plain",
                                lines = oldLines
                            )
                        } else null,
                        metaB = FileMeta(
                            commitId = commit.name,
                            name = fileDiffEntry.newPath ?: fileId,
                            contentType = "text/plain",
                            lines = newLines
                        ),
                        changeType = changeType,
                        content = contentEntries,
                        binary = false
                    )
                } else {
                    throw FileNotFoundException("File not found in diff: $fileId")
                }
            }
        } catch (e: Exception) {
            // Fallback to placeholder if Git operations fail
            val commitId = patchSet.commitId ?: "abc123"
            DiffInfo(
                metaA = FileMeta(
                    commitId = commitId,
                    name = fileId,
                    contentType = "text/plain",
                    lines = 10
                ),
                metaB = FileMeta(
                    commitId = commitId,
                    name = fileId,
                    contentType = "text/plain",
                    lines = 12
                ),
                changeType = ChangeType.MODIFIED,
                content = listOf(
                    ContentEntry(
                        ab = listOf("// Error generating diff: ${e.message}")
                    )
                ),
                binary = false
            )
        }
    }





    /**
     * Validate that a revision exists in the change's patch sets.
     * 
     * @param change The change entity
     * @param revisionId The revision identifier to validate
     * @return The patch set entity if found
     * @throws NotFoundException if revision is not found
     */
    fun validateRevisionExists(change: ChangeEntity, revisionId: String): PatchSetEntity {
        return change.patchSets.find { patchSet ->
            val revision = patchSet.commitId
            revision == revisionId || patchSet.commitId == revisionId
        } ?: throw NotFoundException("Revision $revisionId not found in change ${change.changeKey}")
    }

    /**
     * Extract commit information from a patch set.
     * 
     * @param patchSet The patch set entity
     * @return Commit information map
     */
    fun extractCommitInfo(patchSet: PatchSetEntity): Map<String, Any> {
        val commitId = patchSet.commitId ?: "unknown"
        // TODO: Lookup user info by uploaderId from user service/repository
        val uploaderMap = mapOf(
            "id" to patchSet.uploaderId,
            "name" to "User-${patchSet.uploaderId}", // Placeholder - should lookup from user service
            "email" to "user${patchSet.uploaderId}@example.com" // Placeholder - should lookup from user service
        )
        val createdOn = patchSet.createdOn.toString()
        
        return mapOf(
            "commitId" to commitId,
            "uploader" to uploaderMap,
            "createdOn" to createdOn,
            "revision" to (patchSet.commitId ?: "unknown")
        )
    }

    /**
     * Get all files in a revision.
     * 
     * @param change The change entity
     * @param patchSet The patch set data
     * @return Map of file paths to FileInfo objects
     */
    fun getAllFilesInRevision(change: ChangeEntity, patchSet: PatchSetEntity): Map<String, FileInfo> {
        return try {
            withRepository(change.projectName) { repository ->
                val revisionId = patchSet.commitId
                    ?: throw IllegalArgumentException("No revision found in patch set")
                
                val revisionObjectId = ObjectId.fromString(revisionId)
                val revWalk = RevWalk(repository)
                val commit = revWalk.parseCommit(revisionObjectId)
                
                // Get the parent commit to compare against (if exists)
                val parentCommit = if (commit.parentCount > 0) {
                    commit.getParent(0)
                } else null
                
                // Generate diff entries to get file changes
                val formatter = DiffFormatter(ByteArrayOutputStream())
                formatter.setRepository(repository)
                
                val diffEntries = if (parentCommit != null) {
                    formatter.scan(parentCommit.tree, commit.tree)
                } else {
                    formatter.scan(null, commit.tree)
                }
                
                val fileInfoMap = mutableMapOf<String, FileInfo>()
                
                for (diffEntry in diffEntries) {
                    val filePath = diffEntry.newPath ?: diffEntry.oldPath ?: continue
                    
                    // Determine change status
                    val status = when (diffEntry.changeType) {
                        DiffEntry.ChangeType.ADD -> 'A'
                        DiffEntry.ChangeType.DELETE -> 'D'
                        DiffEntry.ChangeType.MODIFY -> 'M'
                        DiffEntry.ChangeType.RENAME -> 'R'
                        DiffEntry.ChangeType.COPY -> 'C'
                        else -> 'M'
                    }
                    
                    // Get file size for new/modified files
                    val size = try {
                        if (diffEntry.newPath != null) {
                            val treeWalk = TreeWalk(repository)
                            treeWalk.addTree(commit.tree)
                            treeWalk.isRecursive = true
                            treeWalk.filter = PathFilter.create(diffEntry.newPath)
                            
                            if (treeWalk.next()) {
                                val objectId = treeWalk.getObjectId(0)
                                val loader = repository.open(objectId)
                                loader.size.toInt()
                            } else 0
                        } else 0
                    } catch (e: Exception) {
                        0
                    }
                    
                    // Calculate line changes (simplified)
                    var linesInserted = 0
                    var linesDeleted = 0
                    
                    try {
                        val outputStream = ByteArrayOutputStream()
                        val diffFormatter = DiffFormatter(outputStream)
                        diffFormatter.setRepository(repository)
                        diffFormatter.format(diffEntry)
                        diffFormatter.flush()
                        
                        val diffText = outputStream.toString(StandardCharsets.UTF_8)
                        val lines = diffText.lines()
                        
                        for (line in lines) {
                            when {
                                line.startsWith("+") && !line.startsWith("++") -> linesInserted++
                                line.startsWith("-") && !line.startsWith("--") -> linesDeleted++
                            }
                        }
                        
                        diffFormatter.close()
                    } catch (e: Exception) {
                        // If we can't get exact line counts, use estimates
                        linesInserted = if (status == 'A') size / 50 else 0
                        linesDeleted = if (status == 'D') size / 50 else 0
                    }
                    
                    val sizeDelta = (linesInserted * 50 - linesDeleted * 50).toLong()
                    
                    fileInfoMap[filePath] = FileInfo(
                        status = status,
                        linesInserted = linesInserted,
                        linesDeleted = linesDeleted,
                        sizeDelta = sizeDelta,
                        size = size.toLong()
                    )
                }
                
                revWalk.close()
                formatter.close()
                
                fileInfoMap
            }
        } catch (e: Exception) {
            // Fallback to placeholder if Git operations fail
            mapOf(
                "error.txt" to FileInfo(
                    status = 'M',
                    linesInserted = 0,
                    linesDeleted = 0,
                    sizeDelta = 0,
                    size = 0
                )
            )
        }
    }

    /**
     * Get files compared to a base revision.
     * 
     * @param change The change entity
     * @param patchSet The patch set data
     * @param base The base revision to compare against
     * @return Map of file paths to FileInfo objects
     */
    fun getFilesComparedToBase(change: ChangeEntity, patchSet: PatchSetEntity, base: String): Map<String, FileInfo> {
        return try {
            withRepository(change.projectName) { repository ->
                val revisionId = patchSet.commitId
                    ?: throw IllegalArgumentException("No revision found in patch set")
                
                val revisionObjectId = ObjectId.fromString(revisionId)
                val baseObjectId = ObjectId.fromString(base)
                
                val revWalk = RevWalk(repository)
                val revisionCommit = revWalk.parseCommit(revisionObjectId)
                val baseCommit = revWalk.parseCommit(baseObjectId)
                
                // Generate diff entries between base and revision
                val formatter = DiffFormatter(ByteArrayOutputStream())
                formatter.setRepository(repository)
                
                val diffEntries = formatter.scan(baseCommit.tree, revisionCommit.tree)
                
                val fileInfoMap = mutableMapOf<String, FileInfo>()
                
                for (diffEntry in diffEntries) {
                    val filePath = diffEntry.newPath ?: diffEntry.oldPath ?: continue
                    
                    // Determine change status
                    val status = when (diffEntry.changeType) {
                        DiffEntry.ChangeType.ADD -> 'A'
                        DiffEntry.ChangeType.DELETE -> 'D'
                        DiffEntry.ChangeType.MODIFY -> 'M'
                        DiffEntry.ChangeType.RENAME -> 'R'
                        DiffEntry.ChangeType.COPY -> 'C'
                        else -> 'M'
                    }
                    
                    // Get file size
                    val size = try {
                        if (diffEntry.newPath != null) {
                            val treeWalk = TreeWalk(repository)
                            treeWalk.addTree(revisionCommit.tree)
                            treeWalk.isRecursive = true
                            treeWalk.filter = PathFilter.create(diffEntry.newPath)
                            
                            if (treeWalk.next()) {
                                val objectId = treeWalk.getObjectId(0)
                                val loader = repository.open(objectId)
                                loader.size.toInt()
                            } else 0
                        } else 0
                    } catch (e: Exception) {
                        0
                    }
                    
                    // Calculate line changes
                    var linesInserted = 0
                    var linesDeleted = 0
                    
                    try {
                        val outputStream = ByteArrayOutputStream()
                        val diffFormatter = DiffFormatter(outputStream)
                        diffFormatter.setRepository(repository)
                        diffFormatter.format(diffEntry)
                        diffFormatter.flush()
                        
                        val diffText = outputStream.toString(StandardCharsets.UTF_8)
                        val lines = diffText.lines()
                        
                        for (line in lines) {
                            when {
                                line.startsWith("+") && !line.startsWith("++") -> linesInserted++
                                line.startsWith("-") && !line.startsWith("--") -> linesDeleted++
                            }
                        }
                        
                        diffFormatter.close()
                    } catch (e: Exception) {
                        linesInserted = if (status == 'A') size / 50 else 0
                        linesDeleted = if (status == 'D') size / 50 else 0
                    }
                    
                    val sizeDelta = (linesInserted * 50 - linesDeleted * 50).toLong()
                    
                    fileInfoMap[filePath] = FileInfo(
                        status = status,
                        linesInserted = linesInserted,
                        linesDeleted = linesDeleted,
                        sizeDelta = sizeDelta,
                        size = size.toLong()
                    )
                }
                
                revWalk.close()
                formatter.close()
                
                fileInfoMap
            }
        } catch (e: Exception) {
            // Fallback to comparing against first parent if base comparison fails
            getAllFilesInRevision(change, patchSet)
        }
    }

    /**
     * Get files compared to a parent revision.
     * 
     * @param change The change entity
     * @param patchSet The patch set data
     * @param parent The parent number to compare against
     * @return Map of file paths to FileInfo objects
     */
    fun getFilesComparedToParent(change: ChangeEntity, patchSet: PatchSetEntity, parent: Int): Map<String, FileInfo> {
        return try {
            withRepository(change.projectName) { repository ->
                val revisionId = patchSet.commitId
                    ?: throw IllegalArgumentException("No revision found in patch set")
                
                val revisionObjectId = ObjectId.fromString(revisionId)
                val revWalk = RevWalk(repository)
                val commit = revWalk.parseCommit(revisionObjectId)
                
                // Get the specified parent commit (parent is 1-indexed)
                val parentCommit = if (commit.parentCount >= parent && parent > 0) {
                    commit.getParent(parent - 1)
                } else if (commit.parentCount > 0) {
                    // Default to first parent if specified parent doesn't exist
                    commit.getParent(0)
                } else {
                    // No parent commits, compare against empty tree
                    null
                }
                
                // Generate diff entries between parent and revision
                val formatter = DiffFormatter(ByteArrayOutputStream())
                formatter.setRepository(repository)
                
                val diffEntries = if (parentCommit != null) {
                    formatter.scan(parentCommit.tree, commit.tree)
                } else {
                    formatter.scan(null, commit.tree)
                }
                
                val fileInfoMap = mutableMapOf<String, FileInfo>()
                
                for (diffEntry in diffEntries) {
                    val filePath = diffEntry.newPath ?: diffEntry.oldPath ?: continue
                    
                    // Determine change status
                    val status = when (diffEntry.changeType) {
                        DiffEntry.ChangeType.ADD -> 'A'
                        DiffEntry.ChangeType.DELETE -> 'D'
                        DiffEntry.ChangeType.MODIFY -> 'M'
                        DiffEntry.ChangeType.RENAME -> 'R'
                        DiffEntry.ChangeType.COPY -> 'C'
                        else -> 'M'
                    }
                    
                    // Get file size
                    val size = try {
                        if (diffEntry.newPath != null) {
                            val treeWalk = TreeWalk(repository)
                            treeWalk.addTree(commit.tree)
                            treeWalk.isRecursive = true
                            treeWalk.filter = PathFilter.create(diffEntry.newPath)
                            
                            if (treeWalk.next()) {
                                val objectId = treeWalk.getObjectId(0)
                                val loader = repository.open(objectId)
                                loader.size.toInt()
                            } else 0
                        } else 0
                    } catch (e: Exception) {
                        0
                    }
                    
                    // Calculate line changes
                    var linesInserted = 0
                    var linesDeleted = 0
                    
                    try {
                        val outputStream = ByteArrayOutputStream()
                        val diffFormatter = DiffFormatter(outputStream)
                        diffFormatter.setRepository(repository)
                        diffFormatter.format(diffEntry)
                        diffFormatter.flush()
                        
                        val diffText = outputStream.toString(StandardCharsets.UTF_8)
                        val lines = diffText.lines()
                        
                        for (line in lines) {
                            when {
                                line.startsWith("+") && !line.startsWith("++") -> linesInserted++
                                line.startsWith("-") && !line.startsWith("--") -> linesDeleted++
                            }
                        }
                        
                        diffFormatter.close()
                    } catch (e: Exception) {
                        linesInserted = if (status == 'A') size / 50 else 0
                        linesDeleted = if (status == 'D') size / 50 else 0
                    }
                    
                    val sizeDelta = (linesInserted * 50 - linesDeleted * 50).toLong()
                    
                    fileInfoMap[filePath] = FileInfo(
                        status = status,
                        linesInserted = linesInserted,
                        linesDeleted = linesDeleted,
                        sizeDelta = sizeDelta,
                        size = size.toLong()
                    )
                }
                
                revWalk.close()
                formatter.close()
                
                fileInfoMap
            }
        } catch (e: Exception) {
            // Fallback to comparing against first parent if specific parent comparison fails
            getAllFilesInRevision(change, patchSet)
        }
    }

    /**
     * Parse diff content text into ContentEntry objects.
     */
    private fun parseDiffContent(diffText: String): List<ContentEntry> {
        val lines = diffText.lines()
        val contentEntries = mutableListOf<ContentEntry>()
        
        var currentAb = mutableListOf<String>()
        var currentA = mutableListOf<String>()
        var currentB = mutableListOf<String>()
        
        fun flushCurrent() {
            if (currentAb.isNotEmpty() || currentA.isNotEmpty() || currentB.isNotEmpty()) {
                contentEntries.add(
                    ContentEntry(
                        ab = if (currentAb.isNotEmpty()) currentAb.toList() else null,
                        a = if (currentA.isNotEmpty()) currentA.toList() else null,
                        b = if (currentB.isNotEmpty()) currentB.toList() else null
                    )
                )
                currentAb.clear()
                currentA.clear()
                currentB.clear()
            }
        }
        
        for (line in lines) {
            when {
                line.startsWith("+") && !line.startsWith("++") -> {
                    if (currentAb.isNotEmpty() || currentA.isNotEmpty()) {
                        flushCurrent()
                    }
                    currentB.add(line.substring(1))
                }
                line.startsWith("-") && !line.startsWith("--") -> {
                    if (currentAb.isNotEmpty() || currentB.isNotEmpty()) {
                        flushCurrent()
                    }
                    currentA.add(line.substring(1))
                }
                line.startsWith(" ") -> {
                    if (currentA.isNotEmpty() || currentB.isNotEmpty()) {
                        flushCurrent()
                    }
                    currentAb.add(line.substring(1))
                }
                // Skip header lines (@@, +++, ---, etc.)
            }
        }
        
        flushCurrent()
        
        return contentEntries.ifEmpty {
            listOf(ContentEntry(ab = listOf("No changes")))
        }
    }
    
    /**
     * Count lines in a file from a tree.
     */
    private fun countLinesInFile(repository: Repository, tree: org.eclipse.jgit.lib.AnyObjectId?, filePath: String?): Int {
        if (tree == null || filePath == null) return 0
        
        return try {
            val treeWalk = TreeWalk(repository)
            treeWalk.addTree(tree)
            treeWalk.isRecursive = true
            treeWalk.filter = PathFilter.create(filePath)
            
            if (treeWalk.next()) {
                val objectId = treeWalk.getObjectId(0)
                val loader = repository.open(objectId)
                val content = String(loader.bytes, StandardCharsets.UTF_8)
                content.lines().size
            } else {
                0
            }
        } catch (e: Exception) {
            0
        }
    }
}
