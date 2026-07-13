package dyntabs.ai.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a tool the AI is allowed to call, and (optionally) describes it.
 *
 * <p><b>This annotation is the gate.</b> Since 3.0.0, EasyAI exposes tools on an
 * <b>opt-in</b> basis: when you pass a service to {@code withTools(...)}, <i>only</i> the
 * methods you annotate with {@code @EasyTool} are visible to the model. Everything else —
 * including state-mutating methods like {@code cancelOrder}, {@code processPayment}, or
 * {@code deleteUser} — stays invisible and uncallable.</p>
 *
 * <p><b>Why the flip (the "confused deputy" problem):</b> a tool channel is reachable by the
 * model, whose input is not fully trusted (think prompt injection). If every public method
 * were exposed, a manipulated model could invoke anything on the beans you handed it, acting
 * with your application's authority but off your script. Opt-in means <i>adding a method is
 * safe by default</i> — it does nothing until you deliberately annotate it. Think of it like
 * a restaurant menu: the kitchen can cook many things, but guests may only order what's
 * printed on the menu. {@code @EasyTool} is what puts a method on the menu.</p>
 *
 * <h3>Exposing a method (description optional)</h3>
 * <pre>{@code
 * public class OrderService {
 *
 *     // On the menu. No description -> the AI sees tool name="findOrder", description="findOrder".
 *     @EasyTool
 *     public String findOrder(String orderId) {
 *         return orderRepo.findById(orderId).toString();
 *     }
 *
 *     // On the menu, with a description that helps the AI decide WHEN to call it.
 *     @EasyTool("Cancels an active order. Only works for orders not yet shipped.")
 *     public String cancelOrder(String orderId) { ... }
 *
 *     // NOT annotated -> invisible to the model. Cannot be invoked, ever.
 *     public void deleteAllOrders() { ... }
 * }
 * }</pre>
 *
 * <h3>Escape hatch (prototypes only)</h3>
 * <p>If you truly want the old "expose every public method" behavior — e.g. a throwaway
 * prototype where nothing is sensitive — use the deliberately verbose
 * {@link dyntabs.ai.AssistantBuilder#withAllPublicMethodsAsTools(Object...)} instead of
 * {@code withTools(...)}. Its name is meant to make the risk obvious at the call site.</p>
 *
 * <h3>Tips for good descriptions</h3>
 * <ul>
 *   <li>Explain <i>what</i> it does and <i>when</i> to use it.</li>
 *   <li>Add one when the method name alone is ambiguous, or the AI keeps picking the wrong method.</li>
 *   <li>Especially useful to disambiguate several similar methods.</li>
 * </ul>
 *
 * @see dyntabs.ai.assistant.ToolIntrospector
 * @see dyntabs.ai.AssistantBuilder#withTools(Object...)
 * @see dyntabs.ai.AssistantBuilder#withAllPublicMethodsAsTools(Object...)
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface EasyTool {

    /**
     * An optional human-readable description of what the tool method does.
     * When present, it is sent to the AI model to help it decide when to call this method;
     * when omitted (blank), the method name is used as the description.
     *
     * <p>Good descriptions explain <i>what</i> the method does and <i>when</i> to use it:</p>
     * <ul>
     *   <li>"Finds an order by its ID and returns order details including status and items"</li>
     *   <li>"Cancels an active order. Only works for orders that have not been shipped yet."</li>
     *   <li>"Returns the current weather for a given city name"</li>
     * </ul>
     *
     * @return the tool description, or an empty string to fall back to the method name
     */
    String value() default "";
}
