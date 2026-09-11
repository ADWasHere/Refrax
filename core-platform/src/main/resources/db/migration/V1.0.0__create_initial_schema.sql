create extension if not exists timescaledb;

create table if not exists events (
    seq            bigint generated always as identity primary key,
    event_type     text        not null,
    payload        jsonb       not null,
    valid_time     timestamptz,
    event_id       uuid        not null,
    schema_version text        not null,
    recorded_at    timestamptz not null default now(),
    constraint uq_events_event_id unique (event_id)
);

create table if not exists projection_cursor (
    projection text primary key,
    position   bigint not null
);

create table if not exists reading_latest (
    event_type    text        not null,
    entity_id     text        not null,
    exposed_json  jsonb       not null,
    observed_at   timestamptz,
    seq           bigint      not null,
    primary key (event_type, entity_id)
);

create table if not exists reading_series (
    event_type    text        not null,
    seq           bigint      not null,
    entity_id     text        not null,
    exposed_json  jsonb       not null,
    observed_at   timestamptz not null,
    primary key (event_type, seq, observed_at)
);

select create_hypertable(
    'reading_series', 'observed_at',
    if_not_exists => true,
    migrate_data  => true
);

create index if not exists reading_series_entity_time
    on reading_series (event_type, entity_id, observed_at desc);