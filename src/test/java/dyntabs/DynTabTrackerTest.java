package dyntabs;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DynTabTrackerTest {

    private DynTabTracker tracker;

    /**
     * Creates a DynTabTracker and manually populates it with 5 tabs (r0..r4),
     * with the first 2 active and the rest inactive. This bypasses CDI/PostConstruct
     * and allows testing the pure tab tracking logic.
     */
    @BeforeEach
    void setUp() {
        tracker = new DynTabTracker();

        // Manually add 5 tabs: r0, r1 active; r2, r3, r4 inactive
        for (int i = 0; i < 5; i++) {
            DynTab tab = new DynTab("r" + i, "page" + i + ".xhtml");
            tab.setUniqueIdentifier("uid" + i);
            if (i < 2) {
                tab.setActive(true);
            }
            tracker.getTabList().add(tab);
            tracker.getTabMap().put(tab.getId(), tab);
        }
        tracker.setNumActive(2);
        tracker.setSelectedTabId("r0");
    }

    // ---------------------------------------------------------------
    // createId()
    // ---------------------------------------------------------------

    /**
     * createId should produce IDs in the format "r" + number.
     */
    @Test
    void createId_returnsRPrefixedNumber() {
        assertThat(tracker.createId(0)).isEqualTo("r0");
        assertThat(tracker.createId(5)).isEqualTo("r5");
        assertThat(tracker.createId(99)).isEqualTo("r99");
    }

    // ---------------------------------------------------------------
    // getActiveTabList()
    // ---------------------------------------------------------------

    /**
     * getActiveTabList should return only the active tabs (first numActive items).
     */
    @Test
    void getActiveTabList_returnsOnlyActiveTabs() {
        List<DynTab> active = tracker.getActiveTabList();

        assertThat(active).hasSize(2);
        assertThat(active.get(0).getId()).isEqualTo("r0");
        assertThat(active.get(1).getId()).isEqualTo("r1");
    }

    /**
     * When numActive is 0, getActiveTabList should return an empty list.
     */
    @Test
    void getActiveTabList_whenNoneActive_returnsEmptyList() {
        tracker.setNumActive(0);

        assertThat(tracker.getActiveTabList()).isEmpty();
    }

    /**
     * getActiveTabList returns a live subList — changes to it affect the underlying tabList.
     */
    @Test
    void getActiveTabList_isLiveView() {
        List<DynTab> active = tracker.getActiveTabList();
        String originalId = active.get(0).getId();

        // Modifying the subList element modifies the original
        active.get(0).setTitle("Changed");

        assertThat(tracker.getTabList().get(0).getTitle()).isEqualTo("Changed");
    }

    // ---------------------------------------------------------------
    // tabMap lookup
    // ---------------------------------------------------------------

    /**
     * getTabMap should return all tabs (active and inactive) indexed by ID.
     */
    @Test
    void getTabMap_containsAllTabs() {
        assertThat(tracker.getTabMap()).hasSize(5);
        assertThat(tracker.getTabMap().get("r0")).isNotNull();
        assertThat(tracker.getTabMap().get("r4")).isNotNull();
    }

    /**
     * getTabMap returns null for unknown IDs.
     */
    @Test
    void getTabMap_unknownId_returnsNull() {
        assertThat(tracker.getTabMap().get("r99")).isNull();
    }

    // ---------------------------------------------------------------
    // selectedTabId
    // ---------------------------------------------------------------

    /**
     * setSelectedTabId / getSelectedTabId should store and return the selected tab ID.
     */
    @Test
    void selectedTabId_setAndGet() {
        tracker.setSelectedTabId("r1");
        assertThat(tracker.getSelectedTabId()).isEqualTo("r1");
    }

    // ---------------------------------------------------------------
    // numActive
    // ---------------------------------------------------------------

    /**
     * Incrementing numActive should include one more tab in the active list.
     */
    @Test
    void numActive_incrementExpandsActiveList() {
        tracker.setNumActive(3);

        assertThat(tracker.getActiveTabList()).hasSize(3);
        assertThat(tracker.getActiveTabList().get(2).getId()).isEqualTo("r2");
    }

    /**
     * Decrementing numActive should shrink the active list.
     */
    @Test
    void numActive_decrementShrinksActiveList() {
        tracker.setNumActive(1);

        assertThat(tracker.getActiveTabList()).hasSize(1);
        assertThat(tracker.getActiveTabList().get(0).getId()).isEqualTo("r0");
    }
}
