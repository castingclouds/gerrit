package ai.fluxuate.gerrit.git

import ai.fluxuate.gerrit.git.GitConfiguration
import ai.fluxuate.gerrit.git.GitReceivePackService
import ai.fluxuate.gerrit.git.GitRepositoryService
import ai.fluxuate.gerrit.util.ChangeIdUtil
import ai.fluxuate.gerrit.service.ChangeService
import org.eclipse.jgit.http.server.GitServlet
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.transport.*
import org.eclipse.jgit.transport.resolver.ReceivePackFactory
import org.eclipse.jgit.transport.resolver.RepositoryResolver
import org.eclipse.jgit.transport.resolver.UploadPackFactory
import org.eclipse.jgit.revwalk.RevCommit
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.boot.web.servlet.ServletRegistrationBean
import jakarta.servlet.http.HttpServletRequest

@Configuration
class GitHttpServletConfig(
    private val gitConfiguration: GitConfiguration,
    private val repositoryService: GitRepositoryService,
    private val changeService: ChangeService,
    private val gitReceivePackService: GitReceivePackService
) {

    private val logger = LoggerFactory.getLogger(GitHttpServletConfig::class.java)

    @Bean
    fun gitServlet(): ServletRegistrationBean<GitServlet> {
        val servlet = GitServlet()
        
        // Configure repository resolver
        servlet.setRepositoryResolver(RepositoryResolver<HttpServletRequest> { req, projectName ->
            val cleanProjectName = projectName.removeSuffix(".git")
            logger.debug("Resolving repository for project: $cleanProjectName")
            repositoryService.getRepository(cleanProjectName)
        })
        
        // Configure upload pack factory
        servlet.setUploadPackFactory(UploadPackFactory<HttpServletRequest> { req, repository ->
            val uploadPack = UploadPack(repository)
            uploadPack.setTimeout(gitConfiguration.fetchTimeoutSeconds.toInt())
            
            // Add virtual branch advertisement hook for HTTP
            uploadPack.setAdvertiseRefsHook(HttpVirtualBranchAdvertiseRefsHook(repository))
            
            uploadPack
        })
        
        // Configure receive pack factory
        servlet.setReceivePackFactory(ReceivePackFactory<HttpServletRequest> { req, repository ->
            val receivePack = ReceivePack(repository)
            receivePack.isAllowCreates = gitConfiguration.allowCreates
            receivePack.isAllowDeletes = gitConfiguration.allowDeletes
            receivePack.isAllowNonFastForwards = gitConfiguration.allowNonFastForwards
            receivePack.setTimeout(gitConfiguration.pushTimeoutSeconds.toInt())
            
            // Add virtual branch advertisement hook for HTTP
            receivePack.setAdvertiseRefsHook(HttpVirtualBranchAdvertiseRefsHook(repository))
            
            // Add pre-receive hook for processing refs
            receivePack.setPreReceiveHook(HttpPreReceiveHook(req))
            
            // Add post-receive hook for notifications and processing
            receivePack.setPostReceiveHook(HttpPostReceiveHook())
            
            receivePack
        })
        
        // Disable file serving
        servlet.setAsIsFileService(null)
        
        val registration = ServletRegistrationBean(servlet, "/git/*")
        registration.setLoadOnStartup(1)
        return registration
    }
    
    /**
     * HTTP-specific virtual branch advertisement hook.
     * Handles ref advertisement for both upload-pack (fetch) and receive-pack (push) operations.
     */
    private inner class HttpVirtualBranchAdvertiseRefsHook(
        private val repository: Repository
    ) : org.eclipse.jgit.transport.AdvertiseRefsHook {
        
        override fun advertiseRefs(up: org.eclipse.jgit.transport.UploadPack) {
            logger.debug("Advertising refs for HTTP upload-pack on repository: ${repository.directory}")
            
            try {
                // Apply permission-based ref filtering
                filterRefsForUpload(up)
                
                // Advertise virtual branches for changes
                advertiseVirtualBranchesForUpload(up)
                
                // Filter out internal Gerrit refs
                filterInternalRefsForUpload(up)
                
            } catch (e: Exception) {
                logger.error("Error advertising refs for HTTP upload-pack", e)
            }
        }
        
        override fun advertiseRefs(rp: org.eclipse.jgit.transport.ReceivePack) {
            logger.debug("Advertising refs for HTTP receive-pack on repository: ${repository.directory}")
            
            try {
                // Apply permission-based ref filtering
                filterRefsForReceive(rp)
                
                // Advertise virtual branches for changes
                advertiseVirtualBranches(rp)
                
                // Filter out internal Gerrit refs
                filterInternalRefs(rp)
                
            } catch (e: Exception) {
                logger.error("Error advertising refs for HTTP receive-pack", e)
            }
        }
        
        private fun filterRefsForUpload(up: org.eclipse.jgit.transport.UploadPack) {
            try {
                val refDatabase = repository.refDatabase
                val allRefs = refDatabase.refs
                val allowedRefs = mutableMapOf<String, org.eclipse.jgit.lib.Ref>()
                
                for (ref in allRefs) {
                    val refName = ref.name
                    if (canAccessRef(refName)) {
                        allowedRefs[refName] = ref
                    } else {
                        logger.debug("Filtered out ref for HTTP upload: $refName")
                    }
                }
                
                logger.debug("Advertising ${allowedRefs.size} refs out of ${allRefs.size} total refs for HTTP upload")
                
            } catch (e: Exception) {
                logger.error("Error filtering refs for HTTP upload", e)
            }
        }
        
        private fun advertiseVirtualBranchesForUpload(up: org.eclipse.jgit.transport.UploadPack) {
            try {
                val projectName = repository.directory.name ?: return
                
                logger.debug("Advertising virtual branches for HTTP upload in project: $projectName")
                
                // Get virtual branches from the change service
                val virtualBranches = changeService.getVirtualBranchesForProject(projectName)
                
                // Add virtual branches to the advertised refs for fetching
                for ((refName, commitId) in virtualBranches) {
                    try {
                        val ref = org.eclipse.jgit.lib.ObjectIdRef.PeeledNonTag(
                            org.eclipse.jgit.lib.Ref.Storage.LOOSE,
                            refName,
                            org.eclipse.jgit.lib.ObjectId.fromString(commitId)
                        )
                        up.getAdvertisedRefs().put(refName, ref)
                        
                    } catch (e: Exception) {
                        logger.warn("Error creating ref for virtual branch $refName", e)
                    }
                }
                
                logger.debug("Advertised ${virtualBranches.size} virtual branches for HTTP upload in project: $projectName")
                
            } catch (e: Exception) {
                logger.error("Error advertising virtual branches for HTTP upload", e)
            }
        }
        
        private fun filterInternalRefsForUpload(up: org.eclipse.jgit.transport.UploadPack) {
            try {
                val internalRefPrefixes = listOf(
                    "refs/meta/",           // Gerrit metadata refs
                    "refs/users/",          // User-specific refs (unless accessing own)
                    "refs/groups/",         // Group-specific refs
                    "refs/cache-automerge/" // Auto-merge cache refs
                )
                
                logger.debug("Filtering internal refs for HTTP upload: ${internalRefPrefixes.joinToString(", ")}")
                
                // In a full implementation, we would remove these refs from advertisement
                
            } catch (e: Exception) {
                logger.error("Error filtering internal refs for HTTP upload", e)
            }
        }
        
        private fun filterRefsForReceive(rp: org.eclipse.jgit.transport.ReceivePack) {
            // Filter refs based on user permissions for push operations
            try {
                val refDatabase = repository.refDatabase
                val allRefs = refDatabase.refs
                val allowedRefs = mutableMapOf<String, org.eclipse.jgit.lib.Ref>()
                
                for (ref in allRefs) {
                    val refName = ref.name
                    if (canPushToRef(refName)) {
                        allowedRefs[refName] = ref
                    } else {
                        logger.debug("Filtered out ref for HTTP push: $refName")
                    }
                }
                
                logger.debug("Advertising ${allowedRefs.size} refs out of ${allRefs.size} total refs for HTTP push")
                
            } catch (e: Exception) {
                logger.error("Error filtering refs for HTTP receive-pack", e)
            }
        }
        
        private fun advertiseVirtualBranches(rp: org.eclipse.jgit.transport.ReceivePack) {
            try {
                val projectName = repository.directory.name ?: return
                
                logger.debug("Advertising virtual branches for HTTP push in project: $projectName")
                
                // Get virtual branches from the change service
                val virtualBranches = changeService.getVirtualBranchesForProject(projectName)
                
                // Add virtual branches to the advertised refs
                for ((refName, commitId) in virtualBranches) {
                    try {
                        val ref = org.eclipse.jgit.lib.ObjectIdRef.PeeledNonTag(
                            org.eclipse.jgit.lib.Ref.Storage.LOOSE,
                            refName,
                            org.eclipse.jgit.lib.ObjectId.fromString(commitId)
                        )
                        rp.getAdvertisedRefs().put(refName, ref)
                        
                    } catch (e: Exception) {
                        logger.warn("Error creating ref for virtual branch $refName", e)
                    }
                }
                
                logger.debug("Advertised ${virtualBranches.size} virtual branches for HTTP push in project: $projectName")
                
            } catch (e: Exception) {
                logger.error("Error advertising virtual branches for HTTP push", e)
            }
        }
        
        private fun filterInternalRefs(rp: org.eclipse.jgit.transport.ReceivePack) {
            // Filter out internal Gerrit refs that shouldn't be advertised
            val internalRefPrefixes = listOf(
                "refs/meta/",           // Gerrit metadata refs
                "refs/users/",          // User-specific refs
                "refs/groups/",         // Group-specific refs
                "refs/cache-automerge/" // Auto-merge cache refs
            )
            
            try {
                logger.debug("Filtering internal refs for HTTP push: ${internalRefPrefixes.joinToString(", ")}")
                
                // In a full implementation, we would remove these refs from advertisement
                
            } catch (e: Exception) {
                logger.error("Error filtering internal refs for HTTP push", e)
            }
        }
        
        private fun canAccessRef(refName: String): Boolean {
            // Check if the current user can access this ref
            return when {
                refName.startsWith("refs/heads/") -> true // Allow access to branch refs
                refName.startsWith("refs/tags/") -> true  // Allow access to tag refs
                refName.startsWith("refs/changes/") -> true // Allow access to change refs
                refName.startsWith("refs/meta/") -> false // Deny access to internal refs
                refName.startsWith("refs/users/") -> false // Deny access to user refs
                else -> true // Default to allowing access
            }
        }
        
        private fun canPushToRef(refName: String): Boolean {
            // Check if the current user can push to this ref
            return when {
                refName.startsWith("refs/for/") -> true // Allow pushes to refs/for/ for code review
                refName == "refs/heads/${gitConfiguration.trunkBranchName}" -> true // Allow pushes to trunk branch
                refName.startsWith("refs/heads/") -> false // Don't allow direct pushes to other branches in trunk-based workflow
                refName.startsWith("refs/tags/") -> false // Don't allow direct tag creation
                refName.startsWith("refs/changes/") -> false // Don't allow direct pushes to virtual branches
                refName.startsWith("refs/meta/") -> false // Don't allow pushes to internal refs
                refName.startsWith("refs/users/") -> false // Don't allow pushes to user refs
                else -> false // Default to denying access
            }
        }
    }
    
    private inner class HttpPreReceiveHook(private val request: HttpServletRequest) : PreReceiveHook {
        override fun onPreReceive(rp: ReceivePack, commands: Collection<ReceiveCommand>) {
            logger.info("HTTP Pre-receive hook processing ${commands.size} commands")
            
            val projectName = extractProjectNameFromRequest(request)
            
            for (command in commands) {
                val result = gitReceivePackService.processReceiveCommand(command, rp.repository, projectName)
                
                if (result.success) {
                    command.setResult(ReceiveCommand.Result.OK)
                } else {
                    command.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, result.message)
                }
            }
        }
    }
    
    private fun extractProjectNameFromRequest(request: HttpServletRequest): String {
        val pathInfo = request.pathInfo ?: ""
        val segments = pathInfo.split("/").filter { it.isNotEmpty() }
        
        return if (segments.isNotEmpty()) {
            segments[0].removeSuffix(".git")
        } else {
            "unknown"
        }
    }
    
    private inner class HttpPostReceiveHook : PostReceiveHook {
        override fun onPostReceive(rp: ReceivePack, commands: MutableCollection<ReceiveCommand>) {
            // Delegate all post-receive processing to the unified handler
            gitReceivePackService.handlePostReceive(commands, rp.repository)
        }
    }
}
