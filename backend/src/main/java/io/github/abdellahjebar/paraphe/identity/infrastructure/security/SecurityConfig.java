package io.github.abdellahjebar.paraphe.identity.infrastructure.security;

import jakarta.servlet.DispatcherType;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.HeaderWriterFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

/**
 * Three separate rule sets, because the three kinds of caller must not share assumptions:
 *
 * <ol>
 *   <li>{@code /r/**}: reviewers. No account, no session, the token in the path is the credential.
 *       Rate limited, never cached, never leaked through the Referer header.
 *   <li>{@code /api/**}: the contractor. Cookie session, CSRF protection, JSON errors.
 *   <li>everything else: refused, except the health probe.
 * </ol>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig {

    static final String ROLE_USER = "USER";
    static final String ROLE_PASSWORD_CHANGE_REQUIRED = "PASSWORD_CHANGE_REQUIRED";

    /**
     * Accounts live in the database and sign-in is handled by our own endpoint. Declaring this bean
     * stops Spring Boot from creating its default user with a generated password, which it would
     * otherwise print in the log at every start.
     */
    @Bean
    UserDetailsService noContainerManagedUsers() {
        return username -> {
            throw new UsernameNotFoundException("Accounts are not managed by the container");
        };
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    RequestRateLimiter reviewerRateLimiter(Clock clock,
            @Value("${paraphe.rate-limit.reviewer-requests-per-minute:60}") int requestsPerMinute) {
        return new RequestRateLimiter(clock, requestsPerMinute, Duration.ofMinutes(1));
    }

    @Bean
    @Order(1)
    SecurityFilterChain reviewerChain(HttpSecurity http, RequestRateLimiter reviewerRateLimiter) throws Exception {
        http.securityMatcher("/r/**")
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .anyRequest().permitAll())
                // The token in the path is the credential; there is nothing for a forged request to ride on.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER))
                        .frameOptions(frame -> frame.deny())
                        .contentSecurityPolicy(csp -> csp.policyDirectives("frame-ancestors 'none'")))
                .addFilterBefore(new ReviewerRateLimitFilter(reviewerRateLimiter), HeaderWriterFilter.class);
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain apiChain(HttpSecurity http, SecurityContextRepository securityContextRepository)
            throws Exception {
        http.securityMatcher("/api/**")
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        // Reachable even when a password change is still required.
                        .requestMatchers("/api/auth/**").authenticated()
                        .anyRequest().hasRole(ROLE_USER))
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fixation -> fixation.changeSessionId()))
                .headers(headers -> headers
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER))
                        .frameOptions(frame -> frame.deny())
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31_536_000)))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) ->
                                ProblemResponses.write(response, 401, "Unauthorized"))
                        .accessDeniedHandler((request, response, exception) ->
                                ProblemResponses.write(response, 403, "Forbidden")));
        return http.build();
    }

    @Bean
    @Order(3)
    SecurityFilterChain defaultChain(HttpSecurity http,
                                     @Value("${springdoc.api-docs.enabled:false}") boolean apiDocsEnabled)
            throws Exception {
        http.authorizeHttpRequests(auth -> {
                    auth.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll();
                    auth.requestMatchers("/actuator/health", "/actuator/health/**").permitAll();
                    if (apiDocsEnabled) {
                        // Only ever on in tests and local tooling: the contract is not for the public.
                        auth.requestMatchers("/v3/api-docs", "/v3/api-docs/**").permitAll();
                    }
                    auth.anyRequest().denyAll();
                })
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER))
                        .frameOptions(frame -> frame.deny()));
        return http.build();
    }
}
