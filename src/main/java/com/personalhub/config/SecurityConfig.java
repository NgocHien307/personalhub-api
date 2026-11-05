package com.personalhub.config;

import com.personalhub.common.ApiProblems;
import com.personalhub.common.RequestIdFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http, @Value("${app.security.api-key}") String apiKey, JsonMapper mapper) throws Exception {
        ApiKeyFilter apiKeyFilter = new ApiKeyFilter(apiKey);
        http.csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> h.contentSecurityPolicy(c -> c.policyDirectives(
                        "default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self'; "
                        + "connect-src 'self'; font-src 'self'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.GET, dashboardPaths()).permitAll()
                        .requestMatchers(HttpMethod.HEAD, dashboardPaths()).permitAll()
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().denyAll())
                .addFilterBefore(apiKeyFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new RequestIdFilter(), ApiKeyFilter.class)
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((request, response, exception) ->
                                ApiProblems.write(request, response, HttpStatus.UNAUTHORIZED, mapper))
                        .accessDeniedHandler((request, response, exception) ->
                                ApiProblems.write(request, response, HttpStatus.FORBIDDEN, mapper)));
        return http.build();
    }

    private static String[] dashboardPaths() {
        // Public shell/assets only; personal data continues to require the existing API key.
        return new String[]{"/dashboard", "/dashboard/", "/dashboard/index.html", "/dashboard/dashboard.css",
                "/dashboard/core.mjs", "/dashboard/app.mjs", "/dashboard/icons.svg"};
    }
}
