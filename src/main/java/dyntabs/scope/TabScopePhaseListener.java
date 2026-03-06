package dyntabs.scope;

import jakarta.faces.event.PhaseEvent;
import jakarta.faces.event.PhaseId;
import jakarta.faces.event.PhaseListener;

import dyntabs.DynTabManager;

/**
 * JSF PhaseListener that automatically sets the active tab scope
 * at the appropriate point in the JSF lifecycle.
 *
 * <p>This is needed so that {@code @TabScoped} beans work correctly when accessed
 * directly from EL expressions in XHTML pages (e.g. {@code #{myTabBean}}).</p>
 *
 * <p><b>How it works:</b></p>
 * <ol>
 *   <li>After RESTORE_VIEW phase, sets {@code TabScopedContextHolder.currentTabId}
 *       to the ID of the currently selected tab</li>
 *   <li>After RENDER_RESPONSE phase, clears currentTabId</li>
 * </ol>
 *
 * <p><b>IMPORTANT:</b> Setting currentTabId is done after RESTORE_VIEW, NOT before it!
 * Reason: {@code @ViewScoped} beans (DynTabManager, DynTabTracker) become available only
 * AFTER RESTORE_VIEW completes and the view is created/restored. Attempting to access
 * a {@code @ViewScoped} bean before that causes a ContextNotActiveException.</p>
 *
 * <p><b>Registration:</b> This listener is registered in {@code faces-config.xml}:</p>
 * <pre>
 * {@code
 * <lifecycle>
 *     <phase-listener>dyntabs.scope.TabScopePhaseListener</phase-listener>
 * </lifecycle>
 * }
 * </pre>
 *
 * @author DynTabs
 * @see TabScopedContextHolder
 */
public class TabScopePhaseListener implements PhaseListener {

    private static final long serialVersionUID = 1L;

    /**
     * Called after each JSF phase.
     *
     * <p>After RESTORE_VIEW phase, sets currentTabId - this is the earliest
     * point when {@code @ViewScoped} beans (DynTabManager) are available.
     * This ensures that {@code @TabScoped} beans are active during ALL
     * subsequent phases (APPLY_REQUEST_VALUES, UPDATE_MODEL_VALUES,
     * INVOKE_APPLICATION, RENDER_RESPONSE), which is necessary for AJAX
     * operations such as PrimeFaces pagination.</p>
     *
     * <p>After RENDER_RESPONSE phase, clears currentTabId to prevent
     * tab context from "leaking" into the next request.</p>
     *
     * @param event PhaseEvent
     */
    @Override
    public void afterPhase(PhaseEvent event) {
        if (event.getPhaseId() == PhaseId.RESTORE_VIEW) {
            // Set currentTabId IMMEDIATELY after RESTORE_VIEW
            // so it is available for all subsequent phases (AJAX requests!)
            try {
                DynTabManager manager = DynTabManager.getCurrentInstance();
                if (manager != null) {
                    String selectedTabId = manager.getSelectedTabId();
                    if (selectedTabId != null) {
                        TabScopedContextHolder.setCurrentTabId(selectedTabId);
                    }
                }
            } catch (Exception e) {
                // DynTabManager may not be available (e.g. on the login page)
            }
        } else if (event.getPhaseId() == PhaseId.RENDER_RESPONSE) {
            TabScopedContextHolder.clearCurrentTabId();
        }
    }

    /**
     * Called before each JSF phase.
     * Currently no action is needed before any phase.
     *
     * @param event PhaseEvent
     */
    @Override
    public void beforePhase(PhaseEvent event) {
        // Logic moved to afterPhase(RESTORE_VIEW) so that
        // TabScoped context is active during all phases after RESTORE_VIEW
    }

    /**
     * Returns the PhaseId this listener should be activated for.
     * ANY_PHASE means it will be activated for all phases
     * (beforePhase and afterPhase for every phase).
     *
     * @return PhaseId.ANY_PHASE
     */
    @Override
    public PhaseId getPhaseId() {
        return PhaseId.ANY_PHASE;
    }
}
