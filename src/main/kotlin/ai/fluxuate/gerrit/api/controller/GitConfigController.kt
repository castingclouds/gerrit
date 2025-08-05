package ai.fluxuate.gerrit.api.controller

import ai.fluxuate.gerrit.git.GitConfiguration
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/a/config/git")
class GitConfigController(
    private val gitConfiguration: GitConfiguration
) {

    @GetMapping("/default-branch")
    fun getDefaultBranch(): ResponseEntity<DefaultBranchResponse> {
        return ResponseEntity.ok(DefaultBranchResponse(gitConfiguration.trunkBranchName))
    }

    @PutMapping("/default-branch")
    fun setDefaultBranch(@Valid @RequestBody request: SetDefaultBranchRequest): ResponseEntity<DefaultBranchResponse> {
        gitConfiguration.trunkBranchName = request.branchName
        return ResponseEntity.ok(DefaultBranchResponse(gitConfiguration.trunkBranchName))
    }

    @GetMapping("/")
    fun getGitConfig(): ResponseEntity<GitConfigResponse> {
        return ResponseEntity.ok(
            GitConfigResponse(
                defaultBranch = gitConfiguration.trunkBranchName,
                repositoryBasePath = gitConfiguration.repositoryBasePath,
                httpEnabled = gitConfiguration.httpEnabled,
                httpPort = gitConfiguration.httpPort,
                sshEnabled = gitConfiguration.sshEnabled,
                sshHost = gitConfiguration.sshHost,
                sshPort = gitConfiguration.sshPort,
                anonymousReadEnabled = gitConfiguration.anonymousReadEnabled
            )
        )
    }

    data class DefaultBranchResponse(
        val branchName: String
    )

    data class SetDefaultBranchRequest(
        @field:NotBlank(message = "Branch name cannot be blank")
        val branchName: String
    )

    data class GitConfigResponse(
        val defaultBranch: String,
        val repositoryBasePath: String,
        val httpEnabled: Boolean,
        val httpPort: Int,
        val sshEnabled: Boolean,
        val sshHost: String,
        val sshPort: Int,
        val anonymousReadEnabled: Boolean
    )
}
