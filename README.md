# TabForge AI

![TabForge AI](docs/images/TabForge_AI_Logo_128_128_transparent.png)

**Dynamic tabs and AI for Jakarta EE applications — without the boilerplate.**

TabForge AI is a Jakarta EE library with two independent modules:

- **DynTabs** — multi-tab PrimeFaces UI with a proper CDI scope per tab
- **EasyAI** — add AI assistants and chatbots to your app in minutes

Both are designed for real Jakarta EE applications: CDI, EJB, PrimeFaces, GlassFish, WildFly, Payara.

---

## DynTabs — Multi-Tab UI for PrimeFaces

### The problem

Building a multi-tab UI in PrimeFaces means managing tab state, lifecycle, navigation, and component IDs manually. `@ViewScoped` doesn't work per-tab. CDI `@Observes` can't route events to individual tab instances. Duplicate tabs, stale data, and ID collisions are constant problems.

### The solution

Define tabs as annotated CDI beans. DynTabs handles everything else.

```java
@Named
@TabScoped                              // one bean instance per open tab
@DynTab(name        = "OrdersDynTab",
        title       = "Orders",
        includePage = "/WEB-INF/include/orders/orders.xhtml")
public class OrdersBean extends BaseDyntabCdiBean {

    @Inject
    private OrderService orderService;

    @Override
    protected void accessPointMethod(Map parameters) {
        // called when the tab opens — load your data here
        orders = orderService.findAll();
    }
}
```

Open the tab from a menu item:

```xml
<p:menuitem value="Orders" action="uishell:Orders"/>
```


That's it. DynTabs opens the tab, creates an isolated `OrdersBean` instance in `@TabScoped`, calls `accessPointMethod()`, and prevents duplicates if the user clicks again.

### What you get

- **`@TabScoped`** — custom CDI scope with one bean instance per open tab. Open the same tab twice and get two completely independent instances.
- **Tab lifecycle** — `accessPointMethod()` on open, `exitPointMethod()` on close
- **Inter-tab messaging** — `sendMessageToAllAppModules(payload)` / `onApplicationMessage()`
- **Workflow pattern** — child tab closes and returns a value to the parent with `closeAndReturnValueToCaller()`
- **Dynamic tabs** — open tabs programmatically with parameters at runtime
- **In-tab navigation** — switch XHTML pages inside a tab without opening a new one
- **Declarative security** — `@DynTab(securedResource=true, allowedRoles={"ADMIN"})`
- **Repeatable `@DynTab`** — one bean can serve as multiple different tabs with different parameters

---

## EasyAI — AI for Jakarta EE Applications

### The problem

Adding AI to a Jakarta EE application with LangChain4J means learning `ChatModel`, `AiServices`, `ToolSpecification`, `EmbeddingStore`, `ContentRetriever`, and more — before writing a single line of business logic. Spring AI has the same problem.

### The solution

Six things cover 95% of use cases:

```java
// 1. Simple chat
Conversation chat = EasyAI.chat()
    .withMemory(20)
    .withSystemMessage("You are a helpful Java tutor.")
    .build();

String answer = chat.send("What is a HashMap?");
```

```java
// 2. Assistant that calls your Java services — mark the callable methods with @EasyTool
@EasyAIAssistant(systemMessage = "You are an e-commerce support bot.")
public interface SupportBot {
    String ask(String question);
}

// OrderService is a plain POJO or @Stateless EJB; @EasyTool opts each method in
class OrderService {
    @EasyTool("Finds an order by its ID")
    public String findOrder(String orderId) { ... }
    // methods without @EasyTool stay invisible to the model
}

SupportBot bot = EasyAI.assistant(SupportBot.class)
    .withTools(orderService, userService)   // opt-in: only @EasyTool methods exposed
    .build();

bot.ask("Where is my order #12345?");
// AI calls orderService.findOrder("12345") automatically
```

```java
// 3. AI that answers from your documents (PDF, DOCX, TXT)
@EasyRAG(source = "classpath:company-policy.pdf")
@EasyAIAssistant(systemMessage = "Answer based on the company policy.")
public interface PolicyBot {
    String ask(String question);
}

PolicyBot bot = EasyAI.assistant(PolicyBot.class).build();
bot.ask("How many vacation days do employees get?");
```

