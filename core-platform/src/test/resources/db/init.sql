-- Schema for the Dev Services database used by @QuarkusTest. Mirrors what the app's
-- ReadModelStore.ensureSchema() builds, so read queries work before the first catch-up.
-- Runs on a TimescaleDB image (see application.properties) so reading_series can be a real
-- hypertable, exactly like production.
create extension if not exists timescaledb;

-- Append-only event log (source of truth). The app never creates this itself.
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

-- Consumer cursor (one row per projection).
create table if not exists projection_cursor (
    projection text primary key,
    position   bigint not null
);

-- Current-state read model (latest per identity).
create table if not exists reading_latest (
    event_type    text        not null,
    entity_id     text        not null,
    exposed_json  jsonb       not null,
    observed_at   timestamptz,
    seq           bigint      not null,
    primary key (event_type, entity_id)
);

-- Time-series read model. Unique key includes the partition column so it can be a hypertable.
create table if not exists reading_series (
    event_type    text        not null,
    seq           bigint      not null,
    entity_id     text        not null,
    exposed_json  jsonb       not null,
    observed_at   timestamptz not null,
    primary key (event_type, seq, observed_at)
);
select create_hypertable('reading_series', 'observed_at', if_not_exists => true, migrate_data => true);
create index if not exists reading_series_entity_time on reading_series (event_type, entity_id, observed_at desc);
