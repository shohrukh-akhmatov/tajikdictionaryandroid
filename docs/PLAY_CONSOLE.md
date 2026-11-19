# Google Play Console — Complete Answer Sheet

Based on analysis of the code at `versionName 0.1.0` / `versionCode 1` (package `tj.tajikdictionary`).

> **The app shows ads: Yandex Mobile Ads first, Google AdMob as the fallback** (same waterfall as the Somoni app). Every Console answer below is written for this version.
> The "What the code actually does" table further down describes the pre-ads code; the ad SDKs add network access and the data listed in 2.8.
>
> **Automation:** `tools/play_publish.py` pushes everything the Google Play Developer API allows (store listings, contact details, graphics, AAB upload, release to a track). The items marked **🖐 UI only** have no API and must be clicked through once in Play Console.

### Ads in the app
- SDKs: `com.yandex.android:mobileads` 8.7.0, Google Mobile Ads Next-Gen SDK `com.google.android.libraries.ads.mobile.sdk:ads-mobile-sdk` 1.3.1, UMP `com.google.android.ump:user-messaging-platform` 4.0.0 (GDPR/US consent form, required by Google for EEA/UK/CH users). Consent is collected first; its TCF signals switch Google to non-personalized ads and set Yandex's user consent. Location tracking is off for Yandex.
- Placements: a pinned bottom banner on every screen except Settings (hidden while the keyboard is up), a separate banner unit on the word detail screen, and an interstitial after every 3rd finished word-game round (at most once per 3 minutes, shown when the user taps *Done*). No ads during onboarding or the placement test.
- **Release ad IDs:** fill every value in `Config/AdUnits.properties` (AdMob app ID + 3 AdMob units + 3 Yandex units). A release build with any value missing ships with ads **off**; debug builds always use Google test / Yandex demo units.
- Manifest: `INTERNET`, `ACCESS_NETWORK_STATE` and `com.google.android.gms.permission.AD_ID` are merged in automatically; the AdMob app ID comes from `Config/AdUnits.properties` via a manifest placeholder.
- Publish `app-ads.txt` at the **root** of the developer website domain (`https://shohrukh-akhmatov.github.io/app-ads.txt`, i.e. the `shohrukh-akhmatov.github.io` repo) containing **both** the line AdMob gives you (`google.com, pub-…, DIRECT, f08c47fec0942fa0`) and the lines from the Yandex Partner interface (*Monetization → app-ads.txt*).
- Keep the target audience 13+: an under-13 audience requires Families-certified ad SDKs and non-personalized ads.

## What the code actually does (basis for every answer below)

| Check | Finding |
|---|---|
| Internet access | **None.** No `INTERNET` permission in the manifest or merged manifest. The app cannot send data anywhere. |
| Ads SDKs | **None.** No AdMob / Meta / Unity / AppLovin etc. in dependencies. |
| Analytics / crash reporting | **None.** No Firebase, Crashlytics, Sentry, etc. |
| Accounts / login | **None.** |
| In-app purchases / billing | **None.** No Play Billing library. |
| Dangerous permissions | **None.** Only `tj.tajikdictionary.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (auto-added by AndroidX, signature-level, internal). |
| Data stored | Saved words, history, flashcard progress, settings — **on the device only** (Room `user.db` + DataStore). |
| User-initiated actions | Share text/image (system share sheet), copy, export/import favorites JSON (system file picker), email support (opens the mail app), TTS (system engine). None of these send data to the developer. |
| Third-party libraries | AndroidX, Jetpack Compose, Material 3, Room, DataStore, ZXing core (QR generation, offline). |
| Content | ~16,300 dictionary rows (Tajik/Russian/English). Ordinary reference vocabulary; includes neutral dictionary words like "wine", "gun", "to kill" — no profanity, no explicit content. |

---

## 1. Create app

| Field | Value |
|---|---|
| App name | `Tajik Dictionary` |
| Default language | English (United States) – en-US |
| App or game | **App** |
| Free or paid | **Free** (cannot be changed to paid later) |
| Declarations | ✅ Developer Program Policies ✅ US export laws |

---

## 2. Dashboard → "Set up your app"

### 2.1 Privacy policy
**Required for every app.** URL: `https://shohrukh-akhmatov.github.io/tajikdictionary/privacy-policy.html`
→ Text ready in [`docs/privacy-policy.html`](privacy-policy.html). **It must be published at that URL before you submit** (copy it into the GitHub Pages repo).

### 2.2 App access
**All functionality is available without special access.** (No login, no paywall.)

### 2.3 Ads 🖐 UI only
**Yes, my app contains ads.** (Store listing shows a "Contains ads" badge.)

