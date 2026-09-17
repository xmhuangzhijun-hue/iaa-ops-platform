package com.iaaops.agent;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class OpenAiAgentModelClient implements AgentModelClient {
    private static final int MAX_RESPONSE_BYTES = 131072;
    private final ObjectMapper json;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final String baseUrl;
    private final String model;
    private final String apiKey;

    public OpenAiAgentModelClient(ObjectMapper json,
            @Value("${app.agent.base-url:}") String baseUrl,
            @Value("${app.agent.model:deepseek-chat}") String model,
            @Value("${app.agent.api-key:}") String apiKey) {
        this.json = json; this.baseUrl = baseUrl; this.model = model; this.apiKey = apiKey;
    }
    @Override public boolean available() { return !baseUrl.isBlank() && !model.isBlank() && !apiKey.isBlank(); }
    @Override public String model() { return available() ? model : null; }

    @Override @SuppressWarnings("unchecked")
    public Reply complete(List<Map<String, Object>> messages, List<Map<String, Object>> tools) {
        if (!available()) throw new IllegalStateException("Agent provider unavailable");
        try {
            String endpoint = baseUrl.replaceAll("/+$", "");
            if (!endpoint.endsWith("/chat/completions")) endpoint += "/chat/completions";
            URI uri = URI.create(endpoint);
            boolean local = List.of("127.0.0.1", "localhost", "::1").contains(uri.getHost());
            if (!("https".equals(uri.getScheme()) || local && "http".equals(uri.getScheme()))
                    || uri.getUserInfo() != null || uri.getQuery() != null) throw new IllegalArgumentException();
            Map<String, Object> payload = Map.of("model", model, "messages", messages, "tools", tools,
                    "temperature", 0.2, "max_tokens", 3000, "thinking", Map.of("type", "disabled"));
            String serialized = json.writeValueAsString(payload);
            if (serialized.length() > 180000) throw new IllegalArgumentException();
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(45))
                    .header("Content-Type", "application/json").header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(serialized)).build();
            HttpResponse<byte[]> response = client.sendAsync(request, info -> new LimitedBody())
                    .orTimeout(45, TimeUnit.SECONDS).join();
            if (response.statusCode() != 200) throw new IllegalStateException();
            Map<String, Object> value = json.readValue(new String(response.body(), StandardCharsets.UTF_8), Map.class);
            Map<String, Object> choice = (Map<String, Object>) ((List<?>) value.get("choices")).getFirst();
            Map<String, Object> message = (Map<String, Object>) choice.get("message");
            List<ToolCall> calls = new ArrayList<>();
            Object rawCalls = message.get("tool_calls");
            if (rawCalls instanceof List<?> list) {
                if (list.size() > 4) throw new IllegalArgumentException();
                for (Object raw : list) {
                    Map<String, Object> call = (Map<String, Object>) raw;
                    Map<String, Object> fn = (Map<String, Object>) call.get("function");
                    calls.add(new ToolCall((String) call.get("id"), (String) fn.get("name"), (String) fn.get("arguments")));
                }
            }
            String content = message.get("content") instanceof String text ? text : "";
            if (content.length() > 12000) throw new IllegalArgumentException();
            return new Reply(content, calls, message);
        } catch (RuntimeException exception) {
            // Never propagate provider body, URL, credential, prompt or exception cause to logs/UI.
            throw new IllegalStateException("Agent provider request failed");
        }
    }

    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        @Override public CompletionStage<byte[]> getBody() { return result; }
        @Override public void onSubscribe(Flow.Subscription value) { subscription = value; value.request(1); }
        @Override public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if (bytes.size() + buffer.remaining() > MAX_RESPONSE_BYTES) {
                    subscription.cancel(); result.completeExceptionally(new IllegalStateException("Response limit")); return;
                }
                byte[] part = new byte[buffer.remaining()]; buffer.get(part); bytes.writeBytes(part);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable error) { result.completeExceptionally(new IllegalStateException("Transport failure")); }
        @Override public void onComplete() { result.complete(bytes.toByteArray()); }
    }
}
