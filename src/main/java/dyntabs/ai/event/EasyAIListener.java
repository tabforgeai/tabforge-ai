package dyntabs.ai.event;

/**
 * A subscriber that receives {@link EasyAIEvent}s as an EasyAI capability runs.
 *
 * <h2>What this interface is for</h2>
 * <p>Register one of these on a builder (e.g. {@code EasyAI.agent().withEventListener(...)}) to
 * watch an operation unfold in real time instead of only seeing its final result. Each time the
 * capability reaches a noteworthy moment — started, a tool call, progress, a result, finished,
 * an error — it calls {@link #onEvent(EasyAIEvent)} with one event.</p>
 *
 * <p><b>Familiar analogy:</b> a newsletter subscriber. You sign up once (register the listener);
 * thereafter every issue (event) is delivered to you as it is published. You don't poll, you don't
 * ask — it arrives. What you do with each issue (read, file, forward) is entirely up to you.</p>
 *
 * <h2>Where it is called in the chain</h2>
 * <pre>
 *   EasyAI.agent().withEventListener(myListener).build()
 *        → agent runs → EventEmitter.emit(...) for each moment
 *        → myListener.onEvent(event)            // your code, called synchronously
 * </pre>
 *
 * <h2>Contract for implementations</h2>
 * <ul>
 *   <li><b>Be quick and non-blocking.</b> The listener is invoked synchronously on the thread
 *       driving the operation; slow work here slows the AI call. Hand off to a queue/SSE channel
 *       if you need to do anything heavy.</li>
 *   <li><b>Don't throw.</b> EasyAI shields itself (see {@link EventEmitter}), but a well-behaved
 *       listener should still swallow its own errors so one bad event never derails a run.</li>
 * </ul>
 *
 * <p>This is a {@link FunctionalInterface}, so it is typically supplied as a lambda:
 * {@code .withEventListener(e -> log.info("{}", e))}.</p>
 *
 * @see EasyAIEvent the value delivered to {@link #onEvent(EasyAIEvent)}
 * @see EventEmitter the producer side that calls listeners
 */
@FunctionalInterface
public interface EasyAIListener {

    /**
     * Called once per emitted event, synchronously, on the thread running the operation.
     *
     * @param event the event that just occurred; never {@code null}
     */
    void onEvent(EasyAIEvent event);
}
