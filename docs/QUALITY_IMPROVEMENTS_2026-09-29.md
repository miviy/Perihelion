# Gameplay quality improvements — 2026-09-29

Latest source follow-ups and behavior corrections are recorded in
[the October 11 audit](YURI_LATEST_AUDIT_2026-10-11.md). This report
preserves its original implementation and verification baseline.

78 gameplay modules plus ClickGUI are registered. All gameplay modules, including the new
Clutch, remain disabled and unbound by default. Existing native lifecycle, config, events,
shared controls and placement validation are retained.

## Inventory protection and looting

Manager now sorts without dropping anything unless `drop-items=true` is explicitly enabled.
This also applies when importing an older preset without that setting. With dropping enabled:

| Setting | Default | Meaning |
| --- | --- | --- |
| `drop-inferior` | true | Remove inferior equipment; weapon/tool/bow Manage toggles are honored. |
| `drop-junk` | false | Enable the legacy miscellaneous-item cleanup list. |
| `drop-utility-blocks` | false | Permit discarding non-building blocks, including TNT and crafting tables. Requires Manage Block. |
| `drop-bad-potions` | false | Permit harmful-potion cleanup. Requires Manage Potion. |
| `drop-shears` | false | Explicitly permit shears cleanup. |
| `protect-named` | true | Protect custom-named and lore-bearing items from moving or dropping. |
| `protected-slots` | empty | Comma-separated slots: 1–9 hotbar, 10–36 inventory. Empty protected destinations are also reserved. |
| `protected-items` | empty | Comma-separated registry IDs, e.g. `minecraft:tnt,minecraft:compass`. The minecraft namespace is optional. |

Existing Drop Food/Drop Archery switches also require Allow Item Dropping before deletion.
The existing menu-item name protections remain active. Arrow relocation is skipped when
protected slots are configured because vanilla shift-click cannot reserve a destination.
Item scores and protection flags are computed once per action; inventory analysis is rebuilt
after mutations so slot swaps cannot leave stale scoring data. Name-filter tokens are static.

Stealer adds `smart-looting=true`: armor and tool/weapon upgrades are selected ahead of
supplies and compared with both carried and equipped items. `block-limit=256`,
`projectile-limit=64`, and `arrow-limit=64` are resource **targets**: whole stacks are
transferred until the target is reached, so the final stack may exceed it. Zero skips that
resource. Turning off smart looting restores the earlier selection policy; Steal Trash
explicitly bypasses useful-item filtering and these targets. Full inventories can still
merge matching partial stacks. Nonmergeable items do not block other transferable loot,
and remaining useful items prevent premature auto-close when space is unavailable.

## Combat and recovery

- **Bow Aimbot:** silent rotation keeps its own activation-local history, resets for a new
  target or lost control ownership, and pauses for screens, lost focus, death and Freecam.
  Prediction solves bounded, discrete flight with draw-dependent speed, arrow drag and
  gravity, target velocity and the launch hand offset. Without Through Walls, the proposed
  arc is checked against blocks. This is a prediction, not a guarantee against later target
  direction changes or server-side differences.
- **AutoPot:** every potion effect must be beneficial; a later harmful effect invalidates
  a mixed potion. Selection requires at least one currently useful effect. Pending throws
  are cancelled for a screen, loss of focus, death, other item use, Freecam or Scaffold.
  The held potion and winning controls are checked again at execution.
- **Auto Projectile:** retains Aura-combo targeting and its donor timing. `target-range`
  is now displayed as Combo Range and accepts 4–6 blocks, default 6. Existing values within
  that interval are preserved; older values above 6 use the default through the existing
  config validation path. Aura's seek range remains the outer targeting limit; throwing
  occurs beyond 3 and strictly below 6 blocks. Target identity, liveness, distance, hurt
  state, item type and control ownership are rechecked before use. Cancelled/failed actions
  release claims immediately.
- **Aura / AutoClicker:** fractional click deadlines preserve the requested average CPS
  across tick boundaries. At most one click attempt occurs per client tick. Missed deadlines
  after a stall are reset rather than replayed in a catch-up burst. Target, blocking and
  interaction restrictions can still reduce actual attacks below the configured CPS.

## Scaffold and Clutch

Scaffold gains optional `require-use=false`, `holding-blocks=false`,
`pitch-condition=false`, `minimum-pitch=35`, `maximum-pitch=90`, and
`block-selection=FIRST`. Pitch endpoints are order-independent. Block selection supports
FIRST (existing behavior), CURRENT (prefer the current valid stack), and LARGEST (stable
current-slot tie breaking). Conditions are checked again before committing a placement.

New **Clutch** (`clutch`) uses recent player knockback to arm a bounded block catch:

- `knockback-window=500` ms (100–2000).
- `search-depth=3` blocks (0–5).
- `minimum-drop=5` blocks (2–20).

It requires a falling player, a clear drop and a reachable supporting face. Candidate blocks
must intersect the projected landing footprint. It uses normal native strict placement with
owned rotation/hotbar claims, and yields to Scaffold, SafeWalk, Freecam, Block In and Bed
Defender. Voluntary falls do not trigger it. A successful catch briefly extends the current
fall episode; missing support does not cause an endless rotation lock. Disabling, world
changes and faults retire both listeners and controls through ModuleContext. It does not
teleport or freeze the player.

## Rendering cost

Trajectories uses the same arrow speed/gravity/drag definitions as Bow Aimbot. It caches the
bounded trajectory preview for the current client tick instead of recomputing up to 300
collision-query steps on every render frame. Item changes invalidate the cache; releasing
a bow clears its preview immediately. Drawing remains frame-driven; the prediction is
sampled once per client tick.

## Rollback

The original source is retained in `.backups/before-quality-fixes-20260929-092937/`.
`source-only.tar.gz` contains 307 original files with per-file hashes in
`source-manifest.json`. Its SHA-256 is
`61cb55ad0a228630d2ca8ea1465eb2a9fceccb3a84fd9cfddfef040282be816b`.

Run `python3 scripts/restore-quality-fixes.py` to verify the rollback without changing files.
Add `--apply` to restore this batch. The helper checks archive and current affected-file
hashes, refuses to overwrite later edits, and backs up the affected files before restoring.
Rebuild after restoration; generated JARs and test worlds are not source rollback targets.

Verification results are recorded in [VERIFICATION.md](VERIFICATION.md).
