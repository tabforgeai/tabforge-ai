package dyntabs.scope;

import java.lang.annotation.Annotation;

import jakarta.enterprise.context.spi.Context;
import jakarta.enterprise.context.spi.Contextual;
import jakarta.enterprise.context.spi.CreationalContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CDI Context implementation for {@code @TabScoped} beans.
 *
 * <p>This class is the core of the TabScoped mechanism - the CDI container calls it
 * when it needs to create or return an existing instance of a {@code @TabScoped} bean.</p>
 *
 * <p><b>How it works:</b></p>
 * <ol>
 *   <li>When a {@code @TabScoped} bean is accessed, CDI calls the {@link #get} method</li>
 *   <li>{@code get()} uses {@link TabScopedContextHolder#getCurrentTabId()} to determine
 *       which tab is currently active</li>
 *   <li>Looks up an existing instance for that tab, or creates a new one if none exists</li>
 *   <li>Returns the instance (actually a proxy, since this is a NormalScope)</li>
 * </ol>
 *
 * <p><b>IMPORTANT:</b> Before accessing a {@code @TabScoped} bean, you MUST call
 * {@link TabScopedContextHolder#setCurrentTabId(String)} so the Context knows
 * which tab scope to operate in.</p>
 *
 * @author DynTabs
 * @see TabScoped
 * @see TabScopedContextHolder
 */
public class TabContext implements Context {

    private static final Logger log = LoggerFactory.getLogger(TabContext.class);

    /**
     * Returns the scope annotation class that this Context implements.
     *
     * @return TabScoped.class
     */
    @Override
    public Class<? extends Annotation> getScope() {
        return TabScoped.class;
    }

    /**
     * Returns an existing bean instance for the current tab, or creates a new one.
     *
     * <p>This is the main method called by CDI when a {@code @TabScoped} bean is accessed.</p>
     *
     * @param contextual the bean type (contains bean metadata)
     * @param creationalContext the context for creating a new bean instance
     * @return the bean instance for the current tab
     */
    @Override
    public <T> T get(Contextual<T> contextual, CreationalContext<T> creationalContext) {
        String tabId = TabScopedContextHolder.getCurrentTabId();

        if (tabId == null) {
            // Fail-fast: throw an exception instead of creating an untracked instance.
            // Previously, a bean was created here but NOT stored in the tabBeans map,
            // which caused a memory leak (the instance was never destroyed,
            // @PreDestroy was never called, and each subsequent access created
            // a new instance). Now we explicitly report the error.
            log.error("TabContext.get() called without an active tabId! Bean: {}. You must call TabScopedContextHolder.setCurrentTabId() before accessing a @TabScoped bean.", contextual);
            throw new jakarta.enterprise.context.ContextNotActiveException(
                    "TabScoped context is not active: currentTabId is not set. " +
                    "Verify that TabScopePhaseListener is registered and " +
                    "that the @TabScoped bean is used within a JSF request with an active tab.");
        }

        TabScopedContextHolder holder = TabScopedContextHolder.getInstance();

        // Check if an instance already exists for this tab
        TabScopedContextHolder.BeanInstance<T> beanInstance = holder.getBean(tabId, contextual);

        if (beanInstance != null) {
            log.debug("TabContext.get(): returning existing instance for tab {}", tabId);
            return beanInstance.getInstance();
        }

        // Does not exist - create a new instance
        if (creationalContext != null) {
            log.debug("TabContext.get(): creating new instance for tab {}", tabId);
            T instance = contextual.create(creationalContext);
            holder.putBean(tabId, contextual, instance, creationalContext);
            return instance;
        }

        return null;
    }

    /**
     * Returns an existing bean instance for the current tab, or null if none exists.
     *
     * <p>Unlike {@link #get(Contextual, CreationalContext)}, this method NEVER
     * creates a new instance - it only returns an existing one or null.</p>
     *
     * @param contextual the bean type
     * @return the existing instance, or null if not found
     */
    @Override
    public <T> T get(Contextual<T> contextual) {
        String tabId = TabScopedContextHolder.getCurrentTabId();

        if (tabId == null) {
            return null;
        }

        TabScopedContextHolder holder = TabScopedContextHolder.getInstance();
        TabScopedContextHolder.BeanInstance<T> beanInstance = holder.getBean(tabId, contextual);

        if (beanInstance != null) {
            return beanInstance.getInstance();
        }

        return null;
    }

    /**
     * Checks whether this Context is currently active.
     *
     * <p>The context is active if a current tabId is set, meaning code is
     * executing within the context of some tab.</p>
     *
     * @return true if the Context is active (has an active tab)
     */
    @Override
    public boolean isActive() {
        // Context is active if a current tabId exists
        String tabId = TabScopedContextHolder.getCurrentTabId();
        boolean active = tabId != null;
        return active;
    }
}
