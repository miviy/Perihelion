# Yuri 1.3.1 Displace behavior port

The later [follow-up audit](YURI_131_AUDIT_2026-10-17.md) adds transformed Aura/Displace ownership handoff, preceding-look packet order, finishing release and foreign-owner rejection checks.

Reference: read-only Yuri `1cb86ad94ce1f01aa9b715577163c8ecc47b7def`,
`src/main/java/ddlc/yuri/modules/impl/combat/DisplaceModule.java`.
Donor-derived math and arrow geometry are attributed under AGPL-3.0-only.

The native `DisplaceModule(ClientRuntime, AuraModule)` registers `displace` in Combat.
It uses Aura's current target and a supplier seam for a scoped rotation override;
the seam does not replace Aura targeting, attack timing, ray tracing, or auto block.

| Setting | Default | Range / choices |
| --- | --- | --- |
| Mode | Void | Left, Right, Random, Void |
| Angle | 45 | 5–90, step 1 |
| Range | 3 | 3–6, step 0.1 |
| Cooldown | 1 second | 0–5, step 0.1 |
| Max Hurt Time | 3 | 0–10 |
| Only Knockback Items | Off | Boolean |
| Players Only | On | Boolean |
| Render Arrow | On | Boolean |

Arming requires a living, current Aura target within Range and Max Hurt Time,
plus sprinting or an item knockback enchantment. Only Knockback Items additionally
requires that enchantment. Cooldown starts at arming, including the donor's 20 ms
allowance. Left/right offsets retain the donor's uniform ±5 degree jitter; Random
chooses a side for each arming. Void tests left before right, two blocks beyond
the target at the unjittered offset angle, searching from floor(target Y) − 1 down
to eight blocks below that cell, inclusive, stopping at Y = 0. Any non-air material
counts as ground. No supported void side means no override.

The held aim uses target eyes minus 0.2, speed 10 (180 degrees per tick), and the
native sensitivity quantization shared with the Yuri-compatible Aura pipeline.
An arm can renew its fixed yaw/pitch for three subsequent input passes. Death,
stale target identity, Range + 1 distance, or any recorded bad packet window
releases the override. A matching accepted attack retains it until AFTER_MOTION.
The white arrow retains the donor geometry, shadow, outline, 600 ms lifetime,
100 ms easing at each end, 450 ms outward travel, and vertical bob.

Native lifecycle and arbitration differences:

- Priority 55 wins over Aura's 50; higher-priority building/item actions retain
  ownership. Aura's action callback resolves the external request at action time.
- A new aim must first belong to an actual walking packet. Aura only attacks
  through that identical previously submitted rotation, using native temporary
  action orientation and restoration. The first arm can therefore wait one tick;
  ray-cast/mouse attack settings can still reject an offset that misses the target.
- Donor NORMAL movement correction uses the existing native analog-safe movement
  correction and submitted yaw for strafe/jump instead of rounding inputs to the
  donor's discrete keys. Aura's own movement correction yields while overridden.
- Unknown terrain counts as ground rather than evidence of void. The search
  remains bounded and never requests missing chunk data.
- World/connection retirement, disable, and fault cleanup release all claims and
  transient target/arrow state. Freecam and active building routines release the
  override. RenderState restores enclosing GL state even if rendering fails.
- Monotonic native time replaces wall-clock time; the cooldown duration and
  animation timings are preserved.

`DisplaceBehaviorTest` covers knockback eligibility, cooldown boundaries, jitter,
eye aiming, bounded void search, hold timeout/interruption, accepted-hit release,
and arrow fade/travel/expiry math. These are local logic assertions; they do not
establish live-server knockback or anti-cheat compatibility.

Completed integration evidence and remaining limits: [YURI_131_VERIFICATION.json](YURI_131_VERIFICATION.json).
