# Versioning and branch support

Refrax uses [Semantic Versioning](https://semver.org/): `MAJOR.MINOR.PATCH`.

- **MAJOR** — breaking changes. Not applicable yet (pre-`1.0.0`), but the scheme is in place from
  the start.
- **MINOR** — new features, backward compatible.
- **PATCH** — bug fixes only, no new features.

This is about the versioning of Refrax itself as a released artifact — a separate question from
whether the *HTTP API* ever needs its own `v2`, which is still open (see
[ROADMAP.md](../ROADMAP.md)).

## Support window

Exactly two minor lines are ever supported at once: the one currently under active development,
and the one immediately before it. A bug found in the previous minor line gets a patch release
there; anything older than that does not.

The window shifts forward, by one, every time a new minor version starts — it never grows to
cover more than two lines at a time.

**Example:** development is on `v0.3.0` → `v0.2.x` receives patch releases (`v0.2.1`, `v0.2.2`,
...) for bugs found in it; `v0.1.x` and earlier get nothing further. Once `v0.4.0` starts,
the window shifts: `v0.3.x` becomes the supported patch line, and `v0.2.x` stops receiving
patches.

## In practice

- Active development happens on `main` (the next `MINOR`/`MAJOR`).
- The currently-supported previous line lives on its own branch (e.g. `release/0.2`), separate
  from `main`.
- A fix that applies to that supported line is fixed on `main` first, backported (cherry-picked)
  to the release branch if it applies there too, and released as a new `PATCH` off that branch.
- When the next `MINOR` ships, its own release branch is cut, and the branch for the line that
  just fell out of the support window is retired — no further patches, even if a bug is found in
  it.
