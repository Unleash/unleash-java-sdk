// reference implementation here: https://github.com/Unleash/unleash-node-sdk/pull/780

package io.getunleash.streaming;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class FailoverStrategy {

    private static final Logger LOGGER = LoggerFactory.getLogger(FailoverStrategy.class);
    private static final Set<String> FAILOVER_SERVER_HINTS = Set.of("polling");
    private static final Set<Integer> HARD_FAILOVER_STATUS_CODES = Set.of(401, 403, 404, 429, 501);
    private static final Set<Integer> SOFT_FAILOVER_STATUS_CODES = Set.of(408, 500, 502, 503, 504);

    private final int maxFails;
    private final long relaxTimeMs;
    private final List<FailEvent> failures = new ArrayList<>();

    FailoverStrategy(int maxFails, long relaxTimeMs) {
        this.maxFails = maxFails;
        this.relaxTimeMs = relaxTimeMs;
    }

    synchronized boolean shouldFailover(FailEvent event, Instant now) {
        long nowMs = now.toEpochMilli();
        pruneOldFailures(nowMs);

        switch (event.getType()) {
            case HTTP_STATUS_ERROR:
                return handleHttpStatus((HttpStatusError) event);
            case SERVER_HINT:
                return handleServerEvent((ServerEvent) event);
            case NETWORK_ERROR:
                return handleNetwork(event);
            default:
                LOGGER.warn(
                        "Responding to an unknown event, this should not have occurred please report this. Streaming will continue to operate without failing over to polling");
                return false;
        }
    }

    private boolean handleServerEvent(ServerEvent event) {
        return FAILOVER_SERVER_HINTS.contains(event.getEvent());
    }

    private boolean handleNetwork(FailEvent event) {
        return hasTooManyFails(event);
    }

    private boolean handleHttpStatus(HttpStatusError event) {
        if (HARD_FAILOVER_STATUS_CODES.contains(event.getStatusCode())) {
            return true;
        }
        if (SOFT_FAILOVER_STATUS_CODES.contains(event.getStatusCode())) {
            return hasTooManyFails(event);
        }
        return false;
    }

    private boolean hasTooManyFails(FailEvent event) {
        failures.add(event);
        return failures.size() >= maxFails;
    }

    private void pruneOldFailures(long nowMs) {
        long cutoff = nowMs - relaxTimeMs;
        int write = 0;
        for (int read = 0; read < failures.size(); read++) {
            FailEvent failure = failures.get(read);
            if (failure.getOccurredAt().toEpochMilli() >= cutoff) {
                failures.set(write++, failure);
            }
        }
        if (write < failures.size()) {
            failures.subList(write, failures.size()).clear();
        }
    }

    enum EventType {
        NETWORK_ERROR,
        HTTP_STATUS_ERROR,
        SERVER_HINT
    }

    abstract static class FailEvent {
        private final Instant occurredAt;
        private final String message;

        protected FailEvent(Instant occurredAt, String message) {
            this.occurredAt = occurredAt;
            this.message = message;
        }

        abstract EventType getType();

        Instant getOccurredAt() {
            return occurredAt;
        }

        String getMessage() {
            return message;
        }
    }

    static final class NetworkEventError extends FailEvent {
        NetworkEventError(Instant occurredAt, String message) {
            super(occurredAt, message);
        }

        @Override
        EventType getType() {
            return EventType.NETWORK_ERROR;
        }
    }

    static final class HttpStatusError extends FailEvent {
        private final int statusCode;

        HttpStatusError(Instant occurredAt, String message, int statusCode) {
            super(occurredAt, message);
            this.statusCode = statusCode;
        }

        int getStatusCode() {
            return statusCode;
        }

        @Override
        EventType getType() {
            return EventType.HTTP_STATUS_ERROR;
        }
    }

    static final class ServerEvent extends FailEvent {
        private final String event;

        ServerEvent(Instant occurredAt, String message, String event) {
            super(occurredAt, message);
            this.event = event;
        }

        String getEvent() {
            return event;
        }

        @Override
        EventType getType() {
            return EventType.SERVER_HINT;
        }
    }
}
