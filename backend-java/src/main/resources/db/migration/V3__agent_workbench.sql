-- Agent 可查询报表并提出沙盒操作；该表不是媒体 API 的执行记录。
create table agent_campaigns (
    id varchar(32) primary key,
    tenant_id varchar(32) not null references tenants(id),
    media varchar(32) not null,
    account varchar(64) not null,
    campaign varchar(64) not null,
    name varchar(128) not null,
    bid numeric(12,2) not null check (bid > 0 and bid <= 1000),
    daily_budget numeric(14,2) not null check (daily_budget >= 1 and daily_budget <= 1000000),
    revision integer not null default 1,
    updated_at timestamptz not null default now(),
    unique (tenant_id, media, account, campaign)
);
create table agent_runs (
    id varchar(32) primary key,
    tenant_id varchar(32) not null references tenants(id),
    owner_id varchar(32) not null,
    request_id varchar(64) not null,
    request_hash varchar(64) not null,
    scope_fingerprint varchar(64) not null,
    prompt text not null,
    context jsonb not null default '{}'::jsonb,
    status varchar(32) not null check (status in ('running','awaiting_confirmation','succeeded','rejected','failed')),
    answer text,
    error text,
    events jsonb not null default '[]'::jsonb,
    created_at timestamptz not null default now(),
    finished_at timestamptz,
    unique (tenant_id, owner_id, request_id)
);
create index ix_agent_runs_owner on agent_runs(tenant_id, owner_id, created_at desc);
create table agent_proposals (
    id varchar(32) primary key,
    run_id varchar(32) not null unique references agent_runs(id),
    summary text not null,
    changes jsonb not null,
    status varchar(16) not null check(status in ('pending','approved','rejected')),
    expires_at timestamptz not null,
    decision_request_id varchar(64),
    decided_at timestamptz
);
create table agent_receipts (
    id varchar(32) primary key,
    run_id varchar(32) not null unique references agent_runs(id),
    proposal_id varchar(32) not null unique references agent_proposals(id),
    changes jsonb not null,
    created_at timestamptz not null default now()
);
