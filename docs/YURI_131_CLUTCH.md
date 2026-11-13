# Yuri 1.3.1 shift-held Clutch audit

Reference: Yuri `1cb86ad94ce1f01aa9b715577163c8ecc47b7def`,
`modules/impl/ghost/ClutchModule.java` and the consulted placement/rotation helpers
in `utils/player/{PlayerUtils,RotationUtils,BlockUtils}.java`.

The current donor activates while Sneak is held after 50 player ticks, with
Scaffold disabled and a clear initial packet window. It refreshes its attempt
window after more than three airborne ticks when the player's bounding box offset
ten blocks down has no collisions. It counts replaceable underfoot ticks, selects
the first eligible hotbar block, uses normal movement correction and sampled
rotation speed, searches horizontal rays at 45-degree offsets from a lowered eye
origin, and falls back to the selected support face center. Native Clutch preserves
these controls and uses the existing scoped strict placement path.

The audit corrected the ten-block test from a swept column to the donor's offset
bounding box, the placement packet gate from attack/swing/block to attack/inventory,
and the 0–1 place delay from discrete integer sampling to the donor's continuous
sample. An attempted placement resets the underfoot counter even when it fails,
matching the source. Fake placement packets retain the source's 8% fallback chance
and right-click-delay gate.

Native differences remain explicit: the first attempt window allows one extra
update so its owned walking rotation can be submitted before a PRE placement.
Every real placement still requires that exact prior aim, winning native claims,
current target/support blocks and a strict support-face ray. The shared bounded
nearby planner selects reachable placements rather than reproducing the donor's
13-cube support scan and randomized support-face choice. Native building-block
policy excludes additional falling/slime blocks. Scoped persistent hotbar selection
replaces direct slot mutation, and the lowered ray origin is calculated without
temporarily moving the live player. GUI/death guards and actual-placement checks
remain native safeguards.

`ClutchStateTest` covers initial/refresh window boundaries, the exact >3-air-tick
threshold, continuous place-delay sampling, underfoot resets and both packet-window
predicates. The packaged gameplay probe separately exercises shift-held placement
with native prior-aim validation. These checks do not establish live-server success.

Completed integration evidence and remaining limits: [YURI_131_VERIFICATION.json](YURI_131_VERIFICATION.json).
