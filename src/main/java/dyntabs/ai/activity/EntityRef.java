package dyntabs.ai.activity;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * An immutable, lightweight <em>pointer</em> to one business entity the user just touched —
 * not the entity itself, just enough to name it: a {@link #type() type} (e.g. {@code "order"}),
 * an {@link #id() id} (e.g. {@code "4711"}), and a few optional human-readable
 * {@link #attributes() attributes} (e.g. {@code label="Order #4711 — ACME"}).
 *
 * <h2>What this class is for</h2>
 * <p>{@code EntityRef} is the heart of the Ambient Activity Memory feature. When the user opens
 * an order, edits a customer, or searches for an invoice, we don't copy the whole record into the
 * activity timeline — we record a tiny reference to it. Later, when the user asks the assistant
 * <em>"cancel <b>this</b> order"</em> or <em>"who is <b>that</b> customer?"</em>, the recent stream
 * of {@code EntityRef}s is what lets the assistant resolve the deixis ("this"/"that") to a concrete
 * {@code order #4711} without the user ever typing the id.</p>
 *
 * <p><b>Familiar analogy:</b> a sticky note with a name and a desk number on it — "Order 4711, 3rd
 * drawer". It is not the file in the drawer; it is just enough to <em>find</em> the file. We keep a
 * little pile of these sticky notes (the activity timeline) so that when you say "pull that one",
 * we know which drawer you mean.</p>
 *
 * <h2>Why we store refs, not blobs</h2>
 * <p>The application already owns the real data (in its database, its CDI beans, its DTOs).
 * Duplicating it into an activity log would be wasteful, would go stale instantly, and would be a
 * privacy liability. A reference is cheap, always re-resolvable through the app's own services, and
 * carries only the labels a human (or the LLM) needs to recognise the thing. The optional
 * {@link #attributes()} map is for exactly those recognition hints (a display label, a status), not
 * for mirroring the record.</p>
 *
 * <h2>Equality</h2>
 * <p>Two refs are equal when their {@code type} and {@code id} match; attributes are descriptive
 * decoration and are deliberately excluded. This makes a ref behave like a stable identity key, so
 * the same entity referenced twice (perhaps with a richer label the second time) still de-duplicates
 * cleanly in a timeline.</p>
 *
 * <p>Instances are immutable and therefore safe to hand to another thread, queue, or store.</p>
 *
 * @see UserActivityEvent the timeline entry that carries a list of these
 */
public final class EntityRef {

    private final String type;
    private final String id;
    private final Map<String, String> attributes;

    private EntityRef(String type, String id, Map<String, String> attributes) {
        this.type = Objects.requireNonNull(type, "type");
        this.id = Objects.requireNonNull(id, "id");
        // Defensive, insertion-ordered, unmodifiable copy so the ref stays truly immutable.
        this.attributes = attributes == null || attributes.isEmpty()
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    }

    /**
     * Create a bare reference with no descriptive attributes.
     *
     * @param type the kind of entity (e.g. {@code "order"}, {@code "customer"}); required
     * @param id   the entity's identifier as a string (e.g. {@code "4711"}); required
     * @return a new immutable {@link EntityRef}
     */
    public static EntityRef of(String type, String id) {
        return new EntityRef(type, id, null);
    }

    /**
     * Create a reference carrying a single recognition hint — typically a human-readable label.
     *
     * <p><b>Example:</b> {@code EntityRef.of("order", "4711", "label", "Order #4711 — ACME")}.</p>
     *
     * @param type     the kind of entity; required
     * @param id       the entity's identifier; required
     * @param attrKey  the attribute name (e.g. {@code "label"}); required
     * @param attrValue the attribute value; required
     * @return a new immutable {@link EntityRef}
     */
    public static EntityRef of(String type, String id, String attrKey, String attrValue) {
        Map<String, String> single = new LinkedHashMap<>(2);
        single.put(Objects.requireNonNull(attrKey, "attrKey"),
                   Objects.requireNonNull(attrValue, "attrValue"));
        return new EntityRef(type, id, single);
    }

    /**
     * Create a reference with an arbitrary set of recognition hints.
     *
     * @param type       the kind of entity; required
     * @param id         the entity's identifier; required
     * @param attributes descriptive hints (e.g. {@code label}, {@code status}); copied defensively,
     *                   may be {@code null} or empty
     * @return a new immutable {@link EntityRef}
     */
    public static EntityRef of(String type, String id, Map<String, String> attributes) {
        return new EntityRef(type, id, attributes);
    }

    /** @return the kind of entity (e.g. {@code "order"}); never {@code null}. */
    public String type() {
        return type;
    }

    /** @return the entity's identifier as a string (e.g. {@code "4711"}); never {@code null}. */
    public String id() {
        return id;
    }

    /**
     * @return an unmodifiable, insertion-ordered map of recognition hints; never {@code null}
     *         (empty when none were supplied).
     */
    public Map<String, String> attributes() {
        return attributes;
    }

    /**
     * Convenience lookup for a single attribute.
     *
     * @param key the attribute name
     * @return the value, or {@code null} if absent
     */
    public String attribute(String key) {
        return attributes.get(key);
    }

    /**
     * The best human-readable label for this entity: the {@code "label"} attribute if present,
     * otherwise a compact {@code type#id} fallback.
     *
     * <p>This is what a renderer puts into the prompt so the LLM sees "Order #4711 — ACME" rather
     * than a bare id.</p>
     *
     * @return a never-{@code null} display string
     */
    public String label() {
        String label = attributes.get("label");
        return label != null ? label : type + '#' + id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof EntityRef)) {
            return false;
        }
        EntityRef other = (EntityRef) o;
        return type.equals(other.type) && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, id);
    }

    @Override
    public String toString() {
        return "EntityRef[" + type + '#' + id
                + (attributes.isEmpty() ? "" : " " + attributes)
                + ']';
    }
}
