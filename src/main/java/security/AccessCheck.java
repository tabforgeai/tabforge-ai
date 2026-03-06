package security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.enterprise.util.Nonbinding;
import jakarta.interceptor.InterceptorBinding;

/**
 * Interceptor binding annotation for securing individual CDI bean methods.
 *
 * <p>Place this annotation on a method to indicate it requires access control.
 * The {@link AbstractAccessCheckInterceptor} will intercept the method call
 * and verify the caller's permissions before allowing execution.</p>
 *
 * <p><b>Access rights can be granted in two ways:</b></p>
 * <ol>
 *   <li><b>Declarative (InMemory):</b> Specify {@link #allowedRoles()} directly in the annotation.
 *       The {@link InMemorySecuredResourceScanner} reads these at deploy time and stores them
 *       together with the secured resource. The paired {@link InMemoryAccessCheckInterceptor}
 *       enforces access based on these declared roles. This is the default approach provided
 *       by the DynTabs library — zero configuration, everything is in the code.
 *       <pre>{@code
 *       @AccessCheck(resourceDisplayName = "Generate PDF Report",
 *                    allowedRoles = {"ADMIN", "MANAGER"})
 *       public void generatePdfReport() { ... }
 *       }</pre>
 *   </li>
 *   <li><b>DB-based:</b> The developer creates a custom {@code DBSecuredResourceScanner}
 *       (extending {@link AbstractSecuredResourceScanner}) that registers secured resources
 *       in a database table, ignoring the {@code allowedRoles} attribute. Access rules are
 *       managed through an Admin UI where roles are granted permissions on resources.
 *       A paired {@code DBAccessCheckInterceptor} reads allowed roles from the database
 *       at runtime.</li>
 * </ol>
 *
 * <p><b>NOTE:</b> {@code @Nonbinding} is required on all attributes, otherwise CDI
 * would treat different attribute values as different interceptor bindings, and the
 * interceptor would not fire for methods with non-default values.</p>
 *
 * @author DynTabs
 * @see AbstractAccessCheckInterceptor
 * @see AbstractSecuredResourceScanner
 */
@InterceptorBinding
@Target( { ElementType.METHOD, ElementType.TYPE} )
@Retention( RetentionPolicy.RUNTIME )
public @interface AccessCheck {
   /**
    * Human-readable display name for this secured resource.
    *
    * <p>Shown in admin UIs for security management. Can be a literal string
    * (e.g. "Generate PDF Report") or a resource bundle key for i18n.</p>
    */
   @Nonbinding String resourceDisplayName() default "";

   /**
    * Roles allowed to invoke this method (declarative access control).
    *
    * <p>Used by the {@link InMemorySecuredResourceScanner} / {@link InMemoryAccessCheckInterceptor}
    * pair. Comma-separated role names, e.g. {@code {"ADMIN", "MANAGER"}}.</p>
    *
    * <p>Ignored by DB-based implementations, where access rules are managed
    * through an Admin UI and stored in the database.</p>
    */
   @Nonbinding String[] allowedRoles() default {};
}