### 2.4 Content rating (IARC questionnaire)
| Question | Answer |
|---|---|
| Email | your developer email |
| Category | **Reference, News, or Educational** |
| Violence | No |
| Fear / horror | No |
| Sexuality / nudity | No |
| Language (profanity / crude humor) | No |
| Controlled substances (drugs, alcohol, tobacco) | No *(neutral dictionary definitions are not depictions/promotion)* |
| Gambling / simulated gambling | No |
| User interaction: users can interact / communicate | **No** |
| Shares user's location with others | **No** |
| Allows purchase of digital goods | **No** |
| Unrestricted internet / web browser | **No** |
| Is the app a web browser or search engine | **No** |
| Contains news | No |

Expected result: **Everyone / PEGI 3 / USK 0 / ESRB E**.

### 2.5 Target audience and content
| Field | Value |
|---|---|
| Target age groups | **13–15, 16–17, 18 and over** *(recommended)* |
| Appeals to children? | **No** (since under-13 isn't selected you may be asked; answer "No") |

> With ads, keep children out of the target audience. Selecting *Under 13* would require Families-certified ad SDKs, child-directed ad requests, and enrolls the app in the **Families policy** with stricter review and the Teacher Approved program. Do this once Kid mode from the roadmap ships.

### 2.6 News app
**No.**

### 2.7 COVID-19 contact tracing / status
**My app is not a publicly available COVID-19 contact tracing or status app.**

### 2.8 Data safety 🖐 UI only (with Yandex Mobile Ads + AdMob)
The app's own code still sends nothing; everything below comes from the **Yandex Mobile Ads SDK** and the **Google Mobile Ads SDK** (per each network's Data safety guidance; the union of both is listed). The Yandex SDK runs with location tracking off, so precise location stays "not collected".

**Overview questions**
| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **Yes** |
| Is all of the user data collected by your app encrypted in transit? | **Yes** (the SDK uses HTTPS) |
| Which ways can users request that their data is deleted? | **None of the above / I don't provide a way** — ad data is processed by Yandex and Google; reset/delete the Advertising ID in Android settings. *(Choose "Users can request deletion" only if you set up a real process.)* |

**Data types — mark each as Collected ✅ and Shared ✅**
| Category → type | Collected | Shared | Processed ephemerally | Required or optional | Purposes |
|---|---|---|---|---|---|
| Location → **Approximate location** (from IP) | ✅ | ✅ | No | Required | Advertising or marketing, Analytics, Fraud prevention, security & compliance |
| App activity → **App interactions** | ✅ | ✅ | No | Required | Advertising or marketing, Analytics |
| App info and performance → **Crash logs** | ✅ | ✅ | No | Required | Analytics |
| App info and performance → **Diagnostics** | ✅ | ✅ | No | Required | Analytics |
| App info and performance → **Other app performance data** | ✅ | ✅ | No | Required | Analytics |
| Device or other IDs → **Device or other IDs** (Advertising ID, app set ID) | ✅ | ✅ | No | Required | Advertising or marketing, Analytics, Fraud prevention, security & compliance |

Everything else (name, email, contacts, photos, messages, precise location, financial, health, files, search history, web browsing, audio, calendar) → **not collected**. Saved words, history and progress stay on the device and are *not* "collected".

### 2.9 Government apps
**No.**

### 2.10 Financial features
**My app doesn't provide any financial features.**

### 2.11 Health apps
**My app does not have any health features.**

### 2.12 Advertising ID 🖐 UI only
**Yes** — the app uses advertising ID. Purposes: ✅ **Advertising or marketing**, ✅ **Analytics**, ✅ **Fraud prevention, security, and compliance**. (`AD_ID` permission comes from the AdMob and Yandex SDKs.)

### 2.13 App category & contact details (Store settings)
| Field | Value |
|---|---|
| App or game | App |
| Category | **Education** *(alternative: Books & Reference)* |
| Tags (up to 5) | Dictionary, Language learning, Vocabulary, Flashcards, Translation |
| Email | support email (see note) |
| Website | `https://shohrukh-akhmatov.github.io/tajikdictionary/` |
| Phone | optional — leave empty |
| External marketing | ✅ allowed |

> ⚠️ The app's Settings screen opens `support@tajikdictionary.tj`. Make sure that mailbox exists, or change it (`SettingsScreen.kt:403`) to an address you actually read and use the same one here.

---

## 3. Main store listing

### English (en-US) — default

**App name** (≤30): `Tajik Dictionary: RU & EN` *(25 chars)*

**Short description** (≤80):
`Offline Tajik–Russian & Tajik–English dictionary with flashcards and word games` *(79 chars)*

