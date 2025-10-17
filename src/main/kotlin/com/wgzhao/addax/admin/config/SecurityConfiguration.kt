package com.wgzhao.addax.admin.config

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.userdetails.User
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.provisioning.JdbcUserDetailsManager
import org.springframework.security.provisioning.UserDetailsManager
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import javax.sql.DataSource

@Configuration
@EnableWebSecurity
class SecurityConfiguration {
    @Autowired
    var jwtFilter: JwtFilter? = null

    @Bean
    @Throws(Exception::class)
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http.csrf { it.disable() }
            .cors { it.disable() }
            .authorizeHttpRequests {
                it.requestMatchers("/auth/**").permitAll()
                    .requestMatchers("/addax/**").permitAll()
                    .requestMatchers("/log/job-report").permitAll()
                    .requestMatchers(
                        "/dashboard/**",
                        "/etl/**",
                        "/alert/**",
                        "/log/**",
                        "/monitor/**",
                        "/param/**",
                        "/plan/**",
                        "/risk/**",
                        "/source/**",
                        "/table/**",
                        "/task/**"
                    ).authenticated()
                    .anyRequest().permitAll()
            }
            .httpBasic { }
            .formLogin { }
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter::class.java)
            .exceptionHandling {
                it.authenticationEntryPoint(CustomAuthenticationEntryPoint())
                it.accessDeniedHandler(CustomAccessDeniedHandler())
            }

        return http.build()
    }


    @Bean
    fun passwordEncoder(): PasswordEncoder {
        return BCryptPasswordEncoder()
    }

    @Bean
    @Throws(Exception::class)
    fun authenticationManager(config: AuthenticationConfiguration): AuthenticationManager? {
        return config.getAuthenticationManager()
    }

    @Bean
    fun users(dataSource: DataSource?, passwordEncoder: PasswordEncoder): UserDetailsManager {
        val user = User.builder()
            .username("user")
            .password(passwordEncoder.encode("user123"))
            .roles("USER")
            .build()
        val admin = User.builder()
            .username("admin")
            .password(passwordEncoder.encode("389f89beb8d7"))
            .roles("USER", "ADMIN")
            .build()
        val users = JdbcUserDetailsManager(dataSource)
        if (!users.userExists("user")) {
            users.createUser(user)
        }
        if (!users.userExists("admin")) {
            users.createUser(admin)
        }
        return users
    }


    @Bean
    fun corsConfigurer(): WebMvcConfigurer {
        return object : WebMvcConfigurer {
            override fun addCorsMappings(registry: CorsRegistry) {
                registry.addMapping("/**")
                    .allowedOrigins("*")
                    .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            }
        }
    }
}