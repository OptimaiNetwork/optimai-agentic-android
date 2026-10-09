# Third party notices

OptimAI Agentic for Android is released under the MIT License (see `LICENSE`).
This file lists third party material that is bundled with the app or loaded by
it. Trademarks and logos belong to their owners and are used only to identify
the wallet, chain or company they stand for. Their use here does not imply
endorsement by, or affiliation with, those owners.

## Bundled images (`app/src/main/res/drawable-nodpi`)

| File | What it shows | Owner and usage note |
| --- | --- | --- |
| `wallet_metamask.png` | MetaMask fox | Trademark of Consensys Software Inc. Shown only on the "connect MetaMask" button. |
| `wallet_trust.png` | Trust Wallet shield | Trademark of its owner (Trust Wallet). Shown only on the "connect Trust Wallet" button. |
| `chain_bnb.png` | BNB Chain mark | Trademark of BNB Chain / Binance. Shown only to label the BNB Smart Chain network. |
| `tab_logo_bstocks.png` | bStocks mark | Trademark of its issuer. Shown only to label the bStocks tab. |
| `artwork_optimai.jpg` | OptimAI artwork | Created for this project, covered by the MIT License. |
| `mipmap-*/ic_launcher*.png` | App launcher icon | Created for this project, covered by the MIT License. |

Remove or replace any of these before redistributing a modified build if you do
not have the right to use the mark.

## Images loaded at runtime (not bundled)

* Chain and token logos are fetched from the Trust Wallet assets repository
  (https://github.com/trustwallet/assets, MIT License). The token logos in that
  repository are the property of their respective projects.
* Company and stock logos are fetched from URLs returned by the OptimAI Agentic
  server and belong to the respective companies or issuers.

## Keyboard alternate keys

The long-press alternates of the OptimAI Keyboard (`KeyboardLayout.kt`, the
`alternates` table) are our own list of symbol variants. No third-party data or code is included.

## Libraries

These are pulled in by Gradle at build time and are not stored in this repository.
Each is distributed under its own license, shown here with its upstream project.

| Library | License |
| --- | --- |
| AndroidX, Jetpack Compose, Material 3, Core SplashScreen (Google) | Apache License 2.0 |
| Kotlin, kotlinx.coroutines, kotlinx.serialization (JetBrains) | Apache License 2.0 |
| OkHttp (Square) | Apache License 2.0 |
| Coil 3 (Coil Contributors) | Apache License 2.0 |
| Reown Kotlin SDK, `android-bom`, `android-core`, `sign` (Reown) | Apache License 2.0 |

The Apache License 2.0 text is at https://www.apache.org/licenses/LICENSE-2.0.
The build excludes duplicate `META-INF` license and notice files from the APK
(see `packaging` in `app/build.gradle.kts`) only to avoid merge conflicts, so
this file is where the licenses of the shipped libraries are recorded.
