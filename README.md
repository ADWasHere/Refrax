# Refrax

One event-sourced source of truth, refracted into multiple standard-conformant
projections (NGSI-LD, native) that stay consistent by construction.

Refrax is not another IoT platform and not a FIWARE replacement. It is a focused implementation
of a single idea: standards compliance as a structurally guaranteed projection, instead of a
hand-maintained data model — for event-sourced IoT and telemetry data generally, not tied to one
industry. The bundled example (an air-quality sensor schema and its views) happens to be a
smart-city use case because that's the domain the author knows best; nothing in the gate, views,
or projectors assumes it, and using it for a different domain is a matter of writing a different
schema, not changing the core.

For what is planned but not built yet, see [ROADMAP.md](ROADMAP.md).

## Quickstart

Requires Docker, and for running from source, a JDK 25 (the Maven wrapper is included, no
local Maven install needed). Every setting below already has a working default; copy
[core-platform/.env.example](core-platform/.env.example) to `core-platform/.env` only if you
want to change one — Docker Compose reads it automatically.

### Run with Docker Compose

```bash
cd core-platform
docker compose up
```

Refrax listens on `http://localhost:8787` (override with `HTTP_PORT`).

### Run from source (Maven)

Starts only the database via Compose, then runs Refrax in dev mode with live reload:

```bash
cd core-platform
docker compose up -d db
./mvnw quarkus:dev
```

### Try it

Refrax is multi-tenant by default, but boots with one tenant already usable: the database's
default `public` schema counts as a tenant like any other, so you can ingest without
provisioning anything first. One event schema (`AirQualityReading`) and two views of it
(`air-quality-full`, `air-quality-value-only`) are bundled — see
[core-platform/configs/schemas](core-platform/configs/schemas) and
[core-platform/configs/views](core-platform/configs/views).

Send an event:

```bash
curl -X POST http://localhost:8787/v1/events \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: public" \
  -d '{
    "eventType": "AirQualityReading",
    "eventId": "11111111-0000-0000-0000-000000000001",
    "observedAt": "2026-09-11T12:00:00Z",
    "payload": {"sensorId": "sensor-42", "metric": "PM2.5", "value": "12.4", "unit": "ug/m3"}
  }'
```

The read models catch up within a few seconds; then read it back through a view:

```bash
curl -H "X-Tenant-ID: public" "http://localhost:8787/v1/views/air-quality-full/series"
```

To isolate a new tenant instead of using `public`, provision one first. Provisioning is
restricted to identities listed in `REFRAX_TENANT_ADMIN_IDS` (`admin` by default) — this is
not authentication, just a minimal allowlist on top of whatever the gateway or token issuer
already vouches for:

```bash
curl -X POST http://localhost:8787/v1/tenants \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: admin" \
  -d '{"schema":"demo"}'
```

For the full API reference — every endpoint, dynamic JSONB filtering, and how to get output in
a format other than `native` — see [docs/GUIDE.md](docs/GUIDE.md). For an unfamiliar term, see
[docs/GLOSSARY.md](docs/GLOSSARY.md).

## The guarantee

Most NGSI-LD deployments treat compliance as a manual modeling task. You author `@context`
files, map your data to entities and properties by hand, and nothing stops a storage
artifact such as a database primary key, a partition id, or an ingestion offset from
leaking into the domain model you expose. Refrax removes this class of error:

> No field without a declared domain meaning and an explicit vocabulary binding can appear
> in an exposed standard representation. A single capability gate enforces this, not
> discipline or code review.

A projector cannot emit an internal field, because the `ExposableEntity` it is handed is
built by copying only what the schema marked exposable, deny-by-default — the gate is the
one chokepoint, not every call site having to get it right.

## Why event-sourced

The event log, not the current state, is the source of truth. This inversion is what makes
the guarantees possible.

- State is a fold over events. Any current value is derived by reducing the log. The reverse
  does not hold: from a state you cannot recover the history, because folding discards
  information. A system that stores only the current state and later wants history has to add
  change-capture, and at that point it has reimplemented event sourcing with weaker
  guarantees.
- One canonical log derives arbitrarily many representations, such as NGSI-LD today and some
  future standard, and they all stay consistent with no data migration. Adding a standard
  means adding a projector, not migrating data.
- Because the log is canonical and not a side effect of a mutable store, the current-state
  read model and the full history stay consistent with each other, and any projection can be
  rebuilt by replay.

This is the right tool when history, auditability, provenance, or multi-standard consistency
matter. It is the wrong tool when they do not (see When not to use Refrax).

## How it works

- An append-only event stream is the single source of truth. Each event carries a valid-time
  (when it happened in the world) that is separate from its record-time (when it was stored).
  Late and corrected data are handled without rewriting the past, and "what did we know at
  time T" stays answerable.
