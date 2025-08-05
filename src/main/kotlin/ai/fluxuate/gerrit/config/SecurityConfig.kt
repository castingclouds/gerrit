package ai.fluxuate.gerrit.config

import ai.fluxuate.gerrit.service.AccountService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

/**
 * Security configuration for the Gerrit application.
 * Configures authentication and authorization rules.
 */
@Configuration
@EnableWebSecurity
@Profile("!test") // Don't apply this configuration in test profile
class SecurityConfig(
    private val accountService: AccountService
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .cors { cors -> cors.configurationSource(corsConfigurationSource()) }
            .csrf { csrf -> csrf.disable() }
            .sessionManagement { session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS) 
            }
            .authorizeHttpRequests { requests ->
                requests
                    // Allow public access to authentication endpoints
                    .requestMatchers("/api/login", "/api/register").permitAll()
                    // Allow OPTIONS requests for CORS preflight
                    .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                    // Git tools and hooks - allow public access
                    .requestMatchers("/tools/hooks/**").permitAll()
                    // Git HTTP protocol - allow anonymous read, require auth for write
                    .requestMatchers("/git/*/git-upload-pack").permitAll()
                    .requestMatchers("/git/*/info/refs").permitAll()
                    .requestMatchers("/git/*/HEAD").permitAll()
                    .requestMatchers("/git/*/objects/**").permitAll()
                    .requestMatchers("/git/*/git-receive-pack").authenticated()
                    // All other requests require authentication
                    .anyRequest().authenticated()
            }
            .httpBasic { basic ->
                basic.realmName("Gerrit Code Review")
            }
            .authenticationProvider(authenticationProvider())
            .build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()
        configuration.allowedOriginPatterns = listOf("*")
        configuration.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
        configuration.allowedHeaders = listOf("*")
        configuration.allowCredentials = true
        configuration.maxAge = 3600L

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }

    @Bean
    fun authenticationProvider(): AuthenticationProvider {
        return object : AuthenticationProvider {
            override fun authenticate(authentication: Authentication): Authentication? {
                val username = authentication.name
                val password = authentication.credentials.toString()

                return if (accountService.verifyPassword(username, password)) {
                    UsernamePasswordAuthenticationToken(
                        username,
                        password,
                        listOf(SimpleGrantedAuthority("ROLE_USER"))
                    )
                } else {
                    null
                }
            }

            override fun supports(authentication: Class<*>): Boolean {
                return authentication == UsernamePasswordAuthenticationToken::class.java
            }
        }
    }

    @Bean
    fun authenticationManager(authConfig: AuthenticationConfiguration): AuthenticationManager {
        return authConfig.authenticationManager
    }
}
