package dyntabs;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.component.UIComponent;
import jakarta.faces.component.visit.VisitCallback;
import jakarta.faces.component.visit.VisitContext;
import jakarta.faces.component.visit.VisitResult;
import jakarta.faces.context.FacesContext;
import jakarta.faces.event.ActionEvent;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.primefaces.PrimeFaces;
import org.primefaces.component.dialog.Dialog;
import org.primefaces.component.tabview.Tab;
import org.primefaces.event.TabChangeEvent;
import org.primefaces.event.TabCloseEvent;

import dyntabs.interfaces.DyntabBeanInterface;
import dyntabs.scope.TabScopedContextHolder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The central manager for dynamic tabs in the DynTabs framework.
 *
 * <p>This is a {@code @ViewScoped} bean that provides the main API for opening, closing,
 * selecting, and querying dynamic tabs. It coordinates between the UI (PrimeFaces TabView),
 * the tab tracking state ({@link DynTabTracker}), and the tab definition registry
 * ({@link DynTabRegistry}).</p>
 *
 * <p><b>Key responsibilities:</b></p>
 * <ul>
 *   <li>Opening tabs via {@link #launchTab(String)} or {@link #launchDynamicTab}</li>
 *   <li>Closing tabs via {@link #removeTab(String)} and handling tab close events</li>
 *   <li>Selecting tabs and updating the {@code @TabScoped} context accordingly</li>
 *   <li>Dispatching {@link DynTabCDIEvent} and {@link ApplicationCDIEvent} to all active tab beans</li>
 *   <li>Providing the tab menu model to the UI template</li>
 * </ul>
 *
 * <p><b>Event dispatch:</b> Events are NOT dispatched via CDI {@code @Observes}, because that
 * mechanism does not work correctly with {@code @TabScoped} beans (CDI would create new bean
 * instances in the wrong scope). Instead, this manager manually iterates through all active
 * tabs, sets the correct {@code currentTabId} for each, and calls the observer method directly.</p>
 *
 * @author DynTabs
 * @see DynTabTracker
 * @see DynTabRegistry
 * @see dyntabs.scope.TabScoped
 */
@Named
@ViewScoped
public class DynTabManager implements Serializable {

    private static final Logger log = LoggerFactory.getLogger(DynTabManager.class);

    @Inject
    private DynTabTracker tabTracker;

    @Inject
    private DynTabRegistry registry;

    public DynTabTracker getTabTracker() {
        return tabTracker;
    }

    public List<DynTab> getTabMenuModel() {
        List<DynTab> tabList = tabTracker.getActiveTabList();
        return tabList;
    }

    public Map<String, DynTab> getTabMap() {
        return Collections.unmodifiableMap(tabTracker.getTabMap());
    }

    public String getSelectedTabId() {
        String result = tabTracker.getSelectedTabId();
        return result;
    }

    public DynTabManager() {
        super();
        log.info("DynTabManager constructor!");
    }

    @PostConstruct
    public void init() {
        log.info("DynTabManager init() begin");
        // Tab initialization is now done in DynTabTracker.init()
        // Here we only log that DynTabManager is ready
        log.info("DynTabManager init() end");
    }

    /**
     * Handles PrimeFaces TabView tab change events.
     * Called when the user clicks on a different tab header.
     *
     * @param event the PrimeFaces TabChangeEvent
     */
    public void onTabChange(TabChangeEvent event) {
        String tabId = event.getTab().getId();
        setSelectedTabId(tabId);
    }

    private Integer activeTabIndex = 0;

    public Integer getActiveTabIndex() {
        return getIndexOfSelecedTab();
    }

    public void setActiveTabIndex(Integer activeTabIndex) {
        // this.activeTabIndex = activeTabIndex;
    }

    public void setSelectedTabId(String id) {
        DynTab tab = getTab(id);
        if (tab == null) {
            throw new IllegalArgumentException("Tab: " + id + " is unknown!");
        }
        log.debug("setSelectedTabId(), include = {}", tab.getIncludePage());
        setSelectedTab(tab);
    }

    public void setSelectedTab(DynTab tab) {
        tabTracker.setSelectedTabId(tab.getId());
        tab.setActive(true);
        // Set the active tab for the TabScoped context
        // This enables @TabScoped beans to know which tab they belong to
        TabScopedContextHolder.setCurrentTabId(tab.getId());
        fireDynTabEvent(new DynTabCDIEvent("dynTabSelected", tab));
    }

    public DynTab getTab(String id) {
        return tabTracker.getTabMap().get(id);
    }

    /**
     * Handles PrimeFaces TabView tab close events.
     * Registered as an AJAX listener for the "tabClose" event on {@code p:tabView}.
     * Removes the closed tab and cleans up any related dialog visibility state.
     *
     * @param event the PrimeFaces TabCloseEvent
     */
    public void onTabClose(TabCloseEvent event) {
        log.debug("onTabClose() begin");
        Tab tab = event.getTab();
        String tabId = tab.getId();
        removeTab(tabId);
        log.debug("onTabClose() end");
    }

    private void hideDialogsRelatedToTab(DynTab tab) {
        if (tab != null) {
            tab.getDynTabVisibleMap().clear();
            log.debug("for DynTab: {} visible map cleared!", tab.getId());
        }
    }

    class DialogsHideVisitCallback implements VisitCallback {
        @Override
        public VisitResult visit(VisitContext vCtx, UIComponent target) {
            if (target instanceof Dialog) {
                Dialog dlg = (Dialog) target;
                String dlgId = dlg.getId();
                char last = dlgId.charAt(dlgId.length() - 1);
                if (Character.isDigit(last)) {
                    // ok, this is a dialog that should be hidden
                }
            }
            return VisitResult.ACCEPT;
        }
    }

    public void removeTab(String id) {
        removeTab(id, false);
    }

    protected void removeTab(String id, boolean force) {
        DynTab tab = getTab(id);
        if (tab == null) {
            throw new IllegalArgumentException("Tab: " + id + " does not exist!");
        }
        removeTab(tab, force);
    }

    /**
     * Removes (closes) the given tab from the active tab list.
     *
     * <p>This is triggered by the {@code p:ajax} "tabClose" event on the template's {@code p:tabView}.
     * The method does NOT delete the DynTab instance from the tracker; instead, it moves it to the
     * end of the list and resets its properties (uniqueIdentifier becomes null, active and activated
     * become false). The active tab count ({@code tabTracker.numActive}) is decremented by 1.</p>
     *
     * <p>If the removed tab was the currently selected tab, a new tab is automatically selected
     * (the next tab, or the previous one if the removed tab was last).</p>
     *
     * @param tab   the DynTab instance from tabTracker.tabMap
     * @param force if true, allows closing non-closeable tabs
     */
    protected void removeTab(DynTab tab, boolean force) {
        log.debug("removeTab() begin");
        // fix for "TabList state is corrupted!"
        if (tab.getUniqueIdentifier() == null) {
            return;
        }
        if (!tab.isCloseable() && !force) {
            FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_INFO, "Tab cannot be closed ", null);
            FacesContext.getCurrentInstance().addMessage(null, message);
            return;
        }

        // Save the bean reference BEFORE destruction
        // (getCdiBean() returns a proxy, but we need the reference for later)
        DyntabBeanInterface cdiBeanRef = tab.getCdiBean();

        // Call exitPointMethod while the bean still exists
        if (cdiBeanRef != null) {
            cdiBeanRef.callExitPointMethod();
            log.debug("cdi bean {} callExitPointMethod() called", cdiBeanRef);
        }

        // IMMEDIATELY clear the reference on the tab so getCdiBean() no longer creates new instances
        tab.setCdiBean(null);
        tab.setCdiBeanClass(null);

        // ONLY THEN destroy all @TabScoped beans associated with this tab
        // This will invoke @PreDestroy methods on all beans
        TabScopedContextHolder.getInstance().destroyBeansForTab(tab.getId());

        hideDialogsRelatedToTab(tab);

        // Save tab data for the event BEFORE resetting
        String removedTabId = tab.getId();
        String removedUniqueId = tab.getUniqueIdentifier();
        String removedTitle = tab.getTitle();

        tab.setIncludePage("WEB-INF/include/empty/empty.xhtml");
        tab.setParameters(null);
        tab.setUniqueIdentifier(null);
        tab.setTitle("");
        tab.setActive(false);
        tab.setActivated(false);

        // Determine which tab to select BEFORE modifying the list,
        // while indices and numActive are still correct.
        DynTab newSelectedTab = null;
        boolean wasSelected = tab.getId().equals(tabTracker.getSelectedTabId());
        if (wasSelected && tabTracker.getNumActive() > 1) {
            List<DynTab> currentActive = tabTracker.getActiveTabList();
            int activeIndex = currentActive.indexOf(tab);
            if (activeIndex >= 0) {
                if (activeIndex < currentActive.size() - 1) {
                    // Not the last active tab - take the next one
                    newSelectedTab = currentActive.get(activeIndex + 1);
                } else {
                    // Last active tab - take the previous one
                    newSelectedTab = currentActive.get(activeIndex - 1);
                }
            }
        }

        tabTracker.setNumActive(tabTracker.getNumActive() - 1);

        List<DynTab> tabList = tabTracker.getTabList();
        int oldIndex = tabList.indexOf(tab);
        tabList.add(tabList.remove(oldIndex));

        if (newSelectedTab != null) {
            setSelectedTab(newSelectedTab);
        }

        if (closingAllTabs) {
            closedTabCounter--;
            if (closedTabCounter <= 0) {
                // Batch close completed (or interrupted) - reset state and refresh UI
                closingAllTabs = false;
                closedTabCounter = 0;
                PrimeFaces.current().ajax().update("mainForm:mainTabView");
                log.debug("refreshed all dyn tabs content after closing all");
            }
        } else {
            PrimeFaces.current().ajax().update("mainForm:mainTabView");
            log.debug("refreshed all dyn tabs content!");
        }

        // Bean has already been destroyed through destroyBeansForTab() which calls @PreDestroy.
        // Do not access the bean again as that would create a new instance.
        // tab.setCdiBean(null) was already called before destruction.

        fireDynTabEvent(new DynTabCDIEvent("dynTabRemoved", removedTabId, removedUniqueId, removedTitle));
        log.debug("removeTab() end");

        removeTabListener(tab);
    }

    /**
     * Hook method called after a tab is removed. Override in subclasses to perform
     * custom actions (e.g. executing JavaScript, cleanup).
     *
     * @param tabToAdd the tab that was removed
     */
    protected void removeTabListener(DynTab tabToAdd) {
    }

    public DynTab getSelectedTab() {
        String selectedTabId = getSelectedTabId();
        if (selectedTabId != null) {
            return getTab(selectedTabId);
        }
        return null;
    }

    public int getIndexOfSelecedTab() {
        List<DynTab> tabList = tabTracker.getActiveTabList();
        return tabList.indexOf(getSelectedTab());
    }

    public static DynTabManager getCurrentInstance() {
        try {
            DynTabManager context = (DynTabManager) JsfUtils.getExpressionValue("#{dynTabManager}");
            return context;
        } catch (Exception e) {
            log.error("getCurrentInstance() exception: {}", e.toString());
        }
        return null;
    }

    /**
     * Opens a new dynamic tab, or selects an existing tab if one with the same
     * uniqueIdentifier is already open.
     *
     * <p>The {@code tabName} is the short name without the "DynTab" suffix.
     * For example, if tabName is "Users", it looks up the tab registered as "UsersDynTab"
     * in the {@link DynTabRegistry}.</p>
     *
     * @param tabName the tab name without the "DynTab" suffix (e.g. "Users")
     */
    public void launchTab(String tabName) {
        String fullTabName = tabName + "DynTab";
        DynTab tab = registry.createTab(fullTabName);
        if (tab == null) {
            log.error("Tab '{}' is not registered in DynTabRegistry!", fullTabName);
            return;
        }
        tab.setName(tabName);
        addOrSelectTab(tab);
    }

    /**
     * Dynamically opens a new tab at runtime with the specified parameters.
     *
     * <p>Creates a DynTab instance and opens it. If an active tab with the same
     * {@code uniqueIdentifier} already exists, selects it instead of opening a new one.</p>
     *
     * <p>This enables opening multiple instances of the same tab type
     * (same includePage and cdiBeanClass) with different parameters,
     * each with its own {@code @TabScoped} CDI bean instance.</p>
     *
     * <p>Example usage from a managed bean:</p>
     * <pre>
     * {@code
     * dynTabManager.launchDynamicTab("VIPProductsDynTab",
     *     "VIP Products",
     *     "/WEB-INF/include/products/products.xhtml",
     *     "VIPProducts",
     *     true,
     *     ProductsBean.class,
     *     Map.of("category", "vip", "discount", true)
     * );
     * }
     * </pre>
     *
     * @param name             the tab name (e.g. "Products")
     * @param title            the tab title displayed to the user
     * @param includePage      the path to the XHTML page to render in the tab
     * @param uniqueIdentifier the unique ID for duplicate detection
     * @param closeable        whether the user can close the tab
     * @param cdiBeanClass     the CDI bean class associated with the tab
     * @param parameters       the tab parameters map (can be null)
     */
    public void launchDynamicTab(String name, String title, String includePage,
                                 String uniqueIdentifier, boolean closeable,
                                 Class<?> cdiBeanClass, Map<String, Object> parameters) {
        DynTab tab = new DynTab();
        tab.setName(name);
        tab.setTitle(title);
        tab.setIncludePage(includePage);
        tab.setUniqueIdentifier(uniqueIdentifier);
        tab.setCloseable(closeable);
        tab.setCdiBeanClass(cdiBeanClass);
        if (parameters != null) {
            tab.setParameters(parameters);
        }
        addOrSelectTab(tab);
    }

    /**
     * Finds an active tab that matches the given DynTab's uniqueIdentifier.
     *
     * <p>If the given tab's uniqueIdentifier is null, falls back to matching by includePage.</p>
     *
     * @param compareTab the DynTab to match against
     * @return the matching active tab, or null if none found
     */
    public DynTab getMatchingTab(DynTab compareTab) {
        log.debug("getMatchingTab(), include = {}, uniqueId = {}", compareTab.getIncludePage(), compareTab.getUniqueIdentifier());

        if (compareTab.getUniqueIdentifier() == null) {
            return getFirstTabWithIncludePage(compareTab.getIncludePage());
        }

        for (DynTab tab : tabTracker.getActiveTabList()) {
            if (compareTab.getUniqueIdentifier().equals(tab.getUniqueIdentifier())) {
                return tab;
            }
        }
        return null;
    }

    public DynTab getMatchingTab(String uniqueIdentifier) {
        for (DynTab tab : tabTracker.getActiveTabList()) {
            if (uniqueIdentifier.equals(tab.getUniqueIdentifier())) {
                return tab;
            }
        }
        return null;
    }

    public boolean isActiveTabWithUniqueID(String uniqueIdentifier) {
        for (DynTab tab : tabTracker.getActiveTabList()) {
            if (uniqueIdentifier.equalsIgnoreCase(tab.getUniqueIdentifier())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Finds the first active tab that has the specified includePage.
     *
     * @param includePage the XHTML page path to search for
     * @return the matching active tab, or null if none found
     */
    public DynTab getFirstTabWithIncludePage(String includePage) {
        for (DynTab tab : tabTracker.getActiveTabList()) {
            if (tab.getIncludePage().equals(includePage)) {
                return tab;
            }
        }
        return null;
    }

    public void addOrSelectTab(DynTab tab) {
        log.debug("addOrSelectTab: {}", tab.getIncludePage());

        DynTab existingTab = getMatchingTab(tab);

        if (existingTab != null) {
            log.debug("Matching existing tab found, select it");
            setSelectedTabId(existingTab.getId());
        } else {
            addTab(tab);
        }
        PrimeFaces.current().ajax().update("mainForm:mainTabView");
    }

    /**
     * Manually dispatches a DynTab event to all active tab beans.
     *
     * <p>Does NOT use CDI {@code @Observes} because it doesn't work correctly with
     * {@code @TabScoped}: CDI can only see beans in the currently active tab scope and
     * would create new (wrong) bean instances in other tabs.</p>
     *
     * <p>Instead, this method:</p>
     * <ol>
     *   <li>Saves the current {@code currentTabId}</li>
     *   <li>Iterates through ALL active tabs</li>
     *   <li>For each tab with a CDI bean: sets {@code currentTabId} to that tab,
     *       then calls {@code observeDynTabEvent()} directly on the bean</li>
     *   <li>Restores {@code currentTabId} to the original value</li>
     * </ol>
     *
     * <p>This ensures that each bean receives the event in ITS OWN TabScope,
     * enabling correct inter-tab communication.</p>
     */
    private void fireDynTabEvent(DynTabCDIEvent event) {
        if (event == null) {
            return;
        }
        log.debug("fireDynTabEvent(), _Event: {}", event.getEventType());

        // Save the current tab scope
        String savedTabId = TabScopedContextHolder.getCurrentTabId();

        // Snapshot the list before iterating - getActiveTabList() returns a live subList
        // that would throw ConcurrentModificationException if an observer method
        // modifies the tab list (e.g. opens or closes a tab in reaction to the event)
        List<DynTab> snapshot = new ArrayList<>(tabTracker.getActiveTabList());
        // Deliver event to all active tabs
        for (DynTab tab : snapshot) {
            DyntabBeanInterface bean = tab.getCdiBeanDirect();
            if (bean != null) {
                try {
                    // Switch to this tab's scope
                    TabScopedContextHolder.setCurrentTabId(tab.getId());
                    // Call the observer directly on the bean
                    bean.observeDynTabEvent(event);
                } catch (Exception e) {
                    log.error("fireDynTabEvent() error for tab {}: {}", tab.getId(), e.getMessage());
                }
            }
        }

        // Restore the original tab scope
        if (savedTabId != null) {
            TabScopedContextHolder.setCurrentTabId(savedTabId);
        } else {
            TabScopedContextHolder.clearCurrentTabId();
        }
    }

    /**
     * Manually dispatches an ApplicationCDIEvent to all active tab beans.
     * Same principle as {@link #fireDynTabEvent} - iterates through tabs,
     * sets the correct TabScope, and calls the observer directly.
     *
     * <p>Called from {@link BaseDyntabCdiBean#sendMessageToAppModule} instead of
     * CDI {@code eventPublisher.fire()} because {@code @Observes} does not work
     * with {@code @TabScoped} beans.</p>
     */
    public void fireApplicationEvent(ApplicationCDIEvent event) {
        if (event == null) {
            return;
        }
        log.debug("fireApplicationEvent(), eventType: {}", event.getEventType());

        String savedTabId = TabScopedContextHolder.getCurrentTabId();

        // Snapshot the list - same reason as in fireDynTabEvent()
        List<DynTab> snapshot = new ArrayList<>(tabTracker.getActiveTabList());

        for (DynTab tab : snapshot) {
            DyntabBeanInterface bean = tab.getCdiBeanDirect();
            if (bean != null) {
                try {
                    TabScopedContextHolder.setCurrentTabId(tab.getId());
                    bean.observeApplicationEvent(event);
                } catch (Exception e) {
                    log.error("fireApplicationEvent() error for tab {}: {}", tab.getId(), e.getMessage());
                }
            }
        }

        if (savedTabId != null) {
            TabScopedContextHolder.setCurrentTabId(savedTabId);
        } else {
            TabScopedContextHolder.clearCurrentTabId();
        }
    }

    private void handleTooManyTabsOpen() {
        String messageBody = JsfUtils.getStringFromAppResourceBundle("ui_labels", "maxBrojTabovaTextBody") + " "
                + tabTracker.getMaxNumberOfTabs() + ". "
                + JsfUtils.getStringFromAppResourceBundle("ui_labels", "zatvoriteTaboveLabel") + ".";
        FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_ERROR,
                JsfUtils.getStringFromAppResourceBundle("ui_labels", "najveciBrojTabovaOtvorenLabel"), messageBody);
        PrimeFaces.current().dialog().showMessageDynamic(message);
    }

    public void addTab(DynTab tabToAdd) {
        log.debug("DynTabManager.addTab() begin");
        if (tabToAdd != null) {
            log.debug("   adding tab {}, title = {}", tabToAdd.getName(), tabToAdd.getTitle());
        }

        if (tabTracker.getNumActive() >= tabTracker.getMaxNumberOfTabs()) {
            handleTooManyTabsOpen();
        } else {
            DynTab tab = getFirstInactiveTabOrThrow();
            tab.setTitle(tabToAdd.getTitle());
            tab.setActive(true);
            tab.setIncludePage(tabToAdd.getIncludePage());
            tab.setUniqueIdentifier(tabToAdd.getUniqueIdentifier());

            log.debug("tab id = {}", tab.getId());
            tab.setParameters(tabToAdd.getParameters());

            // IMPORTANT: Set currentTabId BEFORE accessing @TabScoped beans
            // This enables CDI to create the bean in the correct tab scope
            TabScopedContextHolder.setCurrentTabId(tab.getId());

            // Now TabScope is active - resolve CDI bean from tabToAdd
            // (Supplier in DynTabRegistry only stores cdiBeanClass, does not create the bean)
            tabToAdd.resolveCdiBean();
            tab.setCdiBean(tabToAdd.getCdiBean());

            if (tab.getCdiBean() != null) {
                tab.getCdiBean().setDynTab(tab);
            }

            if (tab.getCdiBean() != null) {
                tab.getCdiBean().callAccessPointMethod();
            }

            tabTracker.setNumActive(tabTracker.getNumActive() + 1);
            fireDynTabEvent(new DynTabCDIEvent("dynTabAdded", tab));
            setSelectedTabId(tab.getId());

            addTabListener(tabToAdd);
        }
        log.debug("DynTabManager.addTab() end");
    }

    /**
     * Hook method called after a tab is added. Override in subclasses to perform
     * custom actions (e.g. executing JavaScript, additional initialization).
     *
     * @param tabToAdd the tab that was added
     */
    protected void addTabListener(DynTab tabToAdd) {
    }

    private DynTab getFirstInactiveTabOrThrow() {
        log.debug("trackerNumAc  = {}", tabTracker.getNumActive());
        DynTab tab = tabTracker.getTabList().get(tabTracker.getNumActive());
        log.debug("getOrThrow(), tab = {}", tab.getUniqueIdentifier());
        if (!tab.isActive()) {
            return tab;
        }

        throw new IllegalStateException(
                "TabList state is corrupted (the first inactive DynTab in the list is still Active!)!");
    }

    // --- closing tabs

    public void removeCurrentTab(ActionEvent e) {
        DynTabManager manager = DynTabManager.getCurrentInstance();
        if (manager == null) {
            return;
        }

        if (manager.getSelectedTab().isCloseable()) {
            String jsCode = "PF('mainTabView').remove(";
            String selTabId = this.getSelectedTabId();
            List<DynTab> mModel = this.getTabMenuModel();
            int i = -1;
            for (DynTab tab : mModel) {
                i++;
                if (tab.getId().equalsIgnoreCase(selTabId)) {
                    jsCode = jsCode + i + ");";
                    PrimeFaces.current().executeScript(jsCode);
                    break;
                }
            }
        }
    }

    private boolean closingAllTabs = false;
    private int closedTabCounter = 0;

    public void closeAllActiveTabs(ActionEvent e) {
        closingAllTabs = true;
        List<DynTab> mCopy = new ArrayList<DynTab>(getTabMenuModel());

        closedTabCounter = 0;
        String jsCode = "PF('mainTabView').remove(";
        int idx;
        while ((idx = getCloseableTabIndex(mCopy)) != -1) {
            PrimeFaces.current().executeScript(jsCode + idx + ");");
            mCopy.remove(idx);
            closedTabCounter++;
        }
    }

    private int getCloseableTabIndex(List<DynTab> mCopy) {
        log.debug("getCloseableTabIndex() begin");
        int result = -1;
        int i = -1;
        for (DynTab tab : mCopy) {
            i++;
            if (tab.isCloseable()) {
                result = i;
                break;
            }
        }
        log.debug("getCloseableTabIndex() returns: {}", result);
        return result;
    }

    public List<DynTab> getActiveTabList() {
        return Collections.unmodifiableList(tabTracker.getActiveTabList());
    }

    public void removeCurrentTab(boolean ignorePendingChanges) {
        removeTab(getSelectedTabId(), ignorePendingChanges);
    }
}
