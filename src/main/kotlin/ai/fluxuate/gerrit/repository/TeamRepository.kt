package ai.fluxuate.gerrit.repository

import ai.fluxuate.gerrit.model.TeamEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface TeamRepository : JpaRepository<TeamEntity, Long> {
    
    fun findByName(name: String): Optional<TeamEntity>
    
    fun findByUuid(uuid: String): Optional<TeamEntity>
    
    fun existsByName(name: String): Boolean
    
    fun existsByUuid(uuid: String): Boolean
    
    @Query("""
        SELECT * FROM teams t 
        WHERE (:name IS NULL OR LOWER(t.name) LIKE LOWER(CONCAT('%', :name, '%')))
        AND (:description IS NULL OR LOWER(t.description) LIKE LOWER(CONCAT('%', :description, '%')))
        AND (:visibleToAll IS NULL OR t.visible_to_all = :visibleToAll)
        AND (:ownerId IS NULL OR t.owner_id = :ownerId)
        ORDER BY t.name
    """, nativeQuery = true)
    fun queryTeams(
        @Param("name") name: String?,
        @Param("description") description: String?,
        @Param("visibleToAll") visibleToAll: Boolean?,
        @Param("ownerId") ownerId: String?,
        pageable: Pageable
    ): Page<TeamEntity>
    
    @Query("""
        SELECT * FROM teams t 
        WHERE t.members LIKE CONCAT('%"', :userId, '"%')
        ORDER BY t.name
    """, nativeQuery = true)
    fun findTeamsByMember(@Param("userId") userId: String): List<TeamEntity>
    
    @Query("""
        SELECT * FROM teams t 
        WHERE t.subteams LIKE CONCAT('%"', :teamId, '"%')
        ORDER BY t.name
    """, nativeQuery = true)
    fun findTeamsBySubteam(@Param("teamId") teamId: String): List<TeamEntity>
    
    @Query("""
        SELECT t FROM TeamEntity t 
        WHERE t.ownerId = :ownerId
        ORDER BY t.name
    """)
    fun findTeamsByOwner(@Param("ownerId") ownerId: String): List<TeamEntity>
    
    @Query("""
        SELECT t FROM TeamEntity t 
        WHERE t.visibleToAll = true
        ORDER BY t.name
    """)
    fun findVisibleTeams(): List<TeamEntity>
}
