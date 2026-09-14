package br.com.fiapx.gateway.ratelimit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;

/** Contém força bruta no login (spec §9): N tentativas por minuto e por IP. */
@Component
public class LoginRateLimitFilter implements GlobalFilter, Ordered {

    private static final String LOGIN = "/api/v1/auth/login";
    private static final byte[] PROBLEMA = """
            {"type":"about:blank","title":"Muitas tentativas de login","status":429,\
            "detail":"Aguarde um minuto antes de tentar novamente."}"""
            .getBytes(StandardCharsets.UTF_8);

    private final FixedWindowRateLimiter limiter;

    public LoginRateLimitFilter(
            @Value("${fiapx.gateway.login-rate-limit.max-attempts}") int maxAttempts,
            @Value("${fiapx.gateway.login-rate-limit.window}") Duration window) {
        this.limiter = new FixedWindowRateLimiter(maxAttempts, window, Clock.systemUTC());
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        boolean login = HttpMethod.POST.equals(request.getMethod())
                && LOGIN.equals(request.getPath().value());
        if (!login || limiter.tryAcquire(cliente(request))) {
            return chain.filter(exchange);
        }
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        DataBuffer corpo = response.bufferFactory().wrap(PROBLEMA);
        return response.writeWith(Mono.just(corpo));
    }

    /**
     * X-Forwarded-For é ignorado de propósito: é enviado pelo próprio cliente, que
     * poderia forjar um IP novo a cada tentativa e nunca esbarrar no limite. Usamos o
     * endereço TCP do peer, que o cliente não controla. No EKS, para que esse
     * endereço seja o IP real do usuário (e não o do load balancer), o Service do
     * gateway precisa de externalTrafficPolicy: Local — isso é tratado em outra tarefa.
     */
    private static String cliente(ServerHttpRequest request) {
        InetSocketAddress remoto = request.getRemoteAddress();
        return remoto != null ? remoto.getAddress().getHostAddress() : "desconhecido";
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
