# Refrax

One event-sourced source of truth, refracted into multiple standard-conformant
projections (NGSI-LD, OGC SensorThings, native) that stay consistent by construction.

Refrax is not another smart-city platform and not a FIWARE replacement. It is a focused
implementation of a single idea: standards compliance as a structurally guaranteed
projection, instead of a hand-maintained data model.

## The guarantee

Most NGSI-LD deployments treat compliance as a manual modeling task. You author `@context`
files, map your data to entities and properties by hand, and nothing stops a storage
artifact such as a database primary key, a partition id, or an ingestion offset from
leaking into the domain model you expose. Refrax removes this class of error:

> No field without a declared domain meaning and an explicit vocabulary binding can appear
> in an exposed standard representation. The type system enforces this, not discipline or
> code review.

A projector cannot emit an internal field, because the value it is handed does not contain
one. Leak prevention is a property of the types, checked at compile time.

## Why event-sourced

The event log, not the current state, is the source of truth. This inversion is what makes
the guarantees possible.

- State is a fold over events. Any current value is derived by reducing the log. The reverse
  does not hold: from a state you cannot recover the history, because folding discards
  information. A system that stores only the current state and later wants history has to add
  change-capture, and at that point it has reimplemented event sourcing with weaker
  guarantees.
- One canonical log derives arbitrarily many representations, such as NGSI-LD today, OGC
  SensorThings, and some future standard, and they all stay consistent with no data
  migration. Adding a standard means adding a projector, not migrating data.
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
  public open-data view without location and a planning view with it. Each view is a declared,
  gated subset, and a view can only narrow what the schema marks exposable, never widen it.
  This makes purpose limitation and data minimisation structural instead of a filter rule that
  someone has to apply correctly.
- Projectors (NGSI-LD, OGC SensorThings, native) consume an `ExposableEntity` and decide only
  shape, never eligibility. Views and projectors are orthogonal, so any view renders in any
  standard, and adding a standard is one projector that works for every view rather than a
  per-view rewrite.
- Read models (TimescaleDB for time-series, PostGIS for geo) and the standard `@context`
  artifacts are derived, not hand-written. The same replay mechanism that builds a projection
  also rebuilds it after a schema change. Provenance and lineage of every value come from the
  log for free.

See `docs/architecture.puml` for the system overview and `docs/compiler.puml` for the projection
pipeline.

