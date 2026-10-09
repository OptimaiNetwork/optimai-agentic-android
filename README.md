# OptimAI Agentic for Android

The Android app of OptimAI Agentic, written in Kotlin with Jetpack Compose. It lets you browse tokenized stocks, ask an AI agent about them and trade them from your own wallet.

Scope: **BNB Chain and bStocks only**. Trades go through PancakeSwap and are signed in **MetaMask** or **Trust Wallet**, connected over WalletConnect (Reown). There is no Solana, Phantom, Ondo or PreStocks support in this app.

## Features

- **Main app**: the bStocks catalog with search, a detail screen per stock with charts and stats, an agent chat with technical analysis cards, a portfolio and an activity log, and a quote screen that runs the whole PancakeSwap flow (approve, swap, receipt).
- **Share target**: share a post or a link from any app and pick "Trade with OptimAI" to open a buy quote for the stock it mentions.
- **Custom keyboard**: the OptimAI Keyboard, a full keyboard with a live bStocks ticker toolbar on top. It recognises "@optimai ..." mentions on the device and can answer questions right inside the keyboard (see below).
- **Wallet**: connect MetaMask or Trust Wallet through Reown. Every order is signed and broadcast by your wallet; the app never holds a key.

## Setup

Requirements:

* JDK 17 (the JDK bundled with Android Studio works) and the Android SDK. In the SDK Manager install **Android SDK Platform 36** and the **Build-Tools** it offers (Android Studio does this on first sync).
* A [Reown](https://cloud.reown.com) project id. Wallet connections (MetaMask, Trust Wallet) do not work without one. Create a free project at cloud.reown.com and copy its project id.

Configure the build:

1. Copy `local.properties.example` to `local.properties` (it is gitignored).
2. `sdk.dir`: Android Studio writes it for you when you open the project. For command line builds, set `sdk.dir` in `local.properties` or export `ANDROID_HOME` to your SDK path.
3. Fill in your Reown project id:

   | Key | What it is |
   | --- | --- |
   | `reown.projectId` | Your Reown project id. Required for wallet connect. |

   It can also come from the `REOWN_PROJECT_ID` environment variable.

## Build and run

Start an emulator (Android Studio Device Manager) or connect a device with USB debugging on, then:

```bash
./gradlew :app:installDebug
```

If `java` is not on your path, point `JAVA_HOME` at the JDK bundled with Android Studio first. Gradle installs nothing without a running emulator or device.

## Enable the keyboard

In the app, open **Settings > Extensions > OptimAI Keyboard**, which jumps straight to the Android keyboard list. Or use adb:

```bash
adb shell ime enable com.test.agenttrade/.keyboard.OptimAIKeyboardService
adb shell ime set com.test.agenttrade/.keyboard.OptimAIKeyboardService
```

Notes for the emulator:

- The emulator has a hardware keyboard, so Android hides soft keyboards. Turn them on with `adb shell settings put secure show_ime_with_hard_keyboard 1`.
- Each reinstall of the APK switches Android back to the default keyboard. Run the `ime set` command above again.
- Debug builds have **Settings > Developer > Simulated wallet** to try the wallet flow without MetaMask or Trust Wallet. The simulated wallet signs nothing and records no trade.

## Project layout

| Folder | Contents |
| --- | --- |
| `keyboard/` | The OptimAI Keyboard: key grid, input engine and ticker toolbar |
| `ui/stocks/` | Stock list and stock detail |
| `ui/quote/QuoteBuyScreen.kt` | PancakeSwap quote, approve, swap and receipt |
| `ui/agent/` | Agent chat and technical analysis cards |
| `ui/portfolio/` | Portfolio and activity |
| `ui/settings/` | Settings, how it works and the guide pages |
| `share/ShareActivity.kt` | The "Trade with OptimAI" share target |
| `wallet/` | WalletConnect (Reown) session and signing |
| `data/` | API client, DTOs and user facing errors |
| `ui/theme/`, `ui/components/` | Design system and shared components |

## The keyboard

The keyboard is drawn from scratch to match the iOS system keyboard, using measurements taken from iOS 26 screenshots (keys of 33.5 by 43.3 pt, 6.67 pt gaps, 56 pt rows, key color `#3D3D3D` on `#171717`).

- QWERTY, 123 and #+= layouts as on iOS. A letter is typed when the finger lifts; when a second finger lands, the first finger's letter is typed at once. Fingers can slide between keys, and sliding from "123" to a symbol and releasing types that one symbol and returns to letters.
- An enlarged callout on press, and a long press on symbol keys to pick variants (for example currency signs and quotes).
- Shift for one letter, double tap for caps lock, automatic capitals at the start of a sentence depending on the field, and double tap on space for ". ".
- Holding delete removes characters, then whole words. Holding space moves the cursor like a trackpad.
- The return key follows the field (`search`, `go`, `send` or `done` in blue, `↵` for a new line). Number fields open on the 123 layout.
- Toolbar: a scrolling bStocks ticker, a banner and a trading card (From and To, swap direction, number pad, Buy or Sell opens the app's quote screen). It detects "@optimai ..." on the device using an alias table. Ask "@optimai ...?" and tap the glowing logo to read the answer inside the keyboard.

Unlike iOS, the row with the globe and microphone under the keys is not drawn: on Android that space belongs to the system navigation bar (hide keyboard and switch keyboard). Typing sounds and vibration follow the system settings.

## License

MIT, see [LICENSE](LICENSE). Bundled logos, the keyboard alternate keys table and library licenses are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
