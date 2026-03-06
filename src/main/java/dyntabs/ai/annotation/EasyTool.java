package dyntabs.ai.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Optional annotation that adds a description to a tool method.
 *
 * <p><b>Important:</b> This annotation is NOT required. EasyAI automatically discovers
 * all public methods on objects passed to {@code withTools()}. This annotation simply
 * provides a better description that helps the AI understand what the method does.</p>
 *
 * <h3>Without @EasyTool (works fine)</h3>
 * <pre>{@code
 * public class OrderService {
 *     // AI sees this as: tool name="findOrder", description="findOrder"
 *     public String findOrder(String orderId) {
 *         return orderRepo.findById(orderId).toString();
 *     }
 * }
 * }</pre>
 *
 * <h3>With @EasyTool (better AI understanding)</h3>
 * <pre>{@code
 * public class OrderService {
 *     // AI sees this as: tool name="findOrder",
 *     //   description="Finds an order by its ID and returns order details"
 *     @EasyTool("Finds an order by its ID and returns order details")
 *     public String findOrder(String orderId) {
 *         return orderRepo.findById(orderId).toString();
 *     }
 * }
 * }</pre>
 *
 * <h3>When to Use @EasyTool</h3>
 * <ul>
 *   <li>When the method name alone is not descriptive enough</li>
 *   <li>When the AI keeps picking the wrong method</li>
 *   <li>When you have multiple similar methods and the AI needs to distinguish them</li>
 * </ul>
 *
 * @see dyntabs.ai.assistant.ToolIntrospector
 * @see dyntabs.ai.AssistantBuilder#withTools(Object...)
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface EasyTool {

    /**
     * A human-readable description of what the tool method does.
     * This description is sent to the AI model to help it decide when to call this method.
     *
     * <p>Good descriptions explain <i>what</i> the method does and <i>when</i> to use it:</p>
     * <ul>
     *   <li>"Finds an order by its ID and returns order details including status and items"</li>
     *   <li>"Cancels an active order. Only works for orders that have not been shipped yet."</li>
     *   <li>"Returns the current weather for a given city name"</li>
     * </ul>
     *
     * @return the tool description
     */
    String value();
}
