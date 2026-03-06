package dyntabs;

import jakarta.faces.application.ConfigurableNavigationHandler;
import jakarta.faces.context.FacesContext;

//import com.sun.faces.application.NavigationHandlerImpl;

import org.primefaces.application.DialogNavigationHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



//import org.apache.myfaces.trinidadinternal.application.NavigationHandlerImpl;

//import rs.energosoft.itss.dyntabs.view.DynTabManager;


/**
 * Custom JSF NavigationHandler that intercepts navigation outcomes containing a colon
 * ({@code ":"}) to open dynamic tabs instead of performing standard page navigation.
 *
 * <p>When an action outcome contains a colon, the part before the colon is treated as
 * a standard navigation outcome (typically the shell page), and the part after the colon
 * is used as the tab name to open via {@link DynTabManager#launchTab(String)}.</p>
 *
 * <p>Example usage in XHTML:</p>
 * <pre>
 * {@code
 * <!-- Opens the "Users" dynamic tab -->
 * <p:menuitem value="Users" action="uishell:Users" />
 *
 * <!-- Opens the "Products" dynamic tab -->
 * <p:menuitem value="Products" action="uishell:Products" />
 * }
 * </pre>
 *
 * <p>This handler extends PrimeFaces {@link DialogNavigationHandler}, so standard
 * JSF navigation and PrimeFaces dialog navigation both continue to work normally
 * when the outcome does not contain a colon.</p>
 *
 * <p><b>Registration:</b> Configure in {@code faces-config.xml}:</p>
 * <pre>
 * {@code
 * <application>
 *     <navigation-handler>dyntabs.TabNavigationHandler</navigation-handler>
 * </application>
 * }
 * </pre>
 *
 * @author DynTabs
 * @see DynTabManager#launchTab(String)
 */
public class TabNavigationHandler  extends DialogNavigationHandler {

   private static final Logger log = LoggerFactory.getLogger(TabNavigationHandler.class);

   public TabNavigationHandler(ConfigurableNavigationHandler base) {
      super(base);
   }

   /**
    * Handles JSF navigation. If the outcome contains a colon, the part after it
    * is used as a tab name to open via {@link DynTabManager#launchTab(String)}.
    * Otherwise, delegates to the standard PrimeFaces dialog navigation handler.
    *
    * @param facesContext the current FacesContext
    * @param action       the action expression that triggered navigation
    * @param outcome      the navigation outcome (e.g. "uishell:Users")
    */
   @Override
   public void handleNavigation(FacesContext facesContext, String action, String outcome)   {
      log.debug("handleNavigation() begin, action = {}, outcome = {}", action, outcome);
      if ((outcome != null) && (outcome.indexOf(":")>-1))  {
         int pos = outcome.indexOf(":");
         String shellAction = outcome.substring(0, pos);
         String tabName = outcome.substring(outcome.indexOf(":") + 1);
         // launch dyn tab
         log.debug("DynTabManager.getCurrentInstance() = {}", DynTabManager.getCurrentInstance());
         DynTabManager.getCurrentInstance().launchTab(tabName);

         // navigate to uishell page if needed (usually not needed because only
         // page is UIShell page)
         super.handleNavigation(facesContext, action, shellAction);
      }
      else{
         super.handleNavigation(facesContext, action, outcome);
      }
   }
}
