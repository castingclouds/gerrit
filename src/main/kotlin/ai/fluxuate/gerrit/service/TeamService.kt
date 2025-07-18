package ai.fluxuate.gerrit.service

import ai.fluxuate.gerrit.api.dto.*
import ai.fluxuate.gerrit.api.exception.BadRequestException
import ai.fluxuate.gerrit.api.exception.ConflictException
import ai.fluxuate.gerrit.api.exception.NotFoundException
import ai.fluxuate.gerrit.model.TeamEntity
import ai.fluxuate.gerrit.repository.TeamRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
@Transactional
class TeamService(
    private val teamRepository: TeamRepository,
    private val accountService: AccountService
) {
    private val logger = LoggerFactory.getLogger(TeamService::class.java)

    fun queryTeams(
        name: String? = null,
        description: String? = null,
        visibleToAll: Boolean? = null,
        ownerId: String? = null,
        limit: Int = 25,
        start: Int = 0
    ): Map<String, TeamInfo> {
        val pageable: Pageable = PageRequest.of(start / limit, limit)
        val page: Page<TeamEntity> = teamRepository.queryTeams(name, description, visibleToAll, ownerId, pageable)
        
        return page.content.associate { entity ->
            entity.name to toTeamInfo(entity)
        }
    }

    fun getTeam(teamId: String): TeamInfo {
        val entity = resolveTeam(teamId)
        return toTeamInfo(entity)
    }

    fun getTeamDetail(teamId: String): TeamInfo {
        val entity = resolveTeam(teamId)
        return toTeamInfo(entity, includeMembers = true, includeSubteams = true)
    }

    fun createTeam(name: String, input: TeamInput): TeamInfo {
        if (teamRepository.existsByName(name)) {
            throw ConflictException("Team '$name' already exists")
        }

        val uuid = input.uuid ?: java.util.UUID.randomUUID().toString()
        if (teamRepository.existsByUuid(uuid)) {
            throw ConflictException("Team with UUID '$uuid' already exists")
        }

        val members = mutableListOf<String>()
        input.members?.forEach { memberId ->
            // Validate member exists
            try {
                accountService.getAccount(memberId)
                members.add(memberId)
            } catch (e: NotFoundException) {
                throw BadRequestException("Member '$memberId' not found")
            }
        }

        val entity = TeamEntity(
            name = name,
            uuid = uuid,
            description = input.description,
            visibleToAll = input.visibleToAll ?: false,
            ownerId = input.ownerId,
            createdOn = Instant.now(),
            members = members
        )

        val savedEntity = teamRepository.save(entity)
        logger.info("Created team: ${savedEntity.name} (${savedEntity.uuid})")
        
        return toTeamInfo(savedEntity)
    }

    fun updateTeam(teamId: String, input: TeamInput): TeamInfo {
        val entity = resolveTeam(teamId)
        
        val updatedEntity = entity.copy(
            description = input.description ?: entity.description,
            visibleToAll = input.visibleToAll ?: entity.visibleToAll,
            ownerId = input.ownerId ?: entity.ownerId
        )
        
        val savedEntity = teamRepository.save(updatedEntity)
        logger.info("Updated team: ${savedEntity.name}")
        
        return toTeamInfo(savedEntity)
    }

    fun deleteTeam(teamId: String) {
        val entity = resolveTeam(teamId)
        logger.info("Deleting team: ${entity.name}, UUID: ${entity.uuid}, ID: ${entity.id}")
        
        // Note: For simplicity, we allow deletion of teams that might be referenced as subteams.
        // In a production system, you might want to either:
        // 1. Remove the team from all parent teams' subteams arrays, or
        // 2. Prevent deletion if the team is referenced elsewhere
        // For now, we proceed with deletion to keep the API simple.
        
        teamRepository.delete(entity)
        logger.info("Deleted team: ${entity.name}")
    }

    // Member Management
    fun getTeamMembers(teamId: String): List<AccountInfo> {
        val entity = resolveTeam(teamId)
        return entity.members.mapNotNull { memberId ->
            try {
                accountService.getAccount(memberId)
            } catch (e: NotFoundException) {
                logger.warn("Team member not found: $memberId in team ${entity.name}")
                null
            }
        }
    }

    fun addTeamMember(teamId: String, memberId: String): AccountInfo {
        val entity = resolveTeam(teamId)
        val account = accountService.getAccount(memberId)
        
        if (!entity.members.contains(memberId)) {
            entity.members.add(memberId)
            teamRepository.save(entity)
            logger.info("Added member $memberId to team ${entity.name}")
        }
        
        return account
    }

    fun addTeamMembers(teamId: String, input: MembersInput): List<AccountInfo> {
        val entity = resolveTeam(teamId)
        val memberIds = mutableListOf<String>()
        
        input.oneMember?.let { memberIds.add(it) }
        input.members?.let { memberIds.addAll(it) }
        
        val accounts = memberIds.map { memberId ->
            val account = accountService.getAccount(memberId)
            if (!entity.members.contains(memberId)) {
                entity.members.add(memberId)
            }
            account
        }
        
        teamRepository.save(entity)
        logger.info("Added ${accounts.size} members to team ${entity.name}")
        
        return accounts
    }

    fun removeTeamMember(teamId: String, memberId: String) {
        val entity = resolveTeam(teamId)
        
        if (entity.members.remove(memberId)) {
            teamRepository.save(entity)
            logger.info("Removed member $memberId from team ${entity.name}")
        }
    }

    fun removeTeamMembers(teamId: String, input: MembersInput) {
        val entity = resolveTeam(teamId)
        val memberIds = mutableListOf<String>()
        
        input.oneMember?.let { memberIds.add(it) }
        input.members?.let { memberIds.addAll(it) }
        
        var removed = 0
        memberIds.forEach { memberId ->
            if (entity.members.remove(memberId)) {
                removed++
            }
        }
        
        if (removed > 0) {
            teamRepository.save(entity)
            logger.info("Removed $removed members from team ${entity.name}")
        }
    }

    // Subteam Management
    fun getTeamSubteams(teamId: String): List<TeamInfo> {
        val entity = resolveTeam(teamId)
        return entity.subteams.mapNotNull { subteamId ->
            try {
                val subteam = resolveTeam(subteamId)
                toTeamInfo(subteam)
            } catch (e: NotFoundException) {
                logger.warn("Subteam not found: $subteamId in team ${entity.name}")
                null
            }
        }
    }

    fun addTeamSubteam(teamId: String, subteamId: String): TeamInfo {
        val entity = resolveTeam(teamId)
        val subteam = resolveTeam(subteamId)
        
        // Prevent circular references
        if (wouldCreateCircularReference(entity.uuid, subteam.uuid)) {
            throw BadRequestException("Adding subteam would create circular reference")
        }
        
        if (!entity.subteams.contains(subteam.uuid)) {
            entity.subteams.add(subteam.uuid)
            teamRepository.save(entity)
            logger.info("Added subteam ${subteam.name} to team ${entity.name}")
        }
        
        return toTeamInfo(subteam)
    }

    fun addTeamSubteams(teamId: String, input: TeamsInput): List<TeamInfo> {
        val entity = resolveTeam(teamId)
        val subteamIds = mutableListOf<String>()
        
        input.oneGroup?.let { subteamIds.add(it) }
        input.groups?.let { subteamIds.addAll(it) }
        
        val subteams = subteamIds.map { subteamId ->
            val subteam = resolveTeam(subteamId)
            
            // Prevent circular references
            if (wouldCreateCircularReference(entity.uuid, subteam.uuid)) {
                throw BadRequestException("Adding subteam ${subteam.name} would create circular reference")
            }
            
            if (!entity.subteams.contains(subteam.uuid)) {
                entity.subteams.add(subteam.uuid)
            }
            toTeamInfo(subteam)
        }
        
        teamRepository.save(entity)
        logger.info("Added ${subteams.size} subteams to team ${entity.name}")
        
        return subteams
    }

    fun removeTeamSubteam(teamId: String, subteamId: String) {
        val entity = resolveTeam(teamId)
        val subteam = resolveTeam(subteamId)
        
        if (entity.subteams.remove(subteam.uuid)) {
            teamRepository.save(entity)
            logger.info("Removed subteam ${subteam.name} from team ${entity.name}")
        }
    }

    fun removeTeamSubteams(teamId: String, input: TeamsInput) {
        val entity = resolveTeam(teamId)
        val subteamIds = mutableListOf<String>()
        
        input.oneGroup?.let { subteamIds.add(it) }
        input.groups?.let { subteamIds.addAll(it) }
        
        var removed = 0
        subteamIds.forEach { subteamId ->
            try {
                val subteam = resolveTeam(subteamId)
                if (entity.subteams.remove(subteam.uuid)) {
                    removed++
                }
            } catch (e: NotFoundException) {
                logger.warn("Subteam not found for removal: $subteamId")
            }
        }
        
        if (removed > 0) {
            teamRepository.save(entity)
            logger.info("Removed $removed subteams from team ${entity.name}")
        }
    }

    // Helper Methods
    private fun resolveTeam(teamId: String): TeamEntity {
        return when {
            teamId.matches(Regex("\\d+")) -> {
                // Numeric ID
                teamRepository.findById(teamId.toLong())
                    .orElseThrow { NotFoundException("Team with ID '$teamId' not found") }
            }
            teamId.matches(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) -> {
                // UUID (proper UUID format)
                teamRepository.findByUuid(teamId)
                    .orElseThrow { NotFoundException("Team with UUID '$teamId' not found") }
            }
            else -> {
                // Team name
                teamRepository.findByName(teamId)
                    .orElseThrow { NotFoundException("Team '$teamId' not found") }
            }
        }
    }

    private fun toTeamInfo(
        entity: TeamEntity, 
        includeMembers: Boolean = false, 
        includeSubteams: Boolean = false
    ): TeamInfo {
        val members = if (includeMembers) {
            entity.members.mapNotNull { memberId ->
                try {
                    accountService.getAccount(memberId)
                } catch (e: NotFoundException) {
                    null
                }
            }
        } else null

        val subteams = if (includeSubteams) {
            entity.subteams.mapNotNull { subteamId ->
                try {
                    val subteam = resolveTeam(subteamId)
                    toTeamInfo(subteam)
                } catch (e: NotFoundException) {
                    null
                }
            }
        } else null

        return TeamInfo(
            id = entity.uuid,
            name = entity.name,
            url = "#/admin/teams/uuid-${entity.uuid}",
            options = TeamOptionsInfo(visibleToAll = entity.visibleToAll),
            description = entity.description,
            groupId = entity.id?.toInt(),
            owner = entity.ownerId?.let { 
                try { 
                    resolveTeam(it).name 
                } catch (e: NotFoundException) { 
                    null 
                } 
            },
            ownerId = entity.ownerId,
            createdOn = entity.createdOn,
            members = members,
            includes = subteams
        )
    }

    private fun wouldCreateCircularReference(parentUuid: String, childUuid: String): Boolean {
        if (parentUuid == childUuid) return true
        
        try {
            val childEntity = teamRepository.findByUuid(childUuid).orElse(null) ?: return false
            return childEntity.subteams.any { subteamUuid ->
                wouldCreateCircularReference(parentUuid, subteamUuid)
            }
        } catch (e: Exception) {
            return false
        }
    }
}