```java
// 4. Autonomous multi-step agent — plans and executes sequences of tool calls
EasyAgent agent = EasyAI.agent()
    .withServices(orderService, paymentService, shippingService)
    .withMaxSteps(10)
    .withPlanningPrompt(true)
    .withStepListener(step -> log.info("Step {}: {} → {}", step.stepNumber(), step.toolName(), step.result()))
    .build();

String result = agent.execute("Process order #42: verify stock, charge the card, and schedule delivery");
// Agent autonomously calls the right services in the right order
```

```java
// 5. Deterministic pipeline — YOU fix the order in Java, the LLM is called only at the edges
FlowContext out = EasyAI.flow()
    .step("understand", ctx -> EasyAI.extract(OrderRequest.class).from(ctx.inputText())) // LLM: text → typed
    .step("checkStock", ctx -> inventory.checkStock(ctx.get("understand", OrderRequest.class)))
    .step("pay",        ctx -> payment.charge(ctx.get("understand", OrderRequest.class)))
    .step("createOrder",ctx -> orders.create(ctx.get("understand", OrderRequest.class)))
    .step("summarize",  ctx -> EasyAI.chat().build()
                                  .send("Tell the user what happened:\n" + ctx.trail()))    // LLM: summary
    .build()
    .run("Order 3 blue watches, ship home.");

String reply = (String) out.result();
// The money/state path runs in the SAME order every time — the model can't reorder or invent steps
```

```java
// 6. Persistent RAG — index documents once into a vector store, query them forever
EasyAI.indexer()
    .toMilvus("localhost", 19530, "company_kb")
    .index("file:/data/handbook.pdf");          // also accepts byte[] from a DMS, DB BLOB, or upload

@EasyAIAssistant(systemMessage = "Answer from the company knowledge base.")
public interface KbBot { String ask(String question); }

KbBot bot = EasyAI.assistant(KbBot.class)
    .withMilvus("localhost", 19530, "company_kb")   // survives restarts, shared across sessions/nodes
    .build();

bot.ask("How many vacation days do employees get?");
// No documents are loaded or embedded at request time — they were indexed once, up front
```

```java
// 7. Structured extraction — turn unstructured text or a document into a typed Java object
record Invoice(String vendor, String invoiceNumber, LocalDate date,
               BigDecimal total, List<LineItem> items) {}

// From an email body, or straight from a PDF's bytes (parsed + extracted in one call)
Invoice inv = EasyAI.extract(Invoice.class)
    .from(DocumentSource.of("invoice.pdf", pdfBytes));

em.persist(inv);   // no AI from here on — it's just a typed object your code already understands
```


### What you get

- **Opt-in tools** — mark methods with `@EasyTool` and pass any POJO or `@Inject`-ed EJB to `.withTools()`. Only annotated methods are callable, so a prompt-injected model can't reach `cancelOrder`/`deleteUser` by accident. No schema, no config. *(Escape hatch `withAllPublicMethodsAsTools()` restores expose-everything for throwaway prototypes.)*
- **EJB proxy support** — `@Stateless`, `@Stateful`, `@Singleton` beans work transparently. Container services (transactions, security, interceptors) are preserved.
- **RAG from any source** — classpath, file path, or `byte[]` from a DMS, database BLOB, REST API, or user upload
- **Persistent vector store** — `EasyAI.indexer().toMilvus(...).index(...)` writes embeddings to Milvus once; `.withMilvus(...)` lets any assistant query them. Survives restarts, shared across sessions and server nodes. Local embedding model included (no API key, runs offline)
- **Structured extraction** — `EasyAI.extract(Invoice.class).from(text or PDF)` returns a populated record/POJO. Parses the document, extracts, retries on malformed output, and optionally runs Jakarta Bean Validation — in one call
- **Autonomous agent** — `EasyAI.agent()` executes multi-step tasks across your services without manual orchestration. Built-in step limit, step listener, and planning prompt.
- **Deterministic flow** — `EasyAI.flow()` runs named steps in the order *you* declare in Java, calling the LLM only at the edges (understand / summarize). For a known business process (check stock → pay → ship) where the order is fixed and correctness beats flexibility: same order every run, unit-testable, injection-safe. The disciplined counterpart to `agent()` — *LLM for language, Java for logic*.
- **Live observability** *(new in 2.1)* — `.withEventListener(...)` streams an `EasyAIEvent` for every moment of an operation (started → tool call → result → finished). Transport-agnostic: log it, meter it, or push it to a live UI. See below.
- **CDI integration** — assistants are injectable with `@Inject`. Tool beans are auto-wired via `tools = {...}` on the annotation.
- **Global config + per-call override** — set API key once with `EasyAI.configure()`, override per assistant if needed
- **Clean error messages** — `EasyAI.extractErrorMessage(e)` parses JSON error responses from OpenAI-compatible providers

