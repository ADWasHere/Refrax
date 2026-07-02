-- Schema for the Dev Services (Testcontainers) PostgreSQL used by @QuarkusTest.
-- Mirrors the append-only event log the app writes to. Executed once at container
-- startup; there is no migration tool in the repo yet, so the test owns its schema.
create table if not exists events (
    seq            bigint generated always as identity primary key,
    tenant_id      text        not null,
    event_type     text        not null,
    payload        jsonb       not null,
    valid_time     timestamptz,
    event_id       uuid        not null,
    schema_version text        not null,
    recorded_at    timestamptz not null default now(),
    constraint uq_events_tenant_event unique (tenant_id, event_id)
);
