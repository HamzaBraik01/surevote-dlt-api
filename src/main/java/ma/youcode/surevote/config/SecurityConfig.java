package ma.youcode.surevote.config;

import lombok.RequiredArgsConstructor;
import ma.youcode.surevote.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.ForwardedHeaderFilter;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final UserDetailsService userDetailsService;
    private final Environment environment;

    @Value("${cors.allowed-origins:http://localhost:4200}")
    private String allowedOrigins;

    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/auth/**",
            "/api/elections/*/results",
            "/public/**",
            "/actuator/health"
    };

    private static final String[] SWAGGER_ENDPOINTS = {
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/api-docs/**",
            "/api-docs.yaml"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // SEC-5: HTTP Security Headers
            .headers(headers -> headers
                .frameOptions(frame -> frame.deny())
                .contentTypeOptions(contentType -> {})
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31536000)
                )
                .cacheControl(cache -> {})
            )

            .authorizeHttpRequests(auth -> {
                // Public endpoints
                auth.requestMatchers(PUBLIC_ENDPOINTS).permitAll();
                auth.requestMatchers(HttpMethod.GET, "/api/elections").permitAll();
                auth.requestMatchers(HttpMethod.GET, "/api/elections/{id}").permitAll();
                auth.requestMatchers(HttpMethod.GET, "/api/elections/{id}/candidates").permitAll();

                // SEC-4: Swagger — public only in dev/test profiles
                if (isDevOrTestProfile()) {
                    auth.requestMatchers(SWAGGER_ENDPOINTS).permitAll();
                } else {
                    auth.requestMatchers(SWAGGER_ENDPOINTS).hasRole("ADMIN");
                }

                // Admin-only endpoints
                auth.requestMatchers("/api/admin/**").hasRole("ADMIN");

                // Voter endpoints
                auth.requestMatchers("/api/vote/**").hasRole("ELECTEUR");
                auth.requestMatchers("/api/voter/**").hasRole("ELECTEUR");

                // Observer endpoints (also accessible by ADMIN)
                auth.requestMatchers("/api/observer/**").hasAnyRole("ADMIN", "OBSERVATEUR");
                auth.requestMatchers(HttpMethod.GET, "/api/admin/audit-logs").hasAnyRole("ADMIN", "OBSERVATEUR");

                // Authenticated users
                auth.requestMatchers("/api/auth/refresh").authenticated();
                auth.requestMatchers("/api/auth/2fa/verify").hasRole("ELECTEUR");

                // All other requests require authentication
                auth.anyRequest().authenticated();
            })
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * SEC-3: ForwardedHeaderFilter — safely processes X-Forwarded-* headers
     * from trusted reverse proxies. Without this, X-Forwarded-For can be spoofed.
     */
    @Bean
    public ForwardedHeaderFilter forwardedHeaderFilter() {
        return new ForwardedHeaderFilter();
    }

    private boolean isDevOrTestProfile() {
        for (String profile : environment.getActiveProfiles()) {
            if ("dev".equalsIgnoreCase(profile)
                    || "test".equalsIgnoreCase(profile)
                    || "default".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        // If no profiles are active, treat as dev (default behavior)
        return environment.getActiveProfiles().length == 0;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of(allowedOrigins.split(",")));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(Arrays.asList(
                "Authorization", "Content-Type", "Accept",
                "X-Requested-With", "Origin", "Cache-Control"
        ));
        config.setExposedHeaders(List.of("Authorization", "Content-Disposition"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
