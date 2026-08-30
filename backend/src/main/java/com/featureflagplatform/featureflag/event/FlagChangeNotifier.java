package com.featureflagplatform.featureflag.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Broadcasts flag create/update/delete notifications to every subscribed
 * browser tab over Server-Sent Events, so the flags list / dashboard stay
 * live without polling.
 *
 * <p><b>Delivery timing:</b> listens with {@code phase = AFTER_COMMIT}, not
 * the default {@code BEFORE_COMMIT}/synchronous phase — a subscriber must
 * never be told about a change that then rolls back. {@code
 * FeatureFlagService} publishes the event from inside its own
 * {@code @Transactional} method; Spring holds it and only invokes this
 * listener once that transaction actually commits.
 *
 * <p><b>Scope and honest limitation:</b> subscriber state is an in-memory
 * {@link CopyOnWriteArrayList} on this instance — correct and sufficient for
 * a single backend instance (this project's deployment target), but it does
 * not fan out across multiple instances behind a load balancer. A
 * multi-instance deployment would need a shared broker (Redis Pub/Sub —
 * already a dependency here — is the natural next step) so every instance's
 * subscribers hear about a change committed on any instance. See
 * ADR-005.
 */
@Component
public class FlagChangeNotifier {

    private static final Logger log = LoggerFactory.getLogger(FlagChangeNotifier.class);

    /** 30 minutes: long enough that a normal browsing session never has to
     * silently miss updates while reconnecting; the frontend reconnects
     * automatically on close regardless (see lib/sse-client.ts). */
    private static final long EMITTER_TIMEOUT_MS = 30 * 60 * 1000L;

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> {
            emitter.complete();
            emitters.remove(emitter);
        });
        emitter.onError(ex -> emitters.remove(emitter));

        sendSafely(emitter, SseEmitter.event()
                .name("connected")
                .data(Map.of("status", "connected"), MediaType.APPLICATION_JSON));

        return emitter;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFlagChange(FlagChangeEvent event) {
        if (emitters.isEmpty()) {
            return;
        }
        log.debug("Broadcasting flag change {} for flag {} to {} subscriber(s)",
                event.type(), event.flagKey(), emitters.size());
        for (SseEmitter emitter : emitters) {
            sendSafely(emitter, SseEmitter.event().name("flag-change").data(event, MediaType.APPLICATION_JSON));
        }
    }

    /**
     * Keeps intermediary proxies/load balancers from silently closing an
     * idle streaming connection, and lets a client detect a dead connection
     * (no heartbeat for a while) faster than waiting on a TCP-level failure.
     * A comment line, not a data event — nothing for a subscriber to parse.
     */
    @Scheduled(fixedRate = 15_000)
    public void heartbeat() {
        if (emitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            sendSafely(emitter, SseEmitter.event().comment("hb " + Instant.now()));
        }
    }

    private void sendSafely(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (Exception e) {
            // The subscriber disconnected (browser tab closed, network drop,
            // etc.) — this is the expected, common case, not an error to
            // alarm on. Clean up and let the client's own reconnect logic
            // re-subscribe.
            emitter.completeWithError(e);
            emitters.remove(emitter);
        }
    }
}
