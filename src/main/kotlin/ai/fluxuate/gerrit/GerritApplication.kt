package ai.fluxuate.gerrit

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class GerritApplication {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<GerritApplication>(*args)
        }
    }
}
