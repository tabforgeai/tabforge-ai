package dyntabs.interfaces;

import java.util.Map;

import dyntabs.ApplicationCDIEvent;
import dyntabs.DynTab;
import dyntabs.DynTabCDIEvent;

/**
 * Contract that all CDI beans associated with a dynamic tab must implement.
 *
 * <p>Defines the tab lifecycle methods (access/exit points), event handling
 * (tab and application events), and DynTab association (get/set DynTab, parameters, etc.).</p>
 *
 * <p>The default implementation is provided by {@link dyntabs.BaseDyntabCdiBean}.</p>
 *
 * @author DynTabs
 * @see dyntabs.BaseDyntabCdiBean
 * @see dyntabs.DynTab
 */
public interface DyntabBeanInterface {
   public void init();


   public void callAccessPointMethod();
   public void callMethodActivity(String oldViewActivity, String newViewActivity);
   public void callViewActivity(String viewActivity);
   /**
    * Receives DynTab lifecycle events (dynTabAdded, dynTabRemoved, dynTabSelected).
    * NOT a CDI {@code @Observes} method - called directly from
    * {@link dyntabs.DynTabManager#fireDynTabEvent} which iterates through all active tabs
    * and properly sets the TabScope for each one.
    */
   public void observeDynTabEvent(DynTabCDIEvent dynTabEvent);

   /**
    * @deprecated The active flag is no longer needed for {@code @TabScoped} beans.
    * The bean is active while it exists and is destroyed together with the tab.
    */
   @Deprecated
   public void setActive(boolean active);

   /**
    * @deprecated The active flag is no longer needed for {@code @TabScoped} beans.
    */
   @Deprecated
   public boolean getActive();

   public void callExitPointMethod();
   /**
    * Receives application events (inter-tab communication).
    * NOT a CDI {@code @Observes} method - called directly from
    * {@link dyntabs.DynTabManager} which iterates through all active tabs
    * and properly sets the TabScope for each one.
    */
   public void observeApplicationEvent(ApplicationCDIEvent appEvent);


   public void setDynTab(DynTab dt);
   public DynTab getDynTab();
   // Properties delegated to the associated DynTab instance:
   public Map getParameters();
   public String getUniqueIdentifier();
   public String getDynTabId();



}