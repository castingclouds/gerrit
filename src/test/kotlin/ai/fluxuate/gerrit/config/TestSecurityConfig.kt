package ai.fluxuate.gerrit.config

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Profile
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain

/**
 * Test security configuration that allows all requests without authentication.
 * Used only in test profile to enable integration testing without authentication.
 */
@TestConfiguration
@EnableWebSecurity
@Profile("test")
class TestSecurityConfig {

    @Bean
    fun testSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .authorizeHttpRequests { requests ->
                requests.anyRequest().permitAll()
            }
            .csrf { csrf -> csrf.disable() }
            .build()
    }
}
