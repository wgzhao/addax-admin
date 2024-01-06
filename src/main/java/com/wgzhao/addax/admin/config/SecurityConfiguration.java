// package com.wgzhao.fsbrowser.config;


// import com.wgzhao.fsbrowser.handler.LoginFailureHandler;
// import com.wgzhao.fsbrowser.handler.LoginSuccessHandler;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.context.annotation.Bean;
// import org.springframework.context.annotation.Configuration;
// import org.springframework.security.config.annotation.web.builders.HttpSecurity;
// import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
// import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
// import org.springframework.security.web.SecurityFilterChain;

// import javax.sql.DataSource;

// @Configuration
// @EnableWebSecurity
// public class SecurityConfiguration {

//     @Autowired
//     LoginSuccessHandler loginSuccessHandler;

//     @Autowired
//     LoginFailureHandler loginFailureHandler;

//     @Autowired
//     DataSource dataSource;


//     @Bean
//     public BCryptPasswordEncoder passwordEncoder() {
//         return new BCryptPasswordEncoder();
//     }

//     @Bean
//     public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
//         http
//                 .authorizeHttpRequests((requests) -> requests
//                         .requestMatchers("/login", "/logout", "/register", "/css/**", "/js/**", "/images/**").permitAll()
//                         .anyRequest().authenticated()
//                 )
//                 .formLogin((form) -> form
//                         .loginPage("/login")
//                         .defaultSuccessUrl("/home", true)
//                         .failureUrl("/login?error=true")
//                         .permitAll()
//                 )
//                 .logout((logout) -> logout.logoutSuccessUrl("/login?logout=true")
//                         .deleteCookies("JSESSIONID")
//                         .permitAll());

//         return http.build();
//     }
// }
