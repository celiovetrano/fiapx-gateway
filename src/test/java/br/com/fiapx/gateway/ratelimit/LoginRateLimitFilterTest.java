package br.com.fiapx.gateway.ratelimit;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "fiapx.gateway.login-rate-limit.max-attempts=3")
class LoginRateLimitFilterTest {

    static final MockWebServer auth = new MockWebServer();

    @DynamicPropertySource
    static void rotas(DynamicPropertyRegistry registry) throws IOException {
        auth.start();
        String url = "http://" + auth.getHostName() + ":" + auth.getPort();
        registry.add("AUTH_SERVICE_URL", () -> url);
        registry.add("VIDEO_API_URL", () -> url);
    }

    @AfterAll
    static void parar() throws IOException {
        auth.shutdown();
    }

    @MockBean
    ReactiveJwtDecoder jwtDecoder;

    @Autowired
    WebTestClient client;

    private WebTestClient.ResponseSpec post(String caminho) {
        return client.post().uri(caminho)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"email\":\"a@b.com\",\"password\":\"errada123\",\"fullName\":\"A\"}")
                .exchange();
    }

    private WebTestClient.ResponseSpec postComForwardedForFalso(String caminho, String ipFalso) {
        return client.post().uri(caminho)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Forwarded-For", ipFalso)
                .bodyValue("{\"email\":\"a@b.com\",\"password\":\"errada123\",\"fullName\":\"A\"}")
                .exchange();
    }

    @Test
    void quartaTentativaDeLoginNoMesmoMinutoRecebe429MesmoComXForwardedForForjado() {
        int antes = auth.getRequestCount();
        for (int i = 0; i < 3; i++) {
            auth.enqueue(new MockResponse().setResponseCode(401)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE)
                    .setBody("{\"title\":\"Credenciais invalidas\"}"));
            // Cada tentativa alega vir de um IP diferente; como o filtro ignora o
            // X-Forwarded-For (controlado pelo cliente) e conta pelo endereço TCP real
            // do peer, a falsificação não deve escapar do limite.
            postComForwardedForFalso("/api/v1/auth/login", "10.0.0." + i).expectStatus().isUnauthorized();
        }

        postComForwardedForFalso("/api/v1/auth/login", "10.0.0.99")
                .expectStatus().isEqualTo(429)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.title").isEqualTo("Muitas tentativas de login");

        // A quarta tentativa nem chega ao auth-service.
        assertThat(auth.getRequestCount() - antes).isEqualTo(3);
    }

    @Test
    void cadastroNaoEntraNoLimiteDoLogin() {
        for (int i = 0; i < 4; i++) {
            auth.enqueue(new MockResponse().setResponseCode(201)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody("{}"));
            post("/api/v1/auth/register").expectStatus().isCreated();
        }
    }
}
