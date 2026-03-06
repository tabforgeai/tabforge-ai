package security;

import java.io.Serializable;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dyntabs.BaseDyntabCdiBean;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.InvocationContext;

import org.primefaces.PrimeFaces;

/**
 * Abstract base class for security interceptors that enforce access control
 * on methods annotated with {@link AccessCheck}.
 *
 * <p><b>How it works:</b></p>
 * <ol>
 *   <li>Intercepts any method annotated with {@code @AccessCheck}</li>
 *   <li>Determines the resource identifier:
 *       <ul>
 *         <li>For {@code callAccessPointMethod()} on a {@link BaseDyntabCdiBean}
 *             (which is annotated with {@code @AccessCheck}):
 *             uses the bean's {@code uniqueIdentifier} (from the {@code @DynTab} annotation
 *             that matches this specific tab instance). This solves the problem of multiple
 *             {@code @DynTab} annotations on the same class with different
 *             {@code securedResource} values.</li>
 *         <li>For other methods: uses the fully qualified method name
 *             ({@code com.example.MyBean.myMethod})</li>
 *       </ul>
 *   </li>
 *   <li>Calls {@link #isResourceSecured(String)} — if not secured, allows access</li>
 *   <li>If secured, retrieves user roles from the session and calls
 *       {@link #hasPermission(String, Set)}</li>
 *   <li>If denied and the method is {@code callAccessPointMethod()}, sets
 *       {@code accessDenied=true} on the bean and proceeds (the bean renders
 *       an "access denied" page instead of the main content)</li>
 *   <li>If denied and it's a regular method, shows a FacesMessage error
 *       and throws a RuntimeException</li>
 * </ol>
 *
 * <p><b>Two implementation strategies:</b></p>
 * <ol>
 *   <li><b>Declarative (InMemory):</b> Use {@link InMemoryAccessCheckInterceptor}, paired with
 *       {@link InMemorySecuredResourceScanner}. Access rules are declared directly in
 *       annotations ({@code @DynTab(allowedRoles=...)} and {@code @AccessCheck(allowedRoles=...)}).
 *       This is the default approach provided by the DynTabs library.</li>
 *   <li><b>DB-based:</b> The developer creates a custom {@code DBAccessCheckInterceptor}
 *       extending this class, paired with a custom {@code DBSecuredResourceScanner}.
 *       The {@code allowedRoles} annotation attribute is ignored — roles are managed
 *       through an Admin UI and stored in the database.</li>
 * </ol>
 *
 * <p><b>User roles:</b> By default, reads user roles from the session attribute
 * {@code "user_roles"} (a {@code Set<String>}). Override {@link #getUserRoles()}
 * to customize.</p>
 *
 * <p><b>IMPORTANT:</b> Any interceptor applied to a ViewScoped or TabScoped CDI bean
 * MUST implement {@link Serializable}, otherwise passivation will fail.</p>
 *
 * <p><b>NOTE:</b> CDI interceptors only intercept external method calls (via proxy).
 * When a bean calls its own {@code @AccessCheck} method internally,
 * the interceptor will NOT fire.</p>
 *
 * @author DynTabs
 * @see AccessCheck
 * @see InMemoryAccessCheckInterceptor
 * @see AbstractSecuredResourceScanner
 */
public abstract class AbstractAccessCheckInterceptor implements Serializable {

    protected static final Logger log = LoggerFactory.getLogger(AbstractAccessCheckInterceptor.class);

    @AroundInvoke
    public Object checkPermissions(InvocationContext context) throws Exception {
        String methodName = context.getMethod().getName();
        String resource = resolveResource(context);

        log.debug("checkPermissions() resource={}, method={}", resource, methodName);

        // 1. Check if this resource is secured at all
        if (!isResourceSecured(resource)) {
            log.debug("Resource {} is not secured, allowing access", resource);
            return context.proceed();
        }

        // 2. Resource is secured — check user roles
        Set<String> userRoles = getUserRoles();
        if (hasPermission(resource, userRoles)) {
            log.debug("Access granted to resource {} for roles {}", resource, userRoles);
            return context.proceed();
        }

        // 3. Access denied
        log.warn("Access DENIED to resource {} for roles {}", resource, userRoles);

        // For callAccessPointMethod on a DynTab bean: set accessDenied flag and proceed
        // (the bean will render the "access denied" page instead of main content)
        if ("callAccessPointMethod".equals(methodName)
                && context.getTarget() instanceof BaseDyntabCdiBean) {
            BaseDyntabCdiBean cdiBean = (BaseDyntabCdiBean) context.getTarget();
            cdiBean.setAccessDenied(true);
            return context.proceed();
        }

        // For any other method: show error and throw
        FacesContext facesContext = FacesContext.getCurrentInstance();
        if (facesContext != null) {
            FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_ERROR,
                    "Access denied!", "You do not have permission to access this resource.");
            PrimeFaces.current().dialog().showMessageDynamic(message);
        }
        throw new RuntimeException("Access denied to resource: " + resource);
    }

    /**
     * Resolves the resource identifier from the invocation context.
     *
     * <p>For {@code callAccessPointMethod()} on a {@link BaseDyntabCdiBean},
     * returns the bean's {@code uniqueIdentifier} (which identifies the specific
     * tab instance, solving the problem of multiple {@code @DynTab} annotations
     * on the same class with different {@code securedResource} values).</p>
     *
     * <p>For other methods, returns the fully qualified method name.</p>
     */
    private String resolveResource(InvocationContext context) {
        String methodName = context.getMethod().getName();

        if ("callAccessPointMethod".equals(methodName)
                && context.getTarget() instanceof BaseDyntabCdiBean) {
            BaseDyntabCdiBean cdiBean = (BaseDyntabCdiBean) context.getTarget();
            String uniqueId = cdiBean.getUniqueIdentifier();
            if (uniqueId != null) {
                return uniqueId;
            }
        }

        // Fallback: fully qualified method name (ClassName.methodName)
        String className = context.getTarget().getClass().getSuperclass().getName();
        return className + "." + methodName;
    }

    /**
     * Retrieves the current user's roles from the session.
     *
     * <p>Default implementation reads the {@code "user_roles"} attribute
     * from the JSF external context session map. Override to customize
     * (e.g., read from a different session attribute, or from Jakarta Security).</p>
     *
     * @return set of role names for the current user, or empty set if not available
     */
    @SuppressWarnings("unchecked")
    protected Set<String> getUserRoles() {
        FacesContext facesContext = FacesContext.getCurrentInstance();
        if (facesContext == null) {
            return Set.of();
        }
        Set<String> roles = (Set<String>) facesContext.getExternalContext()
                .getSessionMap().get("user_roles");
        return roles != null ? roles : Set.of();
    }

    /**
     * Checks whether the given resource is secured (requires access control).
     *
     * <p>If this returns {@code false}, access is allowed without any role check.</p>
     *
     * @param resource the resource identifier (uniqueIdentifier for tabs, or fully qualified method name)
     * @return true if the resource is secured and requires permission check
     */
    protected abstract boolean isResourceSecured(String resource);

    /**
     * Checks whether the given user roles grant access to the specified resource.
     *
     * <p>Called only if {@link #isResourceSecured(String)} returned {@code true}.</p>
     *
     * @param resource the resource identifier
     * @param userRoles the current user's roles
     * @return true if access should be granted
     */
    protected abstract boolean hasPermission(String resource, Set<String> userRoles);
}
