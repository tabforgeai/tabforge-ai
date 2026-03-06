package dyntabs.ai.cdi;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dyntabs.ai.AssistantBuilder;
import dyntabs.ai.EasyAI;
import dyntabs.ai.annotation.EasyAIAssistant;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.inject.spi.ProcessAnnotatedType;
import jakarta.enterprise.inject.spi.WithAnnotations;

/**
 * CDI Extension that discovers interfaces annotated with {@link EasyAIAssistant}
 * and produces injectable {@code ApplicationScoped} beans for them.
 *
 * <p>Basic usage — just inject:</p>
 * <pre>{@code
 * @Inject SupportBot bot;  // automatically created and ready to use
 * }</pre>
 *
 * <p>With auto-wired tools — declare tool classes in the annotation,
 * the extension resolves their CDI/EJB instances automatically:</p>
 * <pre>{@code
 * @EasyAIAssistant(
 *     systemMessage = "You are a sales bot",
 *     tools = {OrderService.class, InventoryService.class}
 * )
 * public interface SalesBot { String ask(String question); }
 *
 * @Inject SalesBot bot;  // OrderService and InventoryService are wired in
 * }</pre>
 */
public class EasyAIExtension implements Extension {

    private static final Logger log = LoggerFactory.getLogger(EasyAIExtension.class);
    private final Set<Class<?>> assistantInterfaces = new HashSet<>();

    public <T> void processAnnotatedType(
            @Observes @WithAnnotations(EasyAIAssistant.class) ProcessAnnotatedType<T> pat) {

        Class<T> clazz = pat.getAnnotatedType().getJavaClass();
        if (clazz.isInterface()) {
            assistantInterfaces.add(clazz);
            log.info("EasyAIExtension: discovered @EasyAIAssistant interface {}", clazz.getName());
        }
    }

    public void afterBeanDiscovery(@Observes AfterBeanDiscovery abd, BeanManager bm) {
        for (Class<?> iface : assistantInterfaces) {
            addAssistantBean(abd, iface, bm);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> void addAssistantBean(AfterBeanDiscovery abd, Class<T> iface, BeanManager bm) {
        abd.addBean(new Bean<T>() {
            @Override
            public Class<?> getBeanClass() {
                return iface;
            }

            @Override
            public Set<InjectionPoint> getInjectionPoints() {
                return Collections.emptySet();
            }

            @Override
            public T create(CreationalContext<T> ctx) {
                log.info("EasyAIExtension: creating assistant proxy for {}", iface.getName());
                AssistantBuilder<T> builder = EasyAI.assistant(iface);

                // Auto-wire tools declared in @EasyAIAssistant(tools = {...})
                EasyAIAssistant annotation = iface.getAnnotation(EasyAIAssistant.class);
                if (annotation != null && annotation.tools().length > 0) {
                    List<Object> toolInstances = new ArrayList<>();
                    for (Class<?> toolClass : annotation.tools()) {
                        Object instance = resolveBean(bm, toolClass);
                        if (instance != null) {
                            toolInstances.add(instance);
                        }
                    }
                    if (!toolInstances.isEmpty()) {
                        builder.withTools(toolInstances.toArray());
                    }
                }

                return builder.build();
            }

            @Override
            public void destroy(T instance, CreationalContext<T> ctx) {
                ctx.release();
            }

            @Override
            public Set<Type> getTypes() {
                return Set.of(iface, Object.class);
            }

            @Override
            public Set<Annotation> getQualifiers() {
                return Set.of(jakarta.enterprise.inject.Default.Literal.INSTANCE,
                        jakarta.enterprise.inject.Any.Literal.INSTANCE);
            }

            @Override
            public Class<? extends Annotation> getScope() {
                return ApplicationScoped.class;
            }

            @Override
            public String getName() {
                return null;
            }

            @Override
            public Set<Class<? extends Annotation>> getStereotypes() {
                return Collections.emptySet();
            }

            @Override
            public boolean isAlternative() {
                return false;
            }
        });

        log.info("EasyAIExtension: registered CDI bean for {}", iface.getName());
    }

    /**
     * Resolves a live CDI/EJB bean instance from the container by type.
     * Returns null and logs a warning if no bean is found.
     */
    private Object resolveBean(BeanManager bm, Class<?> type) {
        Set<Bean<?>> beans = bm.getBeans(type);
        if (beans.isEmpty()) {
            log.warn("EasyAIExtension: no CDI bean found for tool class {} — skipping", type.getName());
            return null;
        }
        Bean<?> bean = bm.resolve(beans);
        CreationalContext<?> creationalContext = bm.createCreationalContext(bean);
        return bm.getReference(bean, type, creationalContext);
    }
}
