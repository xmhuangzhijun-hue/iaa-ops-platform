package com.iaaops.agent;

import java.util.List;
import java.util.Map;

/** Provider transport only; tool selection and analysis belong to the actual model. */
public interface AgentModelClient {
    boolean available();
    String model();
    Reply complete(List<Map<String, Object>> messages, List<Map<String, Object>> tools);
    record ToolCall(String id, String name, String arguments) {}
    /** continuation may contain provider reasoning required for the next call; it is never persisted or exposed. */
    record Reply(String content, List<ToolCall> calls, Map<String, Object> continuation) {}
}
