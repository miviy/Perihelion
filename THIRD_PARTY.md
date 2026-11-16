# Third-party components

The distribution bundles [SpongePowered Mixin](https://github.com/SpongePowered/Mixin), version `0.7.11-20180703.121122-1`, under the MIT license. Its original `LICENSE.txt` is retained in the JAR. Mixin includes its shaded ASM implementation. No Mixin or ASM sources were modified.

Minecraft, Forge, Gson, Guava, Netty, LWJGL and the game's other libraries are supplied by the user's Minecraft/Forge installation. They are not bundled in Perihelion. The separate Mixin 0.8.5 annotation processor, Essential Loom, Shadow and Pack200 adapter are build tools; the Essential client is not a runtime dependency.

The native gameplay modules reimplement behavior studied in Yuri (MIT, Copyright (c) 2026 Unlegit Inc.) and compare a few other donor implementations. No donor framework is copied. Yuri's `legit.csv` and `ai.csv` rotation sample datasets are redistributed in `assets/perihelion/rotationpresets/`; the module implementations use Perihelion's native architecture. Yuri's full notice is included at `META-INF/licenses/Yuri.txt` as attribution; exact revisions, paths, differences and secondary references are recorded in [DONORS.md](docs/DONORS.md).

The Yuri 1.3.1 update also adapts source algorithms and implementations from revision `1cb86ad94ce1f01aa9b715577163c8ecc47b7def`, published under GNU AGPL version 3. Adapted files carry their source attribution and SPDX identifier; the license is included as `META-INF/licenses/Yuri-AGPL-3.0.txt`. The earlier MIT-era notice remains for the older sources and datasets. Native lifecycle, settings, events, configuration and control ownership remain Perihelion implementations.

The project owner's choice of license for Perihelion's own sources remains open; this notice does not assign one.

The native Target HUD presentation adapters study Yuri's AGPL-3.0 designs,
Arsenic's MIT designs (Copyright (c) 2022 Arsenic Client Development), and
OpenMyau-Plus's GPL-3.0 designs. Notices are retained as
`META-INF/licenses/Yuri-AGPL-3.0.txt`, `Arsenic.txt` and
`OpenMyau-Plus-GPL-3.0.txt`. B4 and Coolware layouts are expressed using existing
Perihelion drawing primitives; no donor framework, font files or assets are bundled.
Exact revisions, paths and intentional differences appear in
[the Target HUD inventory](docs/TARGET_HUD_DESIGNS.md).


## GUI fonts

The GUI bundles unmodified static Regular and Bold font files in
`assets/perihelion/fonts/`:

- [Inter](https://github.com/rsms/inter), [Open Sans](https://github.com/googlefonts/opensans),
  [Lato](https://github.com/google/fonts/tree/main/ofl/lato),
  [Montserrat](https://github.com/JulietaUla/Montserrat),
  [Poppins](https://github.com/google/fonts/tree/main/ofl/poppins), and
  [Monaspace Neon](https://github.com/githubnext/monaspace), under the SIL Open
  Font License 1.1.
- [Roboto and Roboto Condensed 2.138](https://github.com/googlefonts/roboto-2/releases/tag/v2.138),
  under Apache License 2.0.
- [JetBrains Mono Nerd Font](https://github.com/ryanoasis/nerd-fonts/tree/v3.4.0/patched-fonts/JetBrainsMono),
  under the font's SIL Open Font License 1.1; the upstream Nerd Fonts licensing
  notice is retained as well.

The original per-family license and copyright notices are retained in the JAR
under `META-INF/licenses/fonts/`. Exact source revisions, upstream file paths,
and SHA-256 checksums are recorded in
`assets/perihelion/fonts/provenance.json`. Only Regular and Bold faces are
included; no font installer, upstream build code, or runtime network request is
bundled. Simplified resource filenames leave embedded font names and data
unchanged. Minecraft and the original Sans/Serif/Monospace options continue to
use the game's native renderer and the JRE's existing fonts respectively.
