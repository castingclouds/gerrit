package ai.fluxuate.gerrit.api.controller

import ai.fluxuate.gerrit.api.dto.*
import ai.fluxuate.gerrit.service.TeamService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/a/teams")
class TeamsController(
    private val teamService: TeamService
) {

    @GetMapping("/")
    fun queryTeams(
        @RequestParam(required = false) name: String?,
        @RequestParam(required = false) description: String?,
        @RequestParam(required = false) visibleToAll: Boolean?,
        @RequestParam(required = false) ownerId: String?,
        @RequestParam(defaultValue = "25") limit: Int,
        @RequestParam(defaultValue = "0") start: Int
    ): ResponseEntity<Map<String, TeamInfo>> {
        val teams = teamService.queryTeams(name, description, visibleToAll, ownerId, limit, start)
        return ResponseEntity.ok(teams)
    }

    @GetMapping("/{teamId}")
    fun getTeam(@PathVariable teamId: String): ResponseEntity<TeamInfo> {
        val team = teamService.getTeam(teamId)
        return ResponseEntity.ok(team)
    }

    @PostMapping("/{name}")
    fun createTeam(
        @PathVariable name: String,
        @RequestBody input: TeamInput
    ): ResponseEntity<TeamInfo> {
        val team = teamService.createTeam(name, input)
        return ResponseEntity.status(HttpStatus.CREATED).body(team)
    }

    @PutMapping("/{teamId}")
    fun updateTeam(
        @PathVariable teamId: String,
        @RequestBody input: TeamInput
    ): ResponseEntity<TeamInfo> {
        val team = teamService.updateTeam(teamId, input)
        return ResponseEntity.ok(team)
    }

    @DeleteMapping("/{teamId}")
    fun deleteTeam(@PathVariable teamId: String): ResponseEntity<Void> {
        teamService.deleteTeam(teamId)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/{teamId}/detail")
    fun getTeamDetail(@PathVariable teamId: String): ResponseEntity<TeamInfo> {
        val team = teamService.getTeamDetail(teamId)
        return ResponseEntity.ok(team)
    }

    // Member Management Endpoints
    @GetMapping("/{teamId}/members/")
    fun getTeamMembers(@PathVariable teamId: String): ResponseEntity<List<AccountInfo>> {
        val members = teamService.getTeamMembers(teamId)
        return ResponseEntity.ok(members)
    }

    @PutMapping("/{teamId}/members/{memberId}")
    fun addTeamMember(
        @PathVariable teamId: String,
        @PathVariable memberId: String
    ): ResponseEntity<AccountInfo> {
        val member = teamService.addTeamMember(teamId, memberId)
        return ResponseEntity.status(HttpStatus.CREATED).body(member)
    }

    @PostMapping("/{teamId}/members")
    fun addTeamMembers(
        @PathVariable teamId: String,
        @RequestBody input: MembersInput
    ): ResponseEntity<List<AccountInfo>> {
        val members = teamService.addTeamMembers(teamId, input)
        return ResponseEntity.ok(members)
    }

    @PostMapping("/{teamId}/members.add")
    fun addTeamMembersAlternate(
        @PathVariable teamId: String,
        @RequestBody input: MembersInput
    ): ResponseEntity<List<AccountInfo>> {
        val members = teamService.addTeamMembers(teamId, input)
        return ResponseEntity.ok(members)
    }

    @DeleteMapping("/{teamId}/members/{memberId}")
    fun removeTeamMember(
        @PathVariable teamId: String,
        @PathVariable memberId: String
    ): ResponseEntity<Void> {
        teamService.removeTeamMember(teamId, memberId)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{teamId}/members.delete")
    fun removeTeamMembers(
        @PathVariable teamId: String,
        @RequestBody input: MembersInput
    ): ResponseEntity<Void> {
        teamService.removeTeamMembers(teamId, input)
        return ResponseEntity.noContent().build()
    }

    // Subteam Management Endpoints
    @GetMapping("/{teamId}/teams/")
    fun getTeamSubteams(@PathVariable teamId: String): ResponseEntity<List<TeamInfo>> {
        val subteams = teamService.getTeamSubteams(teamId)
        return ResponseEntity.ok(subteams)
    }

    @PutMapping("/{teamId}/teams/{subteamId}")
    fun addTeamSubteam(
        @PathVariable teamId: String,
        @PathVariable subteamId: String
    ): ResponseEntity<TeamInfo> {
        val subteam = teamService.addTeamSubteam(teamId, subteamId)
        return ResponseEntity.status(HttpStatus.CREATED).body(subteam)
    }

    @PostMapping("/{teamId}/teams")
    fun addTeamSubteams(
        @PathVariable teamId: String,
        @RequestBody input: TeamsInput
    ): ResponseEntity<List<TeamInfo>> {
        val subteams = teamService.addTeamSubteams(teamId, input)
        return ResponseEntity.ok(subteams)
    }

    @PostMapping("/{teamId}/teams.add")
    fun addTeamSubteamsAlternate(
        @PathVariable teamId: String,
        @RequestBody input: TeamsInput
    ): ResponseEntity<List<TeamInfo>> {
        val subteams = teamService.addTeamSubteams(teamId, input)
        return ResponseEntity.ok(subteams)
    }

    @DeleteMapping("/{teamId}/teams/{subteamId}")
    fun removeTeamSubteam(
        @PathVariable teamId: String,
        @PathVariable subteamId: String
    ): ResponseEntity<Void> {
        teamService.removeTeamSubteam(teamId, subteamId)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{teamId}/teams.delete")
    fun removeTeamSubteams(
        @PathVariable teamId: String,
        @RequestBody input: TeamsInput
    ): ResponseEntity<Void> {
        teamService.removeTeamSubteams(teamId, input)
        return ResponseEntity.noContent().build()
    }
}
