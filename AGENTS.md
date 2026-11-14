# Perihelion

## Working style

- Complete the requested work without unnecessary confirmation. Respect requests
  to review, explain or plan before editing.
- Read the existing implementation first. Keep changes focused, preserve unrelated
  work and make substantial changes easy to roll back.
- Keep interfaces and solutions simple; avoid speculative features and scaffolding.

## Donor references

- `../donor_clients/` contains Yuri, Arsenic, OpenMyau-Plus, Project-Coolware and
  OpenB4. UniversalAccountManager is in `../alt mannges/UniversalAccountManager/`.
  Keep these checkouts read-only; do not run donor builds, clients or scripts.
- Inspect Perihelion first, then donor modules and supporting hooks. Match their
  behavior through Perihelion's lifecycle, settings, events, shared controls and
  cleanup; do not import donor managers, event buses or config architecture.
- Consult `docs/DONORS.md` for provenance and known issues, especially OpenB4's
  script loader and AutoRequeue. Record substantial ports and intentional differences.
- Account features use Perihelion's UI, client-thread session updates and private vault.

## Verification

- Run checks relevant to the change. Distinguish builds, local runtime checks and
  live-server results; report failures and unverified behavior honestly.
- For visual changes, take and inspect screenshots of the affected screens in the
  running client, fix visible issues and include useful screenshots in the handoff.
  Check that the affected controls still work and saved layouts/settings survive.
- After client changes, launch the updated Minecraft client, verify it stays running
  and leave it open for the user to test. Reuse a running instance if it is current.
- Keep the final response brief: what changed, what was checked and any limitations.

## GitHub

- Keep the repository private. See `docs/GITHUB.md` for setup and commands.
- Check the branch and working tree before Git operations. Stage only this task's
  changes; exclude credentials, local game data and unrelated work.
- When the task is complete and relevant checks pass, automatically commit and push
  its changes to the appropriate remote branch without asking again, unless the
  user requested otherwise. Report the commit and push result; flag any CI failures.
- Never force-push or rewrite shared history unless explicitly requested.
