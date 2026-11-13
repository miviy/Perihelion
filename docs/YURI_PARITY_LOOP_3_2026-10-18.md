# Yuri regression and behavior loop 3

This pass fixes a reproduced hotbar restoration regression and Lag Range sampling.
All parity edits are applied in the shared checkout. **Latest runtime evidence is
for a coherent validation checkpoint, not the evolving combined account-manager
checkout.** The original all-module audit remains unfinished.

Reference: clean, read-only Yuri `1cb86ad94ce1f01aa9b715577163c8ecc47b7def`.
The pre-edit archive contains 691 verified source files at
`.backups/before-yuri-loop-3-20261018-195734/source.tar.gz`, SHA-256
`7f437a822ed7d32dd9dafbb157ab20bcb8a300817e23fd3dcbb21b91b0492cc1`.
No donor code/build was executed or donor file edited.

## Corrections and direct regression evidence

Lag Range now invokes its delay sampler only when entering a new active range
cycle, including Blink mode. It uses source-equivalent ThreadLocalRandom behavior,
returns fixed endpoints without consuming randomness, sorts reversed endpoints
and retains the exclusive upper endpoint. Idle, out-of-range, active, expired-lease
and ending PRE updates no longer consume extra samples.

A persistent potion selection previously committed over an active restoring Auto
Swap baseline. The new actual-module assertion **fails on the preceding verified
JAR** at the eventual restoration step (`forge/run-smoke-forge-pa4hyf9c`). The
adapter now retains the baseline whenever an eligible restoring hotbar request
exists. Uncontested persistent completion still stays selected. The correction
uses existing native controls; no donor SlotManager/global architecture was imported.

The rebuilt checkpoint passes actual potion completion with restoring client and
server Auto Swap, exact potion-slot/C08 order, resumed mining, eventual original-slot
restoration, and manual selection preservation. Existing standalone potion/MLG
persistence and interrupted cleanup checks still pass. Broader MLG, expiry and
multiple-routine interleavings remain explicit work.

## Verification scope

Separate account-manager work began adding files in the same checkout immediately
after this pass's backup. The first combined build failed on then-missing
`AccountScreen`/`AccountUi`; a later attempt failed during shared Essential Loom
cache setup (`client-patched.jar` missing). Those failures are retained in the JSON.
The account UI files subsequently appeared, but combined integration remains unverified.
No account source was removed or altered to make these checks pass.

The validation copy is the verified pre-account source archive plus **all eight
current parity source/fixture edits**. Their hashes still match the shared checkout.
Its `check assemble`, architecture, wrapper, pinned Mixin and JAR checks pass.
All **424 unit tests pass** (161 core, 263 Forge), zero failures/errors/skips.
Three new cases cover lazy sampling/endpoint behavior and eligibility of hidden,
expired, cleared, disabled and old-world restoring requests. The nine pure sampler
suite cases also passed independently against the current files under Java 8.

Both full packaged suites pass on identical JAR bytes and fixture sources:

| Validation runtime | Evidence |
| --- | --- |
| Forge / Minecraft 1.8.9 / Java 8 | `.backups/yuri-loop-3-validation/forge/run-smoke-forge-dd04lnku/result.json` |
| Same JAR with OptiFine M5 | `.backups/yuri-loop-3-validation/forge/run-smoke-optifine-6kny3w58/result.json` |

Each passes 112 recovery/hotbar checks, 66 position/render/release checks,
44 fireball checks, 310 rotation checks, 124 movement checks, 47 final-helper
checks, 72 Scaffold terrain cases and the existing full suite. Both place eleven
bridge blocks at both frame caps. Shutdown leaves zero subscriptions/queued work,
clears references and reports `stdoutError=false`.

Tested artifact: `forge/build/libs/Perihelion-yuri-loop3-isolated-20261018.jar`
(2,249,733 bytes), SHA-256
`d80fc7ffe84881a5afcb1d813e08556e4b438a0d49109808e3e856da1414ae12`.
It was **not** copied over the shared checkout's canonical distribution.
[JSON evidence](YURI_PARITY_LOOP_3_2026-10-18.json) records the tested source map,
owned live-file matches, copied JAR hashes, unit XML, direct failing-before/passing-after
runs, shared-source drift and combined-build failures.

## Rollback and remaining work

`scripts/restore-yuri-loop3.py` is a hash-guarded rollback for this pass's eight
source/fixture edits and the helper addition. It leaves shared documentation and
unrelated account/UI source intact. Actual-checkout dry run, isolated restoration,
later-edit refusal, recovery checkpoint hashes and preservation of all captured
concurrent source paths pass. No live restoration was applied. The older full-update
rollback remains historical and was not expanded to absorb the account task's work.

Next is a stable combined build and packaged verification after account work
settles, followed by broader hotbar/rotation interleavings and the requirement-level
settings/mode audit. The three deferred modules have source-level requirement notes
and remain unimplemented. The 77 mappings and 22 UI exclusions retain their original
scope in the [ledger](YURI_PARITY_REQUIREMENTS.json).

The earlier OptiFine zero-block bridging failure remains unexplained; passing later
runs do not establish its cause or a Scaffold fix. No donor runtime or authenticated
server was run. Exhaustive equivalence, absence of regressions, exact two-client
parity and live anti-cheat acceptance remain unverified.
