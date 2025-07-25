package ai.fluxuate.gerrit.git

import org.eclipse.jgit.http.server.GitServlet
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.transport.ReceivePack
import org.eclipse.jgit.transport.UploadPack
import org.eclipse.jgit.transport.resolver.ReceivePackFactory
import org.eclipse.jgit.transport.resolver.RepositoryResolver
import org.eclipse.jgit.transport.resolver.UploadPackFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.boot.web.servlet.ServletRegistrationBean
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory

@Configuration
class GitHttpServletConfig(
    private val gitRepositoryService: GitRepositoryService,
    private val gitConfiguration: GitConfiguration
) {

    private val logger = LoggerFactory.getLogger(GitHttpServletConfig::class.java)

    @Bean
    fun gitServlet(): ServletRegistrationBean<GitServlet> {
        val servlet = GitServlet()
        
        // Configure repository resolver
        servlet.setRepositoryResolver(RepositoryResolver<HttpServletRequest> { req, projectName ->
            val cleanProjectName = projectName.removeSuffix(".git")
            logger.debug("Resolving repository for project: $cleanProjectName")
            gitRepositoryService.getRepository(cleanProjectName)
        })
        
        // Configure upload pack factory
        servlet.setUploadPackFactory(UploadPackFactory<HttpServletRequest> { req, repository ->
            val uploadPack = UploadPack(repository)
            uploadPack.setTimeout(gitConfiguration.fetchTimeoutSeconds.toInt())
            uploadPack
        })
        
        // Configure receive pack factory
        servlet.setReceivePackFactory(ReceivePackFactory<HttpServletRequest> { req, repository ->
            val receivePack = ReceivePack(repository)
            receivePack.isAllowCreates = gitConfiguration.allowCreates
            receivePack.isAllowDeletes = gitConfiguration.allowDeletes
            receivePack.isAllowNonFastForwards = gitConfiguration.allowNonFastForwards
            receivePack.setTimeout(gitConfiguration.pushTimeoutSeconds.toInt())
            receivePack
        })
        
        // Disable file serving
        servlet.setAsIsFileService(null)
        
        val registration = ServletRegistrationBean(servlet, "/git/*")
        registration.setLoadOnStartup(1)
        return registration
    }
}
