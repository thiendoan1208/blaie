package com.blaie.blaie_be.core.security;

import com.blaie.blaie_be.auth.infrastructure.security.AuthRequestFilter;
import com.blaie.blaie_be.auth.infrastructure.security.AuthCookieService;
import com.blaie.blaie_be.auth.infrastructure.security.EmailVerificationRequiredFilter;
import com.blaie.blaie_be.auth.infrastructure.security.AuthProperties;
import com.blaie.blaie_be.auth.infrastructure.security.BearerTokenResolver;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthRequestFilter authRequestFilter,
            EmailVerificationRequiredFilter emailVerificationRequiredFilter,
            AppAuthenticationEntryPoint authenticationEntryPoint,
            AppAccessDeniedHandler accessDeniedHandler,
            CookieCsrfTokenRepository csrfTokenRepository,
            CorsConfigurationSource corsConfigurationSource
    ) throws Exception {
        CsrfTokenRequestAttributeHandler csrfRequestHandler = new CsrfTokenRequestAttributeHandler();
        return http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(csrfRequestHandler)
                        .requireCsrfProtectionMatcher(cookieCsrfProtectionMatcher()))
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/logout",
                                "/api/v1/auth/csrf",
                                "/api/v1/auth/email/verify",
                                "/api/v1/auth/password-reset/request",
                                "/api/v1/auth/password-reset/confirm",
                                "/api/v1/auth/google/start",
                                "/api/v1/auth/google/callback",
                                "/error"
                        ).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .addFilterBefore(authRequestFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(emailVerificationRequiredFilter, AuthRequestFilter.class)
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    CookieCsrfTokenRepository csrfTokenRepository(AuthProperties authProperties) {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookiePath("/");
        repository.setCookieCustomizer(cookie -> cookie
                .secure(authProperties.cookieSecure())
                .sameSite(authProperties.cookieSameSite()));
        return repository;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(SecurityCorsProperties corsProperties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                HttpHeaders.CONTENT_TYPE,
                HttpHeaders.AUTHORIZATION,
                "X-XSRF-TOKEN",
                "X-Request-ID"
        ));
        configuration.setExposedHeaders(List.of("X-Request-ID"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    UserDetailsService userDetailsService() {
        // Prevent Spring Boot from auto-configuring a generated default user.
        return username -> {
            throw new UsernameNotFoundException("Username/password authentication is handled by the auth module");
        };
    }

    private RequestMatcher cookieCsrfProtectionMatcher() {
        return request -> CsrfFilter.DEFAULT_CSRF_MATCHER.matches(request) && !isBearerOnlyRequest(request);
    }

    private boolean isBearerOnlyRequest(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        return BearerTokenResolver.resolve(authorization).isPresent()
                && !hasAuthCookie(request);
    }

    private boolean hasAuthCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return false;
        }
        for (Cookie cookie : request.getCookies()) {
            if (AuthCookieService.ACCESS_COOKIE_NAME.equals(cookie.getName())
                    || AuthCookieService.REFRESH_COOKIE_NAME.equals(cookie.getName())) {
                return true;
            }
        }
        return false;
    }
}
