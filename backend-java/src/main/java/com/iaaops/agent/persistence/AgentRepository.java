package com.iaaops.agent.persistence;

import com.iaaops.agent.AgentDtos;
import com.iaaops.agent.AgentDtos.*;
import com.iaaops.iam.domain.CurrentUser;
import com.iaaops.shared.error.ApiException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

/** Agent-owned state only. Facts are queried through Reporting's public application service. */
@Repository
public class AgentRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public AgentRepository(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }
    public record StoredRun(String id, String tenantId, String ownerId, String requestId, String requestHash,
            String fingerprint, String prompt, Map<String, Object> context, String status) {}

    public List<Campaign> campaigns(CurrentUser user) {
        List<Object> params = new ArrayList<>(List.of(user.tenantId()));
        StringBuilder where = new StringBuilder("c.tenant_id = ?");
        scope(where, params, "m.agency", user.dataScope().agencies());
        scope(where, params, "m.product", user.dataScope().products());
        scope(where, params, "m.operator", user.dataScope().operators());
        return jdbc.query("""
                select c.*, m.agency, m.product, m.operator from agent_campaigns c
                left join account_mappings m on m.tenant_id=c.tenant_id and m.media=c.media and m.account=c.account
                where
                """ + where + " order by c.id limit 201", (rs, index) -> new Campaign(
                rs.getString("id"), rs.getString("name"), rs.getString("media"), rs.getString("account"),
                rs.getString("campaign"), rs.getString("agency"), rs.getString("product"), rs.getString("operator"),
                rs.getBigDecimal("bid"), rs.getBigDecimal("daily_budget"), rs.getInt("revision"), "sandbox"), params.toArray());
    }

    private static void scope(StringBuilder where, List<Object> params, String column, List<String> values) {
        if (values == null) return;
        if (values.isEmpty()) { where.append(" and false"); return; }
        where.append(" and ").append(column).append(" in (")
                .append(String.join(",", java.util.Collections.nCopies(values.size(), "?"))).append(')');
        params.addAll(values);
    }

    /** Mapping changes can revoke access without altering the user row; include them in history authorization. */
    public void lockMappings(String tenant) {
        // Lock existing mapping rows before authorization and retain locks through the tool/approval
        // transaction. Concurrent updates/deletes cannot revoke a checked mapping before execution.
        jdbc.queryForList("select media,account from account_mappings where tenant_id=? order by media,account for share", tenant);
    }

    public List<Map<String, Object>> mappingScope(String tenant) {
        return jdbc.queryForList("""
                select media,account,agency,product,operator,revision from account_mappings
                where tenant_id=? order by media,account
                """, tenant);
    }

    public StoredRun byRequest(CurrentUser user, String requestId) {
        List<StoredRun> rows = jdbc.query("select * from agent_runs where tenant_id=? and owner_id=? and request_id=?",
                this::stored, user.tenantId(), user.id(), requestId);
        return rows.isEmpty() ? null : rows.getFirst();
    }
    public StoredRun get(String id, boolean lock) {
        List<StoredRun> rows = jdbc.query("select * from agent_runs where id=?" + (lock ? " for update" : ""), this::stored, id);
        if (rows.isEmpty()) throw ApiException.notFound("任务不存在或当前不可访问");
        return rows.getFirst();
    }
    @SuppressWarnings("unchecked")
    private StoredRun stored(ResultSet rs, int index) throws SQLException {
        return new StoredRun(rs.getString("id"), rs.getString("tenant_id"), rs.getString("owner_id"),
                rs.getString("request_id"), rs.getString("request_hash"), rs.getString("scope_fingerprint"),
                rs.getString("prompt"), json.readValue(rs.getString("context"), Map.class), rs.getString("status"));
    }
    public void create(String id, CurrentUser user, AgentDtos.Submit request, String hash, String fingerprint,
            Map<String, Object> context) {
        jdbc.update("""
                insert into agent_runs(id,tenant_id,owner_id,request_id,request_hash,scope_fingerprint,prompt,context,status)
                values (?,?,?,?,?,?,?,cast(? as jsonb),'running')
                """, id, user.tenantId(), user.id(), request.requestId(), hash, fingerprint, request.prompt(), json.writeValueAsString(context));
    }
    public List<StoredRun> list(CurrentUser user) {
        return jdbc.query("select * from agent_runs where tenant_id=? and owner_id=? order by created_at desc limit 30",
                this::stored, user.tenantId(), user.id());
    }
    public Run view(String id) {
        return jdbc.queryForObject("select * from agent_runs where id=?", (rs, index) -> new Run(id,
                rs.getString("prompt"), rs.getString("status"), rs.getString("answer"), rs.getString("error"),
                Arrays.asList(json.readValue(rs.getString("events"), Event[].class)), proposal(id), receipt(id),
                rs.getObject("created_at", OffsetDateTime.class), rs.getObject("finished_at", OffsetDateTime.class)), id);
    }
    public void append(String runId, Event event) {
        jdbc.update("update agent_runs set events=events || cast(? as jsonb) where id=? and status='running'",
                json.writeValueAsString(List.of(event)), runId);
    }
    public void finish(String runId, String answer) {
        jdbc.update("""
                update agent_runs set answer=?, status=case when exists(select 1 from agent_proposals p where p.run_id=agent_runs.id)
                then 'awaiting_confirmation' else 'succeeded' end,finished_at=clock_timestamp() where id=? and status='running'
                """, answer, runId);
    }
    public void fail(String id, String message) {
        jdbc.update("update agent_runs set status='failed',error=?,finished_at=clock_timestamp() where id=? and status='running'", message, id);
    }
    public int recoverInterrupted() {
        return jdbc.update("update agent_runs set status='failed',error='服务重启，任务已中断；请重新发起。',finished_at=clock_timestamp() where status='running'");
    }
    public Proposal proposal(String runId) {
        List<Proposal> values = jdbc.query("select * from agent_proposals where run_id=?", (rs, i) -> new Proposal(
                rs.getString("id"), rs.getString("status"), rs.getString("summary"),
                Arrays.asList(json.readValue(rs.getString("changes"), Change[].class)),
                rs.getObject("expires_at", OffsetDateTime.class)), runId);
        return values.isEmpty() ? null : values.getFirst();
    }
    public void propose(String id, String runId, String summary, List<Change> changes) {
        jdbc.update("""
                insert into agent_proposals(id,run_id,summary,changes,status,expires_at)
                values (?,?,?,cast(? as jsonb),'pending',clock_timestamp()+interval '30 minutes')
                """, id, runId, summary, json.writeValueAsString(changes));
    }
    public String decisionRequest(String proposalId) {
        return jdbc.queryForObject("select decision_request_id from agent_proposals where id=?", String.class, proposalId);
    }
    public void decide(String runId, String proposalId, boolean approve, String requestId) {
        jdbc.update("update agent_proposals set status=?,decision_request_id=?,decided_at=clock_timestamp() where id=?",
                approve ? "approved" : "rejected", requestId, proposalId);
        jdbc.update("update agent_runs set status=?,finished_at=clock_timestamp() where id=?",
                approve ? "succeeded" : "rejected", runId);
    }
    public void apply(CurrentUser user, Change change) {
        int updated = jdbc.update("""
                update agent_campaigns set bid=?,daily_budget=?,revision=revision+1,updated_at=clock_timestamp()
                where tenant_id=? and id=? and revision=? and bid=? and daily_budget=?
                """, change.after().bid(), change.after().dailyBudget(), user.tenantId(), change.campaignId(),
                change.revision(), change.before().bid(), change.before().dailyBudget());
        if (updated != 1) throw ApiException.conflict("计划已发生变化", "请重新查询并生成提案，不能批准过期差异。");
    }
    public void addReceipt(String id, String runId, String proposalId, List<AppliedChange> changes) {
        jdbc.update("insert into agent_receipts(id,run_id,proposal_id,changes) values (?,?,?,cast(? as jsonb))",
                id, runId, proposalId, json.writeValueAsString(changes));
    }
    public Receipt receipt(String runId) {
        List<Receipt> values = jdbc.query("select * from agent_receipts where run_id=?", (rs, i) -> new Receipt(
                rs.getString("id"), runId, rs.getString("proposal_id"), "sandbox", "executed",
                Arrays.asList(json.readValue(rs.getString("changes"), AppliedChange[].class)),
                rs.getObject("created_at", OffsetDateTime.class)), runId);
        return values.isEmpty() ? null : values.getFirst();
    }
}