### Live observability (new in 2.1)

EasyAI normally works silently and hands you a final answer. Add **one method** — `.withEventListener(listener)` — and it will instead *narrate itself* as it runs: an `EasyAIEvent` for "I started", "I'm calling tool X", "tool X returned", "I'm on document 7 of 200", "I finished". It works on every capability — `EasyAI.chat()`, `EasyAI.assistant()`, `EasyAI.agent()`, `EasyAI.flow()`, `EasyAI.indexer()`, and `EasyAI.extract()`.

```java
EasyAgent agent = EasyAI.agent()
        .withServices(orderService, paymentService)
        .withEventListener(event ->
            log.info("[{}] {} — {}", event.source(), event.phase(), event.title()))
        .build();

agent.execute("Order 2 laptops for U123, charge the card, schedule delivery");
// [AGENT] STARTED — Planning task
// [AGENT] STEP_STARTED — checkStock
// [AGENT] STEP — checkStock
// [AGENT] STEP_STARTED — processPayment
// ...
// [AGENT] FINISHED — Task complete
```

The key design choice: **`EasyAIEvent` knows nothing about HTTP, SSE, WebSockets, or any UI's JSON schema.** It is a plain immutable value (`source`, `phase`, `status`, `title`, `detail`, `toolName`, `sequence`, `timestamp`). *You* decide what to do with it — so the same event stream can feed a log file, a metrics counter, or a real-time dashboard without EasyAI ever depending on any of them.

