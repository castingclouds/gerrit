package ai.fluxuate.gerrit.config

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Profile
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain

/**
 * Test security configuration that provides a mock authenticated user for integration tests.
 * Used only in test profile to enable integration testing with a consistent test user.
 */
@TestConfiguration
@EnableWebSecurity
@Profile("test")
class TestSecurityConfig {

    @Bean
    fun testSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .authorizeHttpRequests { requests ->
                requests.anyRequest().authenticated()
            }
            .httpBasic { }
            .csrf { csrf -> csrf.disable() }
            .build()
    }

    @Bean
    fun testUserDetailsService(): UserDetailsService {
        val testUser: UserDetails = User.builder()
            .username("testuser")
            .password("{noop}password") // {noop} means no password encoding
            .roles("USER")
            .build()
        
        return InMemoryUserDetailsManager(testUser)
    }
}
