package dyntabs.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dyntabs.ai.event.EasyAIEvent;
import dyntabs.ai.event.EasyAIEvent.Phase;
import dyntabs.ai.event.EasyAIEvent.Source;
import dyntabs.ai.event.EasyAIEvent.Status;
import dyntabs.ai.flow.FlowContext;
import dyntabs.ai.flow.FlowException;

/**
 * Verifies the promises {@code EasyAI.flow()} makes — and, crucially, does so with <b>no live
 * LLM</b>. Every "LLM edge" here is just a lambda returning a canned value, which is the whole
 * testability argument for {@code flow()}: because the model is only ever called inside a step you
 * wrote, you mock it by writing a different step, and you can assert deterministic invariants
 * ("stock is checked before pay") that a free-running agent loop cannot give you.
 */
class FlowTest {

    // A tiny typed value standing in for what an "understand" LLM step would extract.
    record OrderRequest(String item, int qty) {}

    @Test
    void runsStepsInRegistrationOrderAndThreadsTypedContext() {
        List<String> order = new ArrayList<>();

        FlowContext out = EasyAI.flow()
                .step("understand", ctx -> {
                    order.add("understand");
                    return new OrderRequest("watch", 3);           // stand-in for an LLM extract
                })
                .step("checkStock", ctx -> {
                    order.add("checkStock");
                    OrderRequest req = ctx.get("understand", OrderRequest.class);
                    return req.qty() <= 5 ? "IN_STOCK" : "LOW";
                })
                .step("summarize", ctx -> {
                    order.add("summarize");
                    return "Ordered " + ctx.get("understand", OrderRequest.class).qty()
                            + " (" + ctx.get("checkStock", String.class) + ")";
                })
                .build()
                .run("order 3 watches");

        assertThat(order).containsExactly("understand", "checkStock", "summarize");
        assertThat(out.result()).isEqualTo("Ordered 3 (IN_STOCK)");
        assertThat(out.get("checkStock", String.class)).isEqualTo("IN_STOCK");
        assertThat(out.<OrderRequest>get("understand", OrderRequest.class).item()).isEqualTo("watch");
    }

    @Test
    void inputIsAvailableToEveryStepRawAndTyped() {
        FlowContext out = EasyAI.flow()
                .step("echoText", ctx -> ctx.inputText())
                .step("echoTyped", ctx -> ctx.input(String.class).toUpperCase())
                .build()
                .run("hello");

        assertThat(out.get("echoText", String.class)).isEqualTo("hello");
        assertThat(out.get("echoTyped", String.class)).isEqualTo("HELLO");
    }

    @Test
    void trailRendersNameArrowValuePerStepInOrder() {
        FlowContext out = EasyAI.flow()
                .step("a", ctx -> 1)
                .step("b", ctx -> "two")
                .build()
                .run(null);

        assertThat(out.trail()).isEqualTo("a → 1\nb → two");
    }

    @Test
    void emitsBracketedEventStreamOnTheFlowSource() {
        List<EasyAIEvent> events = new ArrayList<>();

        EasyAI.flow()
                .step("checkStock", ctx -> "IN_STOCK")
                .step("pay", ctx -> "txn-1")
                .withEventListener(events::add)
                .build()
                .run("x");

        assertThat(events).allSatisfy(e -> assertThat(e.source()).isEqualTo(Source.FLOW));
        assertThat(events).extracting(EasyAIEvent::phase).containsExactly(
                Phase.STARTED,
                Phase.STEP_STARTED, Phase.STEP,   // checkStock
                Phase.STEP_STARTED, Phase.STEP,   // pay
                Phase.FINISHED);
        assertThat(events).filteredOn(e -> e.phase() == Phase.STEP)
                .extracting(EasyAIEvent::toolName).containsExactly("checkStock", "pay");
    }

    @Test
    void aFailingStepAbortsWrapsInFlowExceptionNamingItAndEmitsError() {
        List<EasyAIEvent> events = new ArrayList<>();
        List<String> reached = new ArrayList<>();

        assertThatThrownBy(() -> EasyAI.flow()
                .step("checkStock", ctx -> { reached.add("checkStock"); return "IN_STOCK"; })
                .step("pay", ctx -> { throw new IllegalStateException("card declined"); })
                .step("ship", ctx -> { reached.add("ship"); return "SHIPPED"; })
                .withEventListener(events::add)
                .build()
                .run("x"))
                .isInstanceOf(FlowException.class)
                .satisfies(e -> assertThat(((FlowException) e).stepName()).isEqualTo("pay"))
                .hasRootCauseMessage("card declined");

        assertThat(reached).containsExactly("checkStock");          // "ship" never ran
        assertThat(events).extracting(EasyAIEvent::phase)
                .containsExactly(
                        Phase.STARTED,
                        Phase.STEP_STARTED, Phase.STEP,             // checkStock ok
                        Phase.STEP_STARTED, Phase.STEP,             // pay failed (STEP with ERROR)
                        Phase.ERROR);
        assertThat(events).filteredOn(e -> e.status() == Status.ERROR)
                .extracting(EasyAIEvent::phase).contains(Phase.STEP, Phase.ERROR);
    }

    @Test
    void aStepIsTestableInIsolationWithAHandBuiltContext() {
        // The payoff: no flow, no LLM — construct the context a "pay" step would receive and
        // exercise the step directly. This is the unit-test story flow() is built to enable.
        FlowContext ctx = new FlowContext(
                "order 3 watches",
                Map.of("understand", new OrderRequest("watch", 3)));

        // The "pay" step under test, in isolation.
        String txn = payStep(ctx);

        assertThat(txn).isEqualTo("txn-for-3-watch");
    }

