package com.iaaops.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** Real JDK HTTP transport against an ephemeral loopback server; no model account or real credentials. */
class OpenAiAgentModelClientTest {
    private final ObjectMapper json = new ObjectMapper();
    private HttpServer server;
    @AfterEach void stop() { if (server != null) server.stop(0); }

    @Test void invokesCompatibleApiAndRetainsOnlyInternalContinuationReasoning() throws Exception {
        AtomicReference<Map<String, Object>> sent = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer fictional-test-token");
            sent.set(json.readValue(exchange.getRequestBody().readAllBytes(), Map.class));
            byte[] payload = json.writeValueAsBytes(Map.of("choices", List.of(Map.of("message", Map.of(
                    "role", "assistant", "content", "", "reasoning_content", "internal-only",
                    "tool_calls", List.of(Map.of("id", "call1", "type", "function", "function",
                            Map.of("name", "query_metrics", "arguments", "{}"))))))));
            exchange.sendResponseHeaders(200, payload.length); exchange.getResponseBody().write(payload); exchange.close();
        });
        server.start();
        AgentModelClient.Reply reply = client().complete(List.of(Map.of("role", "user", "content", "读取指标定义")), AgentTools.definitions());
        assertThat(sent.get()).containsEntry("model", "fictional-test-model")
                .containsEntry("thinking", Map.of("type", "disabled"));
        assertThat(reply.content()).isEmpty();
        assertThat(reply.calls()).containsExactly(new AgentModelClient.ToolCall("call1", "query_metrics", "{}"));
        assertThat(reply.continuation()).containsEntry("reasoning_content", "internal-only");
    }

    @Test void providerErrorsDoNotLeakResponseBodyOrCredentials() throws Exception {
        serve(401, "provider-debug-body-that-must-not-be-exposed");
        assertThatThrownBy(() -> client().complete(List.of(), AgentTools.definitions()))
                .isInstanceOf(IllegalStateException.class).hasMessage("Agent provider request failed").hasNoCause();
    }

    @Test void rejectsOversizedProviderOutputWithoutParsingIt() throws Exception {
        serve(200, "x".repeat(140000));
        assertThatThrownBy(() -> client().complete(List.of(), AgentTools.definitions()))
                .isInstanceOf(IllegalStateException.class).hasMessage("Agent provider request failed").hasNoCause();
    }

    @Test void unavailableConfigurationCannotReturnFakeAnalysis() {
        OpenAiAgentModelClient client = new OpenAiAgentModelClient(json, "", "deepseek-chat", "");
        assertThat(client.available()).isFalse(); assertThat(client.model()).isNull();
        assertThatThrownBy(() -> client.complete(List.of(), List.of())).hasMessage("Agent provider unavailable");
    }

    @Test void refusesCleartextRemoteEndpointsBeforeSendingAuthorization() {
        OpenAiAgentModelClient client = new OpenAiAgentModelClient(json, "http://example.invalid/v1", "model", "fictional-test-token");
        assertThatThrownBy(() -> client.complete(List.of(), List.of())).hasMessage("Agent provider request failed").hasNoCause();
    }

    @Test void malformedSuccessfulResponseFailsClosed() throws Exception {
        serve(200, "{\"choices\":[]}");
        assertThatThrownBy(() -> client().complete(List.of(), List.of())).hasMessage("Agent provider request failed").hasNoCause();
    }

    private OpenAiAgentModelClient client() {
        return new OpenAiAgentModelClient(json, "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                "fictional-test-model", "fictional-test-token");
    }
    private void serve(int status, String body) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
    }
}
