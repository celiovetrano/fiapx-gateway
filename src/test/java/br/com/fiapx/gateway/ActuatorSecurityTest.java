package br.com.fiapx.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Só /actuator/health/** é público (probes do k8s e HEALTHCHECK do Docker). O
 * Service do EKS é LoadBalancer público, então o resto do /actuator/**
 * (ex.: prometheus) precisa de token, caindo no anyExchange().authenticated().
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ActuatorSecurityTest {

    @MockBean
    ReactiveJwtDecoder jwtDecoder;

    @Autowired
    WebTestClient client;

    @Test
    void healthReadinessEPublicoSemToken() {
        client.get().uri("/actuator/health/readiness")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void prometheusExigeTokenSemToken() {
        client.get().uri("/actuator/prometheus")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
