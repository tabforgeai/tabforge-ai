package dyntabs.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for automatic registration of a CDI bean as a dynamic tab.
 *
 * <p>Placed on a class that implements {@link dyntabs.interfaces.DyntabBeanInterface}.
 * The DynTabs library automatically scans all beans with this annotation
 * and registers them in {@link dyntabs.DynTabRegistry}.</p>
 *
 * <p>This eliminates the need for manual registration in a {@link dyntabs.DynTabConfig} subclass!</p>
 *
 * <p>The annotation is {@code @Repeatable} - it can be placed multiple times on the same class.
 * This allows a single CDI bean to serve as the basis for multiple different tabs,
 * each with its own name, title, and parameters. Typical example: same XHTML page
 * and same bean, but with different parameters controlling behavior.</p>
 *
 * <p><b>Single tab example:</b></p>
 * <pre>
 * {@code
 * @Named
 * @TabScoped
 * @DynTab(name = "UsersDynTab",
 *         uniqueIdentifier="Users",
 *         title = "The Users",
 *         includePage = "/WEB-INF/include/users/users.xhtml")
 * public class UsersBean implements DyntabBeanInterface {
 *     // just business logic, no manual registration needed
 * }
 * }
 * </pre>
 *
 * <p><b>Two tabs on the same bean (one secured with declarative roles):</b></p>
 * <pre>
 * {@code
 * @Named
 * @TabScoped
 * @DynTab(name = "DocumentsDynTab",
 *         uniqueIdentifier="Documents",
 *         title = "The Documents",
 *         includePage = "/WEB-INF/include/docs/docs.xhtml",
 *         parameters = {"listAll=false"})
 * @DynTab(name = "AllDocsDynTab",
 *         uniqueIdentifier="AllDocs",
 *         title = "All Documents",
 *         closeable = true,
 *         securedResource = true,
 *         allowedRoles = {"ADMIN", "MANAGER"},
 *         includePage = "/WEB-INF/include/docs/docs.xhtml",
 *         parameters = {"listAll=true"})
 * public class DocsBean implements DyntabBeanInterface {
 *     // "Documents" tab is open to everyone
 *     // "AllDocs" tab requires ADMIN or MANAGER role
 * }
 * }
 * </pre>
 *
 * <p>Menu items can then simply use:</p>
 * <pre>
 * {@code
 * <p:menuitem value="Users" action="uishell:Users" />
 * <p:menuitem value="Documents" action="uishell:Documents" />
 * <p:menuitem value="All Documents" action="uishell:AllDocs" />
 * }
 * </pre>
 * so, the pattern for action atribute value is: "uishell:_dynTab_uniqueIdentifier"
 *
 * <p><b>NOTE:</b> The bean MUST implement {@link dyntabs.interfaces.DyntabBeanInterface}.
 * Using {@code @TabScoped} instead of {@code @ViewScoped} is recommended for isolation between tabs.</p>
 *
 * @author DynTabs
 * @see DynTabs
 * @see dyntabs.scope.TabScoped
 * @see dyntabs.interfaces.DyntabBeanInterface
 * @see DynTabDiscoveryExtension
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Repeatable(DynTabs.class)
public @interface DynTab {

    /**
     * The tab name for registration (e.g. "UsersDynTab").
     *
     * <p>This name is used in action outcomes on XHTML pages:
     * {@code action="uishell:Users"} will look for a tab registered as "UsersDynTab".
     * Convention: bean name + "DynTab" suffix.</p>
     */
    String name();

    /**
     * The tab title displayed to the user in the tab header.
     *
     * <p>Can be:</p>
     * <ul>
     *   <li>Plain text: "Users"</li>
     *   <li>EL expression: {@code "#{msg['users.title']}"}</li>
     *   <li>EL expression with a bean: {@code "#{usersBean.tabTitle}"}</li>
     * </ul>
     */
    String title();

