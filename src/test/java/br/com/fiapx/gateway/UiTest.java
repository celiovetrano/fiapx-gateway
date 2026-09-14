package br.com.fiapx.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UiTest {

    @MockBean
    ReactiveJwtDecoder jwtDecoder;

    @Autowired
    WebTestClient client;

    @Test
    void paginaInicialServeAUiSemToken() {
        client.get().uri("/")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
                .expectBody(String.class)
                .value(html -> assertThat(html).contains("FIAP X").contains("/app.js"));
    }

    @Test
    void scriptDaUiEPublico() {
        client.get().uri("/app.js")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(js -> assertThat(js).contains("/api/v1/videos"));
    }
}
