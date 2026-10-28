package org.genealogyarchive.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Local sign-in and session security (BUILD-SPEC s.43). No external identity provider.
 * <ul>
 *   <li>Public: the operational status page, sign-in page, static assets and the health endpoints
 *       used by the service managers (health shows no details; nothing else is exposed).</li>
 *   <li>Everything else requires a signed-in user. Role-based authorization is decided in the
 *       services, not here, so it cannot be bypassed by calling a URL directly.</li>
 *   <li>CSRF protection, session fixation protection (new session at sign-in), HttpOnly and
 *       SameSite=Strict session cookies, idle timeout (application.properties).</li>
 * </ul>
 * Plain-HTTP LAN sign-in is not enabled by default: the server binds to loopback unless the
 * installation deliberately changes it (owner decision C); TLS is later-phase deployment hardening.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class WebSecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/login", "/css/**", "/error", "/actuator/**").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form.loginPage("/login").defaultSuccessUrl("/", true).failureUrl("/login?error"))
                .logout(logout -> logout.logoutUrl("/logout").logoutSuccessUrl("/login?signedout"))
                .sessionManagement(session -> session.sessionFixation(fixation -> fixation.changeSessionId()));
        return http.build();
    }
}
