package dyntabs.ai.activity;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.enterprise.util.Nonbinding;
import jakarta.interceptor.InterceptorBinding;

/**
 * Marks a business method (or a whole bean) whose invocation should be recorded onto the ambient
 * activity timeline — the <em>declarative</em> way to feed the assistant's working memory without
 * writing any capture code by hand.
 *
 * <h2>What this annotation is for</h2>
 * <p>Put {@code @ActivityTracked} on a service or backing-bean method, and every successful call to it
 * becomes one {@link UserActivityEvent} in the store. You describe, in the annotation, <em>what</em> the
 * call means: its {@link #type() category}, a {@link #verb() verb}, which method parameters identify the
 * business {@link #entityType() entity} it touched ({@link #entityIdParams()}), and whether a textual
 * argument should be captured ({@link #includeText()}). The {@link ActivityTrackedInterceptor} reads all
 * of this at call time and hands a finished event to the {@link ActivityRecorder}.</p>
 *
 * <p><b>Familiar analogy:</b> a label you stick on a desk drawer that says "log every time this is
 * opened, and note which file was pulled". You attach the label once (annotate the method); thereafter
 * the logging happens by itself, with exactly the detail the label asked for.</p>
 *
 * <h2>How it is used</h2>
 * <pre>{@code
 * @Stateless
 * public class OrderService {
 *
 *     @ActivityTracked(type = Type.BUSINESS_ACTION, verb = "approve",
 *                      entityType = "order", entityIdParams = "orderId")
 *     public void approve(String orderId) { ... }
 *
 *     @ActivityTracked(type = Type.SEARCH, includeText = true)
 *     public List<Order> search(String query) { ... }
 * }
 * }</pre>
 *
 * <h2>Notes</h2>
 * <ul>
 *   <li>All members are {@link Nonbinding} — they configure the recorded event but do not participate
 *       in interceptor binding resolution, so any combination of values is matched by the single
 *       {@link ActivityTrackedInterceptor}.</li>
 *   <li>Placed on a {@link ElementType#TYPE type}, it applies to all of that bean's business methods.</li>
 *   <li>The interceptor records <em>after</em> the method returns successfully; a method that throws
 *       records nothing.</li>
 *   <li>For tabs, the declarative shortcut {@code @DynTab(trackActivity = true)} is the navigation-flavoured
 *       equivalent of annotating the tab's entry method with {@code @ActivityTracked(type = NAVIGATION)}.</li>
 * </ul>
 *
 * @see ActivityTrackedInterceptor the interceptor that reads this annotation and records the event
 * @see ActivityRecorder the facade the interceptor records through
 * @see UserActivityEvent the value produced for each tracked call
 */
@InterceptorBinding
@Inherited
@Target({METHOD, TYPE})
@Retention(RUNTIME)
public @interface ActivityTracked {

    /**
     * The coarse category recorded for the call.
     *
     * @return the {@link UserActivityEvent.Type}; defaults to {@link UserActivityEvent.Type#BUSINESS_ACTION}
     */
    @Nonbinding
    UserActivityEvent.Type type() default UserActivityEvent.Type.BUSINESS_ACTION;

    /**
     * The specific act in your domain's vocabulary (e.g. {@code "approve"}, {@code "cancel"}).
     *
     * <p>When left blank, the interceptor falls back to the method's own name — so an explicit verb
     * always wins, and you get a sensible default for free.</p>
     *
     * @return the verb, or an empty string to derive it from the method name
     */
    @Nonbinding
    String verb() default "";

    /**
     * The {@link EntityRef#type() entity type} to tag the recorded event with (e.g. {@code "order"}).
     *
     * <p>When blank, the interceptor falls back to the simple name of the method's declaring class.</p>
     *
     * @return the entity type, or an empty string to derive it from the declaring class
     */
    @Nonbinding
    String entityType() default "";

    /**
     * Names of the method parameters that carry the touched entity's id(s).
     *
     * <p>For each named parameter present, the interceptor builds an {@link EntityRef} of
     * {@link #entityType()} whose id is the argument's string value. These refs are the raw material the
     * assistant uses to resolve "this"/"that". Requires the module to be compiled with {@code -parameters}
     * (which this project is) so parameter names are available at runtime.</p>
     *
     * @return parameter names to read entity ids from; empty means "record no entity"
     */
    @Nonbinding
    String[] entityIdParams() default {};

    /**
     * Whether to capture a textual argument as the event's {@link UserActivityEvent#text() text}
     * (e.g. a search query or a note body).
     *
     * <p>When {@code true}, the interceptor uses the first {@code String} parameter that is not already
     * consumed as an entity id.</p>
     *
     * @return {@code true} to capture authored text; defaults to {@code false}
     */
    @Nonbinding
    boolean includeText() default false;
}
