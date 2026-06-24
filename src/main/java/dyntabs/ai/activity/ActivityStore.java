package dyntabs.ai.activity;

import java.util.List;

/**
 * The pluggable storage backend for the Ambient Activity timeline — a tiny SPI with two jobs:
 * {@link #record(UserActivityEvent) write} an event as it happens, and {@link #query(ActivityQuery)
 * read} a filtered slice back when the assistant needs context.
 *
 * <h2>What this interface is for</h2>
 * <p>The capture layer pushes a {@link UserActivityEvent} here every time the user does something
 * meaningful; the context layer pulls a recent slice back to render into a prompt. Keeping these
 * two operations behind an interface means the <em>policy</em> of where activity lives — in memory,
 * in a database, in nothing at all — is a swappable decision that the rest of the feature never has
 * to know about.</p>
 *
 * <p><b>Familiar analogy:</b> the standard power socket in a wall. Appliances (the capture and
 * context layers) plug into the same socket shape; behind the wall the electricity might come from
 * the grid, a battery, or a generator. {@code ActivityStore} is that socket — one stable shape, many
 * possible sources behind it.</p>
 *
 * <h2>Implementations</h2>
 * <ul>
 *   <li>{@link InMemoryActivityStore} — the default. A bounded, per-session ring-buffer that keeps
 *       only a recent window and evaporates when the process stops. This is the right choice for v1:
 *       the ambient primitive only ever reads a recent slice, so durable storage isn't needed, and a
 *       memory-only store is also the most privacy-friendly default.</li>
 *   <li>Durable backends (a JPA-backed or SQLite-backed store) are deliberately deferred to a later
 *       version. They become useful only once you want activity to survive restarts or be queried
 *       historically — at which point you provide your own {@code @Alternative} implementing this
 *       same SPI, and nothing else changes.</li>
 * </ul>
 *
 * <h2>Contract for implementations</h2>
 * <ul>
 *   <li><b>Be thread-safe.</b> {@link #record} is called from request threads as users act;
 *       {@link #query} may run on another thread when the assistant is invoked.</li>
 *   <li><b>{@link #query} returns a snapshot</b> in <em>chronological order</em> (oldest first), so a
 *       renderer can read it straight through as a timeline. The returned list is the caller's to
 *       keep; mutating it must not affect the store.</li>
 *   <li><b>Never throw from {@link #record}.</b> Capturing activity is a side-channel; a storage
 *       hiccup must never break the user's actual action.</li>
 * </ul>
 *
 * @see UserActivityEvent the value written and read
 * @see ActivityQuery the read-side filter
 * @see InMemoryActivityStore the default implementation
 */
public interface ActivityStore {

    /**
     * Append one event to the timeline.
     *
     * <p>Implementations should treat this as best-effort and non-throwing: it runs on the user's
     * request thread, and recording is never more important than the action being recorded.</p>
     *
     * @param event the event to store; must not be {@code null}
     */
    void record(UserActivityEvent event);

    /**
     * Return the events matching the given query, oldest first, capped by the query's limit.
     *
     * @param query the filter describing which slice to return; must not be {@code null}
     * @return a chronological, caller-owned snapshot list; never {@code null} (empty if nothing matches)
     */
    List<UserActivityEvent> query(ActivityQuery query);

    /**
     * Convenience shortcut for the overwhelmingly common read: "the last {@code limit} events in
     * this session and tab".
     *
     * <p><b>Analogy:</b> a "show recent" button that pre-fills the search form for you. It simply
     * builds an {@link ActivityQuery} with the session, tab and limit set and delegates to
     * {@link #query(ActivityQuery)}.</p>
     *
     * @param sessionId the session to scope to (or {@code null} for any)
     * @param tabId     the tab to scope to (or {@code null} for any)
     * @param limit     the maximum number of most-recent events ({@code <= 0} means no limit)
     * @return a chronological, caller-owned snapshot list; never {@code null}
     */
    default List<UserActivityEvent> recent(String sessionId, String tabId, int limit) {
        return query(ActivityQuery.builder()
                .sessionId(sessionId)
                .tabId(tabId)
                .limit(limit)
                .build());
    }
}
