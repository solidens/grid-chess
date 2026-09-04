Two fixes to the pieces, both cosmetic, no change to how the engine plays.

<table>
  <tr>
    <td><img src="https://raw.githubusercontent.com/solidens/grid-chess/v0.1.1/docs/screenshots/04-promotion.png" width="250" alt="The promotion picker showing arch, square, diamond and triangle"></td>
    <td><img src="https://raw.githubusercontent.com/solidens/grid-chess/v0.1.1/docs/screenshots/03-drag.png" width="250" alt="Dragging the light-squared bishop, its whole diagonal lit"></td>
  </tr>
  <tr>
    <td align="center"><sub>Shapes, not letters</sub></td>
    <td align="center"><sub>The held piece now carries the right outline weight</sub></td>
  </tr>
</table>

### What changed

**The promotion picker showed the letters Q R B N.** It now draws the same
shapes the board does — arch, square, diamond, triangle. Pieces in this app are
shapes and nothing else, and that screen was the one place breaking the rule.

**Outline weight now scales with the piece.** It was a fixed width, while pieces
are drawn at three sizes: on the board, enlarged in your hand mid-drag, and
larger still in the picker. The same absolute stroke on a bigger shape reads as a
thinner, different drawing. It is now a fraction of the piece, so it is one glyph
at every size. Most visible on the piece you are holding while dragging.

The board itself is untouched — 99.55% of its pixels are identical to 0.1.0, the
rest being sub-pixel antialiasing.

### Also

Promotion and the game-over screen were listed as under-tested in the 0.1.0
notes. Both have now been exercised on device and behave.

### Upgrading

Same signing key as 0.1.0, so this installs straight over it without
uninstalling. Verified on a Pixel 7 emulator.

### Which file do I want?

**`grid-chess-0.1.1-arm64-v8a.apk`** — every phone made in roughly the last
decade. Take this one unless you know otherwise.

| File | For |
|---|---|
| `arm64-v8a` (35 MB) | All modern phones |
| `armeabi-v7a` (30 MB) | Older 32-bit devices |
| `x86_64` (38 MB) | Emulators |
| `universal` (68 MB) | All three in one file, if you don't want to choose |

Android 8.0 or newer. All five Maia networks are inside the APK — the app
declares no permissions and cannot reach the network at all.
