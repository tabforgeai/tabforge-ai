package dyntabs;

import dyntabs.interfaces.DyntabBeanInterface;
import dyntabs.scope.TabScopedContextHolder;
import jakarta.faces.context.FacesContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class DynTabManagerTest {

    private DynTabManager manager;
    private DynTabTracker tracker;
    private MockedStatic<FacesContext> facesContextMock;

    /**
     * Creates a DynTabManager with a manually configured DynTabTracker
     * injected via reflection. Sets up 5 tabs (r0..r4), first 3 active.
     * Mocks FacesContext to prevent NPEs in setSelectedTab().
     */
    @BeforeEach
    void setUp() throws Exception {
        tracker = new DynTabTracker();
        manager = new DynTabManager();

        // Inject tracker into manager via reflection (bypasses @Inject)
        Field trackerField = DynTabManager.class.getDeclaredField("tabTracker");
        trackerField.setAccessible(true);
        trackerField.set(manager, tracker);

        // Populate tracker with 5 tabs: r0, r1, r2 active; r3, r4 inactive
        for (int i = 0; i < 5; i++) {
            DynTab tab = new DynTab("r" + i, "page" + i + ".xhtml");
            tab.setUniqueIdentifier("uid" + i);
            tab.setActive(i < 3);
            tab.setTitle("Tab " + i);
            tracker.getTabList().add(tab);
            tracker.getTabMap().put(tab.getId(), tab);
        }
        tracker.setNumActive(3);
        tracker.setSelectedTabId("r0");

        // Mock FacesContext (needed by setSelectedTab -> fireDynTabEvent)
        facesContextMock = mockStatic(FacesContext.class);
        facesContextMock.when(FacesContext::getCurrentInstance).thenReturn(null);
    }

    @AfterEach
    void tearDown() {
        facesContextMock.close();
        TabScopedContextHolder.clearCurrentTabId();
    }

    // ---------------------------------------------------------------
    // getTab()
    // ---------------------------------------------------------------

    /**
     * getTab should return the tab with the given ID from the tracker's tabMap.
     */
    @Test
    void getTab_existingId_returnsTab() {
        DynTab tab = manager.getTab("r1");

        assertThat(tab).isNotNull();
        assertThat(tab.getId()).isEqualTo("r1");
    }

    /**
     * getTab should return null for an unknown tab ID.
     */
    @Test
    void getTab_unknownId_returnsNull() {
        assertThat(manager.getTab("r99")).isNull();
    }

    // ---------------------------------------------------------------
    // getMatchingTab(DynTab)
    // ---------------------------------------------------------------

    /**
     * getMatchingTab should find an active tab with the same uniqueIdentifier.
     */
    @Test
    void getMatchingTab_byDynTab_matchingUniqueId_returnsExistingTab() {
        DynTab search = new DynTab();
        search.setUniqueIdentifier("uid1");
        search.setIncludePage("page1.xhtml");

        DynTab result = manager.getMatchingTab(search);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo("r1");
    }

    /**
     * getMatchingTab should not find inactive tabs (only searches active tabs).
     */
    @Test
    void getMatchingTab_byDynTab_inactiveTab_returnsNull() {
        DynTab search = new DynTab();
        search.setUniqueIdentifier("uid3"); // r3 is inactive
        search.setIncludePage("page3.xhtml");

        DynTab result = manager.getMatchingTab(search);

        assertThat(result).isNull();
    }

    /**
     * When uniqueIdentifier is null, getMatchingTab falls back to matching by includePage.
     */
    @Test
    void getMatchingTab_byDynTab_nullUniqueId_fallsBackToIncludePage() {
        DynTab search = new DynTab();
        search.setUniqueIdentifier(null);
        search.setIncludePage("page2.xhtml");

        DynTab result = manager.getMatchingTab(search);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo("r2");
    }

    /**
     * When no matching tab exists, getMatchingTab returns null.
     */
    @Test
    void getMatchingTab_byDynTab_noMatch_returnsNull() {
        DynTab search = new DynTab();
        search.setUniqueIdentifier("nonexistent");
        search.setIncludePage("nonexistent.xhtml");

        assertThat(manager.getMatchingTab(search)).isNull();
    }

    // ---------------------------------------------------------------
    // getMatchingTab(String)
    // ---------------------------------------------------------------

    /**
     * getMatchingTab(String) should find an active tab by uniqueIdentifier.
     */
    @Test
    void getMatchingTab_byString_found() {
        assertThat(manager.getMatchingTab("uid0").getId()).isEqualTo("r0");
    }

    /**
     * getMatchingTab(String) returns null when no active tab has that uniqueIdentifier.
     */
    @Test
    void getMatchingTab_byString_notFound() {
        assertThat(manager.getMatchingTab("uid4")).isNull(); // r4 is inactive
    }

    // ---------------------------------------------------------------
    // isActiveTabWithUniqueID()
    // ---------------------------------------------------------------

    /**
     * isActiveTabWithUniqueID should return true for an active tab's uniqueIdentifier.
     */
    @Test
    void isActiveTabWithUniqueID_activeTab_returnsTrue() {
        assertThat(manager.isActiveTabWithUniqueID("uid2")).isTrue();
    }

    /**
     * isActiveTabWithUniqueID should return false for an inactive tab.
     */
    @Test
    void isActiveTabWithUniqueID_inactiveTab_returnsFalse() {
        assertThat(manager.isActiveTabWithUniqueID("uid4")).isFalse();
    }

    /**
     * isActiveTabWithUniqueID uses case-insensitive comparison.
     */
    @Test
    void isActiveTabWithUniqueID_caseInsensitive() {
        assertThat(manager.isActiveTabWithUniqueID("UID0")).isTrue();
    }

    // ---------------------------------------------------------------
    // getFirstTabWithIncludePage()
    // ---------------------------------------------------------------

    /**
     * getFirstTabWithIncludePage should return the first active tab with the given page.
     */
    @Test
    void getFirstTabWithIncludePage_found() {
        DynTab result = manager.getFirstTabWithIncludePage("page1.xhtml");

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo("r1");
    }

    /**
     * getFirstTabWithIncludePage should not find inactive tabs.
     */
    @Test
    void getFirstTabWithIncludePage_inactivePage_returnsNull() {
        assertThat(manager.getFirstTabWithIncludePage("page4.xhtml")).isNull();
    }

    /**
     * getFirstTabWithIncludePage should return null for a page not used by any tab.
     */
    @Test
    void getFirstTabWithIncludePage_unknownPage_returnsNull() {
        assertThat(manager.getFirstTabWithIncludePage("nonexistent.xhtml")).isNull();
    }

    // ---------------------------------------------------------------
    // getSelectedTab() / getIndexOfSelecedTab()
    // ---------------------------------------------------------------

    /**
     * getSelectedTab should return the DynTab instance corresponding to the selected tab ID.
     */
    @Test
    void getSelectedTab_returnsCorrectTab() {
        tracker.setSelectedTabId("r1");

        DynTab selected = manager.getSelectedTab();

        assertThat(selected).isNotNull();
        assertThat(selected.getId()).isEqualTo("r1");
    }

    /**
     * getSelectedTab should return null when no tab is selected.
     */
    @Test
    void getSelectedTab_noSelection_returnsNull() {
        tracker.setSelectedTabId(null);

        assertThat(manager.getSelectedTab()).isNull();
    }

    /**
     * getIndexOfSelecedTab should return the index within the active tab list.
     */
    @Test
    void getIndexOfSelectedTab_returnsCorrectIndex() {
        tracker.setSelectedTabId("r2");

        assertThat(manager.getIndexOfSelecedTab()).isEqualTo(2);
    }

    /**
     * getIndexOfSelecedTab should return -1 when no tab is selected.
     */
    @Test
    void getIndexOfSelectedTab_noSelection_returnsMinusOne() {
        tracker.setSelectedTabId(null);

        assertThat(manager.getIndexOfSelecedTab()).isEqualTo(-1);
    }

    // ---------------------------------------------------------------
    // setSelectedTabId()
    // ---------------------------------------------------------------

    /**
     * setSelectedTabId with an unknown tab ID should throw IllegalArgumentException.
     */
    @Test
    void setSelectedTabId_unknownId_throwsException() {
        assertThatThrownBy(() -> manager.setSelectedTabId("r99"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("r99");
    }

    /**
     * setSelectedTabId should update the tracker's selected tab and set the tab as active.
     */
    @Test
    void setSelectedTabId_validId_updatesSelection() {
        manager.setSelectedTabId("r2");

        assertThat(tracker.getSelectedTabId()).isEqualTo("r2");
        assertThat(TabScopedContextHolder.getCurrentTabId()).isEqualTo("r2");
    }

    // ---------------------------------------------------------------
    // getTabMenuModel() / getActiveTabList()
    // ---------------------------------------------------------------

    /**
     * getTabMenuModel should return the active tabs from the tracker.
     */
    @Test
    void getTabMenuModel_returnsActiveTabs() {
        List<DynTab> model = manager.getTabMenuModel();

        assertThat(model).hasSize(3);
    }

    /**
     * getActiveTabList should return an unmodifiable view of active tabs.
     */
    @Test
    void getActiveTabList_returnsUnmodifiableList() {
        List<DynTab> list = manager.getActiveTabList();

        assertThatThrownBy(() -> list.add(new DynTab()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // ---------------------------------------------------------------
    // getTabMap()
    // ---------------------------------------------------------------

    /**
     * getTabMap should return an unmodifiable view of all tabs.
     */
    @Test
    void getTabMap_returnsUnmodifiableMap() {
        assertThatThrownBy(() -> manager.getTabMap().put("x", new DynTab()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // ---------------------------------------------------------------
    // fireDynTabEvent — via setSelectedTab (indirect test)
    // ---------------------------------------------------------------

    /**
     * When setSelectedTab is called and a tab has a CDI bean,
     * the bean's observeDynTabEvent should be called with "dynTabSelected" event.
     */
    @Test
    void setSelectedTab_firesEventToAllActiveBeans() {
        // Set up mock CDI beans on active tabs
        DyntabBeanInterface bean0 = mock(DyntabBeanInterface.class);
        DyntabBeanInterface bean1 = mock(DyntabBeanInterface.class);
        tracker.getTabMap().get("r0").setCdiBean(bean0);
        tracker.getTabMap().get("r1").setCdiBean(bean1);

        DynTab tabToSelect = tracker.getTabMap().get("r2");
        manager.setSelectedTab(tabToSelect);

        // Both beans should have received the "dynTabSelected" event
        verify(bean0).observeDynTabEvent(argThat(e -> "dynTabSelected".equals(e.getEventType())));
        verify(bean1).observeDynTabEvent(argThat(e -> "dynTabSelected".equals(e.getEventType())));
    }
}
