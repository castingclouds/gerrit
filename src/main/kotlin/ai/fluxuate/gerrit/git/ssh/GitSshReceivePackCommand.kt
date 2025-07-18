package ai.fluxuate.gerrit.git.ssh

import ai.fluxuate.gerrit.git.GitConfiguration
import ai.fluxuate.gerrit.git.GitRepositoryService
import ai.fluxuate.gerrit.service.ChangeService
import ai.fluxuate.gerrit.service.ChangeIdService
import org.eclipse.jgit.transport.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component


class GitSshReceivePackCommand(
    gitConfiguration: GitConfiguration,
    repositoryService: GitRepositoryService,
    private val changeService: ChangeService,
    private val changeIdService: ChangeIdService
) : AbstractGitSshCommand(gitConfiguration, repositoryService) {

    override fun runImpl() {
        try {
            logger.info("Starting git-receive-pack for repository: ${repository?.directory}")
            
            val repo = repository ?: throw IllegalStateException("Repository not available")
            val receivePack = ReceivePack(repo)
            
            // Configure receive pack based on configuration
            receivePack.setAllowCreates(gitConfiguration.allowCreates)
            receivePack.setAllowDeletes(gitConfiguration.allowDeletes)
            receivePack.setAllowNonFastForwards(gitConfiguration.allowNonFastForwards)
            
            // Set up hooks for Gerrit-specific processing
            receivePack.setPreReceiveHook(GerritPreReceiveHook())
            receivePack.setPostReceiveHook(GerritPostReceiveHook())
            
            // Set up advertise refs hook for virtual branch advertisement
            receivePack.setAdvertiseRefsHook(GerritReceiveAdvertiseRefsHook())
            
            val inputStream = getInputStream() ?: throw IllegalStateException("Input stream not available")
            val outputStream = getOutputStream() ?: throw IllegalStateException("Output stream not available")
            val errorStream = getErrorStream() ?: throw IllegalStateException("Error stream not available")
            
            // Execute the receive pack operation
            receivePack.receive(inputStream, outputStream, errorStream)
            
            logger.info("Completed git-receive-pack operation")
            
        } catch (e: Exception) {
            logger.error("Error in git-receive-pack", e)
            throw e
        }
    }


    private inner class GerritPreReceiveHook : PreReceiveHook {
        override fun onPreReceive(rp: ReceivePack, commands: Collection<ReceiveCommand>) {
            logger.info("Pre-receive hook processing ${commands.size} commands")
            
            for (command in commands) {
                val refName = command.refName
                logger.debug("Processing ref: $refName")
                
                // Handle magic branch refs/for/*
                if (refName.startsWith("refs/for/")) {
                    processMagicBranch(command)
                } else {
                    // Handle regular refs (direct push to branches)
                    processRegularRef(command)
                }
            }
        }
        
        private fun processMagicBranch(command: ReceiveCommand) {
            val refName = command.refName
            val targetBranch = refName.removePrefix("refs/for/")
            
            logger.info("Processing magic branch push to: $targetBranch")
            
            try {
                val repo = repository ?: throw IllegalStateException("Repository not available")
                
                // Get the commit being pushed
                val commit = repo.parseCommit(command.newId)
                
                // Extract Change-Id from commit message
                val changeId = changeIdService.extractChangeId(commit.fullMessage)
                
                if (changeId == null) {
                    // Generate Change-Id if missing
                    val generatedChangeId = changeIdService.generateChangeId(
                        treeId = commit.tree.id,
                        parentIds = commit.parents.map { it.id },
                        author = commit.authorIdent,
                        committer = commit.committerIdent,
                        commitMessage = commit.fullMessage
                    )
                    logger.info("Generated Change-Id: $generatedChangeId for commit: ${commit.id.name}")
                    
                    // TODO: In a full implementation, we might want to reject the push
                    // and ask the user to add the Change-Id to their commit message
                    command.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, 
                        "Missing Change-Id in commit message. Please add Change-Id: $generatedChangeId")
                    return
                }
                
                // Process the change (create or update)
                val result = changeService.processChange(
                    changeId = changeId,
                    commit = commit,
                    targetBranch = targetBranch,
                    repository = repo
                )
                
                if (result.success) {
                    logger.info("Successfully processed change: ${result.message}")
                    command.setResult(ReceiveCommand.Result.OK)
                } else {
                    logger.warn("Failed to process change: ${result.message}")
                    command.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, result.message)
                }
                
            } catch (e: Exception) {
                logger.error("Error processing magic branch: $refName", e)
                command.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, 
                    "Internal error processing change: ${e.message}")
            }
        }
        
        private fun processRegularRef(command: ReceiveCommand) {
            val refName = command.refName
            logger.debug("Processing regular ref: $refName")
            
            // TODO: Implement regular ref processing
            // - Validate permissions for direct branch pushes
            // - Apply branch protection rules
            // - Validate commit messages and content
            
            // For now, allow all regular ref updates
            command.setResult(ReceiveCommand.Result.OK)
        }
    }

    /**
     * Post-receive hook for Gerrit-specific post-processing.
     */
    private inner class GerritPostReceiveHook : PostReceiveHook {
        override fun onPostReceive(rp: ReceivePack, commands: Collection<ReceiveCommand>) {
            logger.info("Post-receive hook processing ${commands.size} commands")
            
            for (command in commands) {
                if (command.result == ReceiveCommand.Result.OK) {
                    logger.info("Successfully processed ref: ${command.refName}")
                    
                    // TODO: Implement Gerrit-specific post-receive processing
                    // - Send notifications to reviewers
                    // - Update change status
                    // - Trigger CI/CD pipelines
                    // - Update metrics and statistics
                }
            }
        }
    }

    /**
     * Advertise refs hook for receive-pack operations.
     * Controls which refs are advertised to the client.
     */
    private inner class GerritReceiveAdvertiseRefsHook : AdvertiseRefsHook {
        override fun advertiseRefs(rp: ReceivePack) {
            logger.debug("Advertising refs for receive-pack on repository: ${repository?.directory}")

            // TODO: Implement Gerrit-specific ref advertisement for receive-pack
            // - Filter out internal Gerrit refs
            // - Apply permission-based ref filtering
            // - Advertise only refs the user can push to
            
            // For now, use default behavior
        }

        override fun advertiseRefs(up: UploadPack) {
            throw UnsupportedOperationException("ReceiveAdvertiseRefsHook cannot be used for UploadPack")
        }
    }
}