- A domain schema classifies every field by semantic role. The default is internal. Exposing
  a field is a deliberate act that requires declaring what it means and binding it to a
  vocabulary URI (schema.org, Smart Data Models, or your own ontology).
- A capability gate is the one place where exposability is decided. It produces an
  `ExposableEntity` that by construction contains no internal fields. The entity URN is built
  only from declared identity components, never from a storage key. No domain identity means
  no projection.
- Views let one event type expose different subsets to different consumers, for example a
  public monitoring view without precise location and an operations view with it. Each view is
  a declared, gated subset, and a view can only narrow what the schema marks exposable, never
  widen it. This makes purpose limitation and data minimisation structural instead of a filter
  rule that someone has to apply correctly.
- Fields marked personal data are excluded from every projection and read model outright, not
  merely narrowed out of a specific view. Erasing them from the log itself, rather than just
  keeping them out of projections, is on the roadmap and not built yet.
- Every tenant gets its own Postgres schema, provisioned and migrated independently. Tenant
  resolution (an `X-Tenant-ID` header or an OIDC claim) happens once per request and pins every
  transaction to that schema, so ingestion, replay, scheduled catch-up jobs, views, and reads
  are all tenant-isolated, not just the write path.
- Projectors (native and NGSI-LD today) consume an `ExposableEntity` and decide only
  shape, never eligibility. Views and projectors are orthogonal, so any view renders in any
  standard, and adding a standard is one projector that works for every view rather than a
  per-view rewrite.
- Read models (TimescaleDB for time-series today) and the standard `@context` artifacts are
  derived, not hand-written. The same replay mechanism that builds a projection also rebuilds
  it after a schema change. Provenance and lineage of every value come from the log for free.

See `docs/architecture.puml` for the system overview and `docs/compiler.puml` for the projection
pipeline.

