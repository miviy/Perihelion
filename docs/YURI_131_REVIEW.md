# Yuri 1.3.1 Scaffold and combat review

The later [rotation/timing/packet audit](YURI_131_AUDIT_2026-10-17.md) corrects Aura cooldown resets and own-release gating, and moves Hit Select to client tick START. Its report supersedes earlier verification totals.

Read-only donor: `donor_clients/Yuri`, commit
`1cb86ad94ce1f01aa9b715577163c8ecc47b7def`. Native source was inspected first.
The rollback snapshot for this work is
`.backups/before-yuri-131-20261017-100824`.

## Scaffold

Reviewed the complete donor `ScaffoldModule`, `ScaffoldUtils`, the placement and
face-selection paths in `PlayerUtils`, and the relevant rotation/raycast helpers.
Native support search preserves donor candidate distance, stable support-generation
tie order, wall-cover exclusion, Keep Y plane, five-block candidate bound, and
horizontal-face preference. The bounded 13-cell support cube is equivalent because
every eligible candidate is within five integer offsets and its generator is at
most one cell farther away. Integer offsets preserve the donor distance ordering:
the player-position fractional components cancel in its candidate vectors.

The review repaired normal aim search in Hypixel Telly, retained stored rotation
goals while crossing existing terrain, and applied final aim search after player
movement. Telly retains the 400 ms startup placement window, exact normal straight
and diagonal air-tick thresholds, held-jump lower bound, latched permission until
landing, Hypixel early-air windows, and startup/ground/air rotation-speed priority.
Keep Y now follows the donor landing/drop/stationary reset boundaries instead of
resetting on every grounded step. Hypixel forces Legit sprint; Telly enables Auto
Jump. Relevant numeric steps, ranges and conditional visibility match the donor.

Placement uses donor face hit jitter, a packet animation, the shared placed-block
counter for sneak/Universal/God Bridge thresholds, NCP tower position snapping,
and the stationary Hypixel tower adjustments. Auto Disable observes nearby
fireballs, and Auto Jump observes the donor placement-denial chat response.

Native placement remains ownership-gated: final geometry is prepared before the
walking packet, and the next input pass acts only under the identical submitted
aim and current hotbar claim. This adds a walking interval relative to a donor
same-PRE rotation/placement call. It prevents placement using an unsent rotation.
Native conditions, block-selection controls, search extensions and the simple
native block counter remain additional presentation/configuration options.

## Aura, Legit Velocity and Hit Select

Reviewed the donor Aura module, target selector, Legit velocity implementation,
Hit Select and complete player simulation. Aura switch order uses stable entity
IDs and `Switch Speed * 50 ms`; single-target priorities preserve stable ties.
Normal Aura and Legit velocity now aim at the donor entity center with vertical
height clamped to 90 percent of its bounding box, rather than its nearest surface.
The existing native aim variation and locally owned model-loading options remain.

Aura accepts an activation-owned Displace rotation request through a supplier.
Displace suppresses Aura's own claim and movement correction while active. PRE
attacks resolve the actual request at action time, require its identical preceding
walking aim, and restore only a still-owned unchanged claim in the same generation.

Legit velocity selects its own furthest eligible living target within three blocks,
independently of whether Aura is enabled, observes the donor positive-vertical S12
condition, and supplies the grounded sprint jump. A target and its exact prepared
aim survive the walking interval even with `Reduce Ticks = 1`. The next PRE action
uses that target, while a new target can prepare a separate next aim. A missed or
stolen preceding rotation drops the old action; it cannot execute on later aim.
The action also revalidates target identity, range, visibility, building/item state,
and ray intersection. Mode changes and new activations discard pending state and
owned input. The older Velocity keys remain hidden config-compatibility entries;
the donor-facing control is Reduce Ticks (default 2, range 1 through 3).

Hit Select follows donor counter-hit/trade prediction, hurt-time boundaries,
damage-upgrade exception and activation cleanup. Its simulation uses the native
movement-heading event rather than assuming camera yaw. The player physics body
matches the pinned donor except for constructor/context adaptation and package
ownership; a simulation does not change the live player or world.

## Verification added

- `TellyTimingTest`: 400 ms boundary, normal exact thresholds versus held-jump
  threshold, Hypixel early-air latch, and rotation-speed precedence.
- `ScaffoldPolicyTest`: Keep Y landing/drop/stationary boundaries, auto-jump
  exclusion and Hypixel tower support/potion/air-tick rules.
- `ScaffoldPlacementSearchTest`: bounded search against an independent ordered
  support-generated reference across 32 terrain seeds, expanded offsets, plane
  restrictions and downward placements; separate covered-wall and ceiling faces.
- `AuraTargetCycleTest`: 50 ms delay boundary, changing candidate indices,
  zero-delay switching and reacquisition.
- `PreparedAimAttackTest`: single-tick opportunity retention, rotation identity,
  next-target separation and cleanup.
- Updated transformed-client smoke fixtures for current Hit Select settings,
  hurt-time and damage upgrade rules, Reduce Ticks GUI visibility and independent
  Legit velocity attacks, including stale-ray and activation cleanup checks.

Execution evidence is reported by the parent integration run; these source and
fixture changes alone do not establish authenticated multiplayer or server bypass
behavior.

Completed integration evidence and remaining limits: [YURI_131_VERIFICATION.json](YURI_131_VERIFICATION.json).
