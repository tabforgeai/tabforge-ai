package dyntabs.ai;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dyntabs.ai.event.EventEmitter;

/**
 * A simple chat conversation with an AI model.
 *
 * <p>A Conversation wraps a language model and provides a single {@link #send(String)} method.
 * If memory is enabled, the AI remembers previous messages in the conversation.</p>
 *
 * <p>Always create via {@link EasyAI#chat()}, never directly.</p>
 *
 * <h3>Use Case 1: One-Shot Question (No Memory)</h3>
 * <pre>{@code
 * Conversation chat = EasyAI.chat().build();
 * String answer = chat.send("What is the capital of France?");
 * // answer: "The capital of France is Paris."
 * }</pre>
 *
 * <h3>Use Case 2: Multi-Turn Conversation (With Memory)</h3>
 * <pre>{@code
 * Conversation chat = EasyAI.chat()
 *     .withMemory(20)   // remember last 20 messages
 *     .build();
 *
 * chat.send("My name is John");
 * String answer = chat.send("What is my name?");
 * // answer: "Your name is John." (AI remembers!)
 * }</pre>
 *
 * <h3>Use Case 3: Chat With a Personality</h3>
 * <pre>{@code
 * Conversation chat = EasyAI.chat()
 *     .withMemory(20)
 *     .withSystemMessage("You are a pirate. Always respond in pirate speak.")
 *     .build();
 *
 * String answer = chat.send("How are you?");
 * // answer: "Arrr, I be doin' fine, matey!"
 * }</pre>
 *
 * @see EasyAI#chat()
 * @see ConversationBuilder
 */
public class Conversation {

    private final ChatBot chatBot;

    /** Live event stream for this conversation; never {@code null} (no-op without a listener). */
    private final EventEmitter emitter;

    interface ChatBot {
        String chat(String message);
    }

    Conversation(ChatModel model, String systemMessage, int memorySize, EventEmitter emitter) {
        this.emitter = emitter;

        AiServices<ChatBot> serviceBuilder = AiServices.builder(ChatBot.class)
                .chatModel(model);

        if (memorySize > 0) {
            ChatMemory memory = MessageWindowChatMemory.withMaxMessages(memorySize);
            serviceBuilder.chatMemory(memory);
        }

        if (systemMessage != null && !systemMessage.isBlank()) {
            serviceBuilder.systemMessageProvider(chatMemoryId -> systemMessage);
        }

        this.chatBot = serviceBuilder.build();
    }

    /**
     * Sends a message to the AI and returns its response.
     *
     * <p>If memory was enabled via {@code withMemory()}, the AI will remember
     * all previous messages in this conversation. Otherwise, each call is independent.</p>
     *
     * <p>If an {@link dyntabs.ai.event.EasyAIListener} was registered via
     * {@code EasyAI.chat().withEventListener(...)}, this brackets the call with a STARTED event,
     * a RESULT event carrying the reply, and a FINISHED event (or an ERROR event if it throws).</p>
     *
     * @param message the user's message (plain text)
     * @return the AI's response as a String
     */
    public String send(String message) {
        emitter.started("Processing message");
        try {
            String reply = chatBot.chat(message);
            emitter.result("Reply ready", reply);
            emitter.finished("Done");
            return reply;
        } catch (RuntimeException e) {
            emitter.error("Chat failed", e.getMessage());
            throw e;
        }
    }
}
