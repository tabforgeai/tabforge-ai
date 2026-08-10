package dyntabs.ai.event;

import java.time.Instant;

/**
 * One immutable, transport-agnostic "something happened" notification emitted by any
 * EasyAI capability (chat, assistant, RAG, agent, indexer, extract) while it works.
 *
 * <h2>What this class is for</h2>
 * <p>EasyAI normally does its job silently and hands you a final answer. An
 * {@code EasyAIEvent} is the opposite: it lets a capability <em>narrate itself</em> as it
 * runs — "I started", "I'm calling tool X", "tool X returned", "I'm on document 7 of 200",
 * "I finished". You subscribe with an {@link EasyAIListener} and receive a stream of these.</p>
 *
 * <p><b>Familiar analogy:</b> think of a sports play-by-play commentator. The match (the AI
 * operation) would happen regardless, but the commentator turns it into a live, human-readable
 * stream of moments: "kickoff… pass… shot… goal!". An {@code EasyAIEvent} is one such sentence.
 * Equivalently: it is a single structured log line, but pushed to you in real time instead of
 * written to a file.</p>
 *
 * <h2>Why "transport-agnostic" matters</h2>
 * <p>This object knows nothing about HTTP, Server-Sent Events, WebSockets, or any UI's JSON
 * schema. It is a plain value. <em>You</em> decide what to do with it — log it, count it, or
 * (as in the TabForge demo) map it to a UI event and push it over SSE to an Activity panel.
 * That decoupling is deliberate: EasyAI stays a pure library, and the same event stream can
 * feed a log, a metric, or a live dashboard without EasyAI ever depending on any of them.</p>
 *
 * <h2>How the fields are read downstream</h2>
 * <p>{@link #source} says <em>who</em> emitted it, {@link #phase} says <em>which moment</em> in
 * the lifecycle it is, {@link #status} says <em>how it is going</em>, and {@link #title}/
 * {@link #detail}/{@link #toolName} are the human-readable payload. {@link #sequence} is the
 * 1-based ordinal within a single operation (handy for ordering a timeline), and
 * {@link #timestamp} is when it happened.</p>
 *
 * <p>Instances are created by {@link EventEmitter} (which fills in {@code sequence} and
 * {@code timestamp} for you), or via {@link Builder} directly in tests. They are immutable and
 * therefore safe to hand to another thread or queue.</p>
 *
 * @see EasyAIListener the callback that receives these events
 * @see EventEmitter the helper each capability uses to produce them
 */
public final class EasyAIEvent {

    /**
     * Which EasyAI capability produced an event.
     *
     * <p><b>Analogy:</b> the "department" a memo came from — you read it differently depending
     * on whether it's from the indexing crew or the extraction desk.</p>
     */
    public enum Source {
        /** {@code EasyAI.chat()} — a conversational turn. */
        CHAT,
        /** {@code EasyAI.assistant(...)} — an AI service backed by your tools. */
        ASSISTANT,
        /** Retrieval-augmented generation (document search feeding an answer). */
        RAG,
        /** {@code EasyAI.agent()} — an autonomous multi-step agent. */
        AGENT,
        /** {@code EasyAI.flow()} — a developer-authored, deterministic step pipeline. */
        FLOW,
        /** {@code EasyAI.indexer()} — document ingestion into a vector store. */
        INDEXER,
        /** {@code EasyAI.extract(...)} — structured extraction of a typed object. */
        EXTRACT
    }

    /**
     * Which moment in an operation's lifecycle an event marks.
     *
     * <p><b>Analogy:</b> the stages of a delivery — "dispatched" (STARTED), "out for delivery"
     * (PROGRESS), "attempting delivery" (STEP_STARTED), "delivered" (STEP/RESULT),
     * "redelivery" (RETRY), "completed" (FINISHED), "failed" (ERROR).</p>
     */
    public enum Phase {
        /** The operation has begun. Usually paired with {@link Status#RUNNING}. */
        STARTED,
        /** A discrete unit of work is about to run (e.g. a tool call is being dispatched).
         *  Lets a UI show a spinning "running" row before the result arrives. */
        STEP_STARTED,
        /** A discrete unit of work just completed (e.g. a tool returned). */
        STEP,
        /** Incremental progress within a longer operation (e.g. "document 7 of 200"). */
        PROGRESS,
        /** A meaningful intermediate or final result was produced. */
        RESULT,
        /** A failed attempt is being retried (e.g. malformed JSON during extraction). */
        RETRY,
        /** The operation finished successfully. */
        FINISHED,
        /** The operation failed. Usually paired with {@link Status#ERROR}. */
        ERROR
    }

