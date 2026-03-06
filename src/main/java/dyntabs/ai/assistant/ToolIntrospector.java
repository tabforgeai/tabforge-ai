package dyntabs.ai.assistant;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dyntabs.ai.annotation.EasyTool;

/**
 * Uses reflection to discover public methods on POJOs and build
 * {@link ToolSpecification} instances without requiring {@code @Tool} annotations.
 */
public final class ToolIntrospector {

    private ToolIntrospector() {
    }

    /** Jakarta EJB annotation class names to detect on the superclass. */
    private static final String[] EJB_ANNOTATIONS = {
        "jakarta.ejb.Stateless",
        "jakarta.ejb.Stateful",
        "jakarta.ejb.Singleton"
    };

    /**
     * Discovers all eligible public methods on the given objects
     * and creates {@link ToolMethod} entries for each.
     *
     * <p>If the object is a Jakarta EJB proxy (CDI/Weld-injected {@code @Stateless},
     * {@code @Stateful}, or {@code @Singleton} bean), the methods are read from
     * the actual bean class (the proxy's superclass) while the proxy instance is
     * kept as the invocation target. This ensures method calls still go through
     * the container pipeline (transactions, security, interceptors).</p>
     */
    public static List<ToolMethod> introspect(Object... toolObjects) {
        List<ToolMethod> result = new ArrayList<>();

        for (Object obj : toolObjects) {
            Class<?> targetClass = resolveTargetClass(obj);

            for (Method method : targetClass.getDeclaredMethods()) {
                if (isEligible(method)) {
                    ToolSpecification spec = buildSpecification(method);
                    // Use the proxy (obj) as invocation target, not the unwrapped class
                    result.add(new ToolMethod(spec, obj, method));
                }
            }
        }

        return result;
    }

    /**
     * Resolves the actual bean class behind a CDI/EJB proxy.
     *
     * <p>CDI containers (Weld, OpenWebBeans, etc.) create proxy subclasses
     * when injecting EJB beans. The proxy class itself has no business methods
     * declared — they live on the superclass (the real bean class). This method
     * detects whether the object is such a proxy by checking if the superclass
     * carries a Jakarta EJB annotation ({@code @Stateless}, {@code @Stateful},
     * or {@code @Singleton}). If so, the superclass is returned for method
     * discovery; otherwise the object's own class is returned.</p>
     *
     * @param obj the tool object (possibly a proxy)
     * @return the class to use for method discovery
     */
    public static Class<?> resolveTargetClass(Object obj) {
        Class<?> clazz = obj.getClass();
        Class<?> superclass = clazz.getSuperclass();

        if (superclass != null && superclass != Object.class) {
            if (hasEjbAnnotation(superclass)) {
                return superclass;
            }
        }

        return clazz;
    }

    private static boolean hasEjbAnnotation(Class<?> clazz) {
        for (Annotation annotation : clazz.getAnnotations()) {
            String annotationName = annotation.annotationType().getName();
            for (String ejbAnnotation : EJB_ANNOTATIONS) {
                if (annotationName.equals(ejbAnnotation)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isEligible(Method method) {
        if (!Modifier.isPublic(method.getModifiers())) return false;
        if (Modifier.isStatic(method.getModifiers())) return false;
        if (method.isSynthetic()) return false;
        if (method.isBridge()) return false;
        // Exclude Object methods
        try {
            Object.class.getMethod(method.getName(), method.getParameterTypes());
            return false;
        } catch (NoSuchMethodException e) {
            return true;
        }
    }

    static ToolSpecification buildSpecification(Method method) {
        ToolSpecification.Builder builder = ToolSpecification.builder()
                .name(method.getName());

        // Use @EasyTool description if present, otherwise use method name
        EasyTool easyTool = method.getAnnotation(EasyTool.class);
        if (easyTool != null) {
            builder.description(easyTool.value());
        } else {
            builder.description(method.getName());
        }

        // Build parameters schema
        Parameter[] params = method.getParameters();
        if (params.length > 0) {
            JsonObjectSchema.Builder schemaBuilder = JsonObjectSchema.builder();
            List<String> requiredParams = new ArrayList<>();

            for (Parameter param : params) {
                String paramName = param.getName();
                Class<?> paramType = param.getType();
                addParameter(schemaBuilder, paramName, paramType);
                requiredParams.add(paramName);
            }

            schemaBuilder.required(requiredParams);
            builder.parameters(schemaBuilder.build());
        }

        return builder.build();
    }

    private static void addParameter(JsonObjectSchema.Builder schema, String name, Class<?> type) {
        if (type == String.class) {
            schema.addStringProperty(name);
        } else if (type == int.class || type == Integer.class
                || type == long.class || type == Long.class) {
            schema.addIntegerProperty(name);
        } else if (type == double.class || type == Double.class
                || type == float.class || type == Float.class) {
            schema.addNumberProperty(name);
        } else if (type == boolean.class || type == Boolean.class) {
            schema.addBooleanProperty(name);
        } else {
            // Fallback: treat as string
            schema.addStringProperty(name);
        }
    }
}
