# Perihelion donor references

UniversalAccountManager is a read-only behavior reference at
`../alt mannges/UniversalAccountManager/`, upstream
https://github.com/sc6the/UniversalAccountManager, revision
`9e9b2479af877d90a4618bb4bb685009e19d789a`. Do not edit that checkout or run its
bundled JARs, build, client or scripts. Account features use Perihelion's own
runtime, UI theme, client-thread session boundary and private credential vault.

In addition to the workspace's existing donors, OpenB4 is available as a
read-only behavior reference at `../donor_clients/OpenB4/` (source project:
`OpenB4/` inside that checkout).

- Upstream: https://codeberg.org/abusez/OpenB4
- Reviewed revision: `3d63944cc79be890c013cc79e5ed92d3533b48a5` (`u20`).
- Inspect Perihelion first, then the relevant donor code. Reimplement behavior
  through Perihelion's lifecycle, settings, events, shared controls and cleanup.
- Keep donor source unchanged; do not import its architecture or execute its
  build/client/scripts as part of ordinary reference work.
- Read the OpenB4 entry in `docs/DONORS.md` before using its script loader or
  AutoRequeue behavior. The review found security defects in both paths.
- Record substantial behavior ports and their differences in `docs/DONORS.md`.
