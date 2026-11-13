# Adjust default appearance comparison — 2026-10-12

The default GUI now follows the supplied Adjust references more closely: regular gray text, muted blue headers, narrow translucent columns and compact utility buttons. Custom text colors and weights require **Client → ClickGUI → Custom text style**, which starts off. Turning it off preserves the stored choices while returning to the default palette. Font, opacity and scale remain independently adjustable.

Configuration opens as a peer list beside the module panels. Selecting a preset or **Manage presets** reveals its existing actions. Short categories stack below other short lists, leaving more room for long categories. Search still gives its top area back when disabled; HUD/Configs tabs and window headers retain dragging/right-click behavior. Preset-name focus, module editors and search keep separate input ownership.

The HUD uses denser player-table rows and a centered Players heading. Its player menu has a larger portrait/name, regular Tags/Analysis/Hypixel labels and a stronger background for readability over chat. The independent Logo uses larger text and matching drag bounds; Tab GUI keeps its own size, placement and visibility. Existing actions and unavailable-data labels remain native.

## Visual comparison

All seven images in the read-only `../adjust_visuals/` workspace folder and the attached player-menu image were compared. The primary GUI reference was **Screenshot 2026-09-15 at 11.17.10 AM.png**. A read-only subagent reviewed the previous default, scale-matched captures, the narrower layout and the final HUD refinements.

At the reference window size (1192 × 794) and Minecraft GUI scale 2, the final GUI uses approximately 160-pixel columns, 26-pixel headers/rows, 6-pixel text insets and 4-pixel peer gaps. Long lists retain about 19 visible rows. The reviewer found no remaining material visible failure in the final GUI, player menu and HUD. The logo hierarchy and player-table density are substantially closer to the supplied images. Native categories/module names and platform-dependent font identity prevent a pixel-identical claim.

Final captures:

- [Default GUI](../forge/run-smoke-forge-g9xz0goa/ui-default-adjust.png)
- [Player menu over chat](../forge/run-smoke-forge-g9xz0goa/hud-chat-player-actions-in-world.png)
- [Logo, Tab GUI and HUD](../forge/run-smoke-forge-g9xz0goa/hud-production.png)
- [HUD editor](../forge/run-smoke-forge-g9xz0goa/ui-hud-editor-sans.png)

## Verified artifact

Saved tested JAR: `forge/build/verified/adjust-fidelity-20261012/Perihelion-0.1.0.jar`.

SHA-256: `c77af33ac0882c5ab4f37734c7ecf43608ae6691c8093f26798c45db893385d9`.

`check assemble --offline` passed architecture, wrapper, mixin, packaging and 276 unit tests (156 core, 120 Forge; zero failures/errors/skips). The same saved JAR passed packaged native UI runs:

| Evidence | Forge 1192 × 794, scale 2 | OptiFine M5 854 × 480, Auto |
| --- | --- | --- |
| GUI behavior | 219 checks | 219 checks |
| Production HUD | 64 checks | 64 checks |
| HUD chat interaction | 88 checks | 88 checks |
| Theme/fonts and fractional scale | 214 checks | 227 checks |
| Native render helpers | 55 checks | 55 checks |
| UI scenario, unload/rejoin, teardown | PASS | PASS |

Runs: `forge/run-smoke-forge-g9xz0goa` and `forge/run-smoke-optifine-djty1fjr`. Both teardown reports show cleared references/hook and zero subscriptions/queued work. Each intentionally throws one HUD fixture exception to verify fault/render-state restoration. Final runs have no smoke failure marker. These are native local/integrated-world UI checks, not authenticated multiplayer or full gameplay verification.

Earlier iterations exposed repeated same-size resize drift and Config intercepting Escape/search. Both were corrected. Larger-window fixtures now explicitly create scroll overflow and assert recovery against the current viewport; the overlap test uses a populated category. The final checks cover these paths, config save/load/update/delete, optional style persistence, font transitions, utilities, independent logo geometry and HUD chat actions.

The launcher accepts `--width`, `--height` and `--gui-scale` for isolated reference-sized captures. Defaults remain 854 × 480 with Auto scaling. No user launcher profile, accounts, live configuration or donor files were modified.

## Recovery

Before editing, 517 source/config/docs/script files were archived and individually hash-verified in `.backups/before-adjust-fidelity-20261012-202515/source.tar.gz`.

Archive SHA-256: `c75dd5264b4aa97b97fa42dc76848f11d957a4c74a4a8fd398c9eb49134b8f2b`.

`python3 scripts/restore-adjust-fidelity.py` checks the backup and affected-file manifest without restoring. Explicit `--apply` backs up affected current files, restores only this pass's allowlisted files, and refuses if later edits would be lost. Generated JARs, caches and smoke worlds remain intact. Rebuild after restoring.

Detailed hashes, checks and references: [verification JSON](ADJUST_FIDELITY_2026-10-12.json).
