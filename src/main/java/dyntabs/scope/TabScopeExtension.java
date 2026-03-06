package dyntabs.scope;

import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.BeforeBeanDiscovery;
import jakarta.enterprise.inject.spi.Extension;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CDI Extension that registers the {@code @TabScoped} scope in the CDI container.
 *
 * <p>CDI Extensions are a mechanism for extending CDI functionality.
 * This extension is automatically loaded at application startup
 * (registered in {@code META-INF/services/jakarta.enterprise.inject.spi.Extension}).</p>
 *
 * <p><b>What it does:</b></p>
 * <ol>
 *   <li>{@link #beforeBeanDiscovery} - adds {@code @TabScoped} as a new scope type</li>
 *   <li>{@link #afterBeanDiscovery} - registers {@link TabContext} as the Context implementation</li>
 * </ol>
 *
 * <p>After registration, the CDI container knows how to manage {@code @TabScoped} beans.</p>
 *
 * @author DynTabs
 * @see TabScoped
 * @see TabContext
 */
public class TabScopeExtension implements Extension {

    private static final Logger log = LoggerFactory.getLogger(TabScopeExtension.class);

    /**
     * Called before the CDI container discovers beans.
     * Registers {@code @TabScoped} as a new scope type.
     *
     * @param bbd BeforeBeanDiscovery event
     */
    public void beforeBeanDiscovery(@Observes BeforeBeanDiscovery bbd) {
        log.info("TabScopeExtension: registering @TabScoped scope");
        // Add @TabScoped as a scope annotation
        bbd.addScope(TabScoped.class, true, false);
        // Parameters:
        // - TabScoped.class: the annotation class
        // - true: normalScope (uses proxy)
        // - false: passivating (does not support passivation/serialization)
    }

    /**
     * Called after the CDI container finishes bean discovery.
     * Registers {@link TabContext} as the Context implementation for {@code @TabScoped}.
     *
     * @param abd AfterBeanDiscovery event
     */
    public void afterBeanDiscovery(@Observes AfterBeanDiscovery abd) {
        log.info("TabScopeExtension: registering TabContext");
        // Add TabContext as the Context implementation for @TabScoped
        abd.addContext(new TabContext());
    }
}
