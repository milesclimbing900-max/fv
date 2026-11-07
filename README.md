# DeepScan

Client-side Fabric mod: animated deepslate-layer scanner with glow highlights, a radar/map and a custom settings app.

## Build
1. Install **JDK 25** and Gradle **9.4+**.
2. Open `gradle.properties` and copy the exact `minecraft_version`, `loader_version`, `loom_version` and
   `fabric_api_version` for your game version from <https://fabricmc.net/develop/> (the file ships set to 26.2 with the
   Fabric API version left blank -- for 26.3 use the 26.3 values from that page).
3. `gradle wrapper --gradle-version 9.4.0` (once), then `./gradlew build`. The jar is in `build/libs/`.
4. Push to GitHub: the included workflow (`.github/workflows/build.yml`) builds it and uploads the jar as an artifact.

## Use
| Key | Action |
|---|---|
| `R` | Scan: a radius line sweeps out from you and highlights non-natural blocks as it passes |
| `Right Shift` | Open the menu (Scanner / Visuals / Blocks / Radar) |

Both keys are rebindable in Controls. Settings live in `config/deepscan.json` (any block id can be added to `targets`/`ignored`).

## Notes
- Default mode flags *anything* between Min Y and Max Y (default -64..0) that isn't natural stone-layer content
  (stone, deepslate, tuff, ores, caves, geodes, sculk...). Ancient-city blocks are ignored by default (toggle in Blocks).
- It only reads chunks your client has loaded (render distance) and only what the server sent you.
- Highlights are drawn as a screen overlay, so they show through walls.
- Sensitivity 1-3 requires clusters of unnatural blocks (fewer false positives), 10 reports single blocks.
