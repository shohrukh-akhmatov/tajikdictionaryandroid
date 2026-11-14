# Tajik Dictionary — Android (TJK ⇄ RUS / TJK ⇄ ENG)

An **offline-first** Tajik–Russian–Tajik and Tajik–English–Tajik dictionary app built with **Kotlin, Jetpack Compose, Material 3, SQLite, Room, and DataStore**.

## Key Features
* **100% Offline SQLite Lexicon:** Instant prefix & exact-match search with zero internet required.
* **Two Bidirectional Modes (`TJK ⇄ RUS` & `TJK ⇄ ENG`):** No direction toggle needed — type in either language of the active mode and every result is clearly tagged `[TJ]`, `[RU]`, or `[EN]`.
* **Tajik Keyboard Folding:** Typing `хурдан` on a Russian keyboard finds `хӯрдан` (`ғ→г`, `қ→к`, `ҳ→х`, `ҷ→ч`, `ӣ→и`, `ӯ→у`).
* **On-Screen Tajik Letter Bar:** One-tap `ғ ӣ қ ӯ ҳ ҷ` buttons right under the search bar.
* **Cross-Mode Detection:** Typing English while in `TJK ⇄ RUS` shows a one-tap prompt to switch to `TJK ⇄ ENG`.
* **Entry Detail:** Tappable cross-linked translations, usage notes, trilingual examples, Copy, Share, Favorite, and offline Russian/English TTS.
* **Saved & History + Word of the Day:** Stored in Room (`user.db`) separately from the read-only dictionary (`dictionary.sqlite`) so user data survives dictionary updates.
* **Android Text Selection Lookup:** Registered for `ACTION_PROCESS_TEXT` and `ACTION_SEND`.

## Updating the Word Database (5k / 10k Words)
All words live in [`data/source/dictionary.csv`](data/source/dictionary.csv). After editing or expanding it:

```bash
python3 tools/build_dictionary.py --self-test
python3 tools/build_dictionary.py
```

This regenerates `app/src/main/assets/dictionary.sqlite` automatically.

## Docs
* [`docs/DATA_AND_SEARCH.md`](docs/DATA_AND_SEARCH.md) — SQLite schema, normalization spec, and search queries
* [`docs/FEATURES_AND_UI.md`](docs/FEATURES_AND_UI.md) — UI wireframes, color palette, and feature matrix
* [`docs/ROADMAP.md`](docs/ROADMAP.md) — Detailed multi-phase roadmap

## Build & Test

```bash
./gradlew testDebugUnitTest assembleDebug
```
