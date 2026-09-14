package br.com.fiapx.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * O gateway valida o JWT antes de rotear; cada serviço revalida o mesmo token
 * (defesa em profundidade, spec §9). Nenhum header é injetado para os serviços.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchange -> exchange
                        .pathMatchers("/", "/index.html", "/app.js", "/styles.css", "/favicon.ico").permitAll()
                        // Só health fica público: é o único usado pelas probes do k8s e pelo
                        // HEALTHCHECK do Docker. O Service do EKS é LoadBalancer público, então
                        // o resto do /actuator/** (ex.: prometheus, info) exige token.
                        .pathMatchers("/actuator/health/**", "/.well-known/**").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login").permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()))
                .build();
    }
}
