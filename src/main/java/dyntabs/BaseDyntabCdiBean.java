package dyntabs;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.component.UIComponent;
import jakarta.inject.Inject;

import org.primefaces.PrimeFaces;
import org.primefaces.component.datatable.DataTable;

import dyntabs.ai.activity.ActivityRecorder;
import dyntabs.ai.activity.EntityRef;
import dyntabs.ai.activity.UserActivityEvent;
import dyntabs.interfaces.DyntabBeanInterface;
import security.AccessCheck;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base implementation of {@link DyntabBeanInterface} for CDI-managed tab beans.
 *
 * <p>Provides default implementations for tab lifecycle methods, event handling,
 * inter-tab messaging, view navigation within a tab, and dialog management.
 * Application-specific tab beans should extend this class.</p>
 *
 * <p><b>Key features:</b></p>
 * <ul>
 *   <li>Tab lifecycle: {@link #accessPointMethod} (called on tab open) and
 *       {@link #exitPointMethod} (called on tab close)</li>
 *   <li>Event handling: {@link #observeDynTabEvent} dispatches to
 *       {@link #onDynTabAdded}, {@link #onDynTabRemoved}, {@link #onDynTabSelected}</li>
 *   <li>Inter-tab messaging: {@link #sendMessageToAppModule} and
 *       {@link #onApplicationMessage}</li>
 *   <li>Workflow pattern: {@link #closeAndReturnValueToCaller} for parent-child tab flows</li>
 *   <li>View navigation: {@link #callViewActivity} for switching XHTML pages within a tab</li>
 *   <li>PrimeFaces helpers: widget show/hide/filter within the correct dynamic tab</li>
 * </ul>
 *
 * @author DynTabs
 * @see DyntabBeanInterface
 * @see DynTabManager
 */
public class BaseDyntabCdiBean implements Serializable, DyntabBeanInterface {

   protected static final Logger log = LoggerFactory.getLogger(BaseDyntabCdiBean.class);

   /**
    * Front desk for the ambient activity timeline. Injected so that a tab declared with
    * {@code @DynTab(trackActivity = true)} can have its opening recorded automatically in
    * {@link #callAccessPointMethod()}. The recorder is an {@code @ApplicationScoped} facade, so this
    * normal-scoped proxy is safe to hold in a passivation-capable tab bean.
    */
   @Inject
   protected ActivityRecorder activityRecorder;

   @Override
   @PostConstruct
   public void init() {
      // Subclasses can override for custom initialization
   }
   // For passing parameters from DynTab to the CDI bean that works with a given module:
   // For a usage example, see DrugiBean.getCustomLoadData(), where the code checks whether a
   // forwarded parameter "deptIdToLocate" exists, and if so, uses it to filter the dataset.
   // See also the comment in CrudAwareLazyDataModel.getCustomLoadData().

   // PARAMETER PASSING: 
   // In CDI beans, parameters are forwarded inside
   // DynTabManager.addTab()
   // by calling the method
   // sendParamsToCdiBean()

   @Override
   public Map getParameters() {
      return (getDynTab() != null ? getDynTab().getParameters() : null);
   }

   private boolean mainContentRendered = true;

   public boolean isMainContentRendered() {
      return mainContentRendered;
   }

   public void setMainContentRendered(boolean value) {
      mainContentRendered = value;
   }

   private boolean errPageRendered = false;

   public boolean isErrPageRendered() {
      return errPageRendered;
   }

   public void setErrPageRendered(boolean value) {
      errPageRendered = value;
   }

   private String errMsg;

   public void setErrMsg(String msg) {
      errMsg = msg;
   }

   public String getErrMsg() {
      return errMsg;
   }

   /**
    * Receives DynTab lifecycle events (dynTabAdded, dynTabRemoved, dynTabSelected).
    *
    * <p>This is NOT a CDI {@code @Observes} method! It is called directly from
    * {@link DynTabManager#fireDynTabEvent} which iterates through all active tabs,
    * sets the correct TabScope for each tab, and calls this method on the corresponding bean.</p>
    *
    * <p><b>Why not CDI @Observes?</b> CDI can only see beans in the currently active
    * tab scope. If tab r1 is active and HomeBean exists in r0, CDI would create a
    * NEW HomeBean instance in r1 (wrong!) instead of using the existing one from r0.</p>
    *
    * @param dynTabEvent the tab lifecycle event to handle
    */
   @Override
   public void observeDynTabEvent(DynTabCDIEvent dynTabEvent) {
      log.debug("observeDynTabEvent(), dynTabEvent = {}", dynTabEvent);
      if (dynTabEvent == null) {
         return;
      }

      String eventType = dynTabEvent.getEventType();
      // Check if this bean is the source of the event - compare by tab ID
      // instead of getCdiBean() which has a ThreadLocal side-effect
      boolean isMyEvent = (getDynTab() != null &&
                           dynTabEvent.getTabId() != null &&
                           dynTabEvent.getTabId().equals(getDynTab().getId()));
      log.debug("eventType = {}, -isMyEvent = {}", eventType, isMyEvent);
      if (!isMyEvent) { // react to events from other tabs
         log.debug("not my event");
         if ("dynTabAdded".equalsIgnoreCase(eventType)) {
            log.debug("_JESTE dynTabAdded");
            onDynTabAdded(dynTabEvent);
         } else if ("dynTabRemoved".equalsIgnoreCase(eventType)) {
            log.debug("_JESTE dynTabRemoved");
            onDynTabRemoved(dynTabEvent);
         } else if ("dynTabSelected".equalsIgnoreCase(eventType)) {
            log.debug("_JESTE dynTabSelected");
            onDynTabSelected(dynTabEvent);
         }
      } else { // react to dynTabSelected on this tab itself (for removed/added there is accessPointMethod/exitPointMethod)
         if ("dynTabSelected".equalsIgnoreCase(eventType)) {
            recordTabSelectActivity();
            onThisTabSelected();
         }
      }
   }// of observeDynTabEvent()

   protected void onDynTabAdded(DynTabCDIEvent event) {

   }

   protected void onDynTabRemoved(DynTabCDIEvent event) {

   }

   protected void onDynTabSelected(DynTabCDIEvent event) {

   }

   protected void onThisTabSelected() {

   }

   /**
    * Receives application events (inter-tab communication).
    * Called directly from DynTabManager - same reason as {@link #observeDynTabEvent}.
    */
   @Override
   public void observeApplicationEvent(ApplicationCDIEvent appEvent) {
      log.debug("observeApplicationEvent() call, appEvent = {}", appEvent);
      if (appEvent == null) {
         return;
      }
      // do not react to events sent by this bean itself:
      String senderId = appEvent.getSenderAppModuleId();
      String uniqueId = getUniqueIdentifier();
      log.debug(" -uniqueId = {} klasa: {}", uniqueId, getClass().getSimpleName());
      if (uniqueId.equalsIgnoreCase(senderId)) {
         return;
      }

      String targetId = appEvent.getTargetAppModuleId();
      log.debug(" -targetId: {}, eventType: {}", targetId, appEvent.getEventType());
      if ((targetId != null) &&
               targetId.equalsIgnoreCase(uniqueId) &&
               "JobFlowReturn".equalsIgnoreCase(appEvent.getEventType())) {
         onJobFlowReturn(senderId, appEvent.getPayload());
         return;
      }
      // if target is null, the message is broadcast to all. React only if message is for all or for this module:
      if ((targetId == null) || targetId.equalsIgnoreCase(uniqueId)) { // "JobFlowReturn"
         onApplicationMessage(senderId, appEvent.getPayload());
      }
   }

   protected void onApplicationMessage(String senderId, Object payload) {

   }

   protected void onJobFlowReturn(String senderId, Object jobFlowReturnValue) {

   }

   /**
    * Sends a message to another app module (DynTab) via DynTabManager.
    * Uses {@link DynTabManager#fireApplicationEvent} which iterates
    * through tabs directly instead of the CDI @Observes mechanism.
    */
   public void sendMessageToAppModule(String targetAppModuleId, Object payload) {
      DynTabManager manager = DynTabManager.getCurrentInstance();
      if (manager != null) {
         manager.fireApplicationEvent(
                 new ApplicationCDIEvent(this.getUniqueIdentifier(), targetAppModuleId, payload));
      }
   }

   public void closeAndReturnValueToCaller(Object returnValue) {
      String callerID = getCallerID();
      log.debug("callerID = {}", callerID);
      if (callerID != null) {
         DynTabManager manager = DynTabManager.getCurrentInstance();
         if (manager != null) {
            manager.fireApplicationEvent(
                    new ApplicationCDIEvent("JobFlowReturn", this.getUniqueIdentifier(), callerID, returnValue));
         }
      }
      // close this tab and select the caller tab:
      closeAndSelectCaller();
   }// of closeAndReturnValueToCaller()

   public void closeAndSelectCaller() {
      DynTabManager dtm = DynTabManager.getCurrentInstance();
      dtm.removeCurrentTab(null);
      String callerID = getCallerID();
      // select the caller's dynTab, if it is still present on the UI:
      if (callerID != null) {
         DynTab callerTab = dtm.getMatchingTab(callerID);
         if (callerTab != null) {
            dtm.addOrSelectTab(callerTab);
            log.debug("caller tab selected!");
         }
      }
   }// of closeAndSelectCaller()

   public void closeCurrentTab() {
      DynTabManager dtm = DynTabManager.getCurrentInstance();
      dtm.removeCurrentTab(null);
   }

   /**
    * the callerID is a special param, signaling that this JobFlow is called from another JobFlow
    * This is a uniqueIdentifier of calling JobFlow
    *
    * @return
    */
   public String getCallerID() {
      String result = null;
      Map params = getParameters();
      if ((params != null) && (params.get("callerID") != null)) {
         result = (String) params.get("callerID");
      }
      return result;
   }

   public void sendMessageToAllAppModules(Object payload) {
      sendMessageToAppModule(null, payload);
   }

   @Override
   public String getUniqueIdentifier() {
      return (getDynTab() != null ? getDynTab().getUniqueIdentifier() : null);
   }

   // action="{cdiBean.callViewActivity('/WEB-INF/include/employees/employees_step2.xhtml')}"
   @Override
   public void callViewActivity(String viewActivity) {
      DynTabManager manager = null;
      try {

         // get the current view we are navigating away from:
         manager = (DynTabManager) JsfUtils.getExpressionValue("#{dynTabManager}");
         String oldViewActivity = manager.getSelectedTab().getIncludePage();
         // call the Java method that should execute before navigating to viewActivity:
         callMethodActivity(oldViewActivity, viewActivity);
         // ===
         setMainContentRendered(true);
         setErrPageRendered(false);

         // ===
         // set the new content (include page) for the tab:
         manager.getSelectedTab().setIncludePage(viewActivity);

         String tabClientId = "mainForm:mainTabView:_sub_" + manager.getSelectedTabId() + ":fragmentPanel";
         log.debug("tabClientId = {}", tabClientId);
         // UIComponent tab = JsfUtils.findComponentByClientId(tabClientId);
         // PrimeFaces.current().ajax().update("mainForm:mainTabView");
         PrimeFaces.current().ajax().update(tabClientId);
      } catch (Exception ex) {
         setMainContentRendered(false);
         setErrPageRendered(true);
         setErrMsg(ex.getMessage());
         if (manager != null) {
            PrimeFaces.current().ajax().update("mainForm:mainTabView:_sub_" + manager.getSelectedTabId() + ":fragmentPanel");
         }

         /*
          * FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_ERROR,
          * ex.getMessage(),
          * null);
          * PrimeFaces.current().dialog().showMessageDynamic(message);
          */
      }
   }

   private boolean accessDenied = false;
   public void setAccessDenied(boolean accessDenied) {
      this.accessDenied = accessDenied;
   }

   /**
    * Called when the tab is opened. Subclasses override this for custom initialization logic.
    */
   protected void accessPointMethod(Map parameters) {
      if (accessDenied) {
         throw new RuntimeException("Access denied!");
      }
   }

   /**
    * Wraps {@link #accessPointMethod} with error handling.
    *
    * <p>Note: This method is NOT called for initial tabs (loaded during startup),
    * because those do not go through {@code DynTabManager.addTab()}.
    * This is acceptable since initial tabs are visible to everyone.</p>
    *
    * <p>Cannot be {@code final} because CDI interceptors need to proxy it.</p>
    */
   @Override
   @AccessCheck
   public  void callAccessPointMethod() {
      log.debug("callAccessPointMethod() begin");
      try {
         accessPointMethod(getParameters());
         setMainContentRendered(true);
         setErrPageRendered(false);
         recordTabOpenActivity();
      } catch (Exception exc) {
         log.error("callAccessPointMethod() error: {}", exc.getMessage(), exc);

         setMainContentRendered(false);
         setErrPageRendered(true);
         setErrMsg(exc.getMessage());
      }
      log.debug("callAccessPointMethod() end");
   }

   /**
    * Transient guard that suppresses the one tab-selection event {@code DynTabManager.addTab()} fires
    * immediately after opening a tab (which would otherwise duplicate the "open" entry we just wrote).
    * Set by {@link #recordTabOpenActivity()} and consumed by {@link #recordTabSelectActivity()}.
    */
   private transient boolean justOpenedForActivity = false;

   /**
    * Records a NAVIGATION "open" entry on the ambient activity timeline when this tab opens, but only
    * if the tab was declared with {@code @DynTab(trackActivity = true)}.
    *
    * <p>Called from {@link #callAccessPointMethod()} on the success path (after the tab's content has
    * rendered), so only tabs that actually opened are recorded. On success it raises
    * {@link #justOpenedForActivity} so the selection event that {@code addTab()} fires right afterwards
    * is not double-recorded.</p>
    */
   private void recordTabOpenActivity() {
      if (recordTabActivity("open")) {
         justOpenedForActivity = true;
      }
   }

   /**
    * Records a NAVIGATION "select" entry when this tab is brought back to the front (a genuine focus
    * change), unless the selection is the one that immediately follows opening the tab — that one is
    * suppressed via {@link #justOpenedForActivity} so opening a tab yields a single "open" entry, not
    * "open" + "select".
    *
    * <p>Called from {@link #observeDynTabEvent(DynTabCDIEvent)} when this very tab is selected. Placed
    * in the dispatcher rather than the overridable {@link #onThisTabSelected()} hook, so a subclass
    * that overrides the hook (and forgets {@code super}) cannot silently disable tracking.</p>
    */
   private void recordTabSelectActivity() {
      if (justOpenedForActivity) {
         justOpenedForActivity = false; // consume: this select belongs to the open we just recorded
         return;
      }
      recordTabActivity("select");
   }

   /**
    * Writes one NAVIGATION activity event for this tab with the given verb, honouring the
    * {@code trackActivity} opt-in.
    *
    * <p>Shared by {@link #recordTabOpenActivity()} and {@link #recordTabSelectActivity()}. The event —
    * tagged with the tab's {@code uniqueIdentifier} and {@code title} — is written through
    * {@link #activityRecorder} into the {@link dyntabs.ai.activity.ActivityStore}, from where a
    * {@link dyntabs.ai.activity.ActivityContext} later reads it to make an AI assistant aware of where
    * the user is. Any failure is swallowed: activity tracking must never break tab navigation.</p>
    *
    * @param verb the navigation verb to record (e.g. {@code "open"}, {@code "select"})
    * @return {@code true} if an event was actually recorded; {@code false} if the tab did not opt in or
    *         could not be identified
    */
   private boolean recordTabActivity(String verb) {
      try {
         DynTab dynTab = getDynTab();
         if (dynTab == null || !dynTab.isTrackActivity() || activityRecorder == null) {
            return false;
         }
         String uniqueId = dynTab.getUniqueIdentifier();
         if (uniqueId == null) {
            return false;
         }
         String title = dynTab.getTitle();
         EntityRef tabRef = EntityRef.of("tab", uniqueId, "label", title != null ? title : uniqueId);
         activityRecorder.record(UserActivityEvent.Type.NAVIGATION, verb, List.of(tabRef), null);
         return true;
      } catch (RuntimeException e) {
         log.warn("recordTabActivity({}) failed: {}", verb, e.getMessage());
         return false;
      }
   }

   @Override
   public void callMethodActivity(String oldViewActivity, String newViewActivity) {
   }

   /**
    * @deprecated The active flag is no longer needed for @TabScoped beans
    * since the bean is destroyed together with the tab. Kept for compatibility.
    */
   @Deprecated
   @Override
   public void setActive(boolean active) {
      // No-op: @TabScoped beans do not need an active flag
   }

   /**
    * @deprecated The active flag is no longer needed for @TabScoped beans.
    */
   @Deprecated
   @Override
   public boolean getActive() {
      return true; // Always active while the bean exists
   }

   /**
    * Called when the tab is closed. Subclasses override this for custom cleanup logic.
    */
   protected void exitPointMethod(Map parameters) {

   }

   @Override
   public void callExitPointMethod() {
      log.debug("callExitPointMethod() call");
      try {
         exitPointMethod(getParameters());
      } catch (Exception exc) {
         FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_ERROR,
                  exc.getMessage(),
                  null);
      }
   }

   @Override
   public String getDynTabId() {
      return (getDynTab() != null ? getDynTab().getId() : null);
   }

   private DynTab dt = null;

   @Override
   public void setDynTab(DynTab dt) {
      this.dt = dt;
   }

   @Override
   public DynTab getDynTab() {
      return this.dt;
   }

   protected void removeAppendToBodyElement(String id) {
      DynTab dt = this.getDynTab();
      if (dt != null) {
         String clientId = dt.getTabSubviewClientId() + ":" + id;
         log.debug("clientId = {}", clientId);
         String jsRemove = "var elem = document.getElementById('" + clientId
                  + "'); console.log('elem =' + elem); if (elem != null){ elem.remove(); console.log('Removed appendToBody element!');}";
         log.debug("jsRemove = {}", jsRemove);
         PrimeFaces.current().executeScript(jsRemove);
      }
   }

   public String hideWidgetInDynTab(String compId) {
      log.debug("hideWidgetInDynTab(), compId = {}", compId);
      String result = "";
      UIComponent comp = JsfUtils.findComponentInDynamicTab(compId, this.getDynTabId());
      if ((comp != null)) {
         String wv = ("widget_" + comp.getClientId()).replace(":", "_");
         result = "PF('" + wv + "').hide()";
      }
      return result;
   }

   public String showWidgetInDynTab(String compId) {
      // "PF('deleteProductDialog').show()"
      String result = "";
      UIComponent comp = JsfUtils.findComponentInDynamicTab(compId, this.getDynTabId());
      if ((comp != null)) {
         String wv = ("widget_" + comp.getClientId()).replace(":", "_");
         result = "PF('" + wv + "').show()";
      }
      return result;
   }

   public String filterTableInDynTab(String tableId) {
      String result = "";
      UIComponent tabB1 = JsfUtils.findComponentInDynamicTab(tableId, this.getDynTabId());
      log.debug("filterTableInDynTab, tabB1 = {}", tabB1);
      if ((tabB1 != null) && (tabB1 instanceof DataTable)) {
         String wv = ("widget_" + tabB1.getClientId()).replace(":", "_");
         result = "PF('" + wv + "').filter()";
      }
      return result;
   }

   public String clearTableFiltersInDynTab(String tableId) {
      String result = "";
      UIComponent tabB1 = JsfUtils.findComponentInDynamicTab(tableId, this.getDynTabId());
      if ((tabB1 != null) && (tabB1 instanceof DataTable)) {
         String wv = ("widget_" + tabB1.getClientId()).replace(":", "_");
         result = "PF('" + wv + "').clearFilters()";
      }
      return result;
   }

}
