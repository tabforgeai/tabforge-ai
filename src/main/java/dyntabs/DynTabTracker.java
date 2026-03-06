package dyntabs;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import dyntabs.scope.TabScopedContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tracks the state of all dynamic tabs in the current view (session).
 *
 * <p>This is a {@code @ViewScoped} bean that maintains the complete list of DynTab instances
 * (both active and inactive), the tab-by-ID lookup map, the selected tab ID, and the count
 * of active tabs.</p>
 *
 * <p><b>Tab list structure:</b></p>
 * <ul>
 *   <li>{@code tabList[0..numActive)} contains the active (visible) tabs</li>
 *   <li>{@code tabList[numActive..n)} contains the inactive (placeholder) tabs</li>
 * </ul>
 *
 * <p>When a tab is closed, it is not removed from the list but moved to the inactive region.
 * When a tab is opened, an inactive placeholder is reused and moved to the active region.</p>
 *
 * <p>Initial tabs are loaded in {@link #init()} based on {@link DynTabConfig#getInitialTabNames()},
 * using {@link DynTabRegistry} to create the DynTab instances.</p>
 *
 * @author DynTabs
 * @see DynTabManager
 * @see DynTabConfig
 * @see DynTabRegistry
 */
@Named
@ViewScoped
public class DynTabTracker implements Serializable {

    private static final Logger log = LoggerFactory.getLogger(DynTabTracker.class);

    @Inject
    private DynTabRegistry registry;

    @Inject
    private DynTabConfig config;

    /**
     * The number of tabs that are currently active (visible on screen).
     * This is always equal to the number of DynTab instances in {@code tabList}
     * where {@code tab.isActive()} is true.
     */
    private int numActive = 0;

    public void setNumActive(int numActive) {
        this.numActive = numActive;
    }

    public int getNumActive() {
        return numActive;
    }

    /**
     * The <code>tabMap</code> contains all tabs based on their <code>id</code>.
     */
    private final Map<String, DynTab> tabMap = new HashMap<String, DynTab>();

    /**
     * Returns the map containing all tabs indexed by their IDs.
     *
     * <p>Used in the XHTML template for the {@code rendered} attribute of {@code p:tab}:</p>
     * <pre>
     * {@code
     * <p:tab id="r0" rendered="#{dynTabManager.tabMap['r0'].active}" />
     * }
     * </pre>
     *
     * @return the map containing all tabs (active and inactive) indexed by their IDs
     */
    public Map<String, DynTab> getTabMap() {
        return tabMap;
    }

    /**
     * The list of tabs that defines the order of the tabs on the screen. All
     * tabs are in this list at all times.
     * <ul>
     * <li><code>tabList[0..numActive)</code> contains the active (used) tabs
     * <li><code>tabList[numActive..n)</code> contains the inactive (unused) tabs,
     * where n = the size of tabList.
     * </ul>
     */
    private final List<DynTab> tabList = new ArrayList<DynTab>();

    /**
     * The id of the tab that is currently selected / open.
     */
    private String selectedTabId = null;

    public DynTabTracker() {
        super();
        log.debug("DynTabTracker constructor!");
    }

    @PostConstruct
    public void init() {
        log.info("Tracker init()");

        // Get the initial tab names from the configuration
        List<String> initialTabNames = config.getInitialTabNames();
        log.info("Tracker init(), initialTabNames = {}", initialTabNames);

        for (String tabName : initialTabNames) {
            // First create the tabId, then activate TabScope BEFORE the createTab() call.
            // Reason: the CDI bean created in createTab() may be @TabScoped,
            // and @TabScoped requires an active currentTabId so CDI knows
            // in which tab scope to create the bean instance.
            String tabId = createId(numActive);
            TabScopedContextHolder.setCurrentTabId(tabId);

            // Create a DynTab instance via the registry
            DynTab tab = registry.createTab(tabName);

            tab.setId(tabId);
            // Now that the tab has an ID and TabScope is active, resolve the CDI bean
            tab.resolveCdiBean();
            tab.setActive(true);
            if (numActive == 0) {
                setSelectedTabId(tab.getId());
            }
            tabList.add(tab);
            tabMap.put(tab.getId(), tab);
            numActive++;

            if (tab.getCdiBean() != null) {
                tab.getCdiBean().setDynTab(tab);
                tab.getCdiBean().callAccessPointMethod();
            }
        }
        // After the loop above, tabList and tabMap contain the initial DynTab instances with:
        // - uniqueIdentifier
        // - includePage
        // - Id (r0, r1, ...) set above
        // - active = true (because they are initially displayed when the app opens)

        // Create other "placeholder" tabs
        for (int i = initialTabNames.size(); i < getNumberOfTabsDefined(); i++) {
            DynTab tab = new DynTab(createId(i), "WEB-INF/include/empty/empty.xhtml");
            tabList.add(tab);
            tabMap.put(tab.getId(), tab);
        }
        // After this loop, tabList and tabMap also contain empty placeholder DynTab instances:
        // - Id (r2, r3, ...) set above
        // - includePage is empty (placeholder page)
        // - active = false (default from constructor)
        // - uniqueIdentifier = null

        // Make sure that the shown tab is activated.
        String id = getSelectedTabId();
        if (id != null) {
            tabMap.get(id).setActivated(true);
        }
        // After the above, only one DynTab from tabMap has activated = true -
        // that is the workspace the user is currently working with.
        // Clear ThreadLocal currentTabId after initialization.
        // Without this, currentTabId would remain set to the last
        // initial tab, and code executing after init() on the same
        // thread would use the wrong tab context.
        TabScopedContextHolder.clearCurrentTabId();
        log.info("DynTabTracker init() end");
    }

    /**
     * Creates a DynTab ID using the pattern {@code "r" + n}.
     * These IDs also serve as the {@code p:tab} component IDs in the XHTML template.
     *
     * @param n the tab index number
     * @return the tab ID (e.g. "r0", "r1")
     */
    protected String createId(int n) {
        return "r" + n;
    }

    public void setSelectedTabId(String selectedTabId) {
        this.selectedTabId = selectedTabId;
    }

    public String getSelectedTabId() {
        return selectedTabId;
    }

    public List<DynTab> getTabList() {
        return tabList;
    }

    /**
     * Returns the list of tabs that are currently active on screen. This list is
     * modifiable and can be used to change the order of the tabs on the screen. This
     * list is backed by <code>{@link #getTabList}</code>, so make sure not to modify
     * both structurally at the same time.
     *
     * @return the list of tabs that are currently active on screen
     * @see List#subList
     */
    public List<DynTab> getActiveTabList() {
        return getTabList().subList(0, getNumActive());
    }

    /**
     * Returns the number of tabs defined in the template.
     * Now sourced from {@link DynTabConfig} instead of the legacy managed-property.
     *
     * @return the total number of tab slots (active + placeholder)
     */
    public int getNumberOfTabsDefined() {
        return config.getMaxNumberOfTabs();
    }

    /**
     * Returns the maximum number of tabs the user can open.
     * Now sourced from {@link DynTabConfig}.
     *
     * @return the maximum number of open tabs allowed
     */
    public Integer getMaxNumberOfTabs() {
        return config.getMaxNumberOfTabs();
    }
}
