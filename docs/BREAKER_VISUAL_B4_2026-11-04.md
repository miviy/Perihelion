# Breaker Visual mode and closer B4 controls — 2026-11-04

This follow-up extends the earlier layered-defense repair. The source reference
remains read-only OpenB4 `3d63944cc79be890c013cc79e5ed92d3533b48a5`:
`BedAuraModule`, `BlockBreaker`, `BlockUtils`, `PlayerUtils` and the actual
`assets/clickgui/modules.json` settings. No donor code was built or executed.

## Visual mode

Select **Breaker → Mode → Visual**. It shows the least estimated mining-time
route found by the bounded ray planner from the current position, using actual
block bounds, both bed halves, the available hotbar tools and vanilla mining
penalties. Numbered boxes and connecting lines show the order; green is the
next block. The readout gives the next material, suggested tool/slot, block
count and estimated seconds. Estimates include the short handoff between blocks,
but cannot predict network delay, server rejection, movement, or every curved
route through a defense.

Visual does not claim rotations, hotbar, attack or use input, mark Aura busy,
pause Speed, cancel velocity/interaction packets, or send automatic dig/swing
packets. It does not change blocks. Switching from mining first aborts the old
task, then becomes presentation-only. Routes refresh at Rate and after observed
block changes/manual removal. Empty scans also respect Rate rather than running
every update. Own-bed protection and its unknown-home waiting
remain in effect. World/activation cleanup clears the preview.

## Settings and behavior mapping

| B4 control | Native implementation |
| --- | --- |
| Break mode: Legit / Instant / Swap | Added Instant and Swap to the existing Mode control; retained Vanilla/Hypixel for saved profiles and added Visual. |
| Break speed, 1–1.8 by .01 | B4's per-update progress floor in Legit; initial credit and capped step in Swap. It is not a simple mining-speed multiplier. Default 1. |
| FOV, 20–360 by 5 | Bed-pair horizontal view cone, default 360. |
| Range, 1–8 by .1 | B4 range/range step/default 4.5; saved values remain. Configured reach replaces the former controller-reach clamp. |
| Rate, .05–3 seconds by .05 | Search cadence in game updates, default .2 seconds/four updates. Existing mining progresses every update. |
| Break near block | Existing layered planner is now configurable. Default remains true to preserve Perihelion's layered-defense behavior; B4 defaults false. |
| Only while visible | Exposed beds only; covered beds are skipped. Other modes can explicitly target a covered bed when Break Near Block is off, matching B4. |
| Send animations / Silent swing | START/STOP swings remain; optional intermediate swings; silent swings send animation packets without the local arm animation. Existing Visual Swing setting remains supported. |
| Render block outline | Actual target bounds, including bed height. Visual always renders its route. |
| Disable break effects | Controls STOP break effects. |
| Disable right click | Owned use-key release and connection-scoped placement/use-packet suppression while mining. Default true. |
| Ground spoof | Marks the finishing walking packet grounded without changing local position/ground state. |
| Ignore slow | Optional B4 fatigue/water/airborne penalty exclusion, including Swap material adjustments. Default false. |
| Maximize speed | Instant quick countdown 2 instead of 4 updates. Swap bed countdown uses 17 with fatigue. |
| Skip break verification | Explicit client prediction. Default false waits for server block removal/correction, with bounded retry instead of permanent ghost removal. |
| Allow KillAura / Prioritize KillAura | Native busy/rotation arbitration; priority can retire mining for an Aura target. Swap releases tool and aim between START/STOP pulses. |
| Cancel knockback | Same-connection own velocity and explosion cancellation only while an automatic task is active. Default false. |
| Disable bhop | Pauses native Speed callbacks/claims while mining, without toggling the module or losing its settings. Default false. |

Perihelion retains Auto Tool, Move Fix and its match-scoped Whitelist. Mining
settings with no effect in Visual are hidden there. Settings that change mining
policy abort/reset the previous task rather than inheriting damage or tools.

## Intentional architecture and behavior differences

The default native planner peels visible obstructions. Turning Break Near Block
off with Only While Visible off explicitly selects B4 direct-bed targeting,
including covered beds; the server may reject these attempts. Vanilla retains
its existing exposed-only behavior. The peeling planner handles
multiple layers and actual block-position hardness instead of B4's immediate
neighbor-only search and player-position hardness lookup. B4's Maximize Speed glass-as-an-opening shortcut is
not used by the peeling planner. The planner is bounded
and samples rays; this is not a claim of global optimality across all possible
movement paths.

START and STOP require a matching submitted/applied native rotation receipt and
owned tool. Default cover peeling verifies the actual hit; explicit B4 direct-bed
targeting keeps the rotation/tool checks while allowing an obstructing ray hit.
Instant/Swap release aim during countdown/progress gaps and reacquire
it for the finishing action. Aura cannot use an unsent/conflicting angle to
attack concurrently. These native ownership checks deliberately remain stronger
than B4's global rotation/tool state. Packet ownership, world/player/manager
identity, own-bed tracking, server correction and disable cleanup remain native.

Normal STOP does not erase the client block. The server can confirm removal or
reject/correct it; a missing response retires the task after eight updates for a
bounded retry. Explicit Skip Break Verification uses client prediction. The
server decides whether accelerated modes/settings are accepted.

## Verification

Unit/build checks, the expanded packaged Breaker matrix, a framebuffer capture,
and the final persistent manual launch are recorded in
[`verification/breaker-visual-b4-20261104.json`](verification/breaker-visual-b4-20261104.json).
The focused survival PLAY-peer suite includes the earlier layered/material/home
regressions plus Visual side effects/render-state restoration, mode countdowns,
progress-floor timing, rate/FOV including empty Visual scans, B4 direct-bed
targeting and its cancelled-walking guard, server confirmation/rejection, and packet/input
options. This is local client/packet evidence, not live Hypixel acceptance.

Final checks passed **613 unit tests** (164 core, 449 Forge), **62 mining
scenarios with 1,890 assertions**, and the 26-check home-bed suite. Wrapper,
architecture, Mixin checksum, JAR/refmap/distribution, config/HUD and cleanup
checks passed. The exact verified artifact is in the linked receipt. The native
route renderer passed framebuffer/color and GL-state restoration checks.

[Route preview](verification/breaker-visual-route-20261104.png) is an isolated
framebuffer view of the actual renderer; it does not depict a live server.

Pre-edit backup: `.backups/breaker-visual-20261104-080952/` (hashed manifest).
