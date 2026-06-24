package dyntabs;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

import dyntabs.ai.activity.ActivityRecorder;
import dyntabs.ai.activity.UserActivityEvent.Type;

/**
 * Verifies the tab-open / tab-select activity tracking added to {@link BaseDyntabCdiBean}: opening a
 * {@code trackActivity=true} tab records exactly one "open" event, the selection event that
 * {@code addTab()} fires straight after the open is suppressed (no duplicate), a later genuine
 * re-selection records a "select" event, and a tab that did not opt in records nothing.
 */
class BaseDyntabCdiBeanActivityTest {

    private BaseDyntabCdiBean beanFor(DynTab tab, ActivityRecorder recorder) {
        BaseDyntabCdiBean bean = new BaseDyntabCdiBean();
        bean.setDynTab(tab);
        bean.activityRecorder = recorder;
        return bean;
    }

    private DynTab trackedTab() {
        DynTab tab = new DynTab();
        tab.setId("r1");
        tab.setUniqueIdentifier("Orders");
        tab.setTitle("Orders");
        tab.setTrackActivity(true);
        return tab;
    }

    @Test
    void openRecordsOnceAndSuppressesTheFollowingSelection() {
        ActivityRecorder recorder = mock(ActivityRecorder.class);
        DynTab tab = trackedTab();
        BaseDyntabCdiBean bean = beanFor(tab, recorder);

        // 1. addTab() opens the tab → "open" recorded.
        bean.callAccessPointMethod();
        verify(recorder, times(1)).record(eq(Type.NAVIGATION), eq("open"), anyList(), isNull());

        // 2. addTab() then fires dynTabSelected for the same tab → must be suppressed.
        bean.observeDynTabEvent(new DynTabCDIEvent("dynTabSelected", tab));
        verify(recorder, never()).record(eq(Type.NAVIGATION), eq("select"), anyList(), any());
    }

    @Test
    void laterReselectionRecordsSelect() {
        ActivityRecorder recorder = mock(ActivityRecorder.class);
        DynTab tab = trackedTab();
        BaseDyntabCdiBean bean = beanFor(tab, recorder);

        bean.callAccessPointMethod();                                  // open
        bean.observeDynTabEvent(new DynTabCDIEvent("dynTabSelected", tab)); // suppressed (open-induced)
        bean.observeDynTabEvent(new DynTabCDIEvent("dynTabSelected", tab)); // genuine re-selection

        verify(recorder, times(1)).record(eq(Type.NAVIGATION), eq("open"), anyList(), isNull());
        verify(recorder, times(1)).record(eq(Type.NAVIGATION), eq("select"), anyList(), isNull());
    }

    @Test
    void nonTrackedTabRecordsNothing() {
        ActivityRecorder recorder = mock(ActivityRecorder.class);
        DynTab tab = trackedTab();
        tab.setTrackActivity(false);
        BaseDyntabCdiBean bean = beanFor(tab, recorder);

        bean.callAccessPointMethod();
        bean.observeDynTabEvent(new DynTabCDIEvent("dynTabSelected", tab));
        bean.observeDynTabEvent(new DynTabCDIEvent("dynTabSelected", tab));

        verify(recorder, never()).record(any(), any(), anyList(), any());
    }
}
