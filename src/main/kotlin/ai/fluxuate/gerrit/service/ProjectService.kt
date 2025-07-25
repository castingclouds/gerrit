package ai.fluxuate.gerrit.service

import ai.fluxuate.gerrit.api.dto.*
import ai.fluxuate.gerrit.api.exception.BadRequestException
import ai.fluxuate.gerrit.api.exception.ConflictException
import ai.fluxuate.gerrit.api.exception.NotFoundException
import ai.fluxuate.gerrit.git.GitRepositoryService
import ai.fluxuate.gerrit.model.ProjectEntity
import ai.fluxuate.gerrit.model.ProjectState
import ai.fluxuate.gerrit.repository.ProjectEntityRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Service for managing projects with REST API support.
 */
@Service
@Transactional
class ProjectService(
    private val projectRepository: ProjectEntityRepository,
    private val gitRepositoryService: GitRepositoryService
) {

    // REST API methods

    /**
     * Query projects with optional filters.
     */
    fun queryProjects(
        query: String?,
        limit: Int?,
        start: Int?,
        type: String?,
        includeDescription: Boolean,
        includeTree: Boolean,
        branches: List<String>?,
        includeAll: Boolean
    ): Map<String, ProjectInfo> {
        val pageSize = limit ?: 25
        val offset = start ?: 0
        val pageable = PageRequest.of(offset / pageSize, pageSize, Sort.by("name"))
        
        val projectsPage = when {
            query != null -> projectRepository.findByNameContainingIgnoreCase(query, pageable)
            !includeAll -> projectRepository.findByStateNot(ProjectState.HIDDEN, pageable)
            else -> projectRepository.findAll(pageable)
        }
        
        return projectsPage.content.associate { entity ->
            entity.name to convertToProjectInfo(entity, includeDescription, includeTree, branches)
        }
    }

    /**
     * Get project information.
     */
    fun getProject(projectName: String): ProjectInfo {
        val project = findProjectByName(projectName)
        return convertToProjectInfo(project, includeDescription = true, includeTree = true, branches = null)
    }

    /**
     * Create a new project.
     */
    @Transactional
    fun createProject(projectName: String, input: ProjectInput): ProjectInfo {
        // Validate project name
        if (projectRepository.existsByName(projectName)) {
            throw ConflictException("Project '$projectName' already exists")
        }
        
        // Validate parent if specified
        if (input.parent != null) {
            if (!projectRepository.existsByName(input.parent)) {
                throw BadRequestException("Parent project '${input.parent}' does not exist")
            }
            // Check for circular dependency
            if (wouldCreateCircularDependency(projectName, input.parent)) {
                throw BadRequestException("Creating project would result in circular dependency")
            }
        }
        
        // Create project entity
        val projectEntity = ProjectEntity(
            name = projectName,
            parentName = input.parent,
            description = input.description,
            state = input.state?.let { convertFromApiProjectState(it) } ?: ProjectState.ACTIVE,
            config = buildProjectConfig(input),
            metadata = buildProjectMetadata(input)
        )
        
        val savedProject = projectRepository.save(projectEntity)
        
            // Initialize Git repository
            try {
                gitRepositoryService.createRepository(projectName, bare = true)
                // Repository is created empty - no initial commit or branches
            } catch (e: Exception) {
                // If Git repository creation fails, clean up the database entry
                projectRepository.delete(savedProject)
                throw ConflictException("Failed to initialize Git repository for project '$projectName': ${e.message}")
            }
        
        return convertToProjectInfo(savedProject, includeDescription = true, includeTree = false, branches = null)
    }

    /**
     * Update project configuration.
     */
    @Transactional
    fun updateProjectConfig(projectName: String, config: ConfigInput): ProjectInfo {
        val project = findProjectByName(projectName)
        
        val updatedProject = project.copy(
            description = config.description ?: project.description,
            state = config.state?.let { convertFromApiProjectState(it) } ?: project.state,
            config = project.config + buildConfigFromInput(config),
            metadata = project.metadata + buildMetadataFromInput(config)
        )
        
        val savedProject = projectRepository.save(updatedProject)
        return convertToProjectInfo(savedProject, includeDescription = true, includeTree = false, branches = null)
    }

    /**
     * Delete a project.
     */
    @Transactional
    fun deleteProject(projectName: String, force: Boolean) {
        val project = findProjectByName(projectName)
        
        // Check for child projects
        val children = projectRepository.findByParentName(projectName, PageRequest.of(0, 1))
        if (children.hasContent() && !force) {
            throw ConflictException("Cannot delete project with child projects. Use force=true to override.")
        }
        
        // Delete Git repository and clean up references
        try {
            if (gitRepositoryService.repositoryExists(projectName)) {
                gitRepositoryService.cleanupReferences(projectName)
                gitRepositoryService.deleteRepository(projectName)
            }
        } catch (e: Exception) {
            // Log the error but don't fail the project deletion
            // The database cleanup should still proceed
            println("Warning: Failed to delete Git repository for project '$projectName': ${e.message}")
        }
        
        projectRepository.delete(project)
    }

    /**
     * Get project description.
     */
    fun getDescription(projectName: String): String {
        val project = findProjectByName(projectName)
        return project.description ?: ""
    }

    /**
     * Set project description.
     */
    @Transactional
    fun setDescription(projectName: String, input: DescriptionInput): String {
        val project = findProjectByName(projectName)
        val updatedProject = project.copy(description = input.description)
        val savedProject = projectRepository.save(updatedProject)
        return savedProject.description ?: ""
    }

    /**
     * Get project parent.
     */
    fun getParent(projectName: String): String {
        val project = findProjectByName(projectName)
        return project.parentName ?: ""
    }

    /**
     * Set project parent.
     */
    @Transactional
    fun setParent(projectName: String, input: ParentInput): String {
        val project = findProjectByName(projectName)
        
        // Validate parent exists
        if (!projectRepository.existsByName(input.parent)) {
            throw BadRequestException("Parent project '${input.parent}' does not exist")
        }
        
        // Check for circular dependency
        if (wouldCreateCircularDependency(projectName, input.parent)) {
            throw BadRequestException("Setting parent would result in circular dependency")
        }
        
        val updatedProject = project.copy(parentName = input.parent)
        val savedProject = projectRepository.save(updatedProject)
        return savedProject.parentName ?: ""
    }

    /**
     * Get child projects.
     */
    fun getChildren(projectName: String, recursive: Boolean): List<ProjectInfo> {
        // Verify parent project exists
        findProjectByName(projectName)
        
        val children = mutableListOf<ProjectInfo>()
        val directChildren = projectRepository.findByParentName(projectName, PageRequest.of(0, 1000))
        
        for (child in directChildren.content) {
            children.add(convertToProjectInfo(child, includeDescription = false, includeTree = false, branches = null))
            
            if (recursive) {
                children.addAll(getChildren(child.name, recursive = true))
            }
        }
        
        return children
    }

    /**
     * Get project HEAD.
     */
    fun getHead(projectName: String): String {
        val project = findProjectByName(projectName)
        
        // Get actual HEAD from Git repository
        return try {
            if (gitRepositoryService.repositoryExists(projectName)) {
                gitRepositoryService.getHead(projectName)
            } else {
                // Fallback to config if Git repository doesn't exist
                project.config["head"] as? String ?: "refs/heads/main"
            }
        } catch (e: Exception) {
            // Fallback to config if Git operation fails
            project.config["head"] as? String ?: "refs/heads/main"
        }
    }

    /**
     * Set project HEAD.
     */
    @Transactional
    fun setHead(projectName: String, input: HeadInput): String {
        val project = findProjectByName(projectName)
        
        // Validate ref exists in Git repository
        if (gitRepositoryService.repositoryExists(projectName)) {
            if (!gitRepositoryService.validateRef(projectName, input.ref)) {
                throw BadRequestException("Reference '${input.ref}' does not exist in repository")
            }
        }
        
        // Update Git repository HEAD
        try {
            gitRepositoryService.setHead(projectName, input.ref)
        } catch (e: Exception) {
            // Log the error but don't fail the HEAD update
            println("Warning: Failed to update Git repository HEAD for project '$projectName': ${e.message}")
        }
        
        // Update project config
        val updatedConfig = project.config.toMutableMap()
        updatedConfig["head"] = input.ref
        
        val updatedProject = project.copy(config = updatedConfig)
        val savedProject = projectRepository.save(updatedProject)
        
        return input.ref
    }

    // Helper methods

    private fun findProjectByName(projectName: String): ProjectEntity {
        return projectRepository.findByName(projectName)
            ?: throw NotFoundException("Project '$projectName' not found")
    }

    private fun convertToProjectInfo(
        entity: ProjectEntity,
        includeDescription: Boolean,
        includeTree: Boolean,
        branches: List<String>?
    ): ProjectInfo {
        return try {
            ProjectInfo(
                id = entity.name,
                name = entity.name,
                parent = entity.parentName,
                description = if (includeDescription) entity.description else null,
                state = convertToApiProjectState(entity.state),
                branches = if (branches != null) {
                    // Get actual branches from Git repository
                    try {
                        if (gitRepositoryService.repositoryExists(entity.name)) {
                            gitRepositoryService.listBranches(entity.name).associateWith { branchName ->
                                "refs/heads/$branchName" // Return the full ref name as String
                            }
                        } else {
                            emptyMap()
                        }
                    } catch (e: Exception) {
                        // Fallback to empty branches if Git operation fails
                        println("Warning: Failed to list branches for project '${entity.name}': ${e.message}")
                        emptyMap()
                    }
                } else null,
                labels = try {
                    convertLabelsFromMetadata(entity.metadata)
                } catch (e: Exception) {
                    println("Warning: Failed to convert labels for project '${entity.name}': ${e.message}")
                    null
                },
                webLinks = try {
                    convertWebLinksFromMetadata(entity.metadata)
                } catch (e: Exception) {
                    println("Warning: Failed to convert web links for project '${entity.name}': ${e.message}")
                    null
                },
                configVisible = true // For now, always visible - TODO: Implement proper permission checks based on user context
            )
        } catch (e: Exception) {
            println("Error: Failed to convert ProjectEntity to ProjectInfo for '${entity.name}': ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    private fun convertToApiProjectState(state: ProjectState): ai.fluxuate.gerrit.api.dto.ProjectState {
        return when (state) {
            ProjectState.ACTIVE -> ai.fluxuate.gerrit.api.dto.ProjectState.ACTIVE
            ProjectState.READ_ONLY -> ai.fluxuate.gerrit.api.dto.ProjectState.READ_ONLY
            ProjectState.HIDDEN -> ai.fluxuate.gerrit.api.dto.ProjectState.HIDDEN
        }
    }

    private fun convertFromApiProjectState(state: ai.fluxuate.gerrit.api.dto.ProjectState): ProjectState {
        return when (state) {
            ai.fluxuate.gerrit.api.dto.ProjectState.ACTIVE -> ProjectState.ACTIVE
            ai.fluxuate.gerrit.api.dto.ProjectState.READ_ONLY -> ProjectState.READ_ONLY
            ai.fluxuate.gerrit.api.dto.ProjectState.HIDDEN -> ProjectState.HIDDEN
        }
    }

    private fun buildProjectConfig(input: ProjectInput): Map<String, Any> {
        val config = mutableMapOf<String, Any>()
        
        input.submitType?.let { config["submit_type"] = it.name }
        input.useContributorAgreements?.let { config["use_contributor_agreements"] = it.name }
        input.useSignedOffBy?.let { config["use_signed_off_by"] = it.name }
        input.useContentMerge?.let { config["use_content_merge"] = it.name }
        input.requireChangeId?.let { config["require_change_id"] = it.name }
        input.rejectImplicitMerges?.let { config["reject_implicit_merges"] = it.name }
        input.enableSignedPush?.let { config["enable_signed_push"] = it.name }
        input.requireSignedPush?.let { config["require_signed_push"] = it.name }
        input.maxObjectSizeLimit?.let { config["max_object_size_limit"] = it }
        
        return config
    }

    private fun buildProjectMetadata(input: ProjectInput): Map<String, Any> {
        val metadata = mutableMapOf<String, Any>()
        
        input.branches?.let { metadata["initial_branches"] = it }
        input.owners?.let { metadata["owners"] = it }
        
        return metadata
    }

    private fun buildConfigFromInput(config: ConfigInput): Map<String, Any> {
        val configMap = mutableMapOf<String, Any>()
        
        config.submitType?.let { configMap["submit_type"] = it.name }
        config.useContributorAgreements?.let { configMap["use_contributor_agreements"] = it.name }
        config.useContentMerge?.let { configMap["use_content_merge"] = it.name }
        config.useSignedOffBy?.let { configMap["use_signed_off_by"] = it.name }
        config.requireChangeId?.let { configMap["require_change_id"] = it.name }
        config.rejectImplicitMerges?.let { configMap["reject_implicit_merges"] = it.name }
        config.privateByDefault?.let { configMap["private_by_default"] = it.name }
        config.workInProgressByDefault?.let { configMap["work_in_progress_by_default"] = it.name }
        config.enableSignedPush?.let { configMap["enable_signed_push"] = it.name }
        config.requireSignedPush?.let { configMap["require_signed_push"] = it.name }
        config.rejectEmptyCommit?.let { configMap["reject_empty_commit"] = it.name }
        config.maxObjectSizeLimit?.let { configMap["max_object_size_limit"] = it }
        
        config.commentLinks?.let { configMap["comment_links"] = it }
        config.pluginConfig?.let { configMap["plugin_config"] = it }
        
        return configMap
    }

    private fun buildMetadataFromInput(config: ConfigInput): Map<String, Any> {
        val metadata = mutableMapOf<String, Any>()
        // Add any metadata-specific fields here
        return metadata
    }

    private fun convertLabelsFromMetadata(metadata: Map<String, Any>): Map<String, LabelTypeInfo>? {
        val labelsData = metadata["labels"] as? Map<String, Any> ?: return null
        
        return labelsData.mapValues { (_, labelData) ->
            val labelMap = labelData as? Map<String, Any> ?: return@mapValues LabelTypeInfo(
                values = emptyMap(),
                defaultValue = 0
            )
            
            LabelTypeInfo(
                values = (labelMap["values"] as? Map<String, String>) ?: emptyMap(),
                defaultValue = (labelMap["defaultValue"] as? Int) ?: 0,
                function = labelMap["function"] as? String,
                copyMinScore = labelMap["copyMinScore"] as? Boolean,
                copyMaxScore = labelMap["copyMaxScore"] as? Boolean,
                copyAllScoresIfNoChange = labelMap["copyAllScoresIfNoChange"] as? Boolean,
                copyAllScoresIfNoCodeChange = labelMap["copyAllScoresIfNoCodeChange"] as? Boolean,
                copyAllScoresOnTrivialRebase = labelMap["copyAllScoresOnTrivialRebase"] as? Boolean,
                copyAllScoresOnMergeFirstParentUpdate = labelMap["copyAllScoresOnMergeFirstParentUpdate"] as? Boolean,
                copyCondition = labelMap["copyCondition"] as? String,
                allowPostSubmit = labelMap["allowPostSubmit"] as? Boolean,
                ignoreSelfApproval = labelMap["ignoreSelfApproval"] as? Boolean
            )
        }
    }

    private fun convertWebLinksFromMetadata(metadata: Map<String, Any>): List<WebLinkInfo>? {
        val webLinksData = metadata["webLinks"] as? List<Map<String, Any>> ?: return null
        
        return webLinksData.mapNotNull { linkData ->
            val name = linkData["name"] as? String ?: return@mapNotNull null
            val url = linkData["url"] as? String ?: return@mapNotNull null
            
            WebLinkInfo(
                name = name,
                url = url,
                imageUrl = linkData["imageUrl"] as? String
            )
        }
    }

    private fun wouldCreateCircularDependency(projectName: String, parentName: String): Boolean {
        var current: String? = parentName
        val visited = mutableSetOf<String>()
        
        while (current != null && current !in visited) {
            if (current == projectName) {
                return true
            }
            visited.add(current)
            current = projectRepository.findByName(current)?.parentName
        }
        
        return false
    }
}
