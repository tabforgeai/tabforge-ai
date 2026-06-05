package dyntabs.ai.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import dyntabs.ai.event.EasyAIEvent.Phase;
import dyntabs.ai.event.EasyAIEvent.Source;
import dyntabs.ai.event.EasyAIEvent.Status;

/**
 * Verifies the three guarantees {@link EventEmitter} exists to enforce: it stamps an ordered
 * sequence + timestamp, it is a silent no-op without a listener, and it never lets a listener
 * exception escape into the operation being observed.
 */
class EventEmitterTest {

    /** Collects everything delivered, so a test can assert on the emitted stream. */
    private static List<EasyAIEvent> collector(EventEmitter[] holder, Source source) {
        List<EasyAIEvent> events = new ArrayList<>();
        holder[0] = new EventEmitter(source, events::add);
        return events;
    }

    @Test
    void isActiveReflectsListenerPresence() {
        assertThat(new EventEmitter(Source.AGENT, null).isActive()).isFalse();
        assertThat(new EventEmitter(Source.AGENT, e -> { }).isActive()).isTrue();
    }

    @Test
    void nullListenerIsSilentNoOp() {
        EventEmitter emitter = new EventEmitter(Source.AGENT, null);
        // None of these should do anything observable, and none should throw.
        assertThatCode(() -> {
            emitter.started("x");
            emitter.stepStarted("tool", "args");
            emitter.step("tool", "result", Status.SUCCESS);
            emitter.progress("p", "d");
            emitter.result("r", "d");
            emitter.retry("retry", "d");
            emitter.finished("done");
            emitter.error("err", "d");
        }).doesNotThrowAnyException();
    }

    @Test
    void stampsIncrementingSequenceStartingAtOne() {
        EventEmitter[] holder = new EventEmitter[1];
        List<EasyAIEvent> events = collector(holder, Source.INDEXER);

        holder[0].started("a");
        holder[0].progress("b", null);
        holder[0].finished("c");

        assertThat(events).extracting(EasyAIEvent::sequence).containsExactly(1L, 2L, 3L);
    }

    @Test
    void stampsSourceAndTimestampOnEveryEvent() {
        EventEmitter[] holder = new EventEmitter[1];
        List<EasyAIEvent> events = collector(holder, Source.EXTRACT);

        holder[0].started("a");
        holder[0].finished("b");

        assertThat(events).allSatisfy(e -> {
            assertThat(e.source()).isEqualTo(Source.EXTRACT);
            assertThat(e.timestamp()).isNotNull();
        });
    }

    @Test
    void convenienceMethodsSetExpectedPhaseAndStatus() {
        EventEmitter[] holder = new EventEmitter[1];
        List<EasyAIEvent> events = collector(holder, Source.AGENT);

        holder[0].started("planning");
        holder[0].stepStarted("checkStock", "{qty:2}");
        holder[0].step("checkStock", "42 in stock", Status.SUCCESS);
        holder[0].progress("indexing", "7 of 200");
        holder[0].result("extracted", "Invoice");
        holder[0].retry("retrying", "bad json");
        holder[0].finished("complete");
        holder[0].error("boom", "NPE");

        assertThat(events).extracting(EasyAIEvent::phase).containsExactly(
                Phase.STARTED, Phase.STEP_STARTED, Phase.STEP, Phase.PROGRESS,
                Phase.RESULT, Phase.RETRY, Phase.FINISHED, Phase.ERROR);
        assertThat(events).extracting(EasyAIEvent::status).containsExactly(
                Status.RUNNING, Status.RUNNING, Status.SUCCESS, Status.RUNNING,
                Status.SUCCESS, Status.WARNING, Status.SUCCESS, Status.ERROR);
    }

    @Test
    void stepCarriesToolNameAndStatus() {
        EventEmitter[] holder = new EventEmitter[1];
        List<EasyAIEvent> events = collector(holder, Source.AGENT);

        holder[0].stepStarted("processPayment", "{amount:150}");
        holder[0].step("processPayment", "TOOL_ERROR: declined", Status.ERROR);

        assertThat(events.get(0).toolName()).isEqualTo("processPayment");
        assertThat(events.get(1).toolName()).isEqualTo("processPayment");
        assertThat(events.get(1).status()).isEqualTo(Status.ERROR);
        assertThat(events.get(1).detail()).isEqualTo("TOOL_ERROR: declined");
    }

    @Test
    void listenerExceptionIsSwallowedAndDoesNotStopTheStream() {
        List<EasyAIEvent> delivered = new ArrayList<>();
        EasyAIListener throwingThenRecording = e -> {
            delivered.add(e);
            if (e.phase() == Phase.STARTED) {
                throw new RuntimeException("listener blew up");
            }
        };
        EventEmitter emitter = new EventEmitter(Source.CHAT, throwingThenRecording);

        // The throwing STARTED must not propagate, and the later event must still be delivered
        // with the next sequence number (the operation observing must be unaffected).
        assertThatCode(() -> {
            emitter.started("x");
            emitter.finished("y");
        }).doesNotThrowAnyException();

        assertThat(delivered).extracting(EasyAIEvent::sequence).containsExactly(1L, 2L);
    }
}
