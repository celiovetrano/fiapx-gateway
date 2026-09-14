package br.com.fiapx.gateway;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRoutingTest {

    static final MockWebServer auth = new MockWebServer();
    static final MockWebServer videos = new MockWebServer();

    @DynamicPropertySource
    static void rotas(DynamicPropertyRegistry registry) throws IOException {
        auth.start();
        videos.start();
        registry.add("AUTH_SERVICE_URL", () -> "http://" + auth.getHostName() + ":" + auth.getPort());
        registry.add("VIDEO_API_URL", () -> "http://" + videos.getHostName() + ":" + videos.getPort());
    }

    @AfterAll
    static void parar() throws IOException {
        auth.shutdown();
        videos.shutdown();
    }

    @MockBean
    ReactiveJwtDecoder jwtDecoder;

    @Autowired
    WebTestClient client;

    @Test
    void loginERoteadoParaOAuthServiceSemToken() throws Exception {
        auth.enqueue(new MockResponse().setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"accessToken\":\"abc\"}"));

        client.post().uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"email\":\"a@b.com\",\"password\":\"fiapx2026\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.accessToken").isEqualTo("abc");

        RecordedRequest recebida = auth.takeRequest(5, TimeUnit.SECONDS);
        assertThat(recebida).isNotNull();
        assertThat(recebida.getPath()).isEqualTo("/api/v1/auth/login");
    }

    @Test
    void videosSemTokenRecebem401NoGateway() {
        client.get().uri("/api/v1/videos")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void tokenInvalidoRecebe401() {
        when(jwtDecoder.decode("token-invalido")).thenReturn(Mono.error(new BadJwtException("assinatura")));

        client.get().uri("/api/v1/videos")
                .header(HttpHeaders.AUTHORIZATION, "Bearer token-invalido")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void videosComTokenValidoSaoRoteadosComOAuthorization() throws Exception {
        Jwt jwt = Jwt.withTokenValue("token-valido")
                .header("alg", "RS256")
                .subject(UUID.randomUUID().toString())
                .claim("email", "aluno@fiap.com.br")
                .build();
        when(jwtDecoder.decode("token-valido")).thenReturn(Mono.just(jwt));
        videos.enqueue(new MockResponse().setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"items\":[],\"total\":0}"));

        client.get().uri("/api/v1/videos")
                .header(HttpHeaders.AUTHORIZATION, "Bearer token-valido")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.total").isEqualTo(0);

        // Defesa em profundidade (spec §9): o video-api revalida o mesmo token.
        RecordedRequest recebida = videos.takeRequest(5, TimeUnit.SECONDS);
        assertThat(recebida).isNotNull();
        assertThat(recebida.getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer token-valido");
    }
}
