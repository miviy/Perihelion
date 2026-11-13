# Perihelion logo files

The current B design uses two copper light surfaces, a round shadow and one diagonal light path.

| Use | File |
| --- | --- |
| Full-resolution transparent master | `emblem-copper-transparent.png` |
| Square transparent logo | `emblem-primary-1024.png` |
| Discord / YouTube avatar | `avatar-copper-1024.png` |
| Name beside the logo | `lockup-horizontal-primary.png` |
| Name beneath the logo | `lockup-stacked-primary.png` |
| Single-color watermark / website icon | `mark-white.svg`, `mark-black.svg`, `mark-ivory.svg` |
| Single-color name lockup | `lockup-horizontal-ivory.svg`, `lockup-horizontal-black.svg` |
| Application and size preview | `proof.png` |

The copper PNG is the same transparent artwork used by the main menu and the HUD.
The SVG files are a hand-built single-color companion with outlined lettering. They preserve
the two-sweep structure and circular shadow; they are not a vector tracing of the copper render.
No font installation is needed. Use at least 24 pixels of visible height for the primary emblem.
The square exports include clear space and fit a circular avatar crop.

In Minecraft: open the HUD editor, right-click **Logo**, and choose **Logo design → Emblem**
or **Emblem and name**. Existing text-logo preferences remain available.

The source concepts used the built-in image generation tool. The exact selected prompts and
asset hash are in [prompts.json](prompts.json). The client owns its textures and releases them
when the menu or HUD renderer closes.

To regenerate the exports from the project root:

```sh
.tools/java17/Contents/Home/bin/javac -d .tools/brand-export \
  forge/src/main/java/perihelion/integration/minecraft189/branding/EmblemGeometry.java \
  forge/src/main/java/perihelion/integration/minecraft189/branding/BrandArtwork.java \
  forge/src/main/java/perihelion/integration/minecraft189/gui/font/MenuLetterforms.java \
  scripts/brand/ExportBrand.java
.tools/java17/Contents/Home/bin/java -Djava.awt.headless=true \
  -cp .tools/brand-export:forge/src/main/resources \
  perihelion.tools.ExportBrand design/brand/export
```
