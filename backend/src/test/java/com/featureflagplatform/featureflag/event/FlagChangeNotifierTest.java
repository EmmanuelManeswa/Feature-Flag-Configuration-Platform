package com.featureflagplatform.featureflag.event;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the broadcast/cleanup logic in isolation from Spring's
 * transaction machinery (AFTER_COMMIT delivery specifically is covered by
 * {@code FeatureFlagServiceIntegrationTest} against a real transaction
 * instead, since that behavior is meaningless without one; the "connected"
 * event's actual wire content is covered by {@code FeatureFlagApiTest}
 * through MockMvc, since a bare {@link SseEmitter} exposes no way to
 * inspect what was written to it).
 */
class FlagChangeNotifierTest {

    private final FlagChangeEvent sampleEvent = new FlagChangeEvent(
            UUID.randomUUID(), "sample-flag", UUID.randomUUID(), FlagChangeType.UPDATED, Instant.now());

    @Test
    void aCompletedEmitterRejectsFurtherSends() {
        // The assumption sendSafely()'s try/catch relies on: Spring's
        // SseEmitter really does throw once complete() has been called, so
        // catching around every send() is not defensive-for-no-reason.
        SseEmitter emitter = new SseEmitter();
        emitter.complete();

        assertThatThrownBy(() -> emitter.send(SseEmitter.event().data("late")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void broadcastWithNoSubscribersIsANoOp() {
        FlagChangeNotifier notifier = new FlagChangeNotifier();

        assertThatCode(() -> notifier.onFlagChange(sampleEvent)).doesNotThrowAnyException();
        assertThatCode(notifier::heartbeat).doesNotThrowAnyException();
    }

    @Test
    void broadcastAfterASubscriberDisconnectsDoesNotThrow() throws IOException {
        FlagChangeNotifier notifier = new FlagChangeNotifier();
        SseEmitter emitter = notifier.subscribe();

        // Simulates a browser tab closing the connection cleanly: Spring
        // invokes the onCompletion callback synchronously, which is what
        // actually removes this emitter from the notifier's subscriber list
        // (sendSafely's catch-and-remove is the fallback for an *unclean*
        // disconnect, not this path).
        emitter.complete();

        // Must not throw, and must not attempt to write to the now-dead
        // emitter — either outcome (skipped because onCompletion already
        // removed it, or caught and swallowed by sendSafely) is acceptable;
        // what's under test is that a disconnected subscriber never breaks
        // delivery to everyone else.
        assertThatCode(() -> notifier.onFlagChange(sampleEvent)).doesNotThrowAnyException();
    }

    @Test
    void multipleLiveSubscribersAllReceiveTheSameBroadcastWithoutError() {
        FlagChangeNotifier notifier = new FlagChangeNotifier();
        notifier.subscribe();
        notifier.subscribe();
        notifier.subscribe();

        assertThatCode(() -> notifier.onFlagChange(sampleEvent)).doesNotThrowAnyException();
    }
}
