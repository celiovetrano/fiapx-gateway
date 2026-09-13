package br.com.fiapx.gateway.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Janela fixa em memória, por chave (IP do cliente). Cada réplica do gateway conta as
 * suas próprias tentativas: com 2 réplicas o limite efetivo dobra, o que é aceitável
 * para conter força bruta. Um limite global exigiria Redis (corte 1 da spec §19).
 */
public class FixedWindowRateLimiter {

    private record Janela(Instant inicio, int contagem) {
    }

    private final int maxRequests;
    private final Duration window;
    private final Clock clock;
    private final ConcurrentHashMap<String, Janela> janelas = new ConcurrentHashMap<>();

    public FixedWindowRateLimiter(int maxRequests, Duration window, Clock clock) {
        this.maxRequests = maxRequests;
        this.window = window;
        this.clock = clock;
    }

    public boolean tryAcquire(String key) {
        Instant agora = clock.instant();
        Janela janela = janelas.compute(key, (chave, atual) ->
                atual == null || !agora.isBefore(atual.inicio().plus(window))
                        ? new Janela(agora, 1)
                        : new Janela(atual.inicio(), atual.contagem() + 1));
        return janela.contagem() <= maxRequests;
    }
}