### Architecture
![docs/architecture.puml](https://img.plantuml.biz/plantuml/png/RLHDRzim3BqRy7yWsiCEix6bNPkjXw7vi8CCGpSqmKwvA3BZQ9KbJvAJUaF_-oZRQPmYyCKY7v_8nsVVMyUCgme9pF2jSCXTPJ0Cti4ZRWnx1bjRXmNe7PgTm7qOX87J9JWDQ-sSBY0JXeg4Lc5WduGgcM4Kn0shf4krSW-iGB1CsTYY4Pi-ocvPflT2vR1Xqc8_PNIbCgwDGWE3Z_tCo1YzdsYf3LmEhuTNjvEJEkg5gLmY_0i3WB4s6PeEVPFU93SZ7_PzTS6h2JqKVEcpb8m_iVmSn_ZJ-6hqfGaJ9c_Qx5Bf91sdmpVqXpjVl737vYuMgAms5Zwi56PqKuOxmT5U0BE7Ts8S96do-QHkefMY4vMkL8OSbJDCYh-OHI3rFkOwmuQ3l-BWChbD1-dODt2Oww9RBWbCy6RAtXrpbQDrj5-QZPbwfLZbjX4uL4pnEe9lysK4bc-nOCrTeiM_tdD2bkoeH-ejNwc5hWn7q1jO6c8ath94bSLQIM9Ta_cAKjCu3IbL4EE0v4lUC4vOoTP22bT3pXnw7LGTh-kuCrrJxZCau2YP9CCtzR_8nXRUGsdqxuEK3xF5FFu-ZQ3Tot9BCza857DYrmhgqGrXCZsppaTa6HGalYIofIZGSYPnEexWfxPkDbyq78PGBUZiRXVqGe9dWBb1IqHb9QLljBBRfhwD7xiuzOMj2cnimUnshOc71j1kInyG5quWdPXYidQ2suD5sfAppTY2_WjKMCTSYfMQQI8hoQym4-v7sJ58GJiQfTVJnEjny6qSV-tvBmnwNlIfpYvtuEeIQLXl449rOO-WtOU1Du0_-4s6GRDFV_Hx82FxRVXZAyaHtPFuxh3Xvj_KInph2FEdGbFJM3bgOCz8L5sgcwALY8QWMK-w9_ATiCGTqGVB9JFOVHFMQt5FngQ_QXZy1m00)



### Compiler
![docs/compiler.puml](https://img.plantuml.biz/plantuml/png/RLJVJzj037wFbF_1Oj86KZiDDkZ6mqHR4eIqiGdGdlfoIPpaniNExgv0DF6_pvV-eEsglgJsP_kpFvyld5VMpni3EJUjDcZZw1tSOcdLCxIMVsFkDHEqkaMZ2U4mLwtAjD6-WqfvF8gZEFBSWcV8s7jkeD1s4HL7xa5JgwmI8vPgRdpAv6zKW-0KkQ53gyjDbAjLmK-QAYYLSRZfiZod8cTBQE7umwPdR2q_JTcmXJUZZwEJi-bMd71HL1c4lt44C65Re5sYJzFJv7GSp87NqdWobPyONpQp4Fjz2IQV9YVZBxrPvGzLpt7fIgVf91tjv5uLV0bJUsLsSCcDqWGkhx5HCx9iq0tWaNELpOso7MIQ2XdBO4OjMiUapB1GNa5fL7K0oYr3fUnnaYQVzoN_pWKQkBwT4OkEJj3aTGxUpdtTfz12C3bBZzF9LlWrLUWyl0NdsQeAPwJ9enKAKjMh3cgqkE2m0EmbCTrUcnaLIDqmwuRBnLXsAtBzcSl-53FoDGAJwG2fP9jZW-IXPIdU5mjPfD8ucIQZhKh9SyjEPGOJwSztCoA65MCeDPe2j9CwAgy3ga-6gnYPmB5ebEpeStDrTptyThbw4MpxAAgSxYaab-Cj-8-hATmXERRtjQZcTk9aQdl3RfJNZxXRP65SAoDRkP1wEFmgaiMHo1O-LyC_NpVxI-FJuT4Qq0_i_BNLbRkt1y3Q4qUlymYEqEUkJNhu3zOp3GpxrxAy1bnk9WRukUhEoKt8TO7XqRSu0C97UTfoPIgzM9Jt0NvVOnVUC8OXzEiGzc97um4u1aK1lwDqhaH-3na2DjfxB23hvDIHaxNlRvGKGIhwznsONyWVkOPnz0y0)

## What this is and isn't

- Is: a reference implementation of structurally guaranteed standards projection, usable as a
  building block in a larger IoT or data-platform stack.
- Is not: a complete platform. By its own design boundary, Refrax ships no dashboards and no
  connectors and no auth. Visualization and ingestion are decentralized and left to consumers. Refrax
  provides data integrity, multi-tenancy, the projection guarantee, and the standard egress
  APIs, and it stops there on purpose, so that operators and vendors build their connectors,
  dashboards, and services on top of it.
- Relationship to FIWARE: complementary rather than adversarial. Refrax speaks NGSI-LD on
  egress and is built to interoperate. The contribution is the approach to compliance, which is
  independent of any single broker and can be adopted by them.

## When not to use Refrax

Stated up front, because it builds trust faster than a feature list.

- Actuation-heavy and command-heavy systems. If your primary need is mutable current state
  with low-latency reads and writes and device commands, an entity-centric or
  digital-twin-centric system such as a FIWARE context broker, Eclipse Ditto, or a device
  shadow is the better fit. Refrax models commands as events and delegates their execution to
  external modules, but it is built for telemetry and observability, not as an actuation
  platform.
- Small, low-volume, low-ops deployments. If you do not need history, audit, provenance, or
  multi-standard output, a plain state store is simpler and the right call. The guarantees
  here cost real complexity, so adopt that cost only when you need what it buys.
- Strong read-your-write consistency on every read. The read side is eventually consistent,
  with a projection lag that is usually in the millisecond range. Refrax can serve a
  synchronous read from the log when needed, but weigh this if every read must reflect the
  last write instantly.

## Running alongside an existing system

Refrax does not require replacing your current core. Exactly one system must be the source of
truth for a given datum, and data crosses the boundary in one direction only:

- Refrax as the source of truth, projecting into your existing state system, which becomes
  just another read model.
- Refrax as an audit and history layer behind an existing state system. It consumes that
  system's change events and gives you a replayable, auditable history that is as complete as
  the upstream stream.
- Domain split, where each system owns the data it is the right tool for.

Adoption is therefore additive. You place it next to what you run, not in place of it.

## Status

Early, independent, and actively developed in the author's own time. As of `0.2.0`: event
sourcing, the capability gate, views, the NGSI-LD projector, TimescaleDB read models, and
schema-per-tenant multi-tenancy — ingestion, replay, scheduled jobs, views, and reads are all
tenant-isolated — are implemented and tested. What is not yet built, most notably the OGC
SensorThings projector, a PostGIS-backed geo read model, and full crypto-shredding for GDPR
erasure, is tracked in [ROADMAP.md](ROADMAP.md). It exists because current enterprise
IoT/telemetry data stacks make the core heavier and less trustworthy than it needs to be, and
because that core can be done substantially simpler and safer. Interfaces and internals will
change before `1.0.0`.

## License

Apache License 2.0. You may use, modify, and redistribute Refrax, including in commercial and
closed-source products, as long as you keep the copyright and license notices. See the LICENSE
file for the full text. This summary is not legal advice.

## Contributing and upstream

The most valuable contribution is to the idea. If you maintain an NGSI-LD broker or work in the
FIWARE ecosystem, feedback on the structural-compliance approach is especially useful. Issues
and design proposals are welcome.
