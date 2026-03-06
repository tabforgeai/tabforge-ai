package security;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dyntabs.annotation.DynTab;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
/**
 * Abstract base class for a {@link ServletContextListener} that scans CDI bean classes
 * at deploy time and discovers secured resources.
 *
 * <p>Reads the CDI beans package name from the {@code "cdiBeansPackage"} context-param
 * in {@code web.xml}:</p>
 * <pre>{@code
 * <context-param>
 *     <param-name>cdiBeansPackage</param-name>
 *     <param-value>com.myapp.cdibeans</param-value>
 * </context-param>
 * }</pre>
 *
 * <p>Loads all classes from that package and scans for:</p>
 * <ul>
 *   <li><b>{@link DynTab} annotations</b> on classes — if {@link DynTab#securedResource()} is {@code true},
 *       calls {@link #registerSecuredResource(Class, String, String, String[])} with the tab's
 *       {@code uniqueIdentifier}, {@code securedResourceDisplayName}, and {@code allowedRoles}.</li>
 *   <li><b>{@link AccessCheck} annotations</b> on methods — registers the fully qualified method name
 *       as a secured resource, with its {@code resourceDisplayName} and {@code allowedRoles}.</li>
 * </ul>
 *
 * <p><b>Two implementation strategies:</b></p>
 * <ol>
 *   <li><b>Declarative (InMemory):</b> Use {@link InMemorySecuredResourceScanner}, which stores
 *       secured resources AND their allowed roles (from {@code @DynTab.allowedRoles} /
 *       {@code @AccessCheck.allowedRoles}) in memory. Paired with {@link InMemoryAccessCheckInterceptor}.
 *       This is the default approach provided by the DynTabs library — zero configuration,
 *       access rules are declared directly in annotations.</li>
 *   <li><b>DB-based:</b> The developer creates a custom {@code DBSecuredResourceScanner}
 *       that writes only the secured resource identifiers to a database table (ignoring
 *       {@code allowedRoles}). Access rules are managed through an Admin UI where an
 *       administrator grants roles permissions on resources. Paired with a custom
 *       {@code DBAccessCheckInterceptor} that reads allowed roles from the database.</li>
 * </ol>
 *
 * @author DynTabs
 * @see InMemorySecuredResourceScanner
 * @see AbstractAccessCheckInterceptor
 */
public abstract class AbstractSecuredResourceScanner implements ServletContextListener{
	   protected static final Logger log = LoggerFactory.getLogger(AbstractSecuredResourceScanner.class);
	   
	   @Override
	   public void contextInitialized(ServletContextEvent event) {
		   String cdiBeansPackage =event.getServletContext().getInitParameter("cdiBeansPackage");
		   Set<Class<?>> classes = findAllClassesUsingClassLoader(cdiBeansPackage);
		      try {
		          for (Class<?> cls : classes) {
		             log.debug("-checking " + cls.getName() + " for DynTab annottions");
		             
		             dyntabs.annotation.DynTab[] dynTabs = cls.getAnnotationsByType(dyntabs.annotation.DynTab.class);
		             for (dyntabs.annotation.DynTab dynTab : dynTabs) {
		                 log.debug("dynTab uniqueIdentifier: " + dynTab.uniqueIdentifier() + ", id securedResource: " + dynTab.securedResource());
		                 if (dynTab.securedResource()) {
		                	 registerSecuredResource(cls, dynTab.uniqueIdentifier(),dynTab.securedResourceDisplayName(), dynTab.allowedRoles());
		                 }else {
		                	 un_registerSecuredResource(cls, dynTab.uniqueIdentifier(),dynTab.securedResourceDisplayName());
		                 }
		             }
		             // ------
		             // now search for AccessCheck annotation, over class methods:
		             AccessCheck accCheckAnnotation = null;
		             for (Method method : cls.getMethods()) {
		                String methodName = cls.getName() + "." + method.getName();
		                String displayName = null;
		                // callAccessPointMethod() is always annotated with @AccessCheck but is never registered as a secured resource itself
		                if (method.isAnnotationPresent(AccessCheck.class) && !"callAccessPointMethod".equalsIgnoreCase(method.getName())) {
		                  // maybe the method is already registered, if the annotation is in the super class. 
		                  // In that case, you don't need to register the method again:	
		                   accCheckAnnotation = method.getAnnotation(AccessCheck.class);
		                   displayName = accCheckAnnotation.resourceDisplayName();
		                   String[] allowedRoles = accCheckAnnotation.allowedRoles();
		                   Class deklarisucaKlasa = method.getDeclaringClass();
		                   if (deklarisucaKlasa.getName().equals(cls.getName())) {
		                      registerSecuredResource(cls, methodName, displayName, allowedRoles);
		                   }
		                } else {
		                    if (!"callAccessPointMethod".equalsIgnoreCase(method.getName())) {
		                    	un_registerSecuredResource(cls, methodName, displayName);
		                    }
		                }
		             } 
		          } 
		       } finally {
		          //em.close();
		       }
		   
	   }// of contextInitialized()
	   
	   @Override
	   public void contextDestroyed(ServletContextEvent event) {
	   }
	   
	   // ------------------
	   private Set<Class<?>> findAllClassesUsingClassLoader(String packageName) {
		      String replaceDots = packageName.replaceAll("[.]", "/");
		      ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
		      InputStream stream = classLoader.getResourceAsStream(replaceDots);
		      BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
		      return reader.lines()
		               .filter(line -> line.endsWith(".class"))
		               .map(line -> getClass(line, packageName))
		               .collect(Collectors.toSet());
		   }
	   
	   private Class<?> getClass(String className, String packageName) {
		      try {
		         return Class.forName(packageName + "."
		                  + className.substring(0, className.lastIndexOf('.')));
		      } catch (ClassNotFoundException e) {
		         log.error("*** getClass() error: " + e.getMessage());
		      }
		      return null;
	  }
	   
	  /**
	   * Called when a secured resource is discovered (a {@code @DynTab} with
	   * {@code securedResource=true}, or a method annotated with {@code @AccessCheck}).
	   *
	   * <p>InMemory implementations store both the resource and its {@code allowedRoles}
	   * in memory. DB implementations write the resource to a database table
	   * (typically ignoring {@code allowedRoles}, since roles are managed via Admin UI).</p>
	   *
	   * @param cls the class containing the secured resource
	   * @param resource the resource identifier ({@code uniqueIdentifier} for tabs,
	   *                 fully qualified method name for methods)
	   * @param resourceDisplayName human-readable name for admin UIs
	   * @param allowedRoles roles declared in the annotation (may be empty;
	   *                     used by InMemory implementations, ignored by DB implementations)
	   */
	  protected abstract void registerSecuredResource(Class<?> cls, String resource, String resourceDisplayName, String[] allowedRoles);

	  /**
	   * Called when a resource is found NOT to be secured (e.g., {@code @DynTab} with
	   * {@code securedResource=false}, or a method without {@code @AccessCheck}).
	   *
	   * <p>Useful for cleanup — removing previously registered resources that
	   * have been un-secured in a redeployment.</p>
	   *
	   * @param cls the class containing the resource
	   * @param resource the resource identifier
	   * @param resourceDisplayName human-readable name (may be null)
	   */
	  protected abstract void un_registerSecuredResource(Class<?> cls, String resource, String resourceDisplayName);

}
