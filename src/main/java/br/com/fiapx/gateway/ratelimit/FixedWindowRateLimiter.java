package br.com.fiapx.gateway.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Janela fixa em memória, por chave (IP do cliente). Cada réplica do gateway conta as
 * suas próprias tentativas: com 2 réplicas o limite efetivo dobra, o que é aceitável
 * para conter força bruta. Um limite global exigiria Redis (corte 1 da spec §19).
 *
 * <p>Chaves expiradas são purgadas no máximo uma vez por janela (via CAS em
 * {@code ultimaLimpeza}), para que um atacante trocando de IP não faça o mapa crescer
 * sem limite.
 */
public class FixedWindowRateLimiter {

    private record Janela(Instant inicio, int contagem) {
    }

    private final int maxRequests;
    private final Duration window;
    private final Clock clock;
    private final ConcurrentHashMap<String, Janela> janelas = new ConcurrentHashMap<>();
    private final AtomicReference<Instant> ultimaLimpeza;

    public FixedWindowRateLimiter(int maxRequests, Duration window, Clock clock) {
        this.maxRequests = maxRequests;
        this.window = window;
        this.clock = clock;
        this.ultimaLimpeza = new AtomicReference<>(clock.instant());
    }

    public boolean tryAcquire(String key) {
        Instant agora = clock.instant();
        Janela janela = janelas.compute(key, (chave, atual) ->
                atual == null || !agora.isBefore(atual.inicio().plus(window))
                        ? new Janela(agora, 1)
                        : new Janela(atual.inicio(), atual.contagem() + 1));
        purgarSeNecessario(agora);
        return janela.contagem() <= maxRequests;
    }

    private void purgarSeNecessario(Instant agora) {
        Instant ultima = ultimaLimpeza.get();
        if (!agora.isBefore(ultima.plus(window)) && ultimaLimpeza.compareAndSet(ultima, agora)) {
            janelas.values().removeIf(j -> !agora.isBefore(j.inicio().plus(window)));
        }
    }

    int tamanho() {
        return janelas.size();
    }
}
