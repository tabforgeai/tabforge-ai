package dyntabs.scope;

import jakarta.servlet.annotation.WebListener;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionIdListener;
import jakarta.servlet.http.HttpSessionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HttpSessionListener + HttpSessionIdListener that:
 * <ol>
 *   <li>Destroys all {@code @TabScoped} beans when an HTTP session expires or is invalidated</li>
 *   <li>Tracks session ID changes (session fixation protection) and migrates
 *       beans from the old ID to the new one</li>
 * </ol>
 *
 * <p><b>Why HttpSessionIdListener?</b>
 * When a user logs in, the servlet container changes the session ID to protect
 * against session fixation attacks. Without this listener, beans would remain
 * "trapped" under the old session ID and would never be cleaned up.</p>
 *
 * <p>This listener is automatically registered via the {@code @WebListener} annotation.
 * If automatic registration does not work (e.g. due to scanning configuration),
 * it can be registered in {@code web.xml}:</p>
 * <pre>
 * {@code
 * <listener>
 *     <listener-class>dyntabs.scope.TabScopeSessionListener</listener-class>
 * </listener>
 * }
 * </pre>
 *
 * @author DynTabs
 * @see TabScopedContextHolder#destroyAllBeansForSession(String)
 * @see TabScopedContextHolder#migrateSession(String, String)
 */
@WebListener
public class TabScopeSessionListener implements HttpSessionListener, HttpSessionIdListener {

    private static final Logger log = LoggerFactory.getLogger(TabScopeSessionListener.class);

    /**
     * Called when a new HTTP session is created.
     * Only logs the session creation for debugging purposes.
     *
     * @param se HttpSessionEvent with session information
     */
    @Override
    public void sessionCreated(HttpSessionEvent se) {
        log.info("TabScopeSessionListener: session created {}", se.getSession().getId());
    }

    /**
     * Called when an HTTP session expires or is invalidated.
     *
     * <p>Destroys all {@code @TabScoped} beans associated with that session, calling
     * {@code @PreDestroy} methods on each bean and releasing resources.</p>
     *
     * @param se HttpSessionEvent with session information
     */
    @Override
    public void sessionDestroyed(HttpSessionEvent se) {
        String sessionId = se.getSession().getId();
        log.info("TabScopeSessionListener: session being destroyed {}, cleaning up @TabScoped beans...", sessionId);
        try {
            TabScopedContextHolder.getInstance().destroyAllBeansForSession(sessionId);
        } catch (Exception e) {
            log.error("TabScopeSessionListener: error cleaning beans for session {}", sessionId, e);
        }
    }

    /**
     * Called when the servlet container changes the session ID.
     *
     * <p>This happens during:</p>
     * <ul>
     *   <li>Login (session fixation protection)</li>
     *   <li>Explicit call to {@code HttpServletRequest.changeSessionId()}</li>
     * </ul>
     *
     * <p>Migrates all {@code @TabScoped} beans from the old session ID to the new one,
     * so beans remain accessible after the ID change.</p>
     *
     * @param se HttpSessionEvent with the new session ID
     * @param oldSessionId the previous session ID
     */
    @Override
    public void sessionIdChanged(HttpSessionEvent se, String oldSessionId) {
        String newSessionId = se.getSession().getId();
        log.info("TabScopeSessionListener: session ID changed: {} -> {}", oldSessionId, newSessionId);
        try {
            TabScopedContextHolder.migrateSession(oldSessionId, newSessionId);
        } catch (Exception e) {
            log.error("TabScopeSessionListener: error migrating beans from {} to {}", oldSessionId, newSessionId, e);
        }
    }
}