**Full description** (≤4000):
```
Tajik Dictionary is a fast Tajik–Russian–Tajik and Tajik–English–Tajik dictionary and learning app that works fully offline. No account needed.

🔍 SMART SEARCH
• Two modes: TJK ⇄ RUS and TJK ⇄ ENG — type in either language, no direction switch needed
• Every result is tagged [TJ], [RU] or [EN] so you always know what you're looking at
• Type Tajik on a Russian keyboard: "хурдан" finds "хӯрдан" automatically
• One-tap Tajik letter bar: ғ ӣ қ ӯ ҳ ҷ
• Typo-tolerant search and Tajik word-form hints (ме-, на-, би-, -ҳо, -он, -ам/-ат/-аш)
• Typing English in Russian mode? The app offers to switch with one tap

📖 RICH ENTRIES
• Tappable cross-linked translations
• Example sentences in Tajik, Russian and English
• Listen to pronunciation (Russian and English text-to-speech, normal or slow speed)
• Copy, share, save to favorites, adjustable text size

🎓 LEARN TAJIK
• Spaced-repetition flashcards (SM-2) from your saved words or any topic
• Word games and a placement test to find your level
• Track your progress and daily streak
• Browse words by topic: greetings, food, family, travel, health, numbers and more
• Tajik alphabet and grammar quick reference

📱 WORKS EVERYWHERE ON YOUR PHONE
• Word of the Day home-screen widget
• Select text in any app → "Translate with Tajik Dictionary"
• Share text from any app to look it up
• Back up and restore your saved words (JSON export/import)

🔒 YOUR WORDS STAY ON YOUR PHONE
Your searches, saved words and progress are stored only on your device. The whole dictionary works without internet.

🌐 Interface in English, Russian and Tajik · Light and dark theme

Perfect for students, travelers, the Tajik diaspora, Russian speakers learning Tajik, and anyone learning English from Tajik.
```

### Russian (ru-RU) — add as translation

**Название:** `Таджикский словарь: RU и EN` *(27)*

**Краткое описание:**
`Офлайн-словарь таджикско-русский и таджикско-английский, карточки и игры` *(72)*

**Полное описание:**
```
Таджикский словарь — быстрый таджикско-русско-таджикский и таджикско-англо-таджикский словарь и приложение для изучения языка. Работает полностью офлайн, без регистрации.

🔍 УМНЫЙ ПОИСК
• Два режима: TJK ⇄ RUS и TJK ⇄ ENG — пишите на любом из двух языков, без переключения направления
• Каждый результат помечен [TJ], [RU] или [EN]
• Печатайте по-таджикски на русской клавиатуре: «хурдан» найдёт «хӯрдан»
• Панель таджикских букв в одно касание: ғ ӣ қ ӯ ҳ ҷ
• Поиск с учётом опечаток и форм таджикских слов

📖 ПОДРОБНЫЕ СТАТЬИ
• Переводы-ссылки, по которым можно переходить
• Примеры на таджикском, русском и английском
• Озвучка русских и английских слов (обычная и медленная скорость)
• Копировать, поделиться, добавить в избранное, размер шрифта

🎓 УЧИТЕ ТАДЖИКСКИЙ
• Карточки с интервальным повторением (SM-2)
• Словесные игры и тест на определение уровня
• Прогресс и ежедневная серия
• Слова по темам: приветствия, еда, семья, путешествия, здоровье, числа и др.
• Справочник по алфавиту и грамматике

📱 ИНТЕГРАЦИЯ С ТЕЛЕФОНОМ
• Виджет «Слово дня»
• Выделите текст в любом приложении → «Перевести в Таджикском словаре»
• Резервная копия избранного (экспорт/импорт JSON)

🔒 ВАШИ СЛОВА — НА ВАШЕМ ТЕЛЕФОНЕ
Поиски, избранное и прогресс хранятся только на устройстве. Весь словарь работает без интернета.

🌐 Интерфейс на английском, русском и таджикском · Светлая и тёмная тема
```

> Play Console does not currently offer Tajik (tg) as a listing language; the Russian listing covers most Tajik-speaking users.

### Graphics (must be created — not in the repo yet)
| Asset | Spec | Status |
|---|---|---|
| App icon | 512×512 PNG, 32-bit, ≤1 MB | ❌ export from the launcher icon |
| Feature graphic | 1024×500 PNG/JPG, no alpha | ❌ |
| Phone screenshots | 2–8, 16:9 or 9:16, min 320 px, max 3840 px (1080×1920 recommended) | ❌ suggested: Search results, Entry detail, Flashcards, Learn/Progress, Widget, Dark mode |
| 7" / 10" tablet screenshots | optional | — |

---

## 4. Release

