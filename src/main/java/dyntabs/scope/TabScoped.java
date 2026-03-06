package dyntabs.scope;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.enterprise.context.NormalScope;

/**
 * CDI scope annotation for beans that live in the context of a dynamic tab.
 *
 * <p>Each DynTab has its own isolated scope - when two tabs are opened with the same
 * includePage, each tab gets its own instance of the {@code @TabScoped} bean.</p>
 *
 * <p><b>Lifecycle:</b></p>
 * <ul>
 *   <li>Bean is created when first accessed within a tab</li>
 *   <li>Bean lives as long as the tab is open</li>
 *   <li>Bean is destroyed when the tab is closed</li>
 * </ul>
 *
 * <p><b>Usage example:</b></p>
 * <pre>
 * {@code
 * @Named
 * @TabScoped
 * public class MyTabBean implements DyntabBeanInterface {
 *     // ...
 * }
 * }
 * </pre>
 *
 * <p><b>IMPORTANT:</b> A bean annotated with {@code @TabScoped} MUST implement
 * {@link dyntabs.interfaces.DyntabBeanInterface} and must be associated with a
 * DynTab via the cdiBean property.</p>
 *
 * @author DynTabs
 * @see TabContext
 * @see TabScopedContextHolder
 */
@NormalScope(passivating = false)
@Inherited
@Documented
@Target({ ElementType.TYPE, ElementType.METHOD, ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
public @interface TabScoped {
}
