package com.iaaops.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public final class AgentDtos {
    private AgentDtos() {}
    public record Submit(@NotBlank @Size(max = 4000) String prompt,
            @NotBlank @Pattern(regexp = "[a-zA-Z0-9_-]{1,64}") String requestId, Map<String, Object> context) {
        @JsonAnySetter public void unknown(String name, Object value) { throw new IllegalArgumentException("Unknown field"); }
    }
    public record Decision(@NotNull Boolean approve,
            @NotBlank @Pattern(regexp = "[a-zA-Z0-9_-]{1,64}") String requestId,
            @NotBlank @Size(max = 32) String proposalId) {
        @JsonAnySetter public void unknown(String name, Object value) { throw new IllegalArgumentException("Unknown field"); }
    }
    public record Capabilities(boolean available, String model, String unavailableReason, boolean canExecute,
            String executionMode, int maxSteps) {}
    public record Items<T>(List<T> items) {}
    public record Campaign(String id, String name, String media, String account, String campaign,
            String agency, String product, String operator, BigDecimal bid, BigDecimal dailyBudget,
            int revision, String executionMode) {}
    public record Amounts(BigDecimal bid, BigDecimal dailyBudget) {}
    public record Change(String campaignId, String campaignName, String account, int revision,
            Amounts before, Amounts after) {}
    public record AppliedChange(String campaignId, String campaignName, String account, int revision,
            Amounts before, Amounts after, int newRevision) {}
    public record Proposal(String id, String status, String summary, List<Change> changes,
            OffsetDateTime expiresAt) {}
    public record Receipt(String id, String runId, String proposalId, String executionMode,
            String status, List<AppliedChange> changes, OffsetDateTime createdAt) {}
    public record Event(String id, String tool, String status, String summary, Map<String, Object> arguments,
            Object result, OffsetDateTime createdAt) {}
    public record Run(String id, String prompt, String status, String answer, String error,
            List<Event> events, Proposal proposal, Receipt receipt,
            OffsetDateTime createdAt, OffsetDateTime finishedAt) {}
}
