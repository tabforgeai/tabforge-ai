package security;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link AbstractSecuredResourceScanner}.
 *
 * <p>This is the <b>declarative (InMemory)</b> approach to access control — the default
 * provided by the DynTabs library. Secured resources and their allowed roles are
 * declared directly in annotations ({@code @DynTab(securedResource=true, allowedRoles=...)}
 * and {@code @AccessCheck(allowedRoles=...)}) and stored in memory at deploy time.</p>
 *
 * <p>Stores secured resources in a static {@code ConcurrentHashMap}, keyed by resource identifier
 * (either {@code @DynTab.uniqueIdentifier} for tab-level security, or fully qualified method name
 * for method-level security). Also stores the declared {@code allowedRoles} for each resource,
 * which are read by the paired {@link InMemoryAccessCheckInterceptor} at runtime.</p>
 *
 * <p><b>Must be paired with {@link InMemoryAccessCheckInterceptor}.</b></p>
 *
 * <p><b>Alternative:</b> For applications needing dynamic, admin-managed access rules,
 * the developer creates a custom {@code DBSecuredResourceScanner} (extending
 * {@link AbstractSecuredResourceScanner}) that writes resources to a database table,
 * ignoring the {@code allowedRoles} annotation attribute. Access rules are then managed
 * through an Admin UI and enforced by a custom {@code DBAccessCheckInterceptor}.</p>
 *
 * <p><b>Registration in web.xml:</b></p>
 * <pre>{@code
 * <listener>
 *     <listener-class>security.InMemorySecuredResourceScanner</listener-class>
 * </listener>
 * }</pre>
 *
 * @author DynTabs
 * @see AbstractSecuredResourceScanner
 * @see InMemoryAccessCheckInterceptor
 */
public class InMemorySecuredResourceScanner extends AbstractSecuredResourceScanner {

    private static final Map<String, String> securedResources = new ConcurrentHashMap<>();
    private static final Map<String, Set<String>> allowedRolesMap = new ConcurrentHashMap<>();

    @Override
    protected void registerSecuredResource(Class<?> cls, String resource, String resourceDisplayName, String[] allowedRoles) {
        securedResources.put(resource, resourceDisplayName != null ? resourceDisplayName : "");
        if (allowedRoles != null && allowedRoles.length > 0) {
            allowedRolesMap.put(resource, Set.of(allowedRoles));
        }
        log.info("Registered secured resource: {} ({}), allowedRoles: {}", resource, resourceDisplayName,
                allowedRoles != null ? Set.of(allowedRoles) : "[]");
    }

    @Override
    protected void un_registerSecuredResource(Class<?> cls, String resource, String resourceDisplayName) {
        securedResources.remove(resource);
        allowedRolesMap.remove(resource);
        log.debug("Unregistered resource: {}", resource);
    }

    /**
     * Checks if a resource is registered as secured.
     *
     * @param resource the resource identifier ({@code @DynTab.uniqueIdentifier} or fully qualified method name)
     * @return true if the resource requires access control
     */
    public static boolean isSecuredResource(String resource) {
        return securedResources.containsKey(resource);
    }

    /**
     * Returns the display name of a secured resource.
     *
     * @param resource the resource identifier
     * @return the display name, or null if not a secured resource
     */
    public static String getDisplayName(String resource) {
        return securedResources.get(resource);
    }

    /**
     * Returns the allowed roles for a secured resource, as declared in the annotation.
     *
     * @param resource the resource identifier
     * @return unmodifiable set of role names, or empty set if no roles declared
     */
    public static Set<String> getAllowedRoles(String resource) {
        Set<String> roles = allowedRolesMap.get(resource);
        return roles != null ? roles : Set.of();
    }

    /**
     * Returns an unmodifiable view of all registered secured resources.
     *
     * @return set of secured resource identifiers
     */
    public static Set<String> getAllSecuredResources() {
        return Set.copyOf(securedResources.keySet());
    }
}
