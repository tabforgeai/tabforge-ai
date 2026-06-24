package dyntabs.ai;

import dyntabs.ai.activity.ActivityContext;

/**
 * Internal helper that fuses a fixed system message with a freshly-rendered ambient-activity
 * briefing into the single system message handed to the model for one call.
 *
 * <h2>What this class is for</h2>
 * <p>A TabForge assistant or conversation can have two sources of "standing instruction" for the
 * model: the <em>base</em> system message the developer wrote ("You are a support agent…"), and the
 * <em>ambient activity</em> the user has been generating in the UI (rendered by an
 * {@link ActivityContext}). Both belong in the system message, but the base part is static while the
 * activity part changes turn by turn. This helper is the single place that decides how the two are
 * stitched together, so the rule lives in one tested spot rather than being duplicated across
 * builders.</p>
 *
 * <p><b>Familiar analogy:</b> a teleprompter operator who, right before the host speaks, pastes
 * today's fresh headlines (the activity) under the host's standing intro (the base message) so the
 * host reads one seamless script. The intro never changes; the headlines are re-pasted every time.</p>
 *
 * <p>This type is package-private and stateless — it is wiring used only by {@link Conversation} and
 * {@link AssistantBuilder}, not part of the public API.</p>
 */
final class SystemMessageComposer {

    private SystemMessageComposer() {
    }

    /**
     * Produce the effective system message for one model call by combining the static base message
     * with the activity context's current rendering.
     *
     * <h3>Who calls this, and when</h3>
     * <p>Invoked from inside the LangChain4J {@code systemMessageProvider} lambda that
     * {@link Conversation} and {@link AssistantBuilder#build()} install — i.e. once per model call
     * ({@link Conversation#send(String)} or any assistant method). Calling
     * {@link ActivityContext#render()} here (rather than at build time) is what keeps the injected
     * briefing current for every turn.</p>
     *
     * <h3>Combination rule</h3>
     * <ul>
     *   <li>base present, activity present → {@code base + "\n\n" + activity}</li>
     *   <li>only base present → the base, unchanged</li>
     *   <li>only activity present → just the activity briefing</li>
     *   <li>neither present → {@code null}, meaning "no system message this call"</li>
     * </ul>
     *
     * @param baseSystemMessage the developer-supplied static system message; may be {@code null}/blank
     * @param activityContext   the ambient-activity descriptor to render now; may be {@code null} when
     *                          the feature is not enabled
     * @return the combined system message, or {@code null} if there is nothing to send — the value is
     *         returned straight to LangChain4J's system-message provider, which adds it to the prompt
     *         (or adds no system message when it is {@code null})
     */
    static String compose(String baseSystemMessage, ActivityContext activityContext) {
        String activity = activityContext != null ? activityContext.render() : null;

        boolean hasBase = baseSystemMessage != null && !baseSystemMessage.isBlank();
        boolean hasActivity = activity != null && !activity.isBlank();

        if (hasBase && hasActivity) {
            return baseSystemMessage + "\n\n" + activity;
        }
        if (hasBase) {
            return baseSystemMessage;
        }
        if (hasActivity) {
            return activity;
        }
        return null;
    }
}
