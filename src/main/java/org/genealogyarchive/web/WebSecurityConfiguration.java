package org.genealogyarchive.web;

import org.genealogyarchive.config.InstallationConfig;
import org.genealogyarchive.config.TransportSecurity;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;

import java.util.List;

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
 * Transport boundary (owner decision P1-C, correction GAS-P1-002): the web process refuses to start
 * on a non-loopback binding unless a TLS reverse proxy is declared, and {@link TransportSecurityFilter}
 * refuses requests that arrive neither from this computer nor through that proxy over HTTPS.
 * Built-in TLS is later-phase deployment hardening.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class WebSecurityConfiguration {

    /**
     * The effective policy, from the address the server actually binds ({@code server.address}) and the
     * declared proxies. Without installation configuration no proxy is trusted. An unacceptable
     * deployment fails the application context, so nothing is served.
     */
    @Bean
    TransportSecurity transportSecurity(Environment environment, ObjectProvider<InstallationConfig> installation) {
        InstallationConfig config = installation.getIfAvailable();
        List<String> proxies = config == null ? List.of() : config.server().tlsReverseProxyAddresses();
        return TransportSecurity.forDeployment(environment.getProperty("server.address"), proxies);
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, TransportSecurity transportSecurity) throws Exception {
        http.addFilterBefore(new TransportSecurityFilter(transportSecurity), SecurityContextHolderFilter.class);
        http.authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/login", "/css/**", "/error", "/actuator/**").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form.loginPage("/login").defaultSuccessUrl("/", true).failureUrl("/login?error"))
                .logout(logout -> logout.logoutUrl("/logout").logoutSuccessUrl("/login?signedout"))
                .sessionManagement(session -> session.sessionFixation(fixation -> fixation.changeSessionId()));
        return http.build();
    }
}
