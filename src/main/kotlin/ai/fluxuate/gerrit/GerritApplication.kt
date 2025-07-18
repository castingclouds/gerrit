package ai.fluxuate.gerrit

import ai.fluxuate.gerrit.git.GitConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(GitConfiguration::class)
class GerritApplication {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<GerritApplication>(*args)
        }
    }
}