For the full **"wow"** — every chat turn and agent tool call rendered live in a browser Activity panel — the [**starter**](https://github.com/tabforgeai/tabforge-ai-starter) ships the ~150 lines of (entirely app-side) Server-Sent-Events plumbing that maps `EasyAIEvent` → UI. You never write that mapping into your library code; it stays in your app, exactly where transport belongs.

---

## One chat window for your whole app

A common first question: *"there are six capabilities — does my app need six chat boxes?"* **No.** A real app has **one** chat window (the template's AI panel); the demo shows each capability in its own panel only because it's a **gallery**, not a blueprint.

You usually route nothing yourself — the **model** does it, inside a single assistant. `EasyAI.assistant()` unifies chat + tools + RAG + ambient context behind one `ask()`:

```java
AppAssistant a = EasyAI.assistant(AppAssistant.class)
    .withTools(orderService, inventoryService)   // it can ACT
    .withRAG(policyDocs)                          // it can CITE
    .withActivityContext(ctx)                     // it resolves "this" / "what am I looking at"
    .build();

String reply = a.ask(userMessage);   // one call — the model decides: answer, call a tool, or use RAG
```

`extract()` and `flow()` are triggered by your app **code** (an upload, a button), not chat turns — though the assistant can call a `flow()` as one of its tools. An explicit **orchestrator** is optional: reach for a thin intent-router only when you deliberately keep several *distinct* specialized backends. Most apps never need one. *(Full walkthrough in the EasyAI guide, chapter "One chat window for your whole app".)*

---

## Why Not Spring AI?

If you are building on Jakarta EE, Spring AI is simply the wrong tool — it requires Spring Boot, Spring context, and Spring beans throughout your application. EasyAI is designed for the Jakarta EE runtime you already have.

| | EasyAI | Spring AI |
|---|---|---|
| **Target runtime** | Jakarta EE — CDI, EJB, GlassFish, WildFly, Payara | Spring Boot / Spring context |
| **Tool registration** | Opt-in — mark methods with `@EasyTool` and pass any POJO or EJB to `.withTools()`; only annotated methods are callable | `@Tool` on each method, or a per-method `MethodToolCallback` |
| **EJB bean as tool** | Built-in — `@Stateless`, `@Stateful`, `@Singleton` work as-is, container services preserved | Not supported |
| **AI config** | Single `easyai.properties` file + `EasyAI.configure()` | `application.properties` + Spring bean wiring |
| **RAG from byte[]** | `DocumentSource.of("name.pdf", bytes)` — one line that parses, splits, and embeds | `TikaDocumentReader(ByteArrayResource)` exists, but you assemble the reader → splitter → embedding → store pipeline yourself |
| **Persistent vector store (Milvus)** | Two one-liners — `indexer().toMilvus(...).index(...)` to write, `.withMilvus(...)` to query; local embedding model included | `MilvusVectorStore` bean (needs a `MilvusServiceClient` + `EmbeddingModel`) wired manually, plus the ETL pipeline |
| **Structured extraction** | `EasyAI.extract(T.class).from(text or PDF)` — parses the document, extracts, retries, optional Bean Validation, in one call | `.entity(T.class)` converts text to an object, but document parsing, retries, and validation are on you |
| **CDI injection** | `@Inject SupportBot bot` | `@Autowired` (Spring context only) |
| **Simple chat** | `EasyAI.chat().build()` — one line, no interface needed | `ChatClient` builder + a `ChatModel` bean |
| **Multi-step agent** | `EasyAI.agent()` — built-in, fully managed | Tool calls auto-execute, but no first-class agent (no step cap, typed trace, or planning toggle) |
| **Agent safety limit** | `withMaxSteps(n)` — hard cap on tool calls, returns a final answer when reached | No built-in cap on automatic tool-calling — needs a custom advisor or a manual loop |
| **Agent execution trace** | `withStepListener(step -> ...)` — typed callback with tool name, args, and result per step | Via custom advisors or Micrometer observation — no typed per-step callback out of the box |
| **Agent planning prompt** | `withPlanningPrompt(true)` — instructs the model to plan before acting | Write your own system prompt — no built-in toggle |
| **Deterministic pipeline** | `EasyAI.flow()` — named steps in a fixed order *you* author, LLM only at declared edges; typed context, unit-testable, same order every run | No first-class equivalent — hand-write the orchestration, or let the model drive tool calls (non-deterministic) |

<sub>Comparison reflects Spring AI 2.0.0-M8 (May 2026). Spring AI is a capable, broad framework; the point here is fit for the **Jakarta EE** runtime and the amount of wiring each task takes — not that Spring AI can't do these things. The tool-call cap gap is tracked upstream in [spring-ai#3333](https://github.com/spring-projects/spring-ai/issues/3333).</sub>

EasyAI also works outside Jakarta EE — plain Java, unit tests, standalone apps. Just call `.build()` directly, no container needed.

---

## Requirements

- Java 21+
- Jakarta EE 11+ (CDI 4, EJB 4)
- PrimeFaces 13+ *(DynTabs module only)*
- LangChain4J 1.15.1 *(included transitively)*

---

## Getting Started

**Quickest start:** clone the TabForge AI Starter — a pre-configured Maven WAR project for Eclipse with everything already set up. Open it, deploy, and start writing tab beans immediately.

> [TabForge AI Starter](https://github.com/tabforgeai/tabforge-ai-starter-)

**Add to an existing project:** add the dependency to your `pom.xml` (packaging must be `war`):

```xml
<dependency>
    <groupId>io.github.tabforgeai</groupId>
    <artifactId>tabforge-ai</artifactId>
    <version>3.1.0</version>
</dependency>
```

**Optional RAG & Milvus dependencies.** As of **3.1.0**, the RAG and Milvus integrations
(`langchain4j-easy-rag`, `langchain4j-milvus`) are marked `optional`, so they no longer bloat your
WAR by default. A chat- or tools-only app needs nothing extra. They are loaded lazily (only when you
call the RAG/Milvus features), so a WAR that never uses them never loads them. **If** you use
`withRAG(...)`, `withMilvus(...)` or `extract().from(document)`, add them back explicitly:

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-easy-rag</artifactId>
    <version>1.16.3</version>
</dependency>
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-milvus</artifactId>   <!-- only if you use withMilvus(...) -->
    <version>1.16.3</version>
</dependency>
```

Then follow the setup guides — DynTabs takes 6 steps (faces-config, template include, beans.xml, etc.), EasyAI takes 2 (dependency + `easyai.properties`). Full instructions with copy-paste examples are in the guides below.


---

## Documentation

- [EasyAI Developer Guide](docs/easyai_guide.txt) — full API reference, all use cases, configuration, tips
- [DynTabs Developer Guide](docs/dyntabs_guide.txt) — setup, all use cases, annotations reference, tips
- [JavaDoc](https://tabforgeai.github.io/tabforge-ai/apidocs/index.html) — API reference
- [Demo Application](https://github.com/tabforgeai/tabforge-ai-demo) — working example with DynTabs and EasyAI

---

## License

Apache License 2.0
