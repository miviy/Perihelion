# Perihelion

## Working style

- Complete the requested work without unnecessary confirmation. Respect requests
  to review, explain or plan before editing.
- Read the existing implementation first. Keep changes focused, preserve unrelated
  work and make substantial changes easy to roll back.
- Keep interfaces and solutions simple; avoid speculative features and scaffolding.

## Donor references

- `../donor_clients/` contains Yuri, Arsenic, OpenMyau-Plus, Project-Coolware,
  OpenB4 and OpenAdjust. UniversalAccountManager is in
  `../alt mannges/UniversalAccountManager/`.
  Keep these checkouts read-only; do not run donor builds, clients or scripts.
- Inspect Perihelion first, then donor modules and supporting hooks. Match their
  behavior through Perihelion's lifecycle, settings, events, shared controls and
  cleanup; do not import donor managers, event buses or config architecture.
- Consult `docs/DONORS.md` for provenance and known issues, especially OpenB4's
  script loader and AutoRequeue. Record substantial ports and intentional differences.
- OpenAdjust is a separate restored-source reference, not Perihelion's existing
  Adjust style. See its pinned revision and source map in `docs/DONORS.md` before
  borrowing behavior; its Java 21/LWJGL 3 setup is not Perihelion's toolchain.
- Account features use Perihelion's UI, client-thread session updates and private vault.

## Verification

- Run checks relevant to the change. Distinguish builds, local runtime checks and
  live-server results; report failures and unverified behavior honestly.
- For visual changes, capture and inspect screenshots when useful, fix visible
  issues and include useful screenshots in the handoff. If capture is unavailable,
  report the limitation and continue the checks you can. Check affected controls
  and confirm saved layouts/settings survive.
- For rendering, input, Mixins or gameplay changes, run the packaged client and
  verify the affected behavior. When launching for user testing, verify it stays
  running and leave it open. Reuse a running instance if it is current.
- Documentation-only changes need a diff and link review, not a build or game
  launch. See `docs/GITHUB.md` for code and workflow checks.
- Keep the final response brief: what changed, what was checked and any limitations.

## GitHub

- See `docs/GITHUB.md` for setup and commands.
- Before editing, check the branch and working tree. Start each independent task
  on a descriptive `feature/`, `fix/` or `chore/` branch from current `origin/main`.
  Continue the existing task branch for follow-up work. Never work directly on
  `main`. If unrelated edits or another task are present, use an isolated worktree
  under `.tools/worktrees/`; do not stash, move or commit another task's work.
- Stage only this task's changes; exclude credentials, local game data and
  unrelated work. When relevant local checks pass, automatically commit, push the
  task branch and open or update a pull request targeting `main`. These actions
  are authorized for requested implementation work; do not ask again unless the
  user requested otherwise or the host permission system requires approval.
- Include the problem, resulting behavior, validation and material limitations
  in the PR. Use a draft when checks are incomplete or known failures remain.
  Report the PR link and actual CI state; fix failures caused by this task.
- Leave merging for the user unless explicitly asked to merge. Never push directly
  to `main`. After a merge, update local `main` only when its working tree is clean.
- Respect the installed main-branch push guard; never bypass it with `--no-verify`.
- Never force-push or rewrite shared history unless explicitly requested.
