package dyntabs.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Container annotation for {@code @Repeatable} support of the {@link DynTab} annotation.
 *
 * <p>This annotation exists solely because Java requires a "container" annotation
 * when using the {@code @Repeatable} mechanism. Developers never use it directly -
 * the Java compiler automatically generates it when a class has more than one
 * {@code @DynTab} annotation.</p>
 *
 * <p><b>Why it is needed:</b>
 * A single CDI bean can serve as the basis for multiple different tabs.
 * For example, a DocsBean can be used for both a "Documents" tab and an "All Documents" tab,
 * with different parameters. Without {@code @Repeatable} support, this would not be possible
 * since Java does not allow two identical annotations on the same class.</p>
 *
 * <p><b>Example</b> (developer writes only {@code @DynTab}, compiler automatically wraps in {@code @DynTabs}):</p>
 * <pre>
 * {@code
 * @Named
 * @TabScoped
 * @DynTab(name = "DocumentsDynTab",
 *         title = "Documents",
 *         includePage = "/WEB-INF/include/docs/docs.xhtml",
 *         parameters = {"listAll=false"})
 * @DynTab(name = "AllDocsDynTab",
 *         title = "All Documents",
 *         includePage = "/WEB-INF/include/docs/docs.xhtml",
 *         parameters = {"listAll=true"})
 * public class DocsBean implements DyntabBeanInterface {
 *     // one bean, two different tabs with different parameters
 * }
 * }
 * </pre>
 *
 * @author DynTabs
 * @see DynTab
 * @see DynTabDiscoveryExtension
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface DynTabs {

    /**
     * Array of {@code @DynTab} annotations placed on the class.
     * Automatically populated by the Java compiler - developers do not use this directly.
     *
     * @return all {@code @DynTab} annotations from the class
     */
    DynTab[] value();
}
