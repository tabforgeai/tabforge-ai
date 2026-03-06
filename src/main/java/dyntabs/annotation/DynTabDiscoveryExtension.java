package dyntabs.annotation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.AfterDeploymentValidation;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessAnnotatedType;
import jakarta.enterprise.inject.spi.WithAnnotations;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dyntabs.interfaces.DyntabBeanInterface;

/**
 * CDI Extension that automatically scans {@code @DynTab} annotations during bootstrap.
 *
 * <p>This eliminates the need for manual registration in a {@link dyntabs.DynTabConfig} subclass.
 * The developer just places the {@code @DynTab} annotation on the bean and that's it!</p>
 *
 * <p><b>How it works:</b></p>
 * <ol>
 *   <li>During CDI bootstrap, {@link #processDynTabAnnotation} is called for each
 *       class that has a {@code @DynTab} annotation (one or more, thanks to {@code @Repeatable})</li>
 *   <li>Tab information is stored in the static list {@code discoveredTabs}</li>
 *   <li>After deployment, {@link #afterDeploymentValidation} only logs the result</li>
 *   <li>{@link dyntabs.DynTabRegistry} in its {@code @PostConstruct} calls {@link #getDiscoveredTabs()}
 *       and registers all discovered tabs</li>
 * </ol>
 *
 * <p><b>IMPORTANT:</b> The Extension does NOT access CDI beans (does not use {@code CDI.current()}).
 * Reason: during the Extension lifecycle, the EE environment is not yet fully started,
 * and accessing CDI beans causes a SEVERE error on GlassFish:
 * "No valid EE environment for injection of..."
 * Instead, the Extension only collects data, and DynTabRegistry retrieves it later.</p>
 *
 * <p><b>{@code @Repeatable} support:</b>
 * A class can have one or more {@code @DynTab} annotations. When there are multiple,
 * the Java compiler automatically wraps them in a {@code @DynTabs} container annotation.
 * This Extension handles both cases using {@code getAnnotationsByType()}.</p>
 *
 * <p><b>Registration:</b>
 * The Extension is automatically loaded because it is registered in
 * {@code META-INF/services/jakarta.enterprise.inject.spi.Extension}</p>
 *
 * @author DynTabs
 * @see DynTab
 * @see DynTabs
 * @see dyntabs.DynTabRegistry
 */
public class DynTabDiscoveryExtension implements Extension {

    private static final Logger log = LoggerFactory.getLogger(DynTabDiscoveryExtension.class);

    /**
     * Static list of discovered {@code @DynTab} annotations.
     *
     * <p>It is static because the CDI Extension instance and CDI beans (DynTabRegistry)
     * live in different contexts. The Extension populates this list during bootstrap,
     * and DynTabRegistry reads it in its {@code @PostConstruct}.</p>
     *
     * <p>A single class can produce multiple DiscoveredTab objects
     * (one for each {@code @DynTab} annotation on the class).</p>
     */
    private static final List<DiscoveredTab> discoveredTabs = new ArrayList<>();

    /**
     * Returns the list of all discovered {@code @DynTab} annotations.
     * Called from {@link dyntabs.DynTabRegistry#init()} for tab registration.
     *
     * @return an unmodifiable list of discovered tabs
     */
    public static List<DiscoveredTab> getDiscoveredTabs() {
        return Collections.unmodifiableList(discoveredTabs);
    }

    /**
     * Called for each class that has a {@code @DynTab} or {@code @DynTabs} annotation during CDI scanning.
     *
     * <p>Uses {@code getAnnotationsByType(DynTab.class)} instead of {@code getAnnotation(DynTab.class)}
     * to cover both a single {@code @DynTab} and multiple {@code @DynTab} (wrapped in {@code @DynTabs}).</p>
     *
     * <p>{@code getAnnotationsByType()} works for both cases:</p>
     * <ul>
     *   <li>Single {@code @DynTab} annotation -> returns an array with one element</li>
     *   <li>Multiple {@code @DynTab} (wrapped in {@code @DynTabs}) -> returns an array with all elements</li>
     * </ul>
     *
     * @param pat ProcessAnnotatedType event with class information
     * @param <T> the class type
     */
    public <T> void processDynTabAnnotation(
            @Observes @WithAnnotations({DynTab.class, DynTabs.class}) ProcessAnnotatedType<T> pat) {

        Class<T> clazz = pat.getAnnotatedType().getJavaClass();

        // getAnnotationsByType() covers both single and multiple @DynTab annotations
        DynTab[] annotations = clazz.getAnnotationsByType(DynTab.class);

        if (annotations.length == 0) {
            return;
        }

        log.info("DynTabDiscoveryExtension: found {} @DynTab annotation(s) on {}", annotations.length, clazz.getName());

        // Check if the class implements DyntabBeanInterface
        if (!DyntabBeanInterface.class.isAssignableFrom(clazz)) {
            log.warn("{} has @DynTab annotation but does not implement DyntabBeanInterface! Tab may not work correctly.", clazz.getName());
        }

        // Save info for later registration - one DiscoveredTab per annotation
        for (DynTab annotation : annotations) {
            discoveredTabs.add(new DiscoveredTab(annotation, clazz));
        }
    }

    /**
     * Called after CDI deployment is complete.
     *
     * <p>Does not access CDI beans - only logs the scanning result.
     * Actual tab registration happens in {@link dyntabs.DynTabRegistry#init()}.</p>
     *
     * @param adv AfterDeploymentValidation event
     * @param bm BeanManager
     */
    public void afterDeploymentValidation(
            @Observes AfterDeploymentValidation adv, BeanManager bm) {

        if (discoveredTabs.isEmpty()) {
            log.info("DynTabDiscoveryExtension: no @DynTab annotations found");
        } else {
            log.info("DynTabDiscoveryExtension: found {} tab(s) total, will be registered in DynTabRegistry.init()", discoveredTabs.size());
        }
    }

    /**
     * Public class that holds data about a single discovered {@code @DynTab}.
     *
     * <p>Stores a reference to the annotation and the bean class.
     * It is public because {@link dyntabs.DynTabRegistry} needs to access it from its
     * {@code @PostConstruct}.</p>
     *
     * <p>A single bean can have multiple {@code @DynTab} annotations (thanks to {@code @Repeatable}),
     * so such a bean will have multiple DiscoveredTab instances - one for each annotation.
     * Each instance has a different name (from the annotation) but the same beanClass.</p>
     */
    public static class DiscoveredTab {
        private final DynTab annotation;
        private final Class<?> beanClass;

        DiscoveredTab(DynTab annotation, Class<?> beanClass) {
            this.annotation = annotation;
            this.beanClass = beanClass;
        }

        public DynTab getAnnotation() {
            return annotation;
        }

        public Class<?> getBeanClass() {
            return beanClass;
        }
    }
}
