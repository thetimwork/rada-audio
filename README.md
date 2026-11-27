# RADA audio

A universal, device-aware Android audio enhancement platform. Premium sound for Bluetooth, analogue wired headphones, IEMs and USB DACs, with the lowest practical additional latency.

Your sound. Your rules. An Android sound-enhancement app: it shapes the audio of every app on the phone. It is not a music player.

## This build: Standard engine alpha

- 10-band graphic EQ, preamp, balance, bass and clarity controls, 8 presets (Pure bypasses everything)
- Peak limiter and compressor
- Settings saved per output (Bluetooth model, wired, USB, phone speaker) and restored when it reconnects
- Hold to compare with the original sound
- Uses Android's `DynamicsProcessing` on the global output (session 0). If the phone refuses that, it falls back to the sessions apps announce (Spotify and most players)

Not yet: room reverb, virtual surround, earbud controls (ANC), and the Advanced engine (Shizuku), which adds crossfeed, HRTF spatial, convolution and true mono for apps that allow capture.

## Get the APK

Every push to `main` builds an APK with GitHub Actions and publishes it under **Releases** as `alpha-N`. Android 9 (API 28) or later.

The alpha builds are signed with a shared test key in `keystore/` so each build installs over the last. Do not use that key for Play Store releases.

## Build locally

Open in Android Studio (JDK 17), or run `./gradlew assembleDebug`.

## Layout

- `engine/RadaEngine.kt`: DynamicsProcessing setup, session-0 probe and per-app fallback, settings per output
- `engine/EngineService.kt`: foreground service, session broadcasts, output changes
- `engine/Outputs.kt`: which output is active
- `ui/`: Compose screens (Home, EQ, Space, Devices, Studio)


## Design

Flat, in the Rada brand: Aesthetic White and Rich Black, one Guardsman Red, Figtree throughout. Light and dark themes (Studio › Appearance: System, Light or Dark). Phone, Fold (closed and open) and tablet layouts.

Figtree is by Erik Kennedy, under the SIL Open Font License 1.1 (see `licenses/Figtree-OFL.txt`).
