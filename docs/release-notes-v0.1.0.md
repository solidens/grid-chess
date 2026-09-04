Chess against a Maia network running entirely on the phone. No server, no API
key, no `INTERNET` permission.

### Which file do I want?

**`grid-chess-0.1.0-arm64-v8a.apk`** — every phone made in roughly the last
decade. Take this one unless you know otherwise.

| File | For |
|---|---|
| `arm64-v8a` (35 MB) | All modern phones |
| `armeabi-v7a` (30 MB) | Older 32-bit devices |
| `x86_64` (38 MB) | Emulators |
| `universal` (68 MB) | All three in one file, if you don't want to choose |

Android 8.0 or newer. The APK is signed with a self-signed key, so Android will
ask you to allow installing from outside the Play Store.

### What's in it

- **Five difficulty levels**, and they are five separate Maia networks — 1100,
  1300, 1500, 1700, 1900 — not one engine throttled five ways. Each was trained
  only on games by humans in that rating band, so the mistakes are the ones you
  recognise from your own games rather than the alien ones a depth-limited
  engine makes.
- **No tree search.** One forward pass per move, 4–8 ms on a Pixel 7. The pause
  before the reply is deliberate padding, not thinking.
- **Blunder guard at levels 4–5** only. Levels 1–3 hang pieces on purpose —
  that is what 1100–1500 chess looks like.
- **Play either colour**, drag or tap-tap to move, undo takes back your move and
  the reply together.

### Known gaps

Pawn promotion and the game-over screen work but have had less exercise than the
rest. If either misbehaves, please open an issue.
