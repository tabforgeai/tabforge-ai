package dyntabs.ai.event;

import java.util.concurrent.atomic.AtomicLong;

import dyntabs.ai.event.EasyAIEvent.Phase;
import dyntabs.ai.event.EasyAIEvent.Source;
import dyntabs.ai.event.EasyAIEvent.Status;

/**
 * Internal helper each EasyAI capability uses to produce {@link EasyAIEvent}s and push them to
 * a registered {@link EasyAIListener}.
 *
 * <h2>What this class is for</h2>
 * <p>It removes boilerplate and enforces three guarantees so individual capabilities don't have
 * to re-implement them: it (1) stamps every event with a 1-based {@link EasyAIEvent#sequence()}
 * and a timestamp, (2) is a no-op when no listener is registered, and (3) <b>never throws</b> —
 * a misbehaving listener can never break the AI operation it is observing.</p>
 *
 * <p><b>Familiar analogy:</b> a stadium's play-by-play commentator assigned to <em>one</em> match.
 * It is created knowing which match it covers ({@link Source}), it numbers its calls in order,
 * and if the broadcast booth's microphone fails (the listener throws), the match on the field
 * carries on regardless.</p>
 *
 * <h2>Where it is used in the chain</h2>
 * <pre>
 *   AgentBuilder.build()
 *        → new EventEmitter(Source.AGENT, listener)   // one per operation
 *        → emitter.started("Planning task")
 *        → emitter.stepStarted("checkStock", "...")    // pre-step (spinner)
 *        → emitter.step("checkStock", "→ 42 in stock", Status.SUCCESS)
 *        → emitter.finished("Task complete")
 * </pre>
 *
 * <p>It is {@code public} only because EasyAI builders live in the parent package
 * {@code dyntabs.ai}; treat it as library-internal — application code interacts with events via
 * {@link EasyAIListener}, not this producer.</p>
 *
 * <p>One {@code EventEmitter} should be created per logical operation (per agent run, per index
 * call, per extraction) so its sequence numbering restarts at 1 for each. It is thread-safe in
 * the sense that sequence assignment is atomic, but it is intended for single-operation use.</p>
 *
 * @see EasyAIEvent
 * @see EasyAIListener
 */
public final class EventEmitter {

    /** The capability this emitter narrates; stamped onto every event it produces. */
    private final Source source;

    /** Where events go; may be {@code null}, in which case every emit is a silent no-op. */
    private final EasyAIListener listener;

    /** Per-operation counter so events read 1, 2, 3… within a single run. */
    private final AtomicLong seq = new AtomicLong();

    /**
     * Create an emitter for one capability and one (optional) listener.
     *
     * @param source   the capability that will be stamped on every event (required)
     * @param listener the subscriber to notify, or {@code null} to disable emission entirely
     */
    public EventEmitter(Source source, EasyAIListener listener) {
        this.source = source;
        this.listener = listener;
    }

    /**
     * @return {@code true} if a listener is registered. Capabilities can guard expensive
     *         detail-string building behind this check (e.g. avoid serializing large results
     *         when nobody is listening).
     */
    public boolean isActive() {
        return listener != null;
    }

    /**
     * Emit a "the operation has begun" event ({@link Phase#STARTED}, {@link Status#RUNNING}).
     *
     * @param title short label for the start of the operation (e.g. {@code "Planning task"})
     */
    public void started(String title) {
        emit(EasyAIEvent.builder(source, Phase.STARTED).status(Status.RUNNING).title(title));
    }

    /**
     * Emit a "a unit of work is about to run" event ({@link Phase#STEP_STARTED},
     * {@link Status#RUNNING}). Lets a UI show a spinning row before the result is known.
     *
     * @param toolName the tool/method being dispatched (may be {@code null})
     * @param detail   optional description of the pending work (e.g. the arguments)
     */
    public void stepStarted(String toolName, String detail) {
        emit(EasyAIEvent.builder(source, Phase.STEP_STARTED)
                .status(Status.RUNNING).toolName(toolName).title(toolName).detail(detail));
    }

    /**
     * Emit a "a unit of work just completed" event ({@link Phase#STEP}).
     *
     * @param toolName the tool/method that ran (may be {@code null})
     * @param detail   short description of what came back (e.g. {@code "args → result"})
     * @param status   {@link Status#SUCCESS} or {@link Status#ERROR}
     */
    public void step(String toolName, String detail, Status status) {
        emit(EasyAIEvent.builder(source, Phase.STEP)
                .status(status).toolName(toolName).title(toolName).detail(detail));
    }

    /**
     * Emit an incremental progress event ({@link Phase#PROGRESS}, {@link Status#RUNNING}).
     *
     * @param title  short label (e.g. {@code "Indexing documents"})
     * @param detail progress detail (e.g. {@code "document 7 of 200"})
     */
    public void progress(String title, String detail) {
        emit(EasyAIEvent.builder(source, Phase.PROGRESS).status(Status.RUNNING).title(title).detail(detail));
    }

    /**
     * Emit a "a meaningful result was produced" event ({@link Phase#RESULT}, {@link Status#SUCCESS}).
     *
     * @param title  short label (e.g. {@code "Extracted Invoice"})
     * @param detail optional result detail
     */
    public void result(String title, String detail) {
        emit(EasyAIEvent.builder(source, Phase.RESULT).status(Status.SUCCESS).title(title).detail(detail));
    }

    /**
     * Emit a "retrying after a failed attempt" event ({@link Phase#RETRY}, {@link Status#WARNING}).
     *
     * @param title  short label (e.g. {@code "Retrying extraction"})
     * @param detail why the retry is happening (e.g. {@code "malformed JSON, attempt 2 of 3"})
     */
    public void retry(String title, String detail) {
        emit(EasyAIEvent.builder(source, Phase.RETRY).status(Status.WARNING).title(title).detail(detail));
    }

    /**
     * Emit a "the operation finished successfully" event ({@link Phase#FINISHED},
     * {@link Status#SUCCESS}).
     *
     * @param title short closing label (e.g. {@code "Task complete"})
     */
    public void finished(String title) {
        emit(EasyAIEvent.builder(source, Phase.FINISHED).status(Status.SUCCESS).title(title));
    }

    /**
     * Emit a failure event ({@link Phase#ERROR}, {@link Status#ERROR}).
     *
     * @param title   short label (e.g. {@code "Agent failed"})
     * @param detail  the readable error message
     */
    public void error(String title, String detail) {
        emit(EasyAIEvent.builder(source, Phase.ERROR).status(Status.ERROR).title(title).detail(detail));
    }

    /**
     * Finalize a builder (assign sequence + deliver) and hand it to the listener.
     *
     * <p>This is the single choke point through which every convenience method flows, so the
     * no-listener short-circuit and the never-throw guarantee live here once. A listener that
     * throws is swallowed deliberately: observation must never change the outcome of the work
     * being observed.</p>
     *
     * @param builder a partially-populated event builder; sequence is stamped here
     */
    public void emit(EasyAIEvent.Builder builder) {
        if (listener == null) {
            return;
        }
        try {
            listener.onEvent(builder.sequence(seq.incrementAndGet()).build());
        } catch (RuntimeException ignored) {
            // An observer must never break the operation it observes. Intentionally swallowed.
        }
    }
}
