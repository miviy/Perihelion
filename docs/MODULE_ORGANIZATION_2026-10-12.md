# Module organization — 2026-10-12

The user requested clearer module placement and removal of the Exploit tab. The GUI now uses seven native categories; the HUD navigator uses the same six gameplay categories and keeps Client preferences in the GUI. All module rows are alphabetized by their displayed label.

24 module category definitions changed. Module IDs, settings, binds, activation behavior and gameplay code remain unchanged. Old saved `EXPLOITS` layout entries are ignored; other saved panel positions/collapse states remain valid. The HUD navigator background and drag/overlap bounds now fit all six rows.

| Category | Count | Modules |
| --- | ---: | --- |
| Combat | 16 | Aim Assist, Anti Bot, Anti Fireball, Aura, Auto Clicker, Auto Projectile, Back Track, Bow Aimbot, Criticals, Friends, Hit Select, Jump Reset, Lag Range, Tick Base, Velocity, WTap |
| Movement | 16 | Anti Void, Eagle, Flight, Inv Move, Long Jump, No Fall, No Slow, Phase, Safe Walk, Speed, Sprint, Stasis, Step, Target Strafe, Timer, Water Walk |
| Player | 11 | Auto Apple, Auto Armor, Auto Eat, Auto Extinguish, Auto Pot, Auto Swap, Fast Use, Manager, Refill, Regen, Stealer |
| World | 8 | Bed Defender, Block In, Breaker, Chest Aura, Clutch, Fast Break, FastPlace, Scaffold |
| Render | 23 | Ambience, AntiBlind, Auto F5, Bed Plates, Block ESP, Breadcrumbs, Camera, Chams, ESP, Free Look, Freecam, Fullbright, HUD, Name Protect, Name Tags, NoBob, NoFOV, NoHurtCam, Shader ESP, Skeletons, Target ESP, Trajectories, Zoom |
| Misc | 7 | Auto Chat, BedWars Utility, Blink, Derp, Disabler, Fake Lag, Minigame Aim |
| Client | 2 | ClickGUI, Input Fixes |


World holds block placement, mining, beds and chests. Movement holds travel, edge/fall protection and movement timing. Player holds inventory, healing and item routines. Combat holds targeting, fight assistance and knockback routines. Render holds visual/camera/HUD modules. Misc holds network, chat and minigame utilities. Client holds menu/input preferences.

Verification: the offline `check assemble` build passes 274 tests (core 156, Forge 118), including HUD category navigation/wrapping and compatibility with legacy saved layouts. Forge `run-smoke-forge-0axbb12y` and OptiFine M5 `run-smoke-optifine-e2lum7uo` UI runs both passed with the same artifact and fixture sources: GUI 212, production HUD 63, chat HUD 88, and theme/font checks. Each exited with code 0, cleared references/listeners/queued tasks, and `stdoutError=false`. Legacy audio warnings remain. Native checks cover the actual production categories, sorted rows, seven panels, six HUD rows, rendering/fonts, chat dragging and GUI interaction. The module-menu and HUD screenshots were visually inspected. Exact outcomes and artifact fingerprints are recorded in [MODULE_ORGANIZATION_VERIFICATION_2026-10-12.json](MODULE_ORGANIZATION_VERIFICATION_2026-10-12.json).
