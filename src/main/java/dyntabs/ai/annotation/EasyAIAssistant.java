package dyntabs.ai.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an interface as an EasyAI assistant.
 *
 * <p>Place this annotation on a Java interface. EasyAI will create an AI-powered
 * implementation of that interface at runtime. The AI model will handle all method calls.</p>
 *
 * <h3>Basic Example</h3>
 * <pre>{@code
 * @EasyAIAssistant(systemMessage = "You are a helpful Java tutor")
 * public interface JavaTutor {
 *     String ask(String question);
 * }
 *
 * JavaTutor tutor = EasyAI.assistant(JavaTutor.class).build();
 * String answer = tutor.ask("What is a Stream in Java?");
 * }</pre>
 *
 * <h3>With Tools (AI Calls Your Java Methods)</h3>
 * <pre>{@code
 * @EasyAIAssistant(systemMessage = "You are an e-commerce support bot")
 * public interface SupportBot {
 *     String ask(String question);
 * }
 *
 * // AI will call methods on orderService and userService automatically
 * SupportBot bot = EasyAI.assistant(SupportBot.class)
 *     .withTools(orderService, userService)
 *     .build();
 * }</pre>
 *
 * <h3>With Jakarta EJB Beans as Tools</h3>
 * <p>EJB beans ({@code @Stateless}, {@code @Stateful}, {@code @Singleton}) injected via
 * {@code @Inject} work as tools out of the box. Transactions, security, and interceptors
 * work normally because calls go through the EJB container proxy.</p>
 * <pre>{@code
 * @Inject OrderService orderService;   // @Stateless EJB proxy
 * @Inject CacheService cacheService;   // @Singleton EJB proxy
 *
 * SupportBot bot = EasyAI.assistant(SupportBot.class)
 *     .withTools(orderService, cacheService)   // just pass injected proxies
 *     .build();
 * }</pre>
 *
 * <h3>Combined With @EasyRAG (Document-Powered)</h3>
 * <pre>{@code
 * @EasyRAG(source = "classpath:employee-handbook.pdf")
 * @EasyAIAssistant(systemMessage = "Answer based on the employee handbook")
 * public interface HRBot {
 *     String ask(String question);
 * }
 * }</pre>
 *
 * <h3>CDI / Jakarta EE (Auto-Injectable, No build() Needed)</h3>
 * <p>In a Jakarta EE container, annotated interfaces are automatically available for injection.
 * Use the {@link #tools()} attribute to declare which CDI/EJB beans should be wired as tools
 * — the container resolves and injects them automatically:</p>
 * <pre>{@code
 * @EasyAIAssistant(
 *     systemMessage = "You are an e-commerce support bot",
 *     tools = {OrderService.class, InventoryService.class}
 * )
 * public interface SupportBot {
 *     String ask(String question);
 * }
 *
 * // In your JSF bean / REST endpoint / EJB:
 * @Inject SupportBot bot;  // tools are auto-wired from the CDI container
 * }</pre>
 *
 * @see dyntabs.ai.EasyAI#assistant(Class)
 * @see dyntabs.ai.AssistantBuilder
 * @see EasyRAG
 * @see EasyTool
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface EasyAIAssistant {

    /**
     * System message that defines the AI assistant's behavior and personality.
     *
     * <p>This tells the AI <i>who it is</i> and <i>how to behave</i>. Examples:</p>
     * <ul>
     *   <li>"You are a helpful customer support agent"</li>
     *   <li>"You are a code reviewer. Point out bugs and suggest improvements."</li>
     *   <li>"You are a translator. Translate all input to English."</li>
     * </ul>
     *
     * <p>Can be overridden at build time via {@code .withSystemMessage("...")}</p>
     *
     * @return the system message, or empty string for none
     */
    String systemMessage() default "";

    /**
     * CDI/EJB bean classes to auto-wire as tools when this assistant is injected via {@code @Inject}.
     *
     * <p>The CDI extension resolves live instances of these classes from the container
     * and registers their public methods as AI tools — equivalent to calling
     * {@code .withTools(orderService, inventoryService)} programmatically.</p>
     *
     * <p>Supported bean types:</p>
     * <ul>
     *   <li>CDI managed beans ({@code @ApplicationScoped}, {@code @RequestScoped}, etc.)</li>
     *   <li>Jakarta EJB beans ({@code @Stateless}, {@code @Stateful}, {@code @Singleton})</li>
     *   <li>Any class with a CDI-managed instance in the container</li>
     * </ul>
     *
     * <p>Example:</p>
     * <pre>{@code
     * @EasyAIAssistant(
     *     systemMessage = "You are a sales assistant",
     *     tools = {OrderService.class, InventoryService.class, PricingService.class}
     * )
     * public interface SalesBot {
     *     String ask(String question);
     * }
     *
     * @Inject SalesBot bot;  // all three services are wired automatically
     * }</pre>
     *
     * <p>Note: this attribute is only used when the assistant is obtained via CDI {@code @Inject}.
     * When building manually with {@code EasyAI.assistant(...).build()}, use
     * {@code .withTools(service1, service2)} on the builder instead.</p>
     *
     * @return tool bean classes to auto-wire, empty by default
     */
    Class<?>[] tools() default {};
}