    /**
     * How an event is going, visually.
     *
     * <p><b>Analogy:</b> a traffic light for one timeline row — blue/spinning while in flight,
     * green when it worked, red when it didn't, amber for a caution.</p>
     */
    public enum Status {
        /** Still in flight; a UI typically shows a spinner. */
        RUNNING,
        /** Completed successfully. */
        SUCCESS,
        /** Failed. */
        ERROR,
        /** Completed but with a caveat worth flagging. */
        WARNING
    }

    private final Source source;
    private final Phase phase;
    private final Status status;
    private final String title;
    private final String detail;
    private final String toolName;
    private final long sequence;
    private final Instant timestamp;

    private EasyAIEvent(Builder b) {
        this.source = b.source;
        this.phase = b.phase;
        this.status = b.status;
        this.title = b.title;
        this.detail = b.detail;
        this.toolName = b.toolName;
        this.sequence = b.sequence;
        this.timestamp = b.timestamp != null ? b.timestamp : Instant.now();
    }

    /** @return the capability that emitted this event; never {@code null}. */
    public Source source() {
        return source;
    }

    /** @return the lifecycle moment this event marks; never {@code null}. */
    public Phase phase() {
        return phase;
    }

    /** @return the visual status, or {@code null} if not meaningful for this event. */
    public Status status() {
        return status;
    }

    /** @return a short human-readable label (e.g. {@code "Indexing documents"}), or {@code null}. */
    public String title() {
        return title;
    }

    /** @return a secondary descriptive line (e.g. {@code "document 7 of 200"}), or {@code null}. */
    public String detail() {
        return detail;
    }

    /** @return the tool/method name when this event concerns a tool call, else {@code null}. */
    public String toolName() {
        return toolName;
    }

    /** @return the 1-based ordinal of this event within its operation (0 if unset). */
    public long sequence() {
        return sequence;
    }

    /** @return when the event was created; never {@code null}. */
    public Instant timestamp() {
        return timestamp;
    }

    /**
     * Start building an event for the given source and phase.
     *
     * <p>Most application code never calls this — {@link EventEmitter} builds events for you.
     * It is exposed mainly for tests that want to assert on a hand-crafted event.</p>
     *
     * @param source the emitting capability (required)
     * @param phase  the lifecycle moment (required)
     * @return a new {@link Builder}
     */
    public static Builder builder(Source source, Phase phase) {
        return new Builder(source, phase);
    }

    /**
     * Fluent builder for {@link EasyAIEvent}.
     *
     * <p><b>Analogy:</b> filling in a pre-printed incident form — source and phase are the two
     * required boxes; the rest are optional lines you complete when relevant.</p>
     */
    public static final class Builder {
        private final Source source;
        private final Phase phase;
        private Status status;
        private String title;
        private String detail;
        private String toolName;
        private long sequence;
        private Instant timestamp;

        private Builder(Source source, Phase phase) {
            this.source = source;
            this.phase = phase;
        }

        /** @param status the visual status; @return this builder. */
        public Builder status(Status status) {
            this.status = status;
            return this;
        }

        /** @param title short label; @return this builder. */
        public Builder title(String title) {
            this.title = title;
            return this;
        }

        /** @param detail secondary descriptive line; @return this builder. */
        public Builder detail(String detail) {
            this.detail = detail;
            return this;
        }

        /** @param toolName tool/method name for tool-related events; @return this builder. */
        public Builder toolName(String toolName) {
            this.toolName = toolName;
            return this;
        }

        /** @param sequence 1-based ordinal within the operation; @return this builder. */
        public Builder sequence(long sequence) {
            this.sequence = sequence;
            return this;
        }

        /** @param timestamp event time (defaults to {@code Instant.now()} if unset); @return this builder. */
        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        /** @return the immutable {@link EasyAIEvent}. */
        public EasyAIEvent build() {
            return new EasyAIEvent(this);
        }
    }

    @Override
    public String toString() {
        return "EasyAIEvent[" + source + '/' + phase
                + (status != null ? '/' + status.name() : "")
                + " #" + sequence
                + (title != null ? " '" + title + '\'' : "")
                + (toolName != null ? " tool=" + toolName : "")
                + ']';
    }
}
