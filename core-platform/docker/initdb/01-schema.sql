-- Runs once when the compose database is first created.
-- Enables the extensions, creates the log + read models, and makes the time-series read
-- model a real Timescale hypertable. PostGIS is enabled and ready for the geo read model.

CREATE EXTENSION IF NOT EXISTS timescaledb;
CREATE EXTENSION IF NOT EXISTS postgis;

-- ============================================================================
-- 1. WRITE MODEL (Event Store)
-- ============================================================================

-- Append-only event log (source of truth).
CREATE TABLE IF NOT EXISTS events (
                                      seq            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                      tenant_id      TEXT        NOT NULL,
                                      event_type     TEXT        NOT NULL,
                                      payload        JSONB       NOT NULL,
                                      valid_time     TIMESTAMPTZ,
                                      event_id       UUID        NOT NULL,
                                      schema_version TEXT        NOT NULL,
                                      recorded_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_events_tenant_event UNIQUE (tenant_id, event_id)
    );

-- ============================================================================
-- 2. PROJECTION SYSTEM
-- ============================================================================

-- Cursor: one row per independently rebuildable projection
CREATE TABLE IF NOT EXISTS projection_cursor (
                                                 projection TEXT PRIMARY KEY,
                                                 position   BIGINT NOT NULL
);

-- ============================================================================
-- 3. READ MODELS
-- ============================================================================

-- Latest read model: one row per entity, upserted
CREATE TABLE IF NOT EXISTS reading_latest (
                                              event_type    TEXT        NOT NULL,
                                              entity_id     TEXT        NOT NULL,
                                              exposed_json  JSONB       NOT NULL,
                                              observed_at   TIMESTAMPTZ,
                                              seq           BIGINT      NOT NULL,
                                              PRIMARY KEY (event_type, entity_id)
    );

-- Time-series read model: all rows, becomes a hypertable
CREATE TABLE IF NOT EXISTS reading_series (
                                              event_type    TEXT        NOT NULL,
                                              seq           BIGINT      NOT NULL,
                                              entity_id     TEXT        NOT NULL,
                                              exposed_json  JSONB       NOT NULL,
                                              observed_at   TIMESTAMPTZ NOT NULL,
                                              PRIMARY KEY (event_type, seq, observed_at)
    );

-- Turn reading_series into a hypertable partitioned by time
SELECT create_hypertable(
               'reading_series', 'observed_at',
               if_not_exists => TRUE,
               migrate_data  => TRUE
       );

-- Index for efficient time-series lookups per entity
CREATE INDEX IF NOT EXISTS reading_series_entity_time
    ON reading_series (event_type, entity_id, observed_at DESC);