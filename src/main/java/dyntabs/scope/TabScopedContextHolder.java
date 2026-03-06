package dyntabs.scope;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.spi.Contextual;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.faces.context.FacesContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Singleton class that stores all {@code @TabScoped} bean instances organized
 * by HTTP session and by tab ID.
 *
 * <p><b>IMPORTANT:</b> Bean instances are isolated per session! Each HTTP session has
 * its own independent map of tabs and beans. This prevents cross-session
 * data leaks (user A cannot see user B's beans).</p>
 *
 * <p><b>Data structure:</b></p>
 * <pre>
 * sessionTabBeans (Map)
 *   └── "session_abc" (HTTP session ID)
 *         └── "r0" (tab ID)
 *               └── Map&lt;Contextual, BeanInstance&gt;
 *                     ├── MyBean.class -> instance1
 *                     └── OtherBean.class -> instance2
 *         └── "r1" (tab ID)
 *               └── Map&lt;Contextual, BeanInstance&gt;
 *                     ├── MyBean.class -> instance3
 *                     └── ...
 *   └── "session_xyz" (another session)
 *         └── "r0" (tab ID - same as first session, but DIFFERENT beans!)
 *               └── ...
 * </pre>
 *
 * @author DynTabs
 * @see TabScopeSessionListener
 */
public class TabScopedContextHolder {

    private static final Logger log = LoggerFactory.getLogger(TabScopedContextHolder.class);

    /**
     * Singleton instance.
     */
    private static final TabScopedContextHolder INSTANCE = new TabScopedContextHolder();

    /**
     * Map storing beans by HTTP session, then by tab ID.
     *
     * <p>Outer key: HTTP session ID (e.g. "abc123")<br>
     * Inner key: tab ID (e.g. "r0", "r1")<br>
     * Value: map of beans for that tab in that session</p>
     *
     * <p>ConcurrentHashMap is used because different sessions access
     * the map from different threads simultaneously.</p>
     */
    private static final Map<String, Map<String, Map<Contextual<?>, BeanInstance<?>>>> sessionTabBeans =
            new ConcurrentHashMap<>();

    /**
     * ID of the currently active tab (the tab the user is currently working with).
     * ThreadLocal is per-request, which is correct since each HTTP request
     * belongs to one session and works with one tab.
     */
    private static final ThreadLocal<String> currentTabId = new ThreadLocal<>();

    /**
     * Private constructor - singleton pattern.
     */
    private TabScopedContextHolder() {
    }

    /**
     * Returns the singleton instance of the holder.
     *
     * @return the singleton instance
     */
    public static TabScopedContextHolder getInstance() {
        return INSTANCE;
    }

    // ==================== ThreadLocal methods (per-request) ====================

    /**
     * Sets the ID of the currently active tab.
     * Called before accessing a {@code @TabScoped} bean so the CDI Context
     * knows which tab scope to look up/create the bean in.
     *
     * @param tabId the tab ID (e.g. "r0", "r1")
     */
    public static void setCurrentTabId(String tabId) {
        log.debug("TabScopedContextHolder.setCurrentTabId({})", tabId);
        currentTabId.set(tabId);
    }

    /**
     * Returns the ID of the currently active tab.
     *
     * @return the tab ID, or null if not set
     */
    public static String getCurrentTabId() {
        return currentTabId.get();
    }

    /**
     * Clears the currently active tab ID.
     * Called after tab processing is complete.
     */
    public static void clearCurrentTabId() {
        currentTabId.remove();
    }

    // ==================== Session ID helper ====================

    /**
     * Returns the current HTTP session ID from FacesContext.
     *
     * <p>FacesContext is always available during a JSF request, which covers
     * all situations where {@code @TabScoped} beans are used (PhaseListener,
     * EL expressions, managed bean methods).</p>
     *
     * @return the HTTP session ID
     * @throws IllegalStateException if FacesContext is not available
     */
    private static String getSessionId() {
        FacesContext fc = FacesContext.getCurrentInstance();
        if (fc != null && fc.getExternalContext() != null) {
            // false = do not create a new session if none exists
            String sessionId = fc.getExternalContext().getSessionId(false);
            if (sessionId != null) {
                return sessionId;
            }
        }
        throw new IllegalStateException(
                "TabScopedContextHolder: cannot determine session ID - " +
                "FacesContext is not available or session does not exist");
    }

    // ==================== Per-session bean storage ====================

    /**
     * Returns the tabBeans map for the current HTTP session.
     * If no map exists for this session, creates a new one.
     *
     * @return the tabBeans map for the current session
     */
    private Map<String, Map<Contextual<?>, BeanInstance<?>>> getTabBeansForCurrentSession() {
        String sessionId = getSessionId();
        return sessionTabBeans.computeIfAbsent(sessionId, k -> {
            log.info("TabScopedContextHolder: created tabBeans map for session {}", k);
            return new ConcurrentHashMap<>();
        });
    }

    /**
     * Returns the bean map for the given tab in the current session.
     * If no map exists, creates a new empty one.
     *
     * @param tabId the tab ID
     * @return the bean map for that tab
     */
    public Map<Contextual<?>, BeanInstance<?>> getBeansForTab(String tabId) {
        return getTabBeansForCurrentSession().computeIfAbsent(tabId, k -> new ConcurrentHashMap<>());
    }

    /**
     * Returns the bean instance for the given tab and bean type in the current session.
     *
     * @param tabId the tab ID
     * @param contextual the bean type (Contextual)
     * @return BeanInstance, or null if not found
     */
    @SuppressWarnings("unchecked")
    public <T> BeanInstance<T> getBean(String tabId, Contextual<T> contextual) {
        Map<String, Map<Contextual<?>, BeanInstance<?>>> tabBeans = getTabBeansForCurrentSession();
        Map<Contextual<?>, BeanInstance<?>> beans = tabBeans.get(tabId);
        if (beans != null) {
            return (BeanInstance<T>) beans.get(contextual);
        }
        return null;
    }

    /**
     * Stores a bean instance for the given tab in the current session.
     *
     * @param tabId the tab ID
     * @param contextual the bean type (Contextual)
     * @param instance the bean instance
     * @param creationalContext the CDI creational context
     */
    public <T> void putBean(String tabId, Contextual<T> contextual, T instance,
                            CreationalContext<T> creationalContext) {
        Map<Contextual<?>, BeanInstance<?>> beans = getBeansForTab(tabId);
        beans.put(contextual, new BeanInstance<>(instance, creationalContext));
        log.debug("TabScopedContextHolder: stored bean {} for tab {} (session {})", contextual, tabId, getSessionId());
    }

    /**
     * Destroys all beans associated with the given tab in the current session.
     * Called when a tab is being closed.
     *
     * <p>For each bean, calls {@code destroy()} which triggers {@code @PreDestroy} methods.</p>
     *
     * @param tabId the ID of the tab being closed
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void destroyBeansForTab(String tabId) {
        log.info("TabScopedContextHolder.destroyBeansForTab({}) - begin", tabId);
        Map<String, Map<Contextual<?>, BeanInstance<?>>> tabBeans = getTabBeansForCurrentSession();
        Map<Contextual<?>, BeanInstance<?>> beans = tabBeans.remove(tabId);
        if (beans != null) {
            for (Map.Entry<Contextual<?>, BeanInstance<?>> entry : beans.entrySet()) {
                Contextual contextual = entry.getKey();
                BeanInstance beanInstance = entry.getValue();
                try {
                    // Calls @PreDestroy methods and releases resources
                    contextual.destroy(beanInstance.getInstance(), beanInstance.getCreationalContext());
                    log.debug("TabScopedContextHolder: destroyed bean {} for tab {}", contextual, tabId);
                } catch (Exception e) {
                    log.error("Error destroying bean {}: {}", contextual, e.getMessage(), e);
                }
            }
        }
        log.info("TabScopedContextHolder.destroyBeansForTab({}) - end", tabId);
    }

    /**
     * Checks whether a scope exists for the given tab in the current session.
     *
     * @param tabId the tab ID
     * @return true if at least one bean exists for that tab
     */
    public boolean hasTab(String tabId) {
        return getTabBeansForCurrentSession().containsKey(tabId);
    }

    // ==================== Session cleanup ====================

    /**
     * Destroys ALL beans for the given HTTP session.
     *
     * <p>Called from {@link TabScopeSessionListener} when a session expires or is invalidated.
     * This prevents memory leaks - without this, beans would remain in the map forever
     * even after the session no longer exists.</p>
     *
     * <p><b>IMPORTANT:</b> This method is called OUTSIDE a JSF request (from HttpSessionListener),
     * so it does NOT use FacesContext. Instead, it receives sessionId as a parameter.</p>
     *
     * @param sessionId the ID of the HTTP session being destroyed
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void destroyAllBeansForSession(String sessionId) {
        log.info("TabScopedContextHolder.destroyAllBeansForSession({}) - begin", sessionId);
        Map<String, Map<Contextual<?>, BeanInstance<?>>> tabBeans = sessionTabBeans.remove(sessionId);
        if (tabBeans != null) {
            int totalDestroyed = 0;
            for (Map.Entry<String, Map<Contextual<?>, BeanInstance<?>>> tabEntry : tabBeans.entrySet()) {
                String tabId = tabEntry.getKey();
                Map<Contextual<?>, BeanInstance<?>> beans = tabEntry.getValue();
                for (Map.Entry<Contextual<?>, BeanInstance<?>> beanEntry : beans.entrySet()) {
                    Contextual contextual = beanEntry.getKey();
                    BeanInstance beanInstance = beanEntry.getValue();
                    try {
                        contextual.destroy(beanInstance.getInstance(), beanInstance.getCreationalContext());
                        log.debug("TabScopedContextHolder: destroyed bean {} for tab {} (session cleanup)", contextual, tabId);
                        totalDestroyed++;
                    } catch (Exception e) {
                        log.error("Error destroying bean {} during session cleanup: {}", contextual, e.getMessage(), e);
                    }
                }
            }
            log.info("TabScopedContextHolder.destroyAllBeansForSession({}) - destroyed {} beans", sessionId, totalDestroyed);
        } else {
            log.info("TabScopedContextHolder.destroyAllBeansForSession({}) - no beans for this session", sessionId);
        }
        log.info("TabScopedContextHolder.destroyAllBeansForSession({}) - end", sessionId);
    }

    /**
     * Migrates all beans from the old session ID to the new one.
     *
     * <p>Called from {@link TabScopeSessionListener#sessionIdChanged} when the
     * servlet container changes the session ID (session fixation protection
     * during login, or explicit {@code changeSessionId()} call).</p>
     *
     * <p>Without this, beans would remain "trapped" under the old session ID
     * and would never be cleaned up or accessible under the new ID.</p>
     *
     * @param oldSessionId the previous session ID
     * @param newSessionId the new session ID
     */
    public static void migrateSession(String oldSessionId, String newSessionId) {
        Map<String, Map<Contextual<?>, BeanInstance<?>>> tabBeans = sessionTabBeans.remove(oldSessionId);
        if (tabBeans != null) {
            sessionTabBeans.put(newSessionId, tabBeans);
            log.info("TabScopedContextHolder: migrated beans from session {} to {} ({} tabs)", oldSessionId, newSessionId, tabBeans.size());
        } else {
            log.info("TabScopedContextHolder: session migration {} -> {} - no beans to migrate", oldSessionId, newSessionId);
        }
    }

    /**
     * Returns the number of active sessions with {@code @TabScoped} beans.
     * Useful for monitoring and debugging.
     *
     * @return the number of sessions
     */
    public static int getActiveSessionCount() {
        return sessionTabBeans.size();
    }

    // ==================== BeanInstance inner class ====================

    /**
     * Internal class that stores a bean instance together with its creational context.
     * The creational context is needed for proper bean destruction.
     *
     * @param <T> the bean type
     */
    public static class BeanInstance<T> {
        private final T instance;
        private final CreationalContext<T> creationalContext;

        /**
         * Creates a new BeanInstance.
         *
         * @param instance the bean instance
         * @param creationalContext the CDI creational context
         */
        public BeanInstance(T instance, CreationalContext<T> creationalContext) {
            this.instance = instance;
            this.creationalContext = creationalContext;
        }

        /**
         * @return the bean instance
         */
        public T getInstance() {
            return instance;
        }

        /**
         * @return the CDI creational context
         */
        public CreationalContext<T> getCreationalContext() {
            return creationalContext;
        }
    }
}
