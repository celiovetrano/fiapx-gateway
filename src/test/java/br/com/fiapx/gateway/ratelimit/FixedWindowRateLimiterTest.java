package br.com.fiapx.gateway.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class FixedWindowRateLimiterTest {

    private final RelogioAjustavel relogio = new RelogioAjustavel(Instant.parse("2026-09-15T10:00:00Z"));
    private final FixedWindowRateLimiter limiter =
            new FixedWindowRateLimiter(3, Duration.ofMinutes(1), relogio);

    @Test
    void permiteAteOLimiteDentroDaJanela() {
        assertThat(limiter.tryAcquire("10.0.0.1")).isTrue();
        assertThat(limiter.tryAcquire("10.0.0.1")).isTrue();
        assertThat(limiter.tryAcquire("10.0.0.1")).isTrue();
        assertThat(limiter.tryAcquire("10.0.0.1")).isFalse();
    }

    @Test
    void chavesDiferentesTemContagensIndependentes() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("10.0.0.1");
        }

        assertThat(limiter.tryAcquire("10.0.0.1")).isFalse();
        assertThat(limiter.tryAcquire("10.0.0.2")).isTrue();
    }

    @Test
    void novaJanelaZeraAContagem() {
        for (int i = 0; i < 4; i++) {
            limiter.tryAcquire("10.0.0.1");
        }

        relogio.avancar(Duration.ofMinutes(1));

        assertThat(limiter.tryAcquire("10.0.0.1")).isTrue();
    }

    static final class RelogioAjustavel extends Clock {

        private Instant agora;

        RelogioAjustavel(Instant inicio) {
            this.agora = inicio;
        }

        void avancar(Duration duracao) {
            agora = agora.plus(duracao);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }
}
