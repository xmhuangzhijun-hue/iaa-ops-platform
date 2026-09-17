package com.iaaops.agent.web;

import com.iaaops.agent.AgentDtos.*;
import com.iaaops.agent.AgentService;
import com.iaaops.iam.domain.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/agent")
// Contract x-requirements: REQ-AGENT-001..008 (docs/agent/requirements.md).
public class AgentController {
    private final AgentService agent;
    public AgentController(AgentService agent) { this.agent = agent; }
    @GetMapping("/capabilities") public Capabilities capabilities(@AuthenticationPrincipal CurrentUser user) { return agent.capabilities(user); }
    @GetMapping("/campaigns") public Items<Campaign> campaigns(@AuthenticationPrincipal CurrentUser user) { return new Items<>(agent.campaigns(user)); }
    @GetMapping("/runs") public Items<Run> runs(@AuthenticationPrincipal CurrentUser user) { return new Items<>(agent.list(user)); }
    @PostMapping("/runs") @ResponseStatus(HttpStatus.ACCEPTED)
    public Run submit(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody Submit request) { return agent.submit(user, request); }
    @GetMapping("/runs/{id}") public Run run(@AuthenticationPrincipal CurrentUser user, @PathVariable String id) { return agent.get(user, id); }
    @PostMapping("/runs/{id}/decision") public Run decision(@AuthenticationPrincipal CurrentUser user, @PathVariable String id,
            @Valid @RequestBody Decision request) { return agent.decide(user, id, request); }
    @GetMapping("/receipts") public Items<Receipt> receipts(@AuthenticationPrincipal CurrentUser user) { return new Items<>(agent.receipts(user)); }
}
