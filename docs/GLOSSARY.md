# Glossary

Every term used across the README, ROADMAP, and API Guide, in one alphabetical list. Each entry
has a plain-language definition and a reference to where it's authoritative — a source file for
concrete types, a doc section for concepts and external standards.

## Capability Gate (Gate)

The one place in Refrax where exposability is decided. It builds an
[ExposableEntity](#exposableentity) from a [schema](#schema) and a raw payload by copying
**only** the fields the schema declares exposable — deny-by-default, since it never reads a
payload key the schema didn't name. A [view](#view), when given, narrows further at the same
step.

**Reference:** `core-platform/src/main/java/io/refrax/gate/Gate.java`

## @context

The JSON-LD mechanism that maps short terms in a document (e.g. `unit`) to their full vocabulary
URIs. Refrax's [NGSI-LD](#ngsi-ld) output always derives its `@context` from the schema's own
[vocabulary URI](#vocabulary-uri) bindings — nothing is hand-authored, so it can never drift from
what the fields actually mean.

**Reference:** `core-platform/src/main/java/io/refrax/projection/NgsiLdProjector.java`

## Crypto-shredding

A planned (not yet implemented) mechanism for reconciling an immutable log with the right to
erasure: fields declared [personal data](#personal-data) would be encrypted at rest with a
per-subject key held in a separate, mutable key store. Deleting the key makes that data
permanently unreadable — including in backups and derived read models — without rewriting the
log itself. Today, personal-data fields are simply excluded from every projection instead.

**Reference:** [ROADMAP.md](ROADMAP.md), [GUIDE.md § Personal data](GUIDE.md#personal-data)

## Data minimisation / purpose limitation

The principle that a consumer should see only the fields it actually needs, not everything a
schema happens to expose. Refrax makes this structural rather than a filter someone has to
remember to apply correctly: a [view](#view) declares its own narrower `exposes` list, enforced
at load time, not left to per-request logic.

**Reference:** [README.md § How it works](../README.md#how-it-works)

## Deny-by-default

The [gate](#capability-gate-gate)'s default posture: a field is [internal](#internal-field)
unless explicitly declared [exposable](#exposable-field). The opposite of an allow-list applied
after the fact — nothing has to remember to deny a field, because denial is what happens when
nothing says otherwise.

**Reference:** [README.md § The guarantee](../README.md#the-guarantee)

## Entity URN (`entity_id`)

The identifier assigned to a projected entity. Built **solely** from declared
[identity](#fieldrole) component values via `EventSchema.urn(...)` — never from a storage key
such as a database primary key — so the same identity always produces the same URN regardless of
where in the pipeline it's computed. Shape: `<urnNamespace>:<eventType>:<identityValue>`, e.g.
`urn:refrax:AirQualityReading:sensor-42`.

**Reference:** `core-platform/src/main/java/io/refrax/schema/EventSchema.java` (`urn()`),
`core-platform/src/main/java/io/refrax/gate/Gate.java` (`mintUrn()`)

## Event

A single fact appended to the log: an `eventType`, a `payload`, and a
[valid-time](#valid-time-validtime) distinct from its [record-time](#record-time-recordedat).
Events are never updated or deleted — correcting the past means appending a new event, not
rewriting an old one.

**Reference:** `core-platform/src/main/java/io/refrax/ingestion/Events.java`,
[README.md § How it works](../README.md#how-it-works)

## Event sourcing

Storing the append-only event log — not current mutable state — as the system's source of truth.
Any current value is a fold (replay) over the log; the reverse doesn't hold, since folding
discards information a plain state store would have thrown away already.

**Reference:** [README.md § Why event-sourced](../README.md#why-event-sourced)

## EventSchema

See [Schema](#schema).

## Exposable field

A field explicitly declared in a [schema](#schema) with a [role](#fieldrole), a
[type](#fieldtype), and a [vocabulary binding](#vocabulary-uri). The only kind of field the
[gate](#capability-gate-gate) or any projector can ever see — a field not declared this way is
[internal](#internal-field), full stop.

**Reference:** `core-platform/src/main/java/io/refrax/schema/FieldDeclaration.java`,
[GUIDE.md § Declaring schemas and views](GUIDE.md#declaring-schemas-and-views)

## ExposableEntity

The [gate](#capability-gate-gate)'s output type: an [entity URN](#entity-urn-entity_id), an event
type, a map of [ExposableProperty](#exposableproperty) values, and the event's
[valid-time](#valid-time-validtime). Guaranteed by construction to contain no internal field —
this is the object every [projector](#projection--projector) actually renders.

**Reference:** `core-platform/src/main/java/io/refrax/gate/ExposableEntity.java`

## ExposableProperty

One property on an [ExposableEntity](#exposableentity): its value, [FieldRole](#fieldrole),
[vocabulary URI](#vocabulary-uri), and [personal-data](#personal-data) flag. Carries enough for a
projector to choose the right output shape (e.g. NGSI-LD `Property` vs. `Relationship`) without
looking anything back up.

**Reference:** `core-platform/src/main/java/io/refrax/gate/ExposableProperty.java`

## FieldRole

The semantic role a declared field plays: `IDENTITY` (feeds the [URN](#entity-urn-entity_id),
never exposed as a property itself), `PROPERTY` (an observed/measured value), or `RELATIONSHIP`
(a reference to another entity). A closed, compiler-checked set — internality is the *absence* of
a role, not a role of its own.

**Reference:** `core-platform/src/main/java/io/refrax/schema/FieldRole.java`

## FieldType

The declared data type of a schema field: `STRING`, `NUMBER`, `BOOLEAN`, or `TIMESTAMP`. Drives
which [query-axis](#query-axis) operators are valid for that field — only `NUMBER` and
`TIMESTAMP` support the `gt`/`gte`/`lt`/`lte` (`greater-then`, `greater-then-equals`, `lower-then`, `lower-then-equals`) comparison suffixes.

**Reference:** `core-platform/src/main/java/io/refrax/schema/FieldType.java`,
[GUIDE.md § Dynamic filtering](GUIDE.md#dynamic-filtering-query-axes)

## FIWARE

An open-source platform and ecosystem for context-aware applications, built around the NGSI-LD
Context Broker. Refrax is explicitly complementary rather than a replacement: it speaks
[NGSI-LD](#ngsi-ld) on egress and is built to interoperate, contributing an approach to
compliance rather than a competing broker.

**Reference:** [README.md § What this is and isn't](../README.md#what-this-is-and-isnt)

## Internal field

The default status of any payload field not declared [exposable](#exposable-field) in a schema.
The [gate](#capability-gate-gate) never reads it, so it structurally cannot reach a projection —
not because something filters it out, but because nothing ever looks at it.

**Reference:** [README.md § The guarantee](../README.md#the-guarantee)

## Match Type

How a declared [query axis](#query-axis) matches values: `EXACT` (equality, plus comparison
operators if the field's [type](#fieldtype) supports them) or `RANGE` (used only for `validTime`
today — queried via `from`/`to` on `/series`, never filtered directly on an axis).

**Reference:** `core-platform/src/main/java/io/refrax/view/MatchType.java`,
[GUIDE.md § Range axes can't be filtered directly](GUIDE.md#range-axes-cant-be-filtered-directly)

## Multi-tenancy (schema-per-tenant)

Each tenant gets its own Postgres schema, provisioned and migrated independently. A request's
tenant is resolved once (header or token claim) and pinned to every transaction for its duration
via `SET LOCAL search_path` — so ingestion, replay, scheduled jobs, and reads are all isolated by
the database itself, not by an application-level filter that has to be applied everywhere
correctly.

**Reference:** `core-platform/src/main/java/io/refrax/tenant/TenantAwarePanache.java`,
[README.md § How it works](../README.md#how-it-works)

## Native format

Refrax's own output shape, and the default `format`: `id`, `type`, `observedAt`, then each
exposed property as a plain value — no standard-specific wrapping.

**Reference:** `core-platform/src/main/java/io/refrax/projection/NativeProjector.java`,
[GUIDE.md § Output formats](GUIDE.md#output-formats)

## NGSI-LD

An ETSI-standardised data model and API for context information, widely used in FIWARE-based and
broader IoT context-information systems. One of Refrax's output
[projection](#projection--projector) formats (`format=ngsi-ld`).

**Reference:** `core-platform/src/main/java/io/refrax/projection/NgsiLdProjector.java`,
[GUIDE.md § Output formats](GUIDE.md#output-formats)

## OGC SensorThings API

An OGC standard for exposing sensor and IoT observation data. Planned as a standard
projection alongside NGSI-LD — not yet implemented.

**Reference:** [ROADMAP.md](ROADMAP.md)

## Personal data

A [schema](#schema) field flagged `personalData: true`. Today this excludes the field from every
projection and read model (it's dropped, not merely hidden) and bars it from being declared as an
`EXACT`-[match](#match-type) [query axis](#query-axis), so it can't be probed by equality either.
Full erasure of already-ingested personal data is planned via [crypto-shredding](#crypto-shredding),
not yet built.

**Reference:** [GUIDE.md § Personal data](GUIDE.md#personal-data)

## Projection / Projector

A `Projector` takes an [ExposableEntity](#exposableentity) and decides only its output *shape*
(native, NGSI-LD, ...) — never eligibility, since the entity it receives already contains
nothing but exposable fields. Adding a new standard means adding one projector, which then works
for every [view](#view) automatically, since views and projectors are orthogonal.

**Reference:** `core-platform/src/main/java/io/refrax/projection/Projector.java`

## Query axis

A named, declared filter dimension on a [view](#view) (one entry in its `queryable` map): which
field it filters, and its [match type](#match-type). The *only* thing a request may filter a view
on — an undeclared axis is rejected outright, so a query can never be used to probe a field the
view doesn't already expose.

**Reference:** `core-platform/src/main/java/io/refrax/view/QueryAxis.java`,
[GUIDE.md § Dynamic filtering](GUIDE.md#dynamic-filtering-query-axes)

## Read model

A derived, queryable table (`reading_latest`, `reading_series`) built by folding the event log
through the [gate](#capability-gate-gate). Never hand-written and never the source of truth — it
can always be rebuilt from the log by [replay](#replay--catch-up), and it is what the view
endpoints actually query.

**Reference:** `core-platform/src/main/java/io/refrax/readmodel/ReadModelConsumer.java`,
[README.md § How it works](../README.md#how-it-works)

## Record-time (`recordedAt`)

When an event was stored, as opposed to [valid-time](#valid-time-validtime) (when it happened).
Kept separate so "what did we know at time T" stays answerable even when data arrives late or
gets corrected.

**Reference:** `core-platform/src/main/java/io/refrax/ingestion/Events.java`

## Replay / catch-up

Folding the event log — from a saved cursor position — into the read models. Runs automatically
every 3 seconds per tenant, and can be triggered manually (full rebuild via `replay`, or a single
entity via `reproject`) through the admin endpoints.

**Reference:** `core-platform/src/main/java/io/refrax/readmodel/ReadModelConsumer.java`,
[GUIDE.md § Admin endpoints](GUIDE.md#admin-post-v1adminreadmodelreplay-and-reproject)

## Schema (EventSchema)

The declared domain schema for one event type: its [exposable fields](#exposable-field) (each
with a role, type, and vocabulary binding) and the URN namespace used to mint
[entity URNs](#entity-urn-entity_id). Anything not listed here is
[internal](#internal-field) by default.

**Reference:** `core-platform/src/main/java/io/refrax/schema/EventSchema.java`,
[GUIDE.md § Declaring schemas and views](GUIDE.md#declaring-schemas-and-views)

## Smart Data Models

An open initiative publishing standardised, NGSI-LD-compatible data model definitions. One of the
recommended sources for a field's [vocabulary URI](#vocabulary-uri) (alongside schema.org or a
custom ontology).

**Reference:** [README.md § How it works](../README.md#how-it-works)

## Tenant

An isolated unit of data, materialised as its own Postgres schema (see
[multi-tenancy](#multi-tenancy-schema-per-tenant)). Resolved once per request, either from an
`X-Tenant-ID` header or a verified token claim.

**Reference:** `core-platform/src/main/java/io/refrax/tenant/TenantContext.java`,
`core-platform/src/main/java/io/refrax/tenant/TenantResolver.java`

## Tenant admin allowlist

The list of tenant identities (`REFRAX_TENANT_ADMIN_IDS`) permitted to provision new tenants via
`POST /v1/tenants`. Not authentication — Refrax doesn't do that — just a minimal gate on one
privileged endpoint, on top of whatever the gateway or token issuer already vouches for.

**Reference:** `core-platform/src/main/java/io/refrax/tenant/TenantAdminAllowlist.java`,
[GUIDE.md § POST /v1/tenants](GUIDE.md#post-v1tenants--provision-a-tenant)

## Valid-time (`validTime`)

When an event happened in the world, as declared by the caller (the `observedAt` field on
ingest) — independent of [record-time](#record-time-recordedat). Late-arriving or corrected data
never rewrites the past; it's appended with the valid-time it actually has.

**Reference:** `core-platform/src/main/java/io/refrax/ingestion/Events.java`,
[README.md § How it works](../README.md#how-it-works)

## View

A named, declared subset of one [schema](#schema): which of the schema's exposable fields it
`exposes`, and which of those (plus identity or `validTime`) are declared as
[query axes](#query-axis). A view can only narrow what its schema marks exposable, never widen
it — validated once at load time, so this can never drift into a request-time surprise.

**Reference:** `core-platform/src/main/java/io/refrax/view/View.java`,
[GUIDE.md § Declaring schemas and views](GUIDE.md#declaring-schemas-and-views)

## ViewBinding

A [View](#view) validated against its [Schema](#schema) at load time, and the only form
downstream code (the query service, the filter parser) is ever handed — so nothing has to
re-validate the pairing or risk it drifting apart.

**Reference:** `core-platform/src/main/java/io/refrax/view/ViewBinding.java`

## Vocabulary URI

A URI binding a field to a shared, external meaning — schema.org, [Smart Data
Models](#smart-data-models), QUDT, or a custom ontology. Required for every
[exposable field](#exposable-field); a field cannot even be constructed without one. Doubles as
the fallback term source when a field name collides with an [NGSI-LD](#ngsi-ld) reserved key
(e.g. `value` → `numericValue`).

**Reference:** `core-platform/src/main/java/io/refrax/schema/FieldDeclaration.java`,
[GUIDE.md § ngsi-ld](GUIDE.md#ngsi-ld)