    private static String payStep(FlowContext ctx) {
        OrderRequest req = ctx.get("understand", OrderRequest.class);
        return "txn-for-" + req.qty() + "-" + req.item();
    }

    @Test
    void builderRejectsEmptyBlankAndDuplicateAndNullSteps() {
        assertThatThrownBy(() -> EasyAI.flow().build())
                .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> EasyAI.flow().step("  ", ctx -> 1))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> EasyAI.flow().step("x", null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> EasyAI.flow().step("dup", ctx -> 1).step("dup", ctx -> 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate");
    }

    @Test
    void getRejectsUnknownNameAndWrongType() {
        FlowContext ctx = new FlowContext(null, Map.of("understand", new OrderRequest("watch", 3)));

        assertThatThrownBy(() -> ctx.get("nope", String.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nope");

        assertThatThrownBy(() -> ctx.get("understand", String.class))
                .isInstanceOf(ClassCastException.class);

        assertThat(ctx.has("understand")).isTrue();
        assertThat(ctx.has("missing")).isFalse();
    }

    @Test
    void silentWhenNoEventListenerRegistered() {
        assertThatCode(() -> EasyAI.flow().step("a", ctx -> "ok").build().run("x"))
                .doesNotThrowAnyException();
    }

    // ---- stepIf (declarative conditional branch) ----

    @Test
    void stepIfRunsWhenConditionTrue() {
        FlowContext out = EasyAI.flow()
                .step("checkStock", ctx -> false)                     // NOT in stock
                .stepIf("reserve",
                        ctx -> !ctx.get("checkStock", Boolean.class),  // → condition true
                        ctx -> "reserved-from-WH-EU")
                .build()
                .run("x");

        assertThat(out.has("reserve")).isTrue();
        assertThat(out.get("reserve", String.class)).isEqualTo("reserved-from-WH-EU");
    }

    @Test
    void stepIfSkippedWhenConditionFalseStoresNothingAndEmitsWarningRow() {
        List<EasyAIEvent> events = new ArrayList<>();

        FlowContext out = EasyAI.flow()
                .step("checkStock", ctx -> true)                      // in stock
                .stepIf("reserve",
                        ctx -> !ctx.get("checkStock", Boolean.class),  // → condition false → skip
                        ctx -> "reserved-from-WH-EU")
                .step("pay", ctx -> "txn-1")
                .withEventListener(events::add)
                .build()
                .run("x");

        assertThat(out.has("reserve")).isFalse();                     // skipped → no stored result
        assertThat(out.trail()).doesNotContain("reserve");
        // The skipped step surfaces as a single WARNING "skipped" row (no STEP_STARTED spinner).
        assertThat(events).filteredOn(e -> "reserve".equals(e.toolName()))
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.phase()).isEqualTo(Phase.STEP);
                    assertThat(e.status()).isEqualTo(Status.WARNING);
                    assertThat(e.detail()).contains("skipped");
                });
    }

    // ---- orElse (declarative failure recovery) ----

    @Test
    void orElseRunsAlternativeWhenPrimaryThrowsAndStoresItsResult() {
        List<EasyAIEvent> events = new ArrayList<>();

        FlowContext out = EasyAI.flow()
                .step("pay", ctx -> { throw new IllegalStateException("primary gateway down"); })
                    .orElse(ctx -> "txn-via-backup")
                .withEventListener(events::add)
                .build()
                .run("x");

        assertThat(out.get("pay", String.class)).isEqualTo("txn-via-backup");
        // A RETRY row marks the fallback, and the step ultimately resolves SUCCESS (not ERROR).
        assertThat(events).extracting(EasyAIEvent::phase)
                .containsExactly(Phase.STARTED, Phase.STEP_STARTED, Phase.RETRY, Phase.STEP, Phase.FINISHED);
        assertThat(events).filteredOn(e -> e.phase() == Phase.STEP)
                .singleElement().satisfies(e -> assertThat(e.status()).isEqualTo(Status.SUCCESS));
    }

    @Test
    void orElseTriesAlternativesInOrderUntilOneSucceeds() {
        List<String> tried = new ArrayList<>();

        FlowContext out = EasyAI.flow()
                .step("reserve", ctx -> { tried.add("primary"); throw new RuntimeException("no main stock"); })
                    .orElse(ctx -> { tried.add("alt1"); throw new RuntimeException("WH-EU empty"); })
                    .orElse(ctx -> { tried.add("alt2"); return "reserved-from-WH-US"; })
                    .orElse(ctx -> { tried.add("alt3"); return "unreached"; })
                .build()
                .run("x");

        assertThat(tried).containsExactly("primary", "alt1", "alt2");  // stops at first success
        assertThat(out.get("reserve", String.class)).isEqualTo("reserved-from-WH-US");
    }

    @Test
    void whenEveryAttemptThrowsFlowFailsNamingStepWithFirstErrorAsCause() {
        assertThatThrownBy(() -> EasyAI.flow()
                .step("pay", ctx -> { throw new IllegalStateException("primary down"); })
                    .orElse(ctx -> { throw new RuntimeException("backup down too"); })
                .build()
                .run("x"))
                .isInstanceOf(FlowException.class)
                .satisfies(e -> assertThat(((FlowException) e).stepName()).isEqualTo("pay"))
                .hasRootCauseMessage("primary down");                  // FIRST error is preserved as cause
    }

    @Test
    void orElseWithoutAPrecedingStepIsRejected() {
        assertThatThrownBy(() -> EasyAI.flow().orElse(ctx -> "x"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void stepIfRejectsNullCondition() {
        assertThatThrownBy(() -> EasyAI.flow().stepIf("reserve", null, ctx -> "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
