package io.getunleash.streaming;

import static org.assertj.core.api.Assertions.assertThat;

import io.getunleash.streaming.FailoverStrategy.HttpStatusError;
import io.getunleash.streaming.FailoverStrategy.NetworkEventError;
import io.getunleash.streaming.FailoverStrategy.ServerEvent;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class FailoverStrategyTest {

    @Test
    void should_fail_immediately_on_server_requests_polling() {
        FailoverStrategy strategy = new FailoverStrategy(3, 5_000);
        ServerEvent event = new ServerEvent(Instant.now(), "Switch to polling", "polling");

        assertThat(strategy.shouldFailover(event, Instant.now())).isTrue();
    }

    @Test
    void should_ignore_unknown_server_hint() {
        FailoverStrategy strategy = new FailoverStrategy(3, 5_000);
        ServerEvent event = new ServerEvent(Instant.now(), "Restart complete", "unleash-restarted");

        assertThat(strategy.shouldFailover(event, Instant.now())).isFalse();
    }

    @Test
    void should_fail_immediately_on_hard_http_status() {
        FailoverStrategy strategy = new FailoverStrategy(3, 5_000);
        HttpStatusError error = new HttpStatusError(Instant.now(), "Too many connections", 429);

        assertThat(strategy.shouldFailover(error, Instant.now())).isTrue();
    }

    @Test
    void should_fail_after_sliding_window_on_soft_http_status() {
        FailoverStrategy strategy = new FailoverStrategy(2, 10_000);
        Instant now = Instant.now();

        HttpStatusError first = new HttpStatusError(now, "Temporary error", 503);
        HttpStatusError second = new HttpStatusError(now.plusMillis(5), "Still failing", 503);

        assertThat(strategy.shouldFailover(first, now)).isFalse();
        assertThat(strategy.shouldFailover(second, now.plusMillis(5))).isTrue();
    }

    @Test
    void network_errors_contribute_to_sliding_window() {
        FailoverStrategy strategy = new FailoverStrategy(2, 10_000);
        Instant now = Instant.now();

        NetworkEventError first = new NetworkEventError(now, "Connection reset");
        NetworkEventError second = new NetworkEventError(now.plusMillis(1), "Socket timeout");

        assertThat(strategy.shouldFailover(first, now)).isFalse();
        assertThat(strategy.shouldFailover(second, now.plusMillis(1))).isTrue();
    }

    @Test
    void sliding_window_drops_old_failures() {
        FailoverStrategy strategy = new FailoverStrategy(2, 1_000);
        Instant base = Instant.EPOCH;

        NetworkEventError first = new NetworkEventError(base, "Initial blip");
        NetworkEventError second = new NetworkEventError(base.plusMillis(1_500), "Recovered blip");
        NetworkEventError third = new NetworkEventError(base.plusMillis(1_510), "Another blip");

        assertThat(strategy.shouldFailover(first, base)).isFalse();
        assertThat(strategy.shouldFailover(second, base.plusMillis(1_500))).isFalse();
        assertThat(strategy.shouldFailover(third, base.plusMillis(1_510))).isTrue();
    }
}
