# API Guide

Everything the HTTP API can do today: every endpoint, the dynamic JSONB filter syntax, and how
to get output in a format other than Refrax's own. For the concepts behind all this (event
sourcing, the capability gate, views, multi-tenancy), see the [README](../README.md). For
what's planned but not built yet, see the [ROADMAP](../ROADMAP.md). For an unfamiliar term, see
the [Glossary](GLOSSARY.md).

All examples below were run against a live local stack (`docker compose up`, see the
[Quickstart](../README.md#quickstart)) using the bundled `AirQualityReading` schema and its two
views, `air-quality-full` and `air-quality-value-only`.

## Declaring schemas and views

Both are JSON, loaded from `REFRAX_SCHEMA_LOCATIONS` / `REFRAX_VIEW_LOCATIONS`
(comma-separated classpath resources or, in the shipped container, mounted file paths under
`/configs`; see [`.env.example`](../core-platform/.env.example)).

**Schema** — every field Refrax is allowed to know about; anything not listed is internal and
the gate never sees it:

```json
{
  "eventType": "AirQualityReading",
  "urnNamespace": "urn:refrax",
  "exposable": {
    "sensorId": {"role": "identity", "type": "string", "vocabularyUri": "https://schema.org/identifier"},
    "metric":   {"role": "property", "type": "string", "vocabularyUri": "https://smartdatamodels.org/dataModel.Environment/typeOfMeasurement"},
    "value":    {"role": "property", "type": "number", "vocabularyUri": "https://qudt.org/schema/qudt/numericValue"}
  }
}
```

- `role`: `identity` (goes into the URN, never exposed as a property, at most one per schema
  today for filtering purposes — composite identities are modeled but not filterable by a single
  component), `property`, or `relationship`.
- `type`: `string`, `number`, `boolean`, or `timestamp` — drives which filter operators are valid.
- `vocabularyUri`: required for every field; construction fails without one.
- `personalData` (optional, default `false`): see [Personal data](#personal-data).
- `mandatory` is accepted but currently has no effect (parsed, not enforced).

**View** — a named, narrowed subset of one schema, plus which of the *exposed* fields (or
identity/`validTime`) can be queried:

```json
{
  "name": "air-quality-value-only",
  "eventType": "AirQualityReading",
  "exposes": ["value", "unit"],
  "queryable": {"sensor": {"field": "sensorId", "match": "exact"}}
}
```

`exposes` can only be a subset of the schema's exposable fields — never the identity field, never
something the schema didn't declare. A view that tries to expose more than its schema allows
fails at boot, not at request time.

## Proposed Workflow

A concrete path from nothing to a working, queryable event type, in order. Treat it as a cheat
sheet — each step links down to the section with the full detail.

1. **Design the schema first.** List every field the event will ever carry that you might want
   to expose — including ones you're not sure about yet, since it costs nothing to declare a
   field you don't expose in any view. For each: pick a `role`, a `type`, and a real
   `vocabularyUri`. Anything you don't list is internal by construction; you can't leak it later,
   but you also can't retroactively expose it without editing the schema and restarting.
   → [Declaring schemas and views](#declaring-schemas-and-views)
2. **Design at least one view over it.** Decide which of the schema's fields this consumer should
   see (`exposes`) and which of those — plus identity or `validTime` — they should be able to
   filter on (`queryable`). Start narrower than you think you need: a view can only ever shrink
   further, never widen, so add a second, wider view later rather than loosening this one.
   → [Declaring schemas and views](#declaring-schemas-and-views)
3. **Point the app at both files** via `REFRAX_SCHEMA_LOCATIONS` / `REFRAX_VIEW_LOCATIONS`, then
   (re)start. Both load once at boot, not hot-reloaded. A mismatch — e.g. a view exposing a field
   the schema doesn't declare — fails the boot, not a later request, so you find out immediately.
4. **Pick a tenant.** Use `public` to start without provisioning anything, or create a real one
   with `POST /v1/tenants` (needs an allowlisted `X-Tenant-ID`, `admin` by default).
   → [Every request needs a tenant](#every-request-needs-a-tenant)
5. **Ingest one event**: `POST /v1/events` with your `eventType`, an `observedAt`, and a
   `payload` matching the schema's declared field types. → [Endpoints](#endpoints)
6. **Read it back** through the view: `GET /v1/views/<name>/latest` (or `/series`, `/stream`).
   During development, skip the ~3s catch-up wait with `POST /v1/admin/readmodel/replay`.
   → [Endpoints](#endpoints)
7. **Iterate from real needs, not guesses.** Want to filter on a field you didn't plan for? Add
   it to the view's `queryable` map — no schema change needed if it's already exposed. Want a
   different consumer to see less? Add a second, narrower view over the same schema rather than
   touching this one. Want a different wire format? Add a `Projector`, not a new endpoint.
   → [Dynamic filtering](#dynamic-filtering-query-axes), [Output formats](#output-formats)

## Every request needs a tenant

Every endpoint — including bootstrapping a new tenant — requires an `X-Tenant-ID` header (or,
with `REFRAX_TENANT_RESOLVER=token-claim`, a verified token claim). There is no anonymous path.
Missing or blank → `403 Forbidden`. The database's default `public` schema works out of the box
as a tenant like any other, so `X-Tenant-ID: public` is enough to start without provisioning
anything.

## Endpoints

### `POST /v1/events` — ingest

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
# -> 202 Accepted  {"seq":1}
```

- `eventType` must match a loaded schema's `eventType`, or the request is rejected.
- `eventId` is optional; a random UUID is assigned if omitted. Re-posting the same `eventId` for
  the same tenant is a no-op: `202 Accepted {"status":"duplicate"}`, not an error and not a second
  row — `event_id` is unique per tenant schema (each tenant's `events` table lives in its own
  Postgres schema, so uniqueness only has to hold within one).
- `observedAt` is the event's **valid-time** (when it happened), independent of when it was
  recorded. It defaults to "now" if omitted.
- `payload` fields not declared in the schema are silently ignored — they are accepted but never
  stored or exposed. Fields declared in the schema but sent with the wrong type
  (e.g. `"value": "abc"` for a `NUMBER` field) are rejected with `400`.
- Ingestion never touches read models directly. A scheduled catch-up (every 3s) folds new log
  entries into `reading_latest`/`reading_series`, so a just-posted event typically becomes
  visible through the view endpoints a few seconds later, not instantly.

### `GET /v1/views/{view}/latest` — current state

One row per matching identity — the most recent value.

```bash
curl -H "X-Tenant-ID: public" "http://localhost:8787/v1/views/air-quality-full/latest?sensor=sensor-42"
# -> 200 {"id":"urn:refrax:AirQualityReading:sensor-42","type":"AirQualityReading","observedAt":"2026-09-11T11:00Z","unit":"ug/m3","metric":"PM2.5","value":18.9}
```

- No match → `404`.
- The filter matches **more than one** distinct entity → `400`, since "latest" only makes sense
  for a single identity:
  ```json
  {"error":"Axis filter matched multiple identities on /latest; narrow the filter or use /series"}
  ```
  (e.g. filtering only by `metric=PM2.5` when several sensors report that metric — narrow with
  `sensor=...` too, or use `/series` instead.)

### `GET /v1/views/{view}/series` — time-series slice

All matching rows, ordered by valid-time then sequence, capped at 1000 rows per call.

```bash
curl -H "X-Tenant-ID: public" \
  "http://localhost:8787/v1/views/air-quality-full/series?sensor=sensor-42&from=2026-09-11T10:00:00Z&to=2026-09-11T12:00:00Z"
# -> 200 [{"seq":1,"event":{...}}, {"seq":2,"event":{...}}, ...]
```

`from`/`to` are the only params reserved on `/series` in addition to declared axes (see
[Filtering](#dynamic-filtering-query-axes) below); both are optional and filter on `observedAt`
(valid-time), inclusive on both ends.

### `GET /v1/views/{view}/stream` — cursor-based slice

Like `/series`, but ordered and paged by log sequence (`seq`) rather than time, and without axis
filtering — meant for a consumer walking the whole view forward.

```bash
curl -H "X-Tenant-ID: public" "http://localhost:8787/v1/views/air-quality-full/stream?after=0"
# -> 200 [{"seq":1,"event":{...}}, {"seq":2,"event":{...}}, ...]  (seq > after, up to 1000 rows)
```

`after` defaults to `0` if omitted or unparseable.

### `POST /v1/tenants` — provision a tenant

Creates a new Postgres schema for the given tenant name and runs migrations against it.
Restricted to identities listed in `REFRAX_TENANT_ADMIN_IDS` (`admin` by default) — not real
authentication (Refrax doesn't do that), just a minimal allowlist on top of whatever the gateway
or token issuer already vouches for.

```bash
curl -X POST http://localhost:8787/v1/tenants \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: admin" \
  -d '{"schema":"demo"}'
# -> 201 Created
```

A non-allowlisted caller gets `403 {"error":"Not allowed to provision tenants."}`.

### Admin: `POST /v1/admin/readmodel/replay` and `/reproject`

Operational, **not secured beyond the ordinary tenant header** — don't expose these publicly.

- `replay`: wipes and rebuilds both read models for the calling tenant from the log, from
  sequence 0. Useful after a schema change, or to force catch-up without waiting for the next
  scheduled tick.
  ```bash
  curl -X POST -H "X-Tenant-ID: public" http://localhost:8787/v1/admin/readmodel/replay
  # -> 200 {"status":"rebuilt","throughSeq":4}
  ```
- `reproject?eventType=...&identityField=...&identity=...`: re-derives a single entity's
  `reading_latest` row from the log, touching nothing else.

## Dynamic filtering (query axes)

Views declare which fields can be filtered — nothing else. A view's JSON config has a
`queryable` map of axis name → `{ field, match }`:

```json
{
  "name": "air-quality-full",
  "eventType": "AirQualityReading",
  "exposes": ["metric", "value", "unit"],
  "queryable": {
    "sensor": { "field": "sensorId", "match": "exact" },
    "time":   { "field": "validTime", "match": "range" },
    "metric": { "field": "metric", "match": "exact" },
    "value":  { "field": "value", "match": "exact" }
  }
}
```

Query with `?<axisName>=<value>` for equality, or `?<axisName>.<op>=<value>` for a comparison,
where `<op>` is `gt`, `gte`, `lt`, or `lte`. Filtering on anything not listed in `queryable`
(even a field the view exposes) is rejected — the axis list, not the exposed-fields list, is
what can be queried:

```bash
curl -H "X-Tenant-ID: public" "http://localhost:8787/v1/views/air-quality-full/series?bogus=xyz"
# -> 400 {"error":"Undeclared query axis: bogus"}
```

**What operators are allowed depends on the field's declared type** (from the event schema, not
the view):

| Schema type | Allowed ops             | Value format                    |
|-------------|--------------------------|----------------------------------|
| `STRING`    | `eq` only                 | any string                       |
| `NUMBER`    | `eq`, `gt`, `gte`, `lt`, `lte` | a number (`BigDecimal`-parseable) |
| `BOOLEAN`   | `eq` only                 | `"true"` / `"false"` (case-insensitive) |
| `TIMESTAMP` | `eq`, `gt`, `gte`, `lt`, `lte` | ISO-8601, e.g. `2026-09-11T12:00:00Z` |

An operator the field's type doesn't support is rejected with a `400` naming the field.

Tested examples against the bundled schema (`sensorId`: identity/string, `metric`/`unit`:
string, `value`: number):

```bash
# exact match on a string property
curl -H "X-Tenant-ID: public" ".../air-quality-full/series?metric=NO2"

# numeric comparison
curl -H "X-Tenant-ID: public" ".../air-quality-full/series?sensor=sensor-42&value.gte=15"
curl -H "X-Tenant-ID: public" ".../air-quality-full/series?sensor=sensor-42&value.lt=15"

# combine axes freely (implicit AND)
curl -H "X-Tenant-ID: public" ".../air-quality-full/series?sensor=sensor-42&value.gte=10&value.lte=20"
```

### The identity axis is special

`sensor` above maps to `sensorId`, the schema's `IDENTITY` field. Identity axes:

- only support `eq` — `sensor.gt=...` is rejected (`"Comparison operators are not supported for
  identity filters"`);
- resolve to an exact `entity_id = urn:refrax:AirQualityReading:<value>` match, not a JSONB
  lookup, using the same URN-minting logic the capability gate uses on ingest — so a filter can
  never disagree with how the entity was actually identified;
- cannot be used if the schema's identity is composite (more than one identity field) — filtering
  on one component of a multi-part identity is rejected, since it cannot narrow to one entity.

### Range axes can't be filtered directly

An axis declared `"match": "range"` (only `validTime` today) is for `/series`'s `from`/`to`
parameters, not direct querying:

```bash
curl -H "X-Tenant-ID: public" ".../air-quality-full/series?time=2026-09-11T10:00:00Z"
# -> 400 {"error":"Range axis 'time' is not queryable directly; use from/to on /series"}
```

### Personal data can't be an exact-match axis

If a schema field is flagged `personalData: true`, declaring it as an **exact-match** query axis
fails at boot (not at request time) — `ViewValidationException`, app won't start. This closes the
obvious way personal data could otherwise be probed by equality (`?ssn=123-45-6789` would
otherwise let you test guesses). See [Personal data](#personal-data) below for the rest of that
story.

## Output formats

Every read endpoint (`latest`, `series`, `stream`) takes an optional `?format=` parameter.
Default is `native`. Unknown format → `400 {"error":"Unknown format: <name>"}`.

### `native` (default)

Refrax's own flat shape: `id`, `type`, `observedAt`, then each exposed property as a plain value.

```json
{
  "id": "urn:refrax:AirQualityReading:sensor-77",
  "type": "AirQualityReading",
  "observedAt": "2026-09-11T10:30Z",
  "unit": "ug/m3",
  "metric": "NO2",
  "value": 30.0
}
```

### `ngsi-ld`

```bash
curl -H "X-Tenant-ID: public" ".../air-quality-full/latest?sensor=sensor-77&format=ngsi-ld"
```

```json
{
  "id": "urn:refrax:AirQualityReading:sensor-77",
  "type": "AirQualityReading",
  "unit": {"type": "Property", "value": "ug/m3"},
  "metric": {"type": "Property", "value": "NO2"},
  "numericValue": {"type": "Property", "value": 30.0},
  "@context": [
    {
      "unit": "https://qudt.org/schema/qudt/unit",
      "metric": "https://smartdatamodels.org/dataModel.Environment/typeOfMeasurement",
      "numericValue": "https://qudt.org/schema/qudt/numericValue"
    },
    "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld"
  ]
}
```

Note `value` became `numericValue`: NGSI-LD reserves the key `value` for a Property's own
structural wrapper, so a domain field named `value` would collide with it. The projector detects
the collision and derives a safe term from the field's vocabulary URI (here, the last path
segment of `qudt/numericValue`) — nothing hand-mapped per field, and the `@context` entry always
matches whatever term was actually used. A `RELATIONSHIP`-role field renders as an NGSI-LD
`Relationship` (`object` instead of `value`) the same way.

**Known gaps** in the NGSI-LD output today (tracked in the [ROADMAP](../ROADMAP.md) implicitly
via the OGC SensorThings/compile-time work, called out here since they're specific to this
projector): `observedAt` is not yet emitted as a top-level NGSI-LD temporal property; a `unit`
field is not converted to the NGSI-LD `unitCode` convention; and `@context` is inlined rather
than published as a dereferenceable URL.

### Adding a format

A format is a `Projector` bean (`format()` + `project(ExposableEntity)`); see
[`NativeProjector`](../core-platform/src/main/java/io/refrax/projection/NativeProjector.java) and
[`NgsiLdProjector`](../core-platform/src/main/java/io/refrax/projection/NgsiLdProjector.java) for
the two that exist. A projector only decides shape — it receives the same gated
`ExposableEntity` every other projector does, so it cannot see a field the schema/view didn't
already allow. Register it as a CDI bean and it's picked up automatically; no endpoint or view
changes needed, since views and projectors are orthogonal.

## Personal data

Declaring `"personalData": true` on a schema field does two things today:

1. It's dropped from every projection and read model — `ReadModelProjection` filters it out
   before anything is persisted, so it never reaches `reading_latest`/`reading_series` in the
   clear, regardless of which view or format is requested.
2. It can't be declared as an exact-match query axis (see above).

What it does **not** yet do: enable erasure of already-ingested personal data from the immutable
log itself (crypto-shredding). See the [ROADMAP](../ROADMAP.md) for that distinction.
