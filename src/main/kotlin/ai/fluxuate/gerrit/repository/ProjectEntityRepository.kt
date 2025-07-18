package ai.fluxuate.gerrit.repository

import ai.fluxuate.gerrit.model.ProjectEntity
import ai.fluxuate.gerrit.model.ProjectState
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

/**
 * Clean JPA repository for ProjectEntity, avoiding complex embedded types.
 */
@Repository
interface ProjectEntityRepository : JpaRepository<ProjectEntity, Int> {

    /**
     * Find a project by name.
     */
    fun findByName(name: String): ProjectEntity?

    /**
     * Find projects by parent name.
     */
    fun findByParentName(parentName: String, pageable: Pageable): Page<ProjectEntity>

    /**
     * Find projects by state.
     */
    fun findByState(state: ProjectState, pageable: Pageable): Page<ProjectEntity>

    /**
     * Find projects by name containing text (case-insensitive).
     */
    fun findByNameContainingIgnoreCase(name: String, pageable: Pageable): Page<ProjectEntity>

    /**
     * Find projects with specific configuration using JSONB query.
     */
    @Query("""
        SELECT p FROM ProjectEntity p 
        WHERE jsonb_extract_path_text(p.config, :key) = :value
    """)
    fun findByConfigValue(
        @Param("key") key: String,
        @Param("value") value: String,
        pageable: Pageable
    ): Page<ProjectEntity>

    /**
     * Find all active projects.
     */
    fun findByStateNot(state: ProjectState, pageable: Pageable): Page<ProjectEntity>

    /**
     * Check if project name exists.
     */
    fun existsByName(name: String): Boolean
}
