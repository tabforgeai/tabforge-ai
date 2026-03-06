package dyntabs;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.spi.CDI;

import dyntabs.annotation.DynTab;
import dyntabs.annotation.DynTabDiscoveryExtension;
import dyntabs.annotation.DynTabDiscoveryExtension.DiscoveredTab;
import dyntabs.interfaces.DyntabBeanInterface;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Central registry for all dynamic tab definitions in the application.
 *
 * <p>Stores tab definitions as {@code Supplier<DynTab>} - each time a tab is opened,
 * the Supplier creates a fresh DynTab instance with all the configured data.</p>
 *
 * <p><b>Tabs are registered in two ways:</b></p>
 * <ol>
 *   <li><b>AUTOMATIC:</b> via {@code @DynTab} annotation on the bean class.
 *       {@link dyntabs.annotation.DynTabDiscoveryExtension} scans annotations during CDI bootstrap,
 *       and this registry picks them up in its {@code @PostConstruct}.</li>
 *   <li><b>MANUAL:</b> by calling {@link #register(String, java.util.function.Supplier)} from
 *       a {@link DynTabConfig} subclass. Useful for tabs that require more complex configuration
 *       than what the {@code @DynTab} annotation offers.</li>
 * </ol>
 *
 * <p>This is the Jakarta EE replacement for the legacy {@code <managed-bean>} definitions
 * in {@code faces-config.xml} that were used for tab registration in JSF 2.3.</p>
 *
 * @author DynTabs
 * @see dyntabs.annotation.DynTabDiscoveryExtension
 * @see DynTabConfig
 */
@ApplicationScoped
public class DynTabRegistry {

    private static final Logger log = LoggerFactory.getLogger(DynTabRegistry.class);

    /**
     * Map of tab_name -> Supplier that creates DynTab instances.
     * The Supplier is invoked each time the tab is opened (via {@link #createTab}),
     * so every opening gets a fresh object.
     */
    private Map<String, Supplier<dyntabs.DynTab>> tabDefinitions = new HashMap<>();

    /**
     * On registry creation, automatically registers all tabs discovered by
     * {@link dyntabs.annotation.DynTabDiscoveryExtension} during {@code @DynTab} annotation scanning.
     *
     * <p>This happens AFTER the CDI bootstrap, when the EE environment is fully started,
     * so it is safe to use {@code CDI.current()} for bean lookups.</p>
     *
     * <p><b>Sequence of events:</b></p>
     * <ol>
     *   <li>CDI bootstrap: DynTabDiscoveryExtension scans {@code @DynTab} annotations
     *       and stores them in a static list</li>
     *   <li>Application starts: DynTabRegistry is created as an {@code @ApplicationScoped} bean</li>
     *   <li>{@code @PostConstruct}: this method reads the static list and registers Suppliers</li>
     * </ol>
     */
    @PostConstruct
    public void init() {
        List<DiscoveredTab> discovered = DynTabDiscoveryExtension.getDiscoveredTabs();

        if (discovered.isEmpty()) {
            log.info("DynTabRegistry.init(): no auto-discovered tabs found");
            return;
        }

        log.info("DynTabRegistry.init(): registering {} auto-discovered tabs", discovered.size());

        for (DiscoveredTab dt : discovered) {
            registerDiscoveredTab(dt);
        }

        log.info("DynTabRegistry.init(): registration complete, total tabs: {}", tabDefinitions.size());
    }

    /**
     * Registers a tab discovered via {@code @DynTab} annotation.
     *
     * <p>Creates a Supplier that produces a fresh DynTab instance on each invocation,
     * populated with all data from the annotation (title, includePage, closeable,
     * parameters, cdiBean class).</p>
     *
     * @param dt the discovered tab data (annotation + bean class)
     */
    private void registerDiscoveredTab(DiscoveredTab dt) {
        DynTab ann = dt.getAnnotation();
        Class<?> beanClass = dt.getBeanClass();

        String name = ann.name();

        // Derive uniqueIdentifier if not explicitly specified
        String uniqueId = ann.uniqueIdentifier().isEmpty()
                ? name.replace("DynTab", "")
                : ann.uniqueIdentifier();

        log.info("DynTabRegistry: registering tab '{}' (uniqueId={}, class={})", name, uniqueId, beanClass.getSimpleName());

        // Register a Supplier that creates a DynTab instance
        register(name, () -> {
            dyntabs.DynTab tab = new dyntabs.DynTab();
            tab.setUniqueIdentifier(uniqueId);

            // Title - can be an EL expression
            String title = ann.title();
            if (title.startsWith("#{")) {
                try {
                    Object evaluated = JsfUtils.getExpressionValue(title);
                    title = evaluated != null ? evaluated.toString() : title;
                } catch (Exception e) {
                    log.error("DynTabRegistry: error evaluating title '{}': {}", title, e.getMessage(), e);
                }
            }
            tab.setTitle(title);

            tab.setIncludePage(ann.includePage());
            tab.setCloseable(ann.closeable());

            // Parameters - parse from "key=value" format
            if (ann.parameters().length > 0) {
                Map<String, Object> params = new HashMap<>();
                for (String param : ann.parameters()) {
                    int eqPos = param.indexOf('=');
                    if (eqPos > 0) {
                        String key = param.substring(0, eqPos).trim();
                        String value = param.substring(eqPos + 1).trim();
                        // Convert "true"/"false" strings to Boolean
                        if ("true".equalsIgnoreCase(value)) {
                            params.put(key, Boolean.TRUE);
                        } else if ("false".equalsIgnoreCase(value)) {
                            params.put(key, Boolean.FALSE);
                        } else {
                            params.put(key, value);
                        }
                    }
                }
                tab.setParameters(params);
            }

            // Store the CDI bean class - the bean itself will be created later
            // by calling tab.resolveCdiBean(), when TabScope is active.
            // Do NOT use CDI.current().select() here because TabScope
            // may not be active at the time createTab() is called.
            tab.setCdiBeanClass(beanClass);

            return tab;
        });
    }

    /**
     * Manually registers a tab under the given name.
     *
     * <p>Called from {@link DynTabConfig} subclasses for tabs that require more complex
     * configuration than what the {@code @DynTab} annotation offers.</p>
     *
     * @param name        the tab name for registration (e.g. "UsersDynTab")
     * @param tabSupplier the Supplier that creates a DynTab instance on each opening
     */
    public void register(String name, Supplier<dyntabs.DynTab> tabSupplier) {
        tabDefinitions.put(name, tabSupplier);
    }

    /**
     * Creates a new DynTab instance for the given tab name.
     *
     * <p>Invokes the Supplier registered under the given name.
     * Each call creates a new instance (the Supplier is invoked fresh each time).</p>
     *
     * @param name the registered tab name
     * @return a new DynTab instance
     * @throws IllegalArgumentException if no tab with the given name is registered
     */
    public dyntabs.DynTab createTab(String name) {
        Supplier<dyntabs.DynTab> supplier = tabDefinitions.get(name);
        if (supplier == null) {
            throw new IllegalArgumentException("Tab '" + name + "' is not registered");
        }
        return supplier.get();
    }

    /**
     * Checks whether a tab with the given name is registered.
     *
     * @param name the tab name
     * @return true if the tab is registered
     */
    public boolean hasTab(String name) {
        return tabDefinitions.containsKey(name);
    }
}
