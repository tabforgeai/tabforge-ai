package dyntabs.ai.extract;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Turns a Java type into a human-readable JSON "skeleton" that tells an LLM exactly which
 * fields to produce and of what type.
 *
 * <p><b>Analogy:</b> this is the fill-in-the-blank form generator. Given a class such as
 * {@code Invoice(String vendor, BigDecimal total, List<LineItem> items)} it prints a blank
 * form like:</p>
 * <pre>
 * {
 *   "vendor": "string",
 *   "total": "number",
 *   "items": [ { "description": "string", "amount": "number" } ]
 * }
 * </pre>
 * <p>The model then "fills in the blanks" with values from the source text, and
 * {@link ExtractionEngine} parses the filled form back into a real instance.</p>
 *
 * <p>Supports Java records and plain POJOs (described by their declared fields), nested
 * objects, {@link Collection}s (with their element type), enums (listing the allowed
 * values), the common scalar types, and {@code java.time} types (described as ISO-8601
 * strings). Self-referential types are guarded against infinite recursion.</p>
 *
 * <p>Used only at extraction time by {@link ExtractionEngine}; it never calls a model
 * itself — it is pure reflection over the target class.</p>
 *
 * @see ExtractionEngine
 * @see dyntabs.ai.ExtractionBuilder
 */
public final class SchemaDescriber {

    private static final String INDENT = "  ";

    private SchemaDescriber() {
    }

    /**
     * Produces the JSON skeleton for the given target type.
     *
     * <p>Called once per extraction by {@link ExtractionEngine} to build the portion of the
     * prompt that constrains the model's output shape.</p>
     *
     * @param type the class the caller wants to extract (record, POJO, etc.)
     * @return a pretty-printed JSON skeleton with field names and type hints
     */
    public static String describe(Class<?> type) {
        StringBuilder sb = new StringBuilder();
        appendValue(type, type, sb, 0, new HashSet<>());
        return sb.toString();
    }

    /**
     * Appends the placeholder for a single value (scalar, enum, collection, or nested
     * object) at the given indent depth. Recurses for collections and nested objects.
     *
     * @param raw     the erased class of the value
     * @param generic the generic type (used to discover a collection's element type)
     * @param sb      the buffer being built
     * @param indent  current indentation depth
     * @param visited classes already being described, to break self-reference cycles
     */
    private static void appendValue(Class<?> raw, Type generic, StringBuilder sb,
                                    int indent, Set<Class<?>> visited) {
        if (raw.isEnum()) {
            sb.append('"').append("one of ")
              .append(Arrays.toString(raw.getEnumConstants())).append('"');
        } else if (isScalar(raw)) {
            sb.append('"').append(typeHint(raw)).append('"');
        } else if (Collection.class.isAssignableFrom(raw)) {
            Class<?> element = elementType(generic);
            sb.append("[ ");
            if (element != null) {
                appendValue(element, element, sb, indent, visited);
            } else {
                sb.append("\"...\"");
            }
            sb.append(" ]");
        } else {
            appendObject(raw, sb, indent, visited);
        }
    }

    /**
     * Appends a nested-object skeleton: {@code { "field": <value>, ... }} for every member
     * of a record or POJO.
     *
     * @param type    the structured type to describe
     * @param sb      the buffer being built
     * @param indent  current indentation depth
     * @param visited classes already being described, to break self-reference cycles
     */
    private static void appendObject(Class<?> type, StringBuilder sb,
                                     int indent, Set<Class<?>> visited) {
        if (!visited.add(type)) {
            sb.append("\"(recursive reference)\"");
            return;
        }

        List<Member> members = membersOf(type);
        if (members.isEmpty()) {
            sb.append("{}");
            visited.remove(type);
            return;
        }

        sb.append("{\n");
        for (int i = 0; i < members.size(); i++) {
            Member m = members.get(i);
            sb.append(INDENT.repeat(indent + 1)).append('"').append(m.name).append("\": ");
            appendValue(m.raw, m.generic, sb, indent + 1, visited);
            if (i < members.size() - 1) {
                sb.append(',');
            }
            sb.append('\n');
        }
        sb.append(INDENT.repeat(indent)).append('}');

        visited.remove(type);
    }

    /**
     * Lists the named members of a type: record components for records, otherwise the
     * declared non-static, non-synthetic instance fields for a POJO.
     *
     * @param type the structured type
     * @return the members in declaration order
     */
    private static List<Member> membersOf(Class<?> type) {
        List<Member> members = new ArrayList<>();
        if (type.isRecord()) {
            for (RecordComponent rc : type.getRecordComponents()) {
                members.add(new Member(rc.getName(), rc.getType(), rc.getGenericType()));
            }
        } else {
            for (Field f : type.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.isSynthetic()) {
                    continue;
                }
                members.add(new Member(f.getName(), f.getType(), f.getGenericType()));
            }
        }
        return members;
    }

    /**
     * Resolves the element class of a parameterized {@code Collection<E>}.
     *
     * @param generic the generic type of a collection-typed member
     * @return the element class, or {@code null} if it cannot be determined (raw collection)
     */
    private static Class<?> elementType(Type generic) {
        if (generic instanceof ParameterizedType pt) {
            Type[] args = pt.getActualTypeArguments();
            if (args.length == 1 && args[0] instanceof Class<?> c) {
                return c;
            }
        }
        return null;
    }

    /**
     * Whether a type is a leaf value (printed as a single type-hint string) rather than a
     * structured object to recurse into.
     *
     * @param raw the class to test
     * @return {@code true} for strings, numbers, booleans, dates/times, UUID, etc.
     */
    private static boolean isScalar(Class<?> raw) {
        return raw == String.class || CharSequence.class.isAssignableFrom(raw)
                || raw == char.class || raw == Character.class
                || raw == boolean.class || raw == Boolean.class
                || Number.class.isAssignableFrom(raw)
                || raw.isPrimitive()
                || raw == BigDecimal.class || raw == BigInteger.class
                || raw == UUID.class
                || raw == Date.class
                || raw == LocalDate.class || raw == LocalDateTime.class
                || raw == LocalTime.class || raw == Instant.class
                || raw == OffsetDateTime.class || raw == ZonedDateTime.class;
    }

    /**
     * The human-readable type hint shown to the model for a scalar type.
     *
     * @param raw the scalar class
     * @return a short hint such as {@code "string"}, {@code "number"}, or
     *         {@code "string (ISO-8601 date, e.g. 2026-06-04)"}
     */
    private static String typeHint(Class<?> raw) {
        if (raw == boolean.class || raw == Boolean.class) {
            return "boolean (true or false)";
        }
        if (raw == char.class || raw == Character.class) {
            return "single character";
        }
        if (Number.class.isAssignableFrom(raw) || raw == BigDecimal.class || raw == BigInteger.class
                || (raw.isPrimitive() && raw != boolean.class && raw != char.class)) {
            return "number";
        }
        if (raw == LocalDate.class || raw == Date.class) {
            return "string (ISO-8601 date, e.g. 2026-06-04)";
        }
        if (raw == LocalDateTime.class || raw == Instant.class
                || raw == OffsetDateTime.class || raw == ZonedDateTime.class) {
            return "string (ISO-8601 date-time, e.g. 2026-06-04T13:45:00)";
        }
        if (raw == LocalTime.class) {
            return "string (ISO-8601 time, e.g. 13:45:00)";
        }
        if (raw == UUID.class) {
            return "string (UUID)";
        }
        return "string";
    }

    /**
     * One named member of a structured type (its name, erased class, and generic type).
     */
    private record Member(String name, Class<?> raw, Type generic) {
    }
}
