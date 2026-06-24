package dyntabs.ai.activity;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

/**
 * The interceptor that turns an {@link ActivityTracked} method call into a recorded
 * {@link UserActivityEvent} — the runtime engine behind the declarative annotation.
 *
 * <h2>What this class is for</h2>
 * <p>CDI invokes this interceptor around every method (or every method of a bean) annotated with
 * {@link ActivityTracked}. It lets the real method run, and if it returns successfully, reads the
 * annotation's configuration plus the call's arguments to assemble one activity event, then hands it to
 * the {@link ActivityRecorder}. The developer thus gets automatic, structured capture purely by
 * annotating — no boilerplate inside the business method.</p>
 *
 * <p><b>Familiar analogy:</b> a court stenographer sitting beside the witness. The witness (your method)
 * just does their job; the stenographer quietly writes down, in a fixed format, who said what about which
 * case — but only once the statement is actually completed (the method returns), never for a sentence cut
 * off by an objection (an exception).</p>
 *
 * <h2>Where it sits and when it runs</h2>
 * <p>Enabled globally via {@link Priority} (no {@code beans.xml} entry needed). It runs at
 * {@link #aroundInvoke(InvocationContext)} time, after any container interceptors of lower priority, and
 * records only on the success path. Capture failures are swallowed and logged, never propagated — the
 * user's action must never break because activity logging hiccuped.</p>
 *
 * @see ActivityTracked the binding annotation that activates this interceptor
 * @see ActivityRecorder where the assembled event is sent
 */
@Interceptor
@ActivityTracked
@Priority(Interceptor.Priority.APPLICATION + 100)
public class ActivityTrackedInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ActivityTrackedInterceptor.class);

    @Inject
    ActivityRecorder recorder;

    /**
     * Wraps the tracked call: proceed first, then record the activity on success.
     *
     * <p>Invoked by the CDI container for every {@code @ActivityTracked} method. The original return
     * value is passed straight back to the caller; recording is a side effect whose own errors are
     * contained here. The event produced is consumed downstream by {@link ActivityRecorder} and the
     * {@link ActivityStore}.</p>
     *
     * @param ctx the container-supplied invocation context (method, arguments, target)
     * @return whatever the intercepted method returns
     * @throws Exception only if the intercepted method itself throws (re-propagated unchanged)
     */
    @AroundInvoke
    public Object aroundInvoke(InvocationContext ctx) throws Exception {
        Object result = ctx.proceed();
        try {
            capture(ctx);
        } catch (RuntimeException e) {
            log.warn("@ActivityTracked capture failed for {}: {}",
                    ctx.getMethod(), e.getMessage());
        }
        return result;
    }

    /** Read the annotation + arguments and record one event. */
    private void capture(InvocationContext ctx) {
        Method method = ctx.getMethod();

        // Method-level annotation wins; otherwise fall back to the type-level one.
        ActivityTracked ann = method.getAnnotation(ActivityTracked.class);
        if (ann == null) {
            ann = method.getDeclaringClass().getAnnotation(ActivityTracked.class);
        }
        if (ann == null) {
            return; // defensive: the interceptor only fires when the binding is present
        }

        String verb = ann.verb().isBlank() ? method.getName() : ann.verb();
        String entityType = ann.entityType().isBlank()
                ? method.getDeclaringClass().getSimpleName()
                : ann.entityType();

        Parameter[] params = method.getParameters();
        Object[] args = ctx.getParameters();

        List<EntityRef> entities = extractEntities(ann, entityType, params, args);
        String text = ann.includeText() ? extractText(ann, params, args) : null;

        recorder.record(UserActivityEvent.builder(ann.type())
                .verb(verb)
                .entities(entities)
                .text(text)
                .build());
    }

    /** Build an {@link EntityRef} for each configured id-parameter that is present and non-null. */
    private List<EntityRef> extractEntities(ActivityTracked ann, String entityType,
                                            Parameter[] params, Object[] args) {
        List<EntityRef> entities = new ArrayList<>();
        for (String idParam : ann.entityIdParams()) {
            for (int i = 0; i < params.length; i++) {
                if (params[i].getName().equals(idParam) && args[i] != null) {
                    entities.add(EntityRef.of(entityType, String.valueOf(args[i])));
                    break;
                }
            }
        }
        return entities;
    }

    /** Use the first String argument that is not already consumed as an entity id. */
    private String extractText(ActivityTracked ann, Parameter[] params, Object[] args) {
        List<String> idParams = List.of(ann.entityIdParams());
        for (int i = 0; i < params.length; i++) {
            if (args[i] instanceof String && !idParams.contains(params[i].getName())) {
                return (String) args[i];
            }
        }
        return null;
    }
}
