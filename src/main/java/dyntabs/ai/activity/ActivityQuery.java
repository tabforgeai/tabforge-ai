package dyntabs.ai.activity;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import dyntabs.ai.activity.UserActivityEvent.Type;

/**
 * An immutable description of <em>which</em> slice of the activity timeline you want back from an
 * {@link ActivityStore} — a small set of filters (session, tab, age, category) plus a cap on how
 * many of the most recent matches to return.
 *
 * <h2>What this class is for</h2>
 * <p>The activity timeline can hold many entries, but a prompt only wants the handful that matter
 * <em>right now</em>: usually "the last few things this user did in this tab". An
 * {@code ActivityQuery} is how you say that precisely — scope it to a {@link #sessionId()} and
 * {@link #tabId()}, optionally only keep events {@link #since(Instant) newer than} some moment or
 * of certain {@link #types() types}, and {@link #limit() cap} the count. The store does the
 * filtering and hands back a chronological list.</p>
 *
 * <p><b>Familiar analogy:</b> a search filter on an email client — "from this folder, this label,
 * newer than yesterday, show 20". You aren't fetching the whole mailbox; you're asking for exactly
 * the visible, relevant window. {@code ActivityQuery} is that filter for the activity log.</p>
 *
 * <h2>Filter semantics</h2>
 * <ul>
 *   <li>A {@code null} {@link #sessionId()} or {@link #tabId()} means "don't filter on that field"
 *       (match any). A non-null value must match exactly.</li>
 *   <li>{@link #since()} keeps only events at or after that instant; {@code null} means no lower
 *       bound.</li>
 *   <li>{@link #types()} keeps only events whose {@link Type} is in the set; an empty set means
 *       all types.</li>
 *   <li>{@link #limit()} keeps only the <em>N most recent</em> matches (a value {@code <= 0} means
 *       "no limit").</li>
 * </ul>
 *
 * @see ActivityStore the SPI that interprets this query
 */
public final class ActivityQuery {

    private final String sessionId;
    private final String tabId;
    private final Instant since;
    private final Set<Type> types;
    private final int limit;

    private ActivityQuery(Builder b) {
        this.sessionId = b.sessionId;
        this.tabId = b.tabId;
        this.since = b.since;
        this.types = b.types.isEmpty()
                ? Collections.emptySet()
                : Collections.unmodifiableSet(EnumSet.copyOf(b.types));
        this.limit = b.limit;
    }

    /** @return the session to match exactly, or {@code null} to match any session. */
    public String sessionId() {
        return sessionId;
    }

    /** @return the tab to match exactly, or {@code null} to match any tab. */
    public String tabId() {
        return tabId;
    }

    /** @return the lower time bound (inclusive), or {@code null} for no lower bound. */
    public Instant since() {
        return since;
    }

    /** @return an unmodifiable set of accepted types; empty means "all types". Never {@code null}. */
    public Set<Type> types() {
        return types;
    }

    /** @return the maximum number of most-recent matches to return; {@code <= 0} means no limit. */
    public int limit() {
        return limit;
    }

    /**
     * Test whether a single event satisfies this query's field filters.
     *
     * <p>Note this deliberately does <em>not</em> apply {@link #limit()} — the limit is about how
     * many matches to keep, which only makes sense over a collection. A store calls this per event
     * to decide membership, then applies the limit to the surviving set.</p>
     *
     * @param e the event to test; must not be {@code null}
     * @return {@code true} if the event passes every field filter
     */
    public boolean matches(UserActivityEvent e) {
        if (sessionId != null && !sessionId.equals(e.sessionId())) {
            return false;
        }
        if (tabId != null && !tabId.equals(e.tabId())) {
            return false;
        }
        if (since != null && e.timestamp().isBefore(since)) {
            return false;
        }
        if (!types.isEmpty() && !types.contains(e.type())) {
            return false;
        }
        return true;
    }

    /** @return a new, empty {@link Builder}. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for {@link ActivityQuery}.
     *
     * <p><b>Analogy:</b> ticking boxes on a search form — every box is optional; leaving one blank
     * means "any".</p>
     */
    public static final class Builder {
        private String sessionId;
        private String tabId;
        private Instant since;
        private final EnumSet<Type> types = EnumSet.noneOf(Type.class);
        private int limit;

        private Builder() {
        }

        /** @param sessionId the session to match exactly (or {@code null} for any); @return this builder. */
        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        /** @param tabId the tab to match exactly (or {@code null} for any); @return this builder. */
        public Builder tabId(String tabId) {
            this.tabId = tabId;
            return this;
        }

        /** @param since inclusive lower time bound (or {@code null} for none); @return this builder. */
        public Builder since(Instant since) {
            this.since = since;
            return this;
        }

        /** @param type a type to accept (additive; call repeatedly to widen the set); @return this builder. */
        public Builder type(Type type) {
            if (type != null) {
                this.types.add(type);
            }
            return this;
        }

        /** @param types types to accept; {@code null} is treated as none; @return this builder. */
        public Builder types(Set<Type> types) {
            if (types != null) {
                this.types.addAll(types);
            }
            return this;
        }

        /** @param limit max most-recent matches ({@code <= 0} means no limit); @return this builder. */
        public Builder limit(int limit) {
            this.limit = limit;
            return this;
        }

        /** @return the immutable {@link ActivityQuery}. */
        public ActivityQuery build() {
            return new ActivityQuery(this);
        }
    }

    @Override
    public String toString() {
        return "ActivityQuery["
                + (sessionId != null ? "session=" + sessionId + ' ' : "")
                + (tabId != null ? "tab=" + tabId + ' ' : "")
                + (since != null ? "since=" + since + ' ' : "")
                + (types.isEmpty() ? "" : "types=" + types + ' ')
                + "limit=" + limit + ']';
    }
}
