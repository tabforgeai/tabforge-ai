package dyntabs;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import jakarta.faces.component.UIComponent;
import jakarta.faces.context.FacesContext;
import jakarta.faces.event.ActionEvent;

import org.primefaces.PrimeFaces;
import org.primefaces.component.dialog.Dialog;
import org.primefaces.event.CloseEvent;

import jakarta.enterprise.inject.spi.CDI;

import dyntabs.interfaces.DyntabBeanInterface;
import dyntabs.scope.TabScopedContextHolder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a single dynamic tab in the DynTabs framework.
 *
 * <p>Each DynTab instance holds the tab's metadata (title, include page, parameters,
 * unique identifier) and a reference to its CDI-managed backing bean. Tabs are created
 * by {@link DynTabRegistry} and managed by {@link DynTabManager}.</p>
 *
 * <p><b>Key properties:</b></p>
 * <ul>
 *   <li>{@link #id} - internal ID (e.g. "r0", "r1"), matches the {@code p:tab} ID in the template</li>
 *   <li>{@link #includePage} - path to the XHTML page rendered inside this tab</li>
 *   <li>{@link #uniqueIdentifier} - used to detect duplicate tabs (prevents opening the same tab twice)</li>
 *   <li>{@link #cdiBean} - the {@code @TabScoped} CDI bean associated with this tab</li>
 *   <li>{@link #parameters} - key-value map passed to the bean (supports EL expressions)</li>
 * </ul>
 *
 * <p><b>CDI bean lifecycle:</b> The bean class is stored via {@link #setCdiBeanClass(Class)}
 * at tab creation time, but the actual bean instance is only resolved later via
 * {@link #resolveCdiBean()} when the TabScope is active (i.e., {@code currentTabId} is set).
 * This two-phase approach is necessary because {@code @TabScoped} beans require an active
 * tab context for creation.</p>
 *
 * @author DynTabs
 * @see DynTabManager
 * @see DynTabRegistry
 * @see dyntabs.scope.TabScoped
 */

public class DynTab implements Serializable {

   private static final Logger log = LoggerFactory.getLogger(DynTab.class);

   private String id = null;
   private String name;
   private boolean isActive;
   private String title;
   private String uniqueIdentifier = null;

   private Map parameters = new HashMap();
   private boolean parameterValuesResolved = false;

   private transient DyntabBeanInterface cdiBean = null;

   /**
    * The CDI bean class associated with this tab.
    *
    * <p>Stored when the DynTab instance is created (inside the Supplier), but the
    * actual bean instance is NOT created immediately - it is resolved later by calling
    * {@link #resolveCdiBean()}, when the TabScope is active (currentTabId is set).</p>
    *
    * <p>Reason: {@code @TabScoped} beans require an active tab context for creation.
    * The Supplier in DynTabRegistry may be invoked before the tab ID is known
    * (e.g. from {@code launchTab()}), so calling {@code CDI.current().select()} at
    * that point would fail.</p>
    */
   private Class<?> cdiBeanClass = null;

   public Class<?> getCdiBeanClass() {
      return cdiBeanClass;
   }

   public void setCdiBeanClass(Class<?> cdiBeanClass) {
      this.cdiBeanClass = cdiBeanClass;
   }

   /**
    * Resolves the CDI bean instance from the CDI container using the stored {@link #cdiBeanClass}.
    *
    * <p>MUST be called AFTER {@link dyntabs.scope.TabScopedContextHolder#setCurrentTabId(String)}
    * has been set, because {@code @TabScoped} beans require an active tab context.</p>
    *
    * <p>If {@code cdiBean} is already set (e.g. manually via {@link #setCdiBean}), does nothing.
    * If {@code cdiBeanClass} is not set, also does nothing (tab without a bean).</p>
    */
   @SuppressWarnings("unchecked")
   public void resolveCdiBean() {
      if (cdiBean == null && cdiBeanClass != null) {
         try {
            cdiBean = (DyntabBeanInterface) CDI.current().select(cdiBeanClass).get();
            log.debug("resolveCdiBean(), resolved: {}", cdiBean);
         } catch (Exception e) {
            log.error("DynTab.resolveCdiBean() error for {}: {}", cdiBeanClass.getName(), e.getMessage(), e);
         }
      }
   }

   public void setCdiBean(DyntabBeanInterface cdiBean) {
      this.cdiBean = cdiBean;
      log.debug("setCdiBean(), cdiBean = {}", cdiBean);
   }

   /**
    * Returns the CDI bean associated with this tab.
    *
    * <p><b>IMPORTANT:</b> Before returning the bean (which is a proxy for {@code @NormalScope}),
    * this method sets {@code currentTabId} to this tab's ID. This ensures that when a
    * method is invoked on the proxy, {@link dyntabs.scope.TabContext#get} uses the correct
    * tab context - THIS tab's context, not the currently selected tab's context.</p>
    *
    * <p>This solves the problem that occurs when {@code c:forEach} renders all tabs at once
    * while {@code currentTabId} is set to the selected tab. Without this, accessing the bean
    * from a non-selected tab would create a new instance in the wrong scope.</p>
    *
    * @return the CDI bean for this tab, or null if no bean is associated
    */
   public DyntabBeanInterface getCdiBean() {
      if (this.id != null) {
         TabScopedContextHolder.setCurrentTabId(this.id);
      }
      // After view deserialization, cdiBean is null (transient field),
      // but cdiBeanClass is preserved. Re-resolve the bean from CDI.
      // currentTabId has already been set above, so TabContext.get()
      // returns the existing instance from TabScopedContextHolder.
      if (cdiBean == null && cdiBeanClass != null) {
         resolveCdiBean();
      }
      return this.cdiBean;
   }

   /**
    * Returns the cdiBean reference WITHOUT side effects (does not change ThreadLocal).
    *
    * <p>Use in internal code (e.g. {@link DynTabManager}) where {@code currentTabId}
    * is already set explicitly before accessing the bean.</p>
    *
    * @return the CDI bean instance, or null
    */
   DyntabBeanInterface getCdiBeanDirect() {
      return this.cdiBean;
   }

   public String getId() {
      return id;
   }

   public void setId(String id) {
      this.id = id;

   }

   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   /**
    * Returns whether this tab is currently active (visible on screen).
    * Active tabs are displayed as {@code p:tab} components in the UI template.
    *
    * @return true if the tab is active and visible
    */
   public boolean isActive() {
      return isActive;
   }

   public void setActive(boolean isActive) {
      this.isActive = isActive;
   }

   public String getTitle() {
      return title;
   }

   public void setTitle(String title) {
      this.title = title;
   }

   /** Default constructor. */
   public DynTab() {
      super();
   }

   public String getUniqueIdentifier() {
      return uniqueIdentifier;
   }

   public void setUniqueIdentifier(String uniqueIdentifier) {
      this.uniqueIdentifier = uniqueIdentifier;

   }

   private String includePage;

   public String getIncludePage() {
      return includePage;
   }

   public void setIncludePage(String includePage) {
      this.includePage = includePage;
   }

   public DynTab(String id, String includePage) {
      this.id = id;
      this.includePage = includePage;
      isActive = false;
   }

   private boolean activated = false;

   public void setActivated(boolean activated) {
      if (activated) {
         if (!this.activated) {
            log.debug("Activating tab with id {} (first time).", getId());
         } else {
            log.debug("Tab with id {} has already been activated.", getId());
         }
      }
      // Bug fix: this was previously missing - this.activated was never set,
      // so isActivated() always returned false
      this.activated = activated;
   }

   public boolean isActivated() {
      return activated;
   }

   private boolean isCloseable = true;

   public void setCloseable(boolean isCloseable) {

      this.isCloseable = isCloseable;
   }

   public boolean isCloseable() {
      return this.isCloseable;
   }

   /**
    * Looks up the DynTab bean for the given tab name from the JSF expression context.
    *
    * @param tabName the tab name (without "DynTab" suffix)
    * @deprecated This method is tied to the old approach where tabs were registered
    *             as {@code <managed-bean>} in {@code faces-config.xml}. Since Jakarta EE 10+,
    *             managed beans are no longer supported. Use {@link DynTabRegistry#createTab(String)}
    *             instead.
    *             <p>Example:</p>
    *             <pre>
    *             // Old way (deprecated):
    *             DynTab tab = DynTab.getInstance("Users");
    *
    *             // New way:
    *             &#64;Inject
    *             private DynTabRegistry registry;
    *             ...
    *             DynTab tab = registry.createTab("UsersDynTab");
    *             </pre>
    */
   @Deprecated
   public static DynTab getInstance(String tabName) {
      log.debug("getInstance() tabName = {}", tabName);
      String beanName = tabName + "DynTab";
      String expr = "#{" + beanName + "}";
      DynTab tab = (DynTab) JsfUtils.getExpressionValue(expr);
      if (tab != null) {
         log.warn(" -found tab, uniqueId = {}", tab.getUniqueIdentifier());
      }
      if (tab == null) {
         // try viewScope, must be used when tab is initially displayed
         expr = "#{viewScope." + beanName + "}";
         tab = (DynTab) JsfUtils.getExpressionValue(expr);
         if (tab != null) {
            log.warn(" -found in viewScope, uniqueId = {}", tab.getUniqueIdentifier());
         }

      }
      if (tab == null) {
         // sLog.severe("Could not find DynTab bean " + beanName + "!");
         log.error("Cannot find tab {}", tabName);
      }
      tab.setName(tabName);
      return tab;
   }

   public void setParameters(Map parameters) {
      this.parameters = parameters;
      // set parameters on the CdiBean, if one is associated:
   }

   public Map getParameters() {
      Map newParams = new HashMap();
      if (!parameterValuesResolved) {
         parameterValuesResolved = true;
         Iterator keys = parameters.keySet().iterator();
         while (keys.hasNext()) {
            Object key = keys.next();
            Object value = parameters.get(key);
            if ((value instanceof String) && ((String) value).startsWith("#{")) {
               value = JsfUtils.getExpressionValue((String) value);
            }
            newParams.put(key, value);
         }
         parameters = newParams;
      }
      return parameters;
   }

   /**
    * Checks whether a dialog component should be visible in this dynamic tab,
    * using the component's client ID as the lookup key.
    *
    * <p>Usage in XHTML:</p>
    * <pre>
    * {@code
    * visible="#{dynTab.isComponentVisibleByClientId(fn:replace(component.clientId,':','_'))}"
    * }
    * </pre>
    *
    * <p>The method looks up the given key in the JSF view scope map and returns
    * {@code true} if a Boolean {@code true} value exists for that key, {@code false} otherwise.</p>
    *
    * @param key the component's client ID (with colons replaced by underscores)
    * @return true if the dialog should be visible
    */
   public boolean isComponentVisibleByClientId(String key) {
      // varijanta kada se sa stranice salje clientId od dugmeta, u formi:
      // visible="#{dynTab.isComponentVisibleByClientId(fn:replace(component.clientId,':','_'))}"

      log.debug("isDialogVisible(), key = {}", key);
      Map<String, Object> viewMap = FacesContext.getCurrentInstance().getViewRoot().getViewMap();
      Boolean result = (Boolean) viewMap.get(key);
      result = (result == null ? false : result.booleanValue());
      log.debug("isDialogVisible() returns: {}", result);
      return result;

   }

   /**
    * Checks whether a component should be visible in this dynamic tab,
    * using the component's simple ID as the lookup key.
    *
    * <p>Usage in XHTML:</p>
    * <pre>
    * {@code
    * visible="#{dynTab.isComponentVisibleById(component.id)}"
    * }
    * </pre>
    *
    * @param key the component's ID
    * @return true if the component should be visible
    */
   public boolean isComponentVisibleById(String key) {

      Map<String, Object> visibleMap = getDynTabVisibleMap();
      Boolean result = (Boolean) visibleMap.get(key);
      result = (result == null ? false : result.booleanValue());
      return result;
   }

   public Map<String, Object> getDynTabVisibleMap() {
      Map<String, Object> viewMap = FacesContext.getCurrentInstance().getViewRoot().getViewMap();
      Map<String, Object> visibleMap = (Map<String, Object>) viewMap.get(this.getUniqueIdentifier());
      if (visibleMap == null) {
         visibleMap = new HashMap<String, Object>();
         viewMap.put(this.getUniqueIdentifier(), visibleMap);
      }
      return visibleMap;
   }

   public String getTabSubviewClientId() {
      return "mainForm:mainTabView:_sub_" + getId();
   }

   /**
    * Handles the PrimeFaces dialog close event by removing the visibility flag
    * for the closed dialog from the visible map returned by {@link #getDynTabVisibleMap()}.
    *
    * @param event the PrimeFaces CloseEvent
    */
   public void handleDlgClose(CloseEvent event) {

      log.debug("handleDlgClose() call");
      UIComponent dlgComp = event.getComponent();
      if ((dlgComp != null) && (dlgComp instanceof Dialog)) {
         log.debug("- pre remove() visible returns: {}", isComponentVisibleById(dlgComp.getId()));
         getDynTabVisibleMap().remove(dlgComp.getId());
         log.debug("_AFTER remove() visible returns: {}", isComponentVisibleById(dlgComp.getId()));
         // PrimeFaces.current().ajax().update(dlgComp.getClientId());
      }

   }

   public void closeDialog(ActionEvent e) {
      if (e != null) {
         Dialog parentDlg = (Dialog) JsfUtils.findParentComponentOfClass(e.getComponent(), Dialog.class);
         if (parentDlg != null) {
            String jsCloseDlg = "PF('" + parentDlg.resolveWidgetVar() + "').hide()";
            PrimeFaces.current().executeScript(jsCloseDlg);
            // update the visible property:
            getDynTabVisibleMap().remove(parentDlg.getId());
         }
      }
   }

}
