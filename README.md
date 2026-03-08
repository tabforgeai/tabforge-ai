# TabForge AI

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

Adding AI to a Jakarta EE application with LangChain4J means learning `ChatLanguageModel`, `AiServices`, `ToolSpecification`, `EmbeddingStore`, `ContentRetriever`, and more — before writing a single line of business logic. Spring AI has the same problem.

### The solution

Three things cover 90% of use cases:

```java
// 1. Simple chat
Conversation chat = EasyAI.chat()
    .withMemory(20)
    .withSystemMessage("You are a helpful Java tutor.")
    .build();

String answer = chat.send("What is a HashMap?");
```

```java
// 2. Assistant that calls your Java services (no @Tool annotations needed)
@EasyAIAssistant(systemMessage = "You are an e-commerce support bot.")
public interface SupportBot {
    String ask(String question);
}

// OrderService is a plain POJO or @Stateless EJB — no changes needed
SupportBot bot = EasyAI.assistant(SupportBot.class)
    .withTools(orderService, userService)
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


### What you get

- **Zero-annotation tools** — pass any POJO or `@Inject`-ed EJB to `.withTools()`. EasyAI discovers methods via reflection. No `@Tool`, no schema, no config.
- **EJB proxy support** — `@Stateless`, `@Stateful`, `@Singleton` beans work transparently. Container services (transactions, security, interceptors) are preserved.
- **RAG from any source** — classpath, file path, or `byte[]` from a DMS, database BLOB, REST API, or user upload
- **CDI integration** — assistants are injectable with `@Inject`. Tool beans are auto-wired via `tools = {...}` on the annotation.
- **Global config + per-call override** — set API key once with `EasyAI.configure()`, override per assistant if needed
- **Clean error messages** — `EasyAI.extractErrorMessage(e)` parses JSON error responses from OpenAI-compatible providers

---

## Why Not Spring AI?

| | DynTabs / EasyAI | Spring AI |
|---|---|---|
| Target runtime | Jakarta EE (CDI, EJB, GlassFish, WildFly, Payara) | Spring Boot |
| Tool registration | Automatic — pass any POJO | `@Tool` annotation required on every method |
| EJB bean support | Built-in proxy detection | Not applicable |
| Custom CDI scope | `@TabScoped` included | Not applicable |
| AI config | `easyai.properties` + `EasyAI.configure()` | `application.properties` + Spring beans |
| RAG from byte[] | `DocumentSource.of("name.pdf", bytes)` | Custom `DocumentReader` implementation |
| CDI injection | `@Inject SupportBot bot` | `@Autowired` (Spring only) |

EasyAI also works outside Jakarta EE (plain Java, unit tests) — just call `.build()` directly.

---

## Requirements

- Java 21+
- Jakarta EE 11+ (CDI 4, EJB 4)
- PrimeFaces 13+ *(DynTabs module only)*
- LangChain4J 1.0.0-beta2 *(included transitively)*

---

## Getting Started

**Quickest start:** clone the TabForge AI Starter — a pre-configured Maven WAR project for Eclipse with everything already set up. Open it, deploy, and start writing tab beans immediately.

> [TabForge AI Starter](https://github.com/tabforgeai/tabforge-ai-starter-)

**Add to an existing project:** add the dependency to your `pom.xml` (packaging must be `war`):

```xml
<dependency>
    <groupId>io.github.tabforgeai</groupId>
    <artifactId>tabforge-ai</artifactId>
    <version>1.0.0</version>
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
