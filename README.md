# Bittr

An offline, peer-to-peer "twitter for your workspace". Posts hop from phone to
phone over **Bluetooth Low Energy** — no Wi-Fi, no internet, no server. Each
team is its own room; the network is the building.

> Inspired by Jack Dorsey's BitChat, built as a fun office timeline.

## How it works

- **Identity** — every install gets a random 32-bit `deviceId` (the "device
  fingerprint") and a derived `username`. A user-set **nickname** is a display
  alias advertised to peers.
- **Discovery** — each device broadcasts a 22-byte BLE advert (version, room id,
  deviceId, 16-byte Merkle root) plus a scan-response carrying the nickname.
  Devices scan in windows while advertising continuously.
- **Sync decision** — when two devices' Merkle roots differ, the one with the
  **lower deviceId** becomes the GATT client and connects to the higher-ID
  device (nickname breaks ties). The root gates whether a connection happens at
  all.
- **Sync** — bulk exchange: the client sends a single "send me all your events"
  request, the server streams everything in one pass, and the client pushes its
  own events back. One round-trip instead of hundreds.
- **Events** — posts, likes, and unlikes are signed content-addressed events
  that propagate peer-to-peer (including relayed through intermediaries).

## Features

- Offline BLE mesh sync with relay across peers
- Per-device nicknames, byte-capped (20 UTF-8 bytes) to always fit the advert
- Collapsible mesh status panel: advertising state, "initiating sync", nearby
  peers (nickname, deviceId, root match, RSSI)
- Like/unlike heart toggle; "liked by" modal with scrollable list
- Tap any username/nickname to reveal the device fingerprint
- Composer: char-count indicator (green→yellow→red gradient), hard 280-char
  cap, dismiss keyboard on outside tap, auto-scroll to newest post, newest-first
  feed
- **Debug-only**: in-app Logs tab + Timber + Hyperion

## Build

Requirements: JDK 17, Android SDK (compileSdk 34), Gradle (wrapper included).

```bash
# Debug build (with Logs tab, Timber, Hyperion)
./gradlew assembleDebug

# Release build (no Logs tab, no Timber/Hyperion)
./gradlew assembleRelease

# Unit tests + coverage
./gradlew testDebugUnitTest
./gradlew jacocoTestReport
```

- **Debug APK** → `app/build/outputs/apk/debug/app-debug.apk`
- **Release APK** → `app/build/outputs/apk/release/app-release.apk` (signed with
  the debug key for easy sideloading — replace with a production keystore for
  real distribution)

### Version override

Version is read from Gradle properties with defaults in `app/build.gradle.kts`:

```bash
./gradlew assembleRelease -PversionName=0.2.0 -PversionCode=6
```

## Releasing (GitHub Actions)

Two workflows (see `.github/workflows/`):

- **`release.yml`** — manual dispatch. Enter a version, builds the **release**
  APK (no logging), runs tests, and creates/updates a GitHub Release with the
  APK attached.
- **`debug.yml`** — manual dispatch. Builds the **debug** APK (with Logs tab +
  Timber + Hyperion) and attaches it to a pre-release.

## Testing

160+ unit tests, ~94% line coverage. Run `./gradlew testDebugUnitTest`.

A host-side BLE advert scanner (`tools/scan_adverts.py`, uses `bleak`) can
verify adverts and GATT from a laptop:

```bash
python3 -m venv .venv && .venv/bin/pip install bleak
.venv/bin/python3 tools/scan_adverts.py 10          # scan
.venv/bin/python3 tools/scan_adverts.py --gatt 10   # connect + inspect
```

> **Note:** Android **emulators** cannot do real BLE (no radio). Sync and
> discovery must be tested on physical hardware.

## Project layout

```
app/src/main/java/com/bitter/
  ble/      BLE mesh service, GATT client/server, advert codecs, collision
  crypto/   SHA-256, identity derivation
  log/      logging facade + in-app LogStore (debug wiring in src/debug)
  merkle/   Merkle tree + differ
  mesh/     coordinator, status store, nickname registry
  model/    event + wire codec
  store/    Room/event repository
  sync/     sync engine, peer, local server
  ui/       Compose timeline, status panel
tools/      scan_adverts.py (host-side BLE probe)
```
