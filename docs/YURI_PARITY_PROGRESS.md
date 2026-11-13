# Ongoing Yuri regression and behavior audit

The original goal remains unfinished. Green builds, inventories and selected
probes do not establish exhaustive equivalence or absence of bugs. Yuri remains
clean and read-only at `1cb86ad94ce1f01aa9b715577163c8ecc47b7def`; Perihelion UI is native.

The [requirements ledger](YURI_PARITY_REQUIREMENTS.json) retains all 102 donor
definitions: 77 mappings with requirement-level completeness unproven, 22
presentation exclusions and three deferred definitions. No original scope was dropped.

[Loop 1](YURI_PARITY_LOOP_1_2026-10-18.md) corrected mining/potion suppression,
Auto Swap phase and slot lifetime, Anti Fireball source behavior/action facts,
and normal potion/MLG completion. [Loop 2](YURI_PARITY_LOOP_2_2026-10-18.md) adds
the missing Blink/Lag Range position markers and scalar accepted-position tracking,
corrects Lag Range end releases to Motion PRE, and matches Auto Pot first-effect
selection. No donor managers or architecture were imported.

[Loop 3](YURI_PARITY_LOOP_3_2026-10-18.md) corrects Lag Range sampling and a
confirmed potion/restoring-hotbar regression. The previous verified JAR fails the
new actual-module restoration assertion; the rebuilt checkpoint passes client,
server and manual-input variants. Its 424 unit tests, full Forge/OptiFine suites,
112 recovery and 66 position/render cases pass.

Those latest suites use the verified pre-account source baseline plus all eight
current parity source/fixture edits. A separate active task is adding account
management in the same checkout. Combined builds failed first on then-missing UI
classes and then a shared Loom-cache setup error; combined integration remains
unverified. No account files were edited or removed. A narrow hash-guarded loop-3
rollback preserves unrelated source and passes dry run/restoration/later-edit
refusal/checkpoint checks. The tested JAR is explicitly named isolated and was
not copied over the evolving shared checkout's canonical distribution.

An earlier OptiFine 60-cap bridge phase placed zero blocks. Diagnostic and later
final runs pass, but its cause remains unestablished and no Scaffold fix is claimed.
Keep the failure and diagnostics in subsequent reports.

Next: obtain a stable combined build and packaged verification after account work
settles; broaden persistent/restoring hotbar and rotation interleavings; audit
adjacent item predicates and all remaining settings/mode transitions. The three
deferred definitions have source-level requirement notes in the ledger and remain
unimplemented. Native ownership/FIFO/session protections remain. No donor runtime
or authenticated server was run; exact two-client parity and live bypass
acceptance remain unverified.
