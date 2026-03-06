package dyntabs.ai.assistant;

import java.lang.reflect.Method;

import dev.langchain4j.agent.tool.ToolSpecification;

/**
 * Internal record linking a {@link ToolSpecification} to its target object and method.
 */
public record ToolMethod(
        ToolSpecification specification,
        Object targetObject,
        Method method
) {
}
