package dyntabs.ai.activity;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dyntabs.scope.TabScopedContextHolder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;

/**
 * The single application-facing entry point for writing to the ambient activity timeline — a thin
 * facade over the {@link ActivityStore} that stamps each event with the current session and tab, so
 * callers never have to figure out "where am I" themselves.
 *
 * <h2>What this class is for</h2>
 * <p>Two kinds of caller record activity, and both go through here:</p>
 * <ul>
 *   <li>the {@link ActivityTrackedInterceptor}, automatically, for every {@code @ActivityTracked} method;</li>
 *   <li>your own beans, manually, for things an annotation can't express (a free-text note typed into a
 *       field, a multi-step wizard completing) — e.g. {@code recorder.record(Type.NOTE, "note", null, body)}.</li>
 * </ul>
 * <p>Centralising it means the rule for "which session/tab does this belong to" lives in one place and is
 * applied consistently, whichever path recorded the event.</p>
 *
 * <p><b>Familiar analogy:</b> the front desk where everyone drops off their timesheets. Whether the entry
 * came from an automatic badge-reader (the interceptor) or someone filling in a slip by hand (manual
 * calls), the front desk stamps it with today's date and the right department (session + tab) before
 * filing it. The filing cabinet itself is the {@link ActivityStore}.</p>
 *
 * <h2>How the result is used</h2>
 * <p>Everything recorded here lands in the {@link ActivityStore}, from which an {@link ActivityContext}
 * later reads a recent slice to inject into the assistant's prompt. In other words, this class is the
 * write-end of the loop whose read-end is {@link ActivityContext#render()}.</p>
 *
 * @see ActivityStore the backing store events are written to
 * @see ActivityTrackedInterceptor the automatic caller
 * @see ActivityContext the read-side that consumes what is recorded here
 */
@ApplicationScoped
public class ActivityRecorder {

    private static final Logger log = LoggerFactory.getLogger(ActivityRecorder.class);

    @Inject
    ActivityStore store;

    /**
     * Record a fully-built event, filling in the current session and tab if the caller left them unset.
     *
     * <p>Called by {@link ActivityTrackedInterceptor} (which builds the event from method metadata) and
     * by any bean holding a pre-built {@link UserActivityEvent}. The event is then handed to the
     * {@link ActivityStore}, where it waits to be read back by an {@link ActivityContext}.</p>
     *
     * @param event the event to store; must not be {@code null}. If its {@code sessionId}/{@code tabId}
     *              are {@code null}, they are stamped from the current context before storing.
     */
    public void record(UserActivityEvent event) {
        if (event == null) {
            return;
        }
        UserActivityEvent stamped = event;
        if (event.sessionId() == null || event.tabId() == null) {
            stamped = UserActivityEvent.builder(event.type())
                    .timestamp(event.timestamp())
                    .sessionId(event.sessionId() != null ? event.sessionId() : currentSessionId())
                    .tabId(event.tabId() != null ? event.tabId() : currentTabId())
                    .verb(event.verb())
                    .entities(event.entities())
                    .text(event.text())
                    .build();
        }
        try {
            store.record(stamped);
        } catch (RuntimeException e) {
            // Recording is a side-channel: a storage hiccup must never break the user's real action.
            log.warn("Failed to record activity {}: {}", stamped, e.getMessage());
        }
    }

    /**
     * Convenience builder-and-record for callers that don't want to assemble a {@link UserActivityEvent}.
     *
     * <p>Stamps the current session/tab and timestamp automatically, then delegates to
     * {@link #record(UserActivityEvent)}. Handy for manual notes/searches from a backing bean.</p>
     *
     * @param type     the event category (required)
     * @param verb     the act in your vocabulary (may be {@code null})
     * @param entities the business objects touched (may be {@code null} or empty)
     * @param text     authored text such as a note or query (may be {@code null})
     */
    public void record(UserActivityEvent.Type type, String verb, List<EntityRef> entities, String text) {
        record(UserActivityEvent.builder(type)
                .sessionId(currentSessionId())
                .tabId(currentTabId())
                .verb(verb)
                .entities(entities)
                .text(text)
                .build());
    }

    /**
     * Resolve the id of the tab the caller is currently acting in.
     *
     * <p>Used internally to stamp events; reads from {@link TabScopedContextHolder#getCurrentTabId()},
     * which TabForge keeps set for the duration of a tab-scoped request.</p>
     *
     * @return the current tab id, or {@code null} when there is no active tab (e.g. outside a JSF request)
     */
    public String currentTabId() {
        try {
            return TabScopedContextHolder.getCurrentTabId();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Resolve the id of the HTTP session the caller is currently in.
     *
     * <p>Used internally to scope the timeline per user session; reads the session id from the active
     * {@link FacesContext} without forcing a session to be created.</p>
     *
     * @return the current session id, or {@code null} when there is no active JSF request/session
     */
    public String currentSessionId() {
        try {
            FacesContext fc = FacesContext.getCurrentInstance();
            if (fc == null) {
                return null;
            }
            return fc.getExternalContext().getSessionId(false);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
