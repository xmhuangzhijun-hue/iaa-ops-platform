-- 第 3 阶段：写操作留痕与按天幂等导入。

-- 审计事件。只追加，不修改也不删除：改了就不是审计了。
create table audit_events (
    id          varchar(32)  not null,
    tenant_id   varchar(32)  not null,
    occurred_at timestamptz  not null default now(),
    actor       varchar(32)  not null,
    action      varchar(64)  not null,
    target_type varchar(32)  not null,
    target_id   varchar(128) not null,
    detail      jsonb        not null default '{}'::jsonb,
    constraint pk_audit_events primary key (id),
    constraint fk_audit_events_tenant_id_tenants foreign key (tenant_id) references tenants (id)
);

-- 游标分页按 (occurred_at, id) 倒序取，同一毫秒内也有确定次序。
create index ix_audit_events_tenant_id_occurred_at on audit_events (tenant_id, occurred_at desc, id desc);
create index ix_audit_events_tenant_id_action on audit_events (tenant_id, action, occurred_at desc);

-- 导入任务。stat_dates 记录文件覆盖了哪几天，入库按天整体替换，重复导入同一天不累加。
create table import_tasks (
    id            varchar(32) not null,
    tenant_id     varchar(32) not null,
    media         varchar(32) not null,
    file_name     varchar(255) not null,
    status        varchar(16) not null,
    stat_dates    jsonb       not null default '[]'::jsonb,
    rows_total    integer     not null default 0,
    rows_replaced integer     not null default 0,
    errors        jsonb       not null default '[]'::jsonb,
    created_by    varchar(32) not null,
    created_at    timestamptz not null default now(),
    finished_at   timestamptz,
    constraint pk_import_tasks primary key (id),
    constraint ck_import_tasks_status check (status in ('pending', 'processing', 'succeeded', 'failed')),
    constraint fk_import_tasks_tenant_id_tenants foreign key (tenant_id) references tenants (id)
);

create index ix_import_tasks_tenant_id_created_at on import_tasks (tenant_id, created_at desc);
