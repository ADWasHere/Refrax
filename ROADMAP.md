# Roadmap

This file collects what Refrax does not do yet: features planned on the way to `v1.0.0`, and the
architectural direction beyond it. For what already works today, see [README.md](README.md).

## Toward v1.0.0

- **OGC SensorThings projector.** Today only the native and NGSI-LD projections exist. Views and
  the capability gate are already projector-agnostic, so this is additive: one new projector, not
  a rewrite of the gate or the views.
- **PostGIS-backed geo read model.** TimescaleDB is live for time-series read models today; a
  geo-shaped read model for spatial queries is not built yet.
- **Full crypto-shredding for GDPR erasure.** Today, fields declared `personalData` in the schema
  are excluded from every projection and read model — they simply never leave the gate. That
  satisfies data minimisation but not erasure of what is already in the log. The planned version
  encrypts personal-data fields at rest with a per-subject key held in a separate, mutable key
  store; erasure destroys the key, so the event stays immutable while its personal content becomes
  permanently unreadable, including in backups and derived read models.
- **Strengthen the exposability guarantee toward compile-time.** The capability gate enforces
  deny-by-default today at runtime: a projector cannot be handed an internal field because the
  `ExposableEntity` it receives never contains one. Whether that guarantee can be pushed further,
  into something the compiler itself rejects (e.g. sealed/phantom types over field roles), is an
  open direction rather than a committed one.

## Beyond v1.0.0

Direction, not a committed design. Refrax today is closer to an out-of-the-box tool — a single
artifact you configure and run — than an extensible platform: extension points don't exist yet.
The plan is to close that gap deliberately once `v1.0.0` is out, rather than let the core keep
growing feature by feature indefinitely.

- **Refactor the core toward an SPI.** Split what's one artifact today into a core — the event
  log, the capability gate, views, multi-tenancy, read models, and the `native` projector as the
  only bundled default — and separate extension packages for anything domain- or
  standard-specific (an NGSI-LD projector, industry-flavored schemas and views, and so on). What
  the SPI itself looks like is deliberately not designed yet; that's the work this item is.
- **Split the repository accordingly.** Once the SPI boundary exists, one repo becomes several:
  the core, and one repo per extension — e.g. a smart-city extension bundling the NGSI-LD
  projector with the air-quality-flavored schema and views that currently live in core. Domain
  work can then version and evolve independently of the core, and the community can publish an
  extension without forking it.
- **Expand logging and monitoring substantially.** Today's observability is application logs;
  this is one of the larger gaps between "runnable" and "operable."
- **Work out schema versioning and generations properly.** Every event currently carries a
  `schemaVersion` field hard-coded to `"v1"` — there is no real story yet for how a schema
  evolves, how events written under different generations of it stay readable and projectable
  together, or what that implies for rebuilding read models. This needs designing, not just
  building.
- **Event-log extraction under storage pressure.** A way to archive or offload older parts of the
  log if it grows large enough to matter, without losing the ability to replay or rebuild
  read models from what remains. Not designed yet.
- **Design horizontal scaling from the actual failure modes, not a technology choice.** Today
  there is one Postgres instance behind however many Refrax instances you happen to run by hand.
  Scaling either side is undesigned: does the scheduled read-model catch-up (per-instance,
  skip-if-already-running) coordinate correctly across multiple app instances, or does it race?
  What would "multiple databases" even mean here — sharding tenants across several Postgres
  instances, read replicas for the read models, something else — and what has to change in
  `TenantAwarePanache`'s single-datasource assumption to support it? The point of this item is to
  enumerate what actually breaks first, not to pick a scaling technology before that's understood.
- **Define an API versioning policy.** Every endpoint already sits under a `/v1/...` prefix, but
  nothing says yet what actually triggers a `v2`, whether `v1` and `v2` would run side by side,
  or for how long a version stays supported after a newer one ships. Right now that gap is an
  unwritten policy, which is the same as having none.

None of this is committed on a timeline. Refrax is developed in the author's own time; this file
tracks direction, not promises.
