package dyntabs.ai.activity;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * A reusable, lazily-evaluated description of "the recent activity to feed the assistant" — bind it
 * to a {@link ActivityStore} once, say which session/tab and how far back to look, then call
 * {@link #render()} at each AI call to get fresh prompt text reflecting the latest activity.
 *
 * <h2>What this class is for</h2>
 * <p>This is the piece that makes the assistant context-aware. You attach an {@code ActivityContext}
 * to a conversation or assistant builder (via {@code .withActivityContext(...)}); each time the AI is
 * invoked, the framework calls {@link #render()}, which queries the store for the recent slice and
 * runs it through an {@link ActivityRenderer} to produce a short briefing. That briefing is injected
 * into the <em>system</em> message, so when the user says "summarise <b>this</b>" the model already
 * knows what "this" is.</p>
 *
 * <p><b>Familiar analogy:</b> a standing instruction to your assistant — "before every meeting, hand
 * me a one-page brief of what I've been working on in this project for the last 30 minutes." You set
 * the standing order once (build the context); the brief itself is produced fresh each time, so it is
 * never stale. The {@code ActivityContext} is that standing order, not the brief.</p>
 *
 * <h2>Why lazy / re-evaluated</h2>
 * <p>An {@code ActivityContext} holds only the <em>recipe</em> (store + filters + renderer), never a
 * captured snapshot. Calling {@link #render()} twice a minute apart yields two different briefings as
 * the user keeps working. That is deliberate: the same context object can be reused across many turns
 * and always reflects the present.</p>
 *
 * <h2>Defaults</h2>
 * <p>Scope defaults to "everything" until you narrow it: set {@link Builder#forSession(String)} and
 * {@link Builder#forTab(String)} to get the intended per-tab working memory. {@link Builder#window}
 * and {@link Builder#limit} bound how much is shown ({@value #DEFAULT_LIMIT} events by default); the
 * renderer defaults to {@link ActivityRenderer#compactDefault()}.</p>
 *
 * @see ActivityRenderer how the events become text
 * @see ActivityStore where the events come from
 */
public final class ActivityContext {

    /** Default cap on how many recent events are rendered into the prompt. */
    public static final int DEFAULT_LIMIT = 20;

    private final ActivityStore store;
    private final String sessionId;
    private final String tabId;
    private final Duration window;
    private final int limit;
    private final ActivityRenderer renderer;

    private ActivityContext(Builder b) {
        this.store = b.store;
        this.sessionId = b.sessionId;
        this.tabId = b.tabId;
        this.window = b.window;
        this.limit = b.limit;
        this.renderer = b.renderer != null ? b.renderer : ActivityRenderer.compactDefault();
    }

    /**
     * Query the store for the configured slice and render it to prompt text, <em>now</em>.
     *
     * <h3>Who calls this, and exactly when</h3>
     * <p>You never call this yourself in normal use. Once an {@code ActivityContext} is attached via
     * {@code .withActivityContext(...)} on {@link dyntabs.ai.ConversationBuilder} or
     * {@link dyntabs.ai.AssistantBuilder}, the builder hands it to the underlying LangChain4J
     * AI-service as part of its <em>system-message provider</em> — the small function LangChain4J
     * evaluates afresh on <b>every</b> model call. Concretely:</p>
     * <ul>
     *   <li>For a {@link dyntabs.ai.Conversation}, that is once per {@link dyntabs.ai.Conversation#send(String)}:
     *       just before the user's turn is dispatched to the model, the provider runs, calls
     *       {@code render()}, and stitches the result onto the base system message for that turn.</li>
     *   <li>For an assistant built by {@link dyntabs.ai.AssistantBuilder}, that is once per call to any
     *       of the assistant's methods — the same provider fires before each invocation.</li>
     * </ul>
     * <p>Because the provider is evaluated per call rather than captured once at build time, each turn
     * sees a briefing that reflects whatever the user has done <em>up to that exact moment</em> — which
     * is the whole point of {@linkplain ActivityContext#render() rendering lazily} rather than snapshotting.</p>
     *
     * <p>Returns an empty string when there is no matching activity, which signals the caller (the
     * provider) to inject nothing rather than an empty "recent activity" heading.</p>
     *
     * @return the rendered briefing, or an empty string if there is nothing to show; never {@code null}
     */
    public String render() {
        ActivityQuery.Builder q = ActivityQuery.builder()
                .sessionId(sessionId)
                .tabId(tabId)
                .limit(limit);
        if (window != null) {
            q.since(Instant.now().minus(window));
        }
        List<UserActivityEvent> events = store.query(q.build());
        return renderer.render(events);
    }

    /** @return the session this context is scoped to, or {@code null} for any. */
    public String sessionId() {
        return sessionId;
    }

    /** @return the tab this context is scoped to, or {@code null} for any. */
    public String tabId() {
        return tabId;
    }

    /**
     * Start building a context bound to the given store.
     *
     * @param store the activity store to read from; must not be {@code null}
     * @return a new {@link Builder}
     */
    public static Builder of(ActivityStore store) {
        return new Builder(store);
    }

    /**
     * Fluent builder for {@link ActivityContext}.
     *
     * <p><b>Analogy:</b> writing the standing order — name the project (session/tab), say "last N
     * minutes" or "last N items", and optionally pick a brief format.</p>
     */
    public static final class Builder {
        private final ActivityStore store;
        private String sessionId;
        private String tabId;
        private Duration window;
        private int limit = DEFAULT_LIMIT;
        private ActivityRenderer renderer;

        private Builder(ActivityStore store) {
            this.store = Objects.requireNonNull(store, "store");
        }

        /** @param sessionId scope to this session (or {@code null} for any); @return this builder. */
        public Builder forSession(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        /** @param tabId scope to this tab (or {@code null} for any); @return this builder. */
        public Builder forTab(String tabId) {
            this.tabId = tabId;
            return this;
        }

        /** @param window only show activity newer than this far back (or {@code null} for no bound); @return this builder. */
        public Builder window(Duration window) {
            this.window = window;
            return this;
        }

        /** @param limit cap on rendered events ({@code <= 0} means no cap); @return this builder. */
        public Builder limit(int limit) {
            this.limit = limit;
            return this;
        }

        /** @param renderer how events become text (defaults to {@link ActivityRenderer#compactDefault()}); @return this builder. */
        public Builder renderer(ActivityRenderer renderer) {
            this.renderer = renderer;
            return this;
        }

        /** @return the immutable {@link ActivityContext}. */
        public ActivityContext build() {
            return new ActivityContext(this);
        }
    }
}
