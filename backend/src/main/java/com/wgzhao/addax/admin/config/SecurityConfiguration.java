package com.wgzhao.addax.admin.config;

import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.beans.factory.annotation.Value;

import javax.sql.DataSource;

@Configuration
@EnableWebSecurity
@AllArgsConstructor
public class SecurityConfiguration
{
    private final JwtFilter jwtFilter;
    private final CustomAuthenticationEntryPoint authenticationEntryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http)
        throws Exception
    {
        http.csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())
            .authorizeHttpRequests((authorize) -> authorize
                // 公开接口
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/auth/login").permitAll()
                .requestMatchers(HttpMethod.POST, "/auth/change-password").authenticated()
                .requestMatchers("/addax/**").permitAll()
                .requestMatchers("/log/job-report")
                .permitAll()
                // 只读接口：登录即可
                .requestMatchers(HttpMethod.GET, "/**").authenticated()
                // 写操作接口：仅 admin
                .requestMatchers(HttpMethod.POST, "/**").hasAuthority("admin")
                .requestMatchers(HttpMethod.PUT, "/**").hasAuthority("admin")
                .requestMatchers(HttpMethod.PATCH, "/**").hasAuthority("admin")
                .requestMatchers(HttpMethod.DELETE, "/**").hasAuthority("admin")
                // 其他情况默认需要登录
                .anyRequest().authenticated()
            )
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling((exceptionHandling) -> exceptionHandling
                // 无 token/无效 token -> 401
                .authenticationEntryPoint(authenticationEntryPoint)
                // 已认证但权限不足 -> 403
                .accessDeniedHandler(accessDeniedHandler)
            );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder()
    {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
        throws Exception
    {
        return config.getAuthenticationManager();
    }

    @Bean
    public UserDetailsService userDetailsService(DataSource dataSource) {
        // 只返回JdbcUserDetailsManager，不创建用户
        return new JdbcUserDetailsManager(dataSource);
    }

    @Bean
    public WebMvcConfigurer corsConfigurer(@Value("${cors.allowed-origins:*}") String allowedOrigins)
    {
        return new WebMvcConfigurer()
        {
            @Override
            public void addCorsMappings(CorsRegistry registry)
            {
                registry.addMapping("/**")
                    .allowedOrigins(allowedOrigins.split(","))
                    .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS");
            }
        };
    }
}
