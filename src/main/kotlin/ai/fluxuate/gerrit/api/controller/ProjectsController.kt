package ai.fluxuate.gerrit.api.controller

import ai.fluxuate.gerrit.api.dto.*
import ai.fluxuate.gerrit.service.ProjectService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * REST controller for Gerrit Projects API.
 * Implements endpoints matching legacy Gerrit API structure.
 */
@RestController
@RequestMapping("/a/projects")
class ProjectsController(
    private val projectService: ProjectService
) {

    /**
     * Query projects with optional filters.
     * GET /a/projects/
     */
    @GetMapping("/")
    fun queryProjects(
        @RequestParam(required = false) query: String?,
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) start: Int?,
        @RequestParam(required = false) type: String?,
        @RequestParam(required = false) description: Boolean?,
        @RequestParam(required = false) tree: Boolean?,
        @RequestParam(required = false) branches: List<String>?,
        @RequestParam(required = false) all: Boolean?
    ): ResponseEntity<Map<String, ProjectInfo>> {
        val projects = projectService.queryProjects(
            query = query,
            limit = limit,
            start = start,
            type = type,
            includeDescription = description ?: true,
            includeTree = tree ?: false,
            branches = branches,
            includeAll = all ?: false
        )
        
        return ResponseEntity.ok(projects)
    }

    /**
     * Get project information.
     * GET /a/projects/{project-name}
     */
    @GetMapping("/{projectName}")
    fun getProject(
        @PathVariable projectName: String
    ): ResponseEntity<ProjectInfo> {
        val project = projectService.getProject(projectName)
        return ResponseEntity.ok(project)
    }

    /**
     * Create a new project.
     * PUT /a/projects/{project-name}
     */
    @PutMapping("/{projectName}")
    fun createProject(
        @PathVariable projectName: String,
        @Valid @RequestBody input: ProjectInput
    ): ResponseEntity<ProjectInfo> {
        val project = projectService.createProject(projectName, input)
        return ResponseEntity.status(HttpStatus.CREATED).body(project)
    }

    /**
     * Update project configuration.
     * PUT /a/projects/{project-name}/config
     */
    @PutMapping("/{projectName}/config")
    fun updateProjectConfig(
        @PathVariable projectName: String,
        @Valid @RequestBody config: ConfigInput
    ): ResponseEntity<ProjectInfo> {
        val project = projectService.updateProjectConfig(projectName, config)
        return ResponseEntity.ok(project)
    }

    /**
     * Delete a project.
     * DELETE /a/projects/{project-name}
     */
    @DeleteMapping("/{projectName}")
    fun deleteProject(
        @PathVariable projectName: String,
        @RequestParam(required = false) force: Boolean?
    ): ResponseEntity<Void> {
        projectService.deleteProject(projectName, force ?: false)
        return ResponseEntity.noContent().build()
    }

    /**
     * Get project description.
     * GET /a/projects/{project-name}/description
     */
    @GetMapping("/{projectName}/description")
    fun getDescription(
        @PathVariable projectName: String
    ): ResponseEntity<String> {
        val description = projectService.getDescription(projectName)
        return ResponseEntity.ok(description)
    }

    /**
     * Set project description.
     * PUT /a/projects/{project-name}/description
     */
    @PutMapping("/{projectName}/description")
    fun setDescription(
        @PathVariable projectName: String,
        @Valid @RequestBody input: DescriptionInput
    ): ResponseEntity<String> {
        val description = projectService.setDescription(projectName, input)
        return ResponseEntity.ok(description)
    }

    /**
     * Get project parent.
     * GET /a/projects/{project-name}/parent
     */
    @GetMapping("/{projectName}/parent")
    fun getParent(
        @PathVariable projectName: String
    ): ResponseEntity<String> {
        val parent = projectService.getParent(projectName)
        return ResponseEntity.ok(parent)
    }

    /**
     * Set project parent.
     * PUT /a/projects/{project-name}/parent
     */
    @PutMapping("/{projectName}/parent")
    fun setParent(
        @PathVariable projectName: String,
        @Valid @RequestBody input: ParentInput
    ): ResponseEntity<String> {
        val parent = projectService.setParent(projectName, input)
        return ResponseEntity.ok(parent)
    }

    /**
     * Get child projects.
     * GET /a/projects/{project-name}/children
     */
    @GetMapping("/{projectName}/children")
    fun getChildren(
        @PathVariable projectName: String,
        @RequestParam(required = false) recursive: Boolean?
    ): ResponseEntity<List<ProjectInfo>> {
        val children = projectService.getChildren(projectName, recursive ?: false)
        return ResponseEntity.ok(children)
    }

    /**
     * Get project HEAD.
     * GET /a/projects/{project-name}/HEAD
     */
    @GetMapping("/{projectName}/HEAD")
    fun getHead(
        @PathVariable projectName: String
    ): ResponseEntity<String> {
        val head = projectService.getHead(projectName)
        return ResponseEntity.ok(head)
    }

    /**
     * Set project HEAD.
     * PUT /a/projects/{project-name}/HEAD
     */
    @PutMapping("/{projectName}/HEAD")
    fun setHead(
        @PathVariable projectName: String,
        @Valid @RequestBody input: HeadInput
    ): ResponseEntity<String> {
        val head = projectService.setHead(projectName, input)
        return ResponseEntity.ok(head)
    }
}