    /**
     * Path to the XHTML page included in the tab.
     *
     * <p>Example: "/WEB-INF/include/users/users.xhtml"</p>
     *
     * <p>This page will be rendered inside the tab and has access
     * to the dynTab object via the {@code #{dynTab}} parameter.</p>
     */
    String includePage();

    /**
     * Whether the user can close the tab by clicking the X button.
     *
     * <p>Default: true</p>
     *
     * <p>For "Home" or "Dashboard" tabs that should always remain open,
     * set this to false.</p>
     */
    boolean closeable() default true;

    /**
     * Unique identifier for the tab.
     *
     * <p>Used to check if a tab already exists (to prevent opening duplicates).</p>
     *
     * <p>If not specified, it is automatically derived from the {@link #name} attribute
     * by removing the "DynTab" suffix (e.g. "UsersDynTab" -> "Users").</p>
     */
    String uniqueIdentifier() default "";

    /**
     * Whether this dynamic tab is a secured (protected) resource.
     *
     * <p>When set to {@code true}, the {@link security.AbstractSecuredResourceScanner}
     * registers this tab's {@link #uniqueIdentifier()} as a secured resource at deploy time.
     * The {@link security.AbstractAccessCheckInterceptor} then enforces access control
     * when the tab is opened via {@code callAccessPointMethod()}.</p>
     *
     * <p><b>Access rights can be granted in two ways:</b></p>
     * <ol>
     *   <li><b>Declarative (InMemory):</b> Specify {@link #allowedRoles()} in this annotation.
     *       The {@link security.InMemorySecuredResourceScanner} reads both {@code securedResource}
     *       and {@code allowedRoles} at deploy time and stores them in memory. The paired
     *       {@link security.InMemoryAccessCheckInterceptor} enforces access based on these
     *       declared roles. This is the default approach — zero configuration, everything
     *       is declared in the annotation.</li>
     *   <li><b>DB-based:</b> The developer creates a custom {@code DBSecuredResourceScanner}
     *       (extending {@link security.AbstractSecuredResourceScanner}) that writes secured
     *       resources to a database table, ignoring {@code allowedRoles}. Access rules
     *       are managed through an Admin UI. A paired {@code DBAccessCheckInterceptor}
     *       reads allowed roles from the database at runtime.</li>
     * </ol>
     *
     * <p>Default: {@code false} (tab is not protected)</p>
     */
    boolean securedResource() default false;

    /**
     * Human-readable display name for this secured resource.
     *
     * <p>Used in admin UIs for security management. Can be a literal string
     * or a resource bundle key for i18n. Only meaningful when
     * {@link #securedResource()} is {@code true}.</p>
     */
    String securedResourceDisplayName() default "";

    /**
     * Roles allowed to access this tab (declarative access control).
     *
     * <p>Used by the {@link security.InMemorySecuredResourceScanner} /
     * {@link security.InMemoryAccessCheckInterceptor} pair. Only meaningful when
     * {@link #securedResource()} is {@code true}.</p>
     *
     * <p>Example:</p>
     * <pre>{@code
     * @DynTab(name = "AllDocsDynTab",
     *         uniqueIdentifier = "AllDocs",
     *         title = "All Documents",
     *         securedResource = true,
     *         allowedRoles = {"ADMIN", "MANAGER"},
     *         includePage = "/WEB-INF/include/docs/docs.xhtml")
     * }</pre>
     *
     * <p>Ignored by DB-based implementations, where access rules are managed
     * through an Admin UI and stored in the database.</p>
     */
    String[] allowedRoles() default {};

    /**
     * Tab parameters in "key=value" format.
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>{@code {"listAll=false", "mode=edit"}}</li>
     *   <li>{@code {"maxResults=50"}}</li>
     *   <li>For EL expressions: {@code {"dynamicParam=#{someBean.value}"}}</li>
     * </ul>
     *
     * <p>Parameters are accessible in the bean via:</p>
     * <ul>
     *   <li>{@code getParameters().get("key")}</li>
     *   <li>{@code getDynTab().getParameters().get("key")}</li>
     * </ul>
     */
    String[] parameters() default {};
}
