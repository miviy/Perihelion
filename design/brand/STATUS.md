# Perihelion B — installed working revision, 20 October 2026

The user's reference is the original B emblem. They identified uneven curves, an irregular
ball and unequal side reach as the problem, then asked for more expression and rejected
added channels as "weird squiggles". The installed revision keeps two broad B sweeps and
one dominant diagonal light path. Copper surface lighting supplies depth. The central
shadow is a round, open rim. The added stripe/channel experiments are not used.

## Installed artwork and integration

- Primary: `forge/src/main/resources/assets/perihelion/brand/emblem-copper.png`.
  This is the selected first transparent extraction, unchanged from the generated source.
  SHA-256: `4969f589dfb674146be0bb6dcab244bab5ab1745288a8ef5f5f44bd11b53cdc7`.
- `BrandArtwork` loads it once, measures visible ink and shares it with the title and HUD.
  Inspect it alpha-composited: RGB values hidden in transparent pixels are not artwork.
- `MenuWordmark` replaces the P with this image and keeps the existing title/button centerline.
- HUD editor → right-click Logo → Logo design offers Text, Emblem, Emblem and name.
  The default stays Text. Both Adjust and Raven draw the same copper asset at24px visible
  height; renderer-owned textures are deleted on close. Name lettering matches the title.
- `EmblemGeometry` is a separate single-color companion: rotationally paired bands,
  circular rim and one light path. It is not an exact vector trace of the copper rendering.
- Exported PNG, avatar, outlined SVG, wordmark lockups and exact prompts:
  `design/brand/export/`; download pack: `design/brand/Perihelion-brand.zip`.

## Three-reviewer sequence

| Round | Material reviewed | Result / action |
| --- | --- | --- |
| 1 | Balanced native B core and sweeps | True circle and equal tip reach; user found it too basic. |
| 2 | Added flow channels and copper finish | Reviewers noted tiny spurs; user rejected channels as squiggles. They were removed. |
| 3 | Reference-based copper B and transparent cutout | Two main sweeps retained; actual alpha composition clean. Small-size filtering needed checking. |
| 4 | Final asset and visible-height proof |24px legible; avatar safe; mono companion documented as a companion. |
| 5 | Packaged Minecraft title/HUD/editor captures | All three found no actionable visual defect. Centering, rim, diagonal light and HUD spacing clear. |

Reviewer feedback supports implementation checks; it is not a claim of user aesthetic approval.
Earlier rejected studies remain under `rejected-curl/`, `rejected-lens/`, `reviews/`,
`concepts/` and `explorations/`. They are not installed or included in the download pack.

## Verification

- `forge/brand-b-final-build.log`: check/assemble passed;465 tests, zero failures/errors/skips.
- Packaged Forge full UI: `forge/run-smoke-forge-hiav29i3/result.json`, passed.
- Packaged OptiFine full UI: `forge/run-smoke-optifine-6hq71vku/result.json`, passed.
- Both runs: title234 checks, menu family83, production HUD112, plus native GUI/style,
  editor dropdown, bounds, world rejoin, GL restoration and texture cleanup checks.
- Both shutdown reports: stdoutError=false. The packaged PNG bytes match the source.
- Verified artifact: `forge/build/verified/brand-b-20261020/Perihelion-0.1.0.jar`.
  SHA-256: `ccec9f18342cf43fbcf4af16bafc9c35245a232637700e0ee1f3d746aa2fa5c6`.

The existing interactive profile `forge/run-main-menu-kutm2c9t` was updated atomically,
keeping its worlds/settings and backing up the previous JAR. Minecraft startup confirmed,
PID99616, with the verified artifact above. It is left open without smoke fixtures or timeout.
