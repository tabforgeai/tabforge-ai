package dyntabs.ai;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;

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

    interface ChatBot {
        String chat(String message);
    }

    Conversation(ChatLanguageModel model, String systemMessage, int memorySize) {
        AiServices<ChatBot> serviceBuilder = AiServices.builder(ChatBot.class)
                .chatLanguageModel(model);

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
     * @param message the user's message (plain text)
     * @return the AI's response as a String
     */
    public String send(String message) {
        return chatBot.chat(message);
    }
}