| Step | Value |
|---|---|
| App signing | **Use Play App Signing** (default). Generate an *upload key* in Android Studio → Build → Generate Signed App Bundle. ⚠️ No signing config exists in `app/build.gradle.kts` yet. |
| Artifact | `./gradlew bundleRelease` → `app/build/outputs/bundle/release/app-release.aab` |
| First track | **Internal testing** → then **Closed testing**. ⚠️ New *personal* developer accounts (created after Nov 2023) must run a closed test with **≥12 testers for 14 consecutive days** before Production access is granted. |
| Release name | `0.1.0 (1)` |
| Release notes (en-US) | `First release: offline Tajik–Russian and Tajik–English dictionary with flashcards, word games, Word of the Day widget and system-wide text lookup.` |
| Release notes (ru-RU) | `Первый выпуск: офлайн таджикско-русский и таджикско-английский словарь с карточками, играми, виджетом «Слово дня» и поиском выделенного текста.` |
| Countries | All countries (at minimum: Tajikistan, Russia, Uzbekistan, Kazakhstan, Kyrgyzstan, USA, Germany, etc.) |
| Pricing | Free |
| Devices | Phones + tablets (minSdk 26 / Android 8.0+). Exclude Wear OS, TV, Auto (not targeted). |

---

## 5. Permissions declaration form
**Not required.** With AdMob the app gets `INTERNET`, `ACCESS_NETWORK_STATE` and `AD_ID` (all normal permissions; `AD_ID` is covered by the Advertising ID answer in 2.12). No sensitive/restricted permissions (no SMS, Call Log, location, all-files access, accessibility, exact alarms, foreground service, QUERY_ALL_PACKAGES, photo/video). The `<queries>` for `TTS_SERVICE` is a targeted intent query and needs no declaration.

> If you later add the **daily reminder** from the roadmap: `POST_NOTIFICATIONS` needs no Console declaration, but `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM` would — prefer inexact WorkManager alarms to avoid it.

---

## 6. Things to verify before submitting
1. **Dictionary data rights** — Play's IP policy requires you to have the right to distribute the ~16k entries. Keep a note of their sources/licenses.
2. Privacy policy published at the URL above.
3. Support email mailbox exists.
4. Graphics created (icon 512, feature graphic, ≥2 screenshots).
5. Signed AAB built with an upload key you've backed up.
6. Production IDs filled in `Config/AdUnits.properties` (otherwise release ads are off), `app-ads.txt` published with AdMob **and** Yandex lines, privacy policy re-published with the ads section (it names both networks).

---

## 7. What the API can and cannot do

| Console section | Google Play Developer API | How |
|---|---|---|
| Create app | ❌ | 🖐 UI, once |
| Store listing text (en-US, ru-RU) | ✅ `edits.listings` | `tools/play_publish.py` |
| Icon, feature graphic, screenshots | ✅ `edits.images` | script, from `play/graphics/…` |
| Contact email / website / default language | ✅ `edits.details` | script |
| Upload AAB + release to internal/closed/production | ✅ `edits.bundles`, `edits.tracks` | script |
| Countries | ✅ `edits.countryavailability` (read) / track `countryTargeting` | defaults to all |
| Category & tags | ❌ | 🖐 UI |
| Privacy policy URL, App access, Ads, Content rating, Target audience, Data safety, Advertising ID, News/Gov/Financial/Health | ❌ | 🖐 UI — answers above |

`edits.dataSafety` exists but only accepts the Console's own exported CSV, so it saves no work for a first submission.

### One-time API setup
1. Google Cloud Console → create a project → enable **Google Play Android Developer API**.
2. IAM → **Service accounts** → create one → **Keys → Add key → JSON**. Save it outside the repo (e.g. `%USERPROFILE%\.play\service-account.json`). Never commit it.
3. Play Console → **Users and permissions → Invite new users** → paste the service-account email → app access to *Tajik Dictionary* with **Release apps to testing tracks**, **Manage store presence** (add *Release to production* only when ready).
4. Create the app in Play Console (section 1) — the API cannot create it.
5. Run:
   ```
   pip install google-api-python-client google-auth
   python tools/play_publish.py --key %USERPROFILE%\.play\service-account.json --listings --details
   python tools/play_publish.py --key ... --images
   python tools/play_publish.py --key ... --aab app/build/outputs/bundle/release/app-release.aab --track internal
   ```
   Add `--dry-run` to validate without committing the edit.

> Google requires the **very first** AAB to be uploaded through the Console UI for some new apps ("upload your first app bundle manually"). If the script's upload returns *"Only releases with status draft may be created on draft app"*, it automatically retries with `--status draft`; then press *Review release* in the UI.