### Architecture
![docs/architecture.puml](https://img.plantuml.biz/plantuml/png/RLHDRzim3BqRy7yWsiCEix6bNPkjXw7vi8CCGpSqmKwvA3BZQ9KbJvAJUaF_-oZRQPmYyCKY7v_8nsVVMyUCgme9pF2jSCXTPJ0Cti4ZRWnx1bjRXmNe7PgTm7qOX87J9JWDQ-sSBY0JXeg4Lc5WduGgcM4Kn0shf4krSW-iGB1CsTYY4Pi-ocvPflT2vR1Xqc8_PNIbCgwDGWE3Z_tCo1YzdsYf3LmEhuTNjvEJEkg5gLmY_0i3WB4s6PeEVPFU93SZ7_PzTS6h2JqKVEcpb8m_iVmSn_ZJ-6hqfGaJ9c_Qx5Bf91sdmpVqXpjVl737vYuMgAms5Zwi56PqKuOxmT5U0BE7Ts8S96do-QHkefMY4vMkL8OSbJDCYh-OHI3rFkOwmuQ3l-BWChbD1-dODt2Oww9RBWbCy6RAtXrpbQDrj5-QZPbwfLZbjX4uL4pnEe9lysK4bc-nOCrTeiM_tdD2bkoeH-ejNwc5hWn7q1jO6c8ath94bSLQIM9Ta_cAKjCu3IbL4EE0v4lUC4vOoTP22bT3pXnw7LGTh-kuCrrJxZCau2YP9CCtzR_8nXRUGsdqxuEK3xF5FFu-ZQ3Tot9BCza857DYrmhgqGrXCZsppaTa6HGalYIofIZGSYPnEexWfxPkDbyq78PGBUZiRXVqGe9dWBb1IqHb9QLljBBRfhwD7xiuzOMj2cnimUnshOc71j1kInyG5quWdPXYidQ2suD5sfAppTY2_WjKMCTSYfMQQI8hoQym4-v7sJ58GJiQfTVJnEjny6qSV-tvBmnwNlIfpYvtuEeIQLXl449rOO-WtOU1Du0_-4s6GRDFV_Hx82FxRVXZAyaHtPFuxh3Xvj_KInph2FEdGbFJM3bgOCz8L5sgcwALY8QWMK-w9_ATiCGTqGVB9JFOVHFMQt5FngQ_QXZy1m00)



### Compiler
![docs/compiler.puml](https://img.plantuml.biz/plantuml/png/RLJVJzj037wFbF_1Oj86KZiDDkZ6mqHR4eIqiGdGdlfoIPpaniNExgv0DF6_pvV-eEsglgJsP_kpFvyld5VMpni3EJUjDcZZw1tSOcdLCxIMVsFkDHEqkaMZ2U4mLwtAjD6-WqfvF8gZEFBSWcV8s7jkeD1s4HL7xa5JgwmI8vPgRdpAv6zKW-0KkQ53gyjDbAjLmK-QAYYLSRZfiZod8cTBQE7umwPdR2q_JTcmXJUZZwEJi-bMd71HL1c4lt44C65Re5sYJzFJv7GSp87NqdWobPyONpQp4Fjz2IQV9YVZBxrPvGzLpt7fIgVf91tjv5uLV0bJUsLsSCcDqWGkhx5HCx9iq0tWaNELpOso7MIQ2XdBO4OjMiUapB1GNa5fL7K0oYr3fUnnaYQVzoN_pWKQkBwT4OkEJj3aTGxUpdtTfz12C3bBZzF9LlWrLUWyl0NdsQeAPwJ9enKAKjMh3cgqkE2m0EmbCTrUcnaLIDqmwuRBnLXsAtBzcSl-53FoDGAJwG2fP9jZW-IXPIdU5mjPfD8ucIQZhKh9SyjEPGOJwSztCoA65MCeDPe2j9CwAgy3ga-6gnYPmB5ebEpeStDrTptyThbw4MpxAAgSxYaab-Cj-8-hATmXERRtjQZcTk9aQdl3RfJNZxXRP65SAoDRkP1wEFmgaiMHo1O-LyC_NpVxI-FJuT4Qq0_i_BNLbRkt1y3Q4qUlymYEqEUkJNhu3zOp3GpxrxAy1bnk9WRukUhEoKt8TO7XqRSu0C97UTfoPIgzM9Jt0NvVOnVUC8OXzEiGzc97um4u1aK1lwDqhaH-3na2DjfxB23hvDIHaxNlRvGKGIhwznsONyWVkOPnz0y0)

## What this is and isn't

- Is: a reference implementation of structurally guaranteed standards projection, usable for
  evaluation, research, and as a building block.
- Is not: a complete platform. By its own design boundary, Refrax ships no dashboards and no
  connectors. Visualization and ingestion are decentralized and left to consumers. Refrax
  provides data integrity, multi-tenancy, the projection guarantee, and the standard egress
  APIs, and it stops there on purpose, so that operators and vendors build their connectors,
  dashboards, and services on top of it.
- Relationship to FIWARE: complementary rather than adversarial. Refrax speaks NGSI-LD and OGC
  SensorThings on egress and is built to interoperate. The contribution is the approach to
  compliance, which is independent of any single broker and can be adopted by them.

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

## Compliance and data protection

The properties Refrax guarantees structurally, namely provenance, integrity,
standards-conformant interoperable egress, and portability without lock-in, are the ones EU
regulation is moving from optional to mandatory. The Data Act sets machine-readable access,
interoperability, and portability obligations, and the AI Act requires proof of the provenance
and integrity of input data. Refrax does not make an organisation compliant, since compliance
is organisational, but it provides the technical substrate that makes the relevant controls
structural instead of bolted on.

The immutable log and the right to erasure are reconciled by crypto-shredding. Fields declared
as personal data are encrypted at rest with a per-subject key held in a separate, mutable key
store. Erasure destroys the key, so the event stays immutable while its personal content
becomes permanently unreadable, including in backups and derived read models. Personal-data
classification is part of the schema and is enforced, so a field declared personal cannot be
stored or projected in clear.

## For evaluators

If you run or plan an urban data platform, or any system where externally shared data must be
trusted, traceable, and standards-conformant, the practical question is usually this: can I
trust that what we expose actually reflects our domain, and not accidents of our database?
Refrax answers that with a guarantee you can point at, instead of a process you have to police.
It is standards-conformant by design (NGSI-LD and OGC SensorThings), so it fits interoperability
and procurement requirements, including the DIN SPEC 91357 reference architecture for open urban
platforms, without locking you into a proprietary model. Smart-city telemetry is the first
target, and the pattern extends to other domains with hard provenance and audit requirements. It
is early-stage. Evaluation and feedback are welcome, and there is nothing to buy.

## Status

Early, independent, and actively developed in the author's own time. It exists because current
enterprise urban-data stacks make the core heavier and less trustworthy than it needs to be, and
because that core can be done substantially simpler and safer. Interfaces and internals will
change.

## License

Apache License 2.0. You may use, modify, and redistribute Refrax, including in commercial and
closed-source products, as long as you keep the copyright and license notices. See the LICENSE
file for the full text. This summary is not legal advice.

## Contributing and upstream

The most valuable contribution is to the idea. If you maintain an NGSI-LD broker or work within
ETSI ISG CIM or the FIWARE ecosystem, the structural-compliance approach here is meant to be
taken and improved on. Issues and design proposals are welcome.
