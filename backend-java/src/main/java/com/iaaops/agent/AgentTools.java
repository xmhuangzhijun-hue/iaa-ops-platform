package com.iaaops.agent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Public tool schemas, not intent routing. The model chooses the next tool. */
final class AgentTools {
    private AgentTools() {}
    static List<Map<String, Object>> definitions() {
        Map<String, Object> filters = object(Map.of("media", strings(), "accounts", strings(),
                "products", strings(), "agencies", strings(), "operators", strings()), List.of());
        Map<String, Object> reports = new LinkedHashMap<>();
        reports.put("view", Map.of("type", "string", "enum", List.of("aggregate", "daily", "raw", "trend", "roi-anomalies")));
        reports.put("date_from", Map.of("type", "string", "description", "ISO date YYYY-MM-DD"));
        reports.put("date_to", Map.of("type", "string", "description", "ISO date YYYY-MM-DD"));
        reports.put("filters", filters);
        reports.put("keyword", Map.of("type", "string", "maxLength", 100,
                "description", "Preserve the page keyword for aggregate/daily/raw observations. Matches report dimensions; not supported by trend/roi-anomalies."));
        reports.put("group_by", strings()); reports.put("metrics", strings());
        reports.put("page", Map.of("type", "integer", "minimum", 1));
        reports.put("page_size", Map.of("type", "integer", "minimum", 1, "maximum", 50));
        reports.put("granularity", Map.of("type", "string", "enum", List.of("day", "hour")));
        reports.put("split_by", Map.of("type", "string"));
        reports.put("min_cost", Map.of("type", "number", "minimum", 0));
        reports.put("roi_below", Map.of("type", "number", "exclusiveMinimum", 0, "maximum", 10));
        Map<String, Object> change = object(Map.of("campaign_id", Map.of("type", "string"),
                "revision", Map.of("type", "integer", "minimum", 1),
                "bid", Map.of("type", "number", "minimum", 0.01, "maximum", 1000),
                "daily_budget", Map.of("type", "number", "minimum", 1, "maximum", 1000000)), List.of("campaign_id", "revision"));
        return List.of(
                tool("query_reports", "Query the authenticated user's visible reports. Use raw for observations, aggregate for exact totals. No tenant/user override. ROI is a ratio (1.1=110%). Maximum 93 days and 50 rows per page.", object(reports, List.of("view", "date_from", "date_to"))),
                tool("query_metrics", "List metric definitions allowed for this user. Do not invent metrics or recompute report ratios by averaging.", object(Map.of(), List.of())),
                tool("list_campaigns", "List visible sandbox campaign configurations and current revisions. No media-platform API is connected.", object(Map.of(), List.of())),
                tool("propose_change", "Propose 1-10 precise sandbox bid/budget changes. Does NOT execute. User must explicitly approve the returned proposal. Include current revision. Only one proposal per run.",
                        object(Map.of("summary", Map.of("type", "string", "maxLength", 2000),
                                "changes", Map.of("type", "array", "minItems", 1, "maxItems", 10, "items", change)), List.of("summary", "changes"))));
    }
    private static Map<String, Object> strings() { return Map.of("type", "array", "items", Map.of("type", "string"), "maxItems", 50); }
    private static Map<String, Object> object(Map<String, Object> properties, List<String> required) {
        return Map.of("type", "object", "properties", properties, "required", required, "additionalProperties", false);
    }
    private static Map<String, Object> tool(String name, String description, Map<String, Object> schema) {
        return Map.of("type", "function", "function", Map.of("name", name, "description", description, "parameters", schema));
    }
}
