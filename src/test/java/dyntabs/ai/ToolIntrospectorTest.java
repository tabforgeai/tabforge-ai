package dyntabs.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import dyntabs.ai.annotation.EasyTool;
import dyntabs.ai.assistant.ToolIntrospector;
import dyntabs.ai.assistant.ToolMethod;

class ToolIntrospectorTest {

    // Dummy POJO tool - no @Tool annotations needed
    public static class OrderService {
        public String findById(String orderId) {
            return "Order #" + orderId;
        }

        public List<String> findByCustomer(String customerName, int limit) {
            return List.of("order1", "order2");
        }

        @EasyTool("Cancels an order and returns confirmation")
        public boolean cancelOrder(String orderId) {
            return true;
        }

        // Should be excluded (private)
        private void internalMethod() {
        }

        // Should be excluded (static)
        public static void staticMethod() {
        }
    }

    @Test
    void discoversPublicMethods() {
        OrderService service = new OrderService();
        List<ToolMethod> tools = ToolIntrospector.introspect(service);

        assertThat(tools).hasSize(3);

        List<String> toolNames = tools.stream()
                .map(tm -> tm.specification().name())
                .toList();

        assertThat(toolNames).containsExactlyInAnyOrder("findById", "findByCustomer", "cancelOrder");
    }

    @Test
    void excludesObjectMethods() {
        OrderService service = new OrderService();
        List<ToolMethod> tools = ToolIntrospector.introspect(service);

        List<String> toolNames = tools.stream()
                .map(tm -> tm.specification().name())
                .toList();

        assertThat(toolNames).doesNotContain("toString", "hashCode", "equals", "getClass");
    }

    @Test
    void usesEasyToolDescriptionWhenPresent() {
        OrderService service = new OrderService();
        List<ToolMethod> tools = ToolIntrospector.introspect(service);

        ToolMethod cancelTool = tools.stream()
                .filter(tm -> tm.specification().name().equals("cancelOrder"))
                .findFirst()
                .orElseThrow();

        assertThat(cancelTool.specification().description()).isEqualTo("Cancels an order and returns confirmation");
    }

    @Test
    void fallsBackToMethodNameAsDescription() {
        OrderService service = new OrderService();
        List<ToolMethod> tools = ToolIntrospector.introspect(service);

        ToolMethod findTool = tools.stream()
                .filter(tm -> tm.specification().name().equals("findById"))
                .findFirst()
                .orElseThrow();

        assertThat(findTool.specification().description()).isEqualTo("findById");
    }

    @Test
    void detectsParametersCorrectly() {
        OrderService service = new OrderService();
        List<ToolMethod> tools = ToolIntrospector.introspect(service);

        ToolMethod findByCustomer = tools.stream()
                .filter(tm -> tm.specification().name().equals("findByCustomer"))
                .findFirst()
                .orElseThrow();

        assertThat(findByCustomer.specification().parameters()).isNotNull();
    }

    @Test
    void introspectMultipleObjects() {
        OrderService orderService = new OrderService();
        UserService userService = new UserService();

        List<ToolMethod> tools = ToolIntrospector.introspect(orderService, userService);

        List<String> toolNames = tools.stream()
                .map(tm -> tm.specification().name())
                .toList();

        assertThat(toolNames).contains("findById", "getUser");
    }

    public static class UserService {
        public String getUser(String userId) {
            return "User " + userId;
        }
    }

    // --- EJB proxy simulation tests ---

    /**
     * Simulates a real @Stateless EJB bean class.
     */
    @jakarta.ejb.Stateless
    public static class InvoiceService {
        public String createInvoice(String customerId, double amount) {
            return "INV-001";
        }

        public boolean deleteInvoice(String invoiceId) {
            return true;
        }
    }

    /**
     * Simulates a CDI/Weld proxy subclass of InvoiceService.
     * In a real container, this would be generated at runtime by the CDI container.
     * The proxy class does NOT redeclare business methods - they live on the superclass.
     */
    public static class InvoiceService$Proxy$_$$_WeldSubclass extends InvoiceService {
        // Proxy classes don't declare business methods.
        // They might have container-internal synthetic methods, but we don't simulate those.
    }

    @Test
    void resolveTargetClassDetectsStatelessProxy() {
        InvoiceService$Proxy$_$$_WeldSubclass proxy = new InvoiceService$Proxy$_$$_WeldSubclass();
        Class<?> resolved = ToolIntrospector.resolveTargetClass(proxy);

        assertThat(resolved).isEqualTo(InvoiceService.class);
    }

    @Test
    void resolveTargetClassReturnsOwnClassForPlainPojo() {
        OrderService pojo = new OrderService();
        Class<?> resolved = ToolIntrospector.resolveTargetClass(pojo);

        assertThat(resolved).isEqualTo(OrderService.class);
    }

    @Test
    void introspectFindsMethodsOnEjbProxy() {
        InvoiceService$Proxy$_$$_WeldSubclass proxy = new InvoiceService$Proxy$_$$_WeldSubclass();
        List<ToolMethod> tools = ToolIntrospector.introspect(proxy);

        List<String> toolNames = tools.stream()
                .map(tm -> tm.specification().name())
                .toList();

        assertThat(toolNames).containsExactlyInAnyOrder("createInvoice", "deleteInvoice");
    }

    @Test
    void ejbProxyToolMethodUsesProxyAsTarget() {
        InvoiceService$Proxy$_$$_WeldSubclass proxy = new InvoiceService$Proxy$_$$_WeldSubclass();
        List<ToolMethod> tools = ToolIntrospector.introspect(proxy);

        // The invocation target must be the proxy (not the unwrapped class),
        // so that calls go through the container pipeline
        for (ToolMethod tm : tools) {
            assertThat(tm.targetObject()).isSameAs(proxy);
        }
    }

    /**
     * Simulates a @Singleton EJB bean.
     */
    @jakarta.ejb.Singleton
    public static class CacheService {
        public String getCachedValue(String key) {
            return "cached-" + key;
        }
    }

    public static class CacheService$Proxy extends CacheService {
    }

    @Test
    void resolveTargetClassDetectsSingletonProxy() {
        CacheService$Proxy proxy = new CacheService$Proxy();
        Class<?> resolved = ToolIntrospector.resolveTargetClass(proxy);

        assertThat(resolved).isEqualTo(CacheService.class);
    }

    /**
     * Simulates a @Stateful EJB bean.
     */
    @jakarta.ejb.Stateful
    public static class CartService {
        public String addItem(String itemId) {
            return "added " + itemId;
        }
    }

    public static class CartService$Proxy extends CartService {
    }

    @Test
    void resolveTargetClassDetectsStatefulProxy() {
        CartService$Proxy proxy = new CartService$Proxy();
        Class<?> resolved = ToolIntrospector.resolveTargetClass(proxy);

        assertThat(resolved).isEqualTo(CartService.class);
    }
}
