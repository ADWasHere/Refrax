# Logging levels

Pick a level by what it means, not by habit:

- **ERROR** — a safeguard that was supposed to make something impossible didn't hold (e.g. a
  value reached the database and failed a type cast despite the filter's own validation). Someone
  needs to look at *why the guard failed*, not just handle this one request.
- **WARN** — the request was rejected for a reason the caller (or their own misconfiguration)
  caused — bad input, no resolvable tenant identity, an unauthorized action, a reference to a
  tenant that doesn't exist. Self-resolving (the caller fixes their request), but must be
  reconstructable later: log the resolved caller identity and the resource/tenant they attempted,
  not just "denied."
- **INFO** — normal, expected lifecycle: startup, schema migration, a tenant provisioned, an
  admin-triggered replay. Things that happen rarely and are always worth a permanent record.
- **DEBUG** — routine, per-event or per-batch detail (an event ingested, one projected into a
  read model, a batch caught up). High-frequency by nature; off by default so it isn't production
  noise, on when troubleshooting a specific request or event.

When adding a log statement, ask: does a human need to act on this immediately (ERROR)? Is it
worth attention but the caller/system already handled it (WARN)? Is it a normal thing that
happens rarely enough to always want a record of (INFO)? Or is it detail only useful while
actively troubleshooting (DEBUG)?
