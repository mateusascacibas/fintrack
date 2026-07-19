package com.fintrack.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * SecurityConfig — Configuração do Spring Security 6.
 *
 * MUDANÇA IMPORTANTE do Spring Security 5 para 6:
 * A DSL antiga usava method chaining: http.csrf().disable().sessionManagement()...
 * A nova DSL usa lambdas: http.csrf(AbstractHttpConfigurer::disable)...
 * É mais verbosa mas mais legível e type-safe.
 *
 * WHY STATELESS session?
 * JWT é stateless — o servidor não guarda estado de sessão.
 * Cada request carrega suas próprias credenciais (o token).
 * Isso é escalável: qualquer instância do serviço pode validar qualquer token.
 * Com sessões, você precisaria de sticky sessions ou sessão distribuída (Redis).
 *
 * NOTE: UserDetailsService com InMemoryUserDetailsManager é apenas para
 * desenvolvimento inicial. No projeto real, implemente com o banco de dados
 * (UserDetailsService que busca no repositório de usuários).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final AuthenticationProvider authenticationProvider;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter,
                          AuthenticationProvider authenticationProvider) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.authenticationProvider = authenticationProvider;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            // WHY disable CSRF?
            // CSRF attacks exploram sessões baseadas em cookie.
            // Como usamos JWT (sem cookies), CSRF não se aplica.

            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers("/actuator/health", "/actuator/prometheus").permitAll()
                .anyRequest().authenticated()
            )

            .authenticationProvider(authenticationProvider)

            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        // WHY addFilterBefore?
        // O filtro JWT precisa executar ANTES do UsernamePasswordAuthenticationFilter.
        // Assim, se o JWT for válido, a autenticação já está no contexto quando
        // o filtro padrão do Spring executar.

        return http.build();
    }
}
