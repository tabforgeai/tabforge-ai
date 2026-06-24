package dyntabs.ai.activity;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Turns a slice of the activity timeline into the plain text that gets injected into an AI prompt —
 * the bridge between structured {@link UserActivityEvent}s and the words the model actually reads.
 *
 * <h2>What this interface is for</h2>
 * <p>The store keeps activity as typed objects, but a language model consumes text. An
 * {@code ActivityRenderer} decides <em>how</em> those objects become a short, readable briefing:
 * which fields to show, in what order, how terse. Keeping this swappable means you can tune the
 * wording for your model or domain without touching capture or storage.</p>
 *
 * <p><b>Familiar analogy:</b> a chief-of-staff writing the one-paragraph morning brief from a stack
 * of memos. The memos (events) are structured and complete; the brief is a deliberately compact
 * prose summary tuned for the person who has to read it in ten seconds. The renderer is that
 * chief-of-staff.</p>
 *
 * <h2>Contract for implementations</h2>
 * <ul>
 *   <li><b>Empty in, empty out.</b> Given an empty list, return an empty string — that signals the
 *       caller to inject nothing rather than an awkward "no activity" line.</li>
 *   <li><b>Assume chronological input.</b> The store hands events oldest-first; a renderer should
 *       preserve that order so the model reads a natural timeline.</li>
 *   <li><b>Stay compact.</b> This text spends prompt tokens on every AI call, so favour labels over
 *       blobs (one line per event is the intended scale).</li>
 * </ul>
 *
 * <p>This is a {@link FunctionalInterface}, so a custom renderer is typically a lambda; for the
 * common case use the ready-made {@link #compactDefault()}.</p>
 *
 * @see ActivityContext which calls a renderer to produce the injected text
 */
@FunctionalInterface
public interface ActivityRenderer {

    /**
     * Render the given events into prompt text.
     *
     * @param events the matching events, oldest first; never {@code null}, possibly empty
     * @return the text to inject, or an empty string if {@code events} is empty
     */
    String render(List<UserActivityEvent> events);

    /**
     * A sensible built-in renderer: a short header followed by one bullet per event, each showing
     * the time, the action (the event's {@code verb}, or a humanised category if none), the labels
     * of any entities it touched, and any authored text.
     *
     * <p>Example output:</p>
     * <pre>
     * Recent user activity in this tab (oldest first):
     * - 14:03 opened Order #4711 — ACME
     * - 14:04 searched "unpaid invoices"
     * - 14:05 approved Order #4711 — ACME
     * </pre>
     *
     * <p><b>Analogy:</b> the default brief format — clean bullets, newest at the bottom, just enough
     * for the reader to say "ah, 'this' must be order 4711".</p>
     *
     * @return a reusable, stateless {@link ActivityRenderer}
     */
    static ActivityRenderer compactDefault() {
        return CompactDefault.INSTANCE;
    }

    /**
     * Holder for the stateless default renderer. Kept as a nested class so the {@code static} method
     * above can return a single shared instance rather than allocating one per call.
     */
    final class CompactDefault implements ActivityRenderer {

        private static final ActivityRenderer INSTANCE = new CompactDefault();

        private static final DateTimeFormatter TIME =
                DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

        private CompactDefault() {
        }

        @Override
        public String render(List<UserActivityEvent> events) {
            if (events.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder("Recent user activity in this tab (oldest first):\n");
            for (UserActivityEvent e : events) {
                sb.append("- ").append(line(e)).append('\n');
            }
            // Trim the trailing newline so the block sits cleanly in a larger system message.
            return sb.substring(0, sb.length() - 1);
        }

        private static String line(UserActivityEvent e) {
            StringBuilder sb = new StringBuilder();
            sb.append(TIME.format(e.timestamp())).append(' ');
            sb.append(e.verb() != null ? e.verb() : humanize(e.type()));
            if (!e.entities().isEmpty()) {
                sb.append(' ').append(e.entities().stream()
                        .map(EntityRef::label)
                        .collect(Collectors.joining(", ")));
            }
            if (e.text() != null && !e.text().isBlank()) {
                sb.append(" \"").append(e.text().trim()).append('"');
            }
            return sb.toString();
        }

        /** A readable fallback verb when an event carries no explicit one. */
        private static String humanize(UserActivityEvent.Type type) {
            switch (type) {
                case NAVIGATION:      return "navigated to";
                case BUSINESS_ACTION: return "acted on";
                case NOTE:            return "noted";
                case SEARCH:          return "searched";
                case CUSTOM:
                default:              return "did";
            }
        }
    }
}
