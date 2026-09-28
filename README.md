<h1>GRID CHESS</h1>

Chess against a neural network that runs on the phone. No server, no internet permission.

<p>
  <a href="../../releases/latest"><img alt="Download APK" src="https://img.shields.io/badge/download-APK-111111?style=for-the-badge"></a>
  <img alt="Android 8.0+" src="https://img.shields.io/badge/android-8.0%2B-D9D3C3?style=for-the-badge&labelColor=111111">
  <img alt="GPL-3.0" src="https://img.shields.io/badge/licence-GPL--3.0-D9D3C3?style=for-the-badge&labelColor=111111">
</p>

<table>
  <tr>
    <td><img src="docs/screenshots/01-levels.png" width="260" alt="Five difficulty levels, each a separate Maia network"></td>
    <td><img src="docs/screenshots/02-game.png" width="260" alt="A game in progress: the Scotch, 1.e4 e5 2.Nf3 Nc6 3.d4 exd4"></td>
    <td><img src="docs/screenshots/03-drag.png" width="260" alt="Dragging the light-squared bishop, its whole diagonal lit"></td>
  </tr>
  <tr>
    <td align="center"><sub>Five levels, five nets</sub></td>
    <td align="center"><sub>The Scotch, played by maia-1500</sub></td>
    <td align="center"><sub>Drag or tap-tap</sub></td>
  </tr>
</table>

## Opponent

Each level is a separate [Maia](https://github.com/CSSLab/maia-chess) net, from maia-1100 to maia-1900. Maia was trained on human games in one rating band, so it makes the mistakes a player of that rating makes. There is no search: one forward pass picks the move in 4–8 ms.

Levels 4 and 5 check the top moves with a short capture probe, so the engine won't hang its queen. Levels 1–3 skip the check. At 1100–1500, people hang pieces.

## Design

Neo-brutalism with Bauhaus shapes. The king is a cross, queen an arch, rook a square, bishop a diamond, knight a triangle, pawn a circle. Move by tapping or dragging.

## Build

```bash
brew install openjdk@21 lc0
brew install --cask android-commandlinetools
python3 tools/export_maia.py   # downloads the nets and converts them to ONNX
./gradlew :app:assembleDebug
```

Without the nets the app still builds and plays random legal moves. Release signing reads `keystore.properties` from the project root.

## Licence

GPL-3.0, because the bundled Maia nets are GPL-3. See [LICENSE](LICENSE) and [NOTICE](NOTICE). Maia is by [CSSLab, University of Toronto](https://github.com/CSSLab/maia-chess): McIlroy-Young, Sen, Kleinberg, Anderson, KDD 2020.
