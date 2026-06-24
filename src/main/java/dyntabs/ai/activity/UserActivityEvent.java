package dyntabs.ai.activity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * One immutable "the user just did something meaningful" entry in the Ambient Activity timeline —
 * the {@code UI → AI} counterpart to {@link dyntabs.ai.event.EasyAIEvent} (which is {@code AI → UI}).
 *
 * <h2>What this class is for</h2>
 * <p>As a user works inside a TabForge application — opening an order, running a search, jotting a
 * note — each of those acts is captured as one {@code UserActivityEvent} and dropped onto a short,
 * per-tab timeline. When the user later asks the assistant something context-dependent
 * (<em>"summarise <b>this</b>"</em>, <em>"email <b>that</b> customer"</em>), we render a compact
 * slice of this timeline into the prompt so the assistant already knows what "this" and "that"
 * refer to. The user never has to re-type the context they just clicked through.</p>
 *
 * <p><b>Familiar analogy:</b> a flight recorder's <em>event</em> track — not the raw cockpit video,
 * just the labelled milestones: "gear up", "autopilot engaged", "descent started". Each milestone
 * is timestamped, typed, and tagged with what it concerned. When you want to understand the last
 * few minutes, you read this clean track, not hours of footage. A {@code UserActivityEvent} is one
 * such milestone, and crucially it is <em>semantic</em>: because we live inside the app we record
 * "opened order #4711", never "clicked pixel (812, 344)".</p>
 *
 * <h2>How the fields are read downstream</h2>
 * <p>{@link #type} is the coarse category, {@link #verb} is the specific act in the app's own
 * vocabulary (e.g. {@code "approve"}, {@code "cancel"}), {@link #entities} are the business objects
 * the act touched (the raw material for resolving "this"/"that"), and {@link #text} is any free text
 * the user authored (a note or a search query). {@link #sessionId} and {@link #tabId} scope the
 * timeline — by default the assistant only sees activity from the current tab and session, which is
 * what makes {@code @TabScoped} per-tab memory fall out for free. {@link #timestamp} orders the
 * timeline.</p>
 *
 * <p>Instances are created via {@link Builder} (typically by the capture layer, not application
 * code) and are immutable, so they are safe to share across threads, queues, or a ring-buffer store.</p>
 *
 * @see EntityRef the entity pointers carried in {@link #entities()}
 * @see dyntabs.ai.event.EasyAIEvent the mirror-image AI→UI event this is modelled on
 */
public final class UserActivityEvent {

    /**
     * The coarse category of a user act.
     *
     * <p><b>Analogy:</b> the colour-coded tabs on a logbook — you can flip straight to all the
     * "navigation" entries or all the "notes" without reading each line.</p>
     */
    public enum Type {
        /** The user moved their focus (opened a tab, selected a record, navigated to a screen). */
        NAVIGATION,
        /** The user performed a domain action with a verb (approve, cancel, ship, recalculate…). */
        BUSINESS_ACTION,
        /** The user authored free text attached to something (a comment, an annotation). */
        NOTE,
        /** The user searched or filtered ({@link #text()} carries the query). */
        SEARCH,
        /** Anything that doesn't fit the above; {@link #verb()} should describe it. */
        CUSTOM
    }

    private final Instant timestamp;
    private final String sessionId;
    private final String tabId;
    private final Type type;
    private final String verb;
    private final List<EntityRef> entities;
    private final String text;

    private UserActivityEvent(Builder b) {
        this.timestamp = b.timestamp != null ? b.timestamp : Instant.now();
        this.sessionId = b.sessionId;
        this.tabId = b.tabId;
        this.type = Objects.requireNonNull(b.type, "type");
        this.verb = b.verb;
        this.entities = b.entities.isEmpty()
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(b.entities));
        this.text = b.text;
    }

    /** @return when the act happened; never {@code null}. */
    public Instant timestamp() {
        return timestamp;
    }

    /** @return the session this act belongs to (scopes the timeline), or {@code null} if unscoped. */
    public String sessionId() {
        return sessionId;
    }

    /** @return the tab this act belongs to (per-tab memory partition), or {@code null} if none. */
    public String tabId() {
        return tabId;
    }

    /** @return the coarse category of the act; never {@code null}. */
    public Type type() {
        return type;
    }

    /** @return the specific act in the app's vocabulary (e.g. {@code "approve"}), or {@code null}. */
    public String verb() {
        return verb;
    }

    /**
     * @return an unmodifiable list of the business objects this act touched; never {@code null}
     *         (empty when the act concerned no specific entity, e.g. a free search).
     */
    public List<EntityRef> entities() {
        return entities;
    }

    /** @return free text the user authored (a note or a search query), or {@code null}. */
    public String text() {
        return text;
    }

    /**
     * Convenience accessor for the entity this act primarily concerned — useful for deixis, where
     * "this" usually means the single most relevant object.
     *
     * @return the first {@link EntityRef}, or {@code null} if the act touched no entity.
     */
    public EntityRef primaryEntity() {
        return entities.isEmpty() ? null : entities.get(0);
    }

    /**
     * Start building an activity event of the given type.
     *
     * @param type the coarse category (required)
     * @return a new {@link Builder}
     */
    public static Builder builder(Type type) {
        return new Builder(type);
    }

    /**
     * Fluent builder for {@link UserActivityEvent}.
     *
     * <p><b>Analogy:</b> stamping one line into the logbook — the category is the mandatory column;
     * the verb, the entities it touched, and any note are the columns you fill in when relevant.</p>
     */
    public static final class Builder {
        private final Type type;
        private Instant timestamp;
        private String sessionId;
        private String tabId;
        private String verb;
        private final List<EntityRef> entities = new ArrayList<>();
        private String text;

        private Builder(Type type) {
            this.type = type;
        }

        /** @param timestamp event time (defaults to {@code Instant.now()} if unset); @return this builder. */
        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        /** @param sessionId the owning session (scopes the timeline); @return this builder. */
        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        /** @param tabId the owning tab (per-tab memory partition); @return this builder. */
        public Builder tabId(String tabId) {
            this.tabId = tabId;
            return this;
        }

        /** @param verb the specific act in the app's vocabulary; @return this builder. */
        public Builder verb(String verb) {
            this.verb = verb;
            return this;
        }

        /**
         * Add one business object this act touched.
         *
         * @param entity a non-{@code null} {@link EntityRef}
         * @return this builder
         */
        public Builder entity(EntityRef entity) {
            this.entities.add(Objects.requireNonNull(entity, "entity"));
            return this;
        }

        /**
         * Add several business objects this act touched.
         *
         * @param entities entity refs to add; {@code null} is treated as none
         * @return this builder
         */
        public Builder entities(List<EntityRef> entities) {
            if (entities != null) {
                for (EntityRef e : entities) {
                    this.entities.add(Objects.requireNonNull(e, "entity"));
                }
            }
            return this;
        }

        /** @param text free text the user authored (note or query); @return this builder. */
        public Builder text(String text) {
            this.text = text;
            return this;
        }

        /** @return the immutable {@link UserActivityEvent}. */
        public UserActivityEvent build() {
            return new UserActivityEvent(this);
        }
    }

    @Override
    public String toString() {
        return "UserActivityEvent[" + type
                + (verb != null ? '/' + verb : "")
                + (tabId != null ? " tab=" + tabId : "")
                + (entities.isEmpty() ? "" : " entities=" + entities.size())
                + (text != null ? " text='" + text + '\'' : "")
                + ']';
    }
}
