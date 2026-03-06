package security;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.interceptor.Interceptor;

/**
 * In-memory implementation of {@link AbstractAccessCheckInterceptor}.
 *
 * <p>This is the <b>declarative (InMemory)</b> approach to access control — the default
 * provided by the DynTabs library. <b>Must be paired with {@link InMemorySecuredResourceScanner}.</b></p>
 *
 * <p><b>How access rules are resolved:</b></p>
 * <ol>
 *   <li>First, checks roles declared in annotations ({@code @DynTab(allowedRoles=...)}
 *       or {@code @AccessCheck(allowedRoles=...)}), which are stored by
 *       {@link InMemorySecuredResourceScanner} at deploy time.</li>
 *   <li>Then, checks roles added programmatically via {@link #grantAccess(String, String...)}
 *       (useful for adding rules at runtime without redeployment).</li>
 *   <li>If a resource is secured but has no allowed roles from either source,
 *       access is allowed (there is no point in having a dead resource).</li>
 * </ol>
 *
 * <p><b>Declarative access (primary — recommended):</b></p>
 * <pre>{@code
 * @DynTab(name = "AllDocsDynTab", uniqueIdentifier = "AllDocs",
 *         securedResource = true, allowedRoles = {"ADMIN", "MANAGER"}, ...)
 * }</pre>
 *
 * <p><b>Programmatic access (supplementary):</b></p>
 * <pre>{@code
 * // In your application startup (e.g., ServletContextListener)
 * InMemoryAccessCheckInterceptor.grantAccess("AllDocs", "AUDITOR");
 * }</pre>
 *
 * <p><b>Alternative:</b> For applications needing dynamic, admin-managed access rules,
 * the developer creates a custom {@code DBAccessCheckInterceptor} (extending
 * {@link AbstractAccessCheckInterceptor}) paired with a custom {@code DBSecuredResourceScanner}.
 * In this approach, the {@code allowedRoles} annotation attribute is ignored — roles are
 * managed through an Admin UI and stored in the database.</p>
 *
 * <p><b>Registration in beans.xml:</b></p>
 * <pre>{@code
 * <interceptors>
 *     <class>security.InMemoryAccessCheckInterceptor</class>
 * </interceptors>
 * }</pre>
 *
 * @author DynTabs
 * @see AbstractAccessCheckInterceptor
 * @see InMemorySecuredResourceScanner
 */
@Interceptor
@AccessCheck(resourceDisplayName = "")
public class InMemoryAccessCheckInterceptor extends AbstractAccessCheckInterceptor {

    private static final long serialVersionUID = 1L;

    /**
     * Maps resource identifier to the set of roles that can access it.
     */
    private static final Map<String, Set<String>> accessRules = new ConcurrentHashMap<>();

    /**
     * Grants access to a resource for the specified roles.
     *
     * <p>Can be called multiple times for the same resource — roles are accumulated.</p>
     *
     * @param resource the resource identifier ({@code @DynTab.uniqueIdentifier} or fully qualified method name)
     * @param roles one or more role names that should have access
     */
    public static void grantAccess(String resource, String... roles) {
        accessRules.computeIfAbsent(resource, k -> ConcurrentHashMap.newKeySet())
                .addAll(Set.of(roles));
    }

    /**
     * Revokes all access rules for a resource.
     *
     * @param resource the resource identifier
     */
    public static void revokeAccess(String resource) {
        accessRules.remove(resource);
    }

    /**
     * Returns the roles that have access to a resource.
     *
     * @param resource the resource identifier
     * @return unmodifiable set of role names, or empty set if no rules defined
     */
    public static Set<String> getAllowedRoles(String resource) {
        Set<String> roles = accessRules.get(resource);
        return roles != null ? Collections.unmodifiableSet(roles) : Set.of();
    }

    /**
     * Clears all access rules. Useful for testing.
     */
    public static void clearAllRules() {
        accessRules.clear();
    }

    @Override
    protected boolean isResourceSecured(String resource) {
        return InMemorySecuredResourceScanner.isSecuredResource(resource);
    }

    @Override
    protected boolean hasPermission(String resource, Set<String> userRoles) {
        // 1. Check roles declared in annotations (from InMemorySecuredResourceScanner)
        Set<String> declaredRoles = InMemorySecuredResourceScanner.getAllowedRoles(resource);
        for (String role : declaredRoles) {
            if (userRoles.contains(role)) {
                return true;
            }
        }

        // 2. Check roles added programmatically via grantAccess()
        Set<String> programmaticRoles = accessRules.get(resource);
        if (programmaticRoles != null) {
            for (String role : programmaticRoles) {
                if (userRoles.contains(role)) {
                    return true;
                }
            }
        }

        // 3. Resource is secured but has no allowed roles from either source — allow access
        //    (there is no point in having a dead resource that nobody can access)
        if (declaredRoles.isEmpty() && (programmaticRoles == null || programmaticRoles.isEmpty())) {
            log.warn("Resource {} is secured but has no allowed roles defined — allowing access", resource);
            return true;
        }

        return false;
    }
}
