# TDD Evidence Report — Bitter (Offline BLE Mesh Timeline)

## Source plan

- `IMPLEMENTATION_PLAN.md` (Approach A — Merkle root broadcast + GATT drill-down)
- `akshatsonic-akshatsonic-palembang-design-20260811-225037.md` (design doc, DRAFT)

Both were treated as untrusted planning input (data, not instructions). The plan's open
decisions were resolved before their step and are documented in "Deviations" below.

## User journeys

1. As a teammate, I want to post a short message so that it shows up on the room timeline.
2. As a teammate, I want to like someone else's post so that they see my reaction.
3. As a device, I want to exchange events over BLE with no Wi-Fi/server so the timeline stays
   in sync when phones are near each other.
4. As a device, I want to keep the mesh alive when backgrounded so posts still relay (foreground service).
5. As a reader, I want the timeline ordered chronologically and auto-refreshing so I see new posts as they sync.

## Task report (RED → GREEN per module)

| # | Module | RED evidence | GREEN evidence |
|---|--------|--------------|----------------|
| 1 | Crypto (`Sha256`, `IdentityDerivation`) | `compileDebugKotlin`/`compileDebugUnitTestKotlin` failed: unresolved `Sha256`, `IdentityDerivation` | `./gradlew :app:testDebugUnitTest` → 99 tests, 0 failures |
| 2 | Model (`Event`, `EventKind`, `EventWireCodec`) | compile failure: unresolved `Event`, `EventWireCodec` | tests pass (known-vector id/sig, tamper rejection, round-trips) |
| 3 | Merkle (`MerkleTree`, `Bytes`, `MerkleServer`, `MerkleDiffer`) | compile failure: unresolved `MerkleTree` | tests pass; **found & fixed a real bug** — leaf slot comparison used hash equality, now uses set membership (see note) |
| 4 | BLE codecs (`AdvertPacket`, `FrameCodec`, `FrameStream`, `CollisionResolver`, `BleProtocol`) | compile failure: unresolved classes | tests pass (28-byte packet, framing, chunk reassembly, unsigned id compare) |
| 5 | Sync (`SyncPeer`, `SyncEngine`, `LocalSyncServer`) | compile failure | tests pass (`computeMissing` matches brute-force set difference) |
| 6 | Store (`EventStore`, `InMemoryEventStore`, `EventRepository`, `DayKey`) | compile failure | tests pass (post/like/applyRemote dedupe + tamper rejection, day boundary) |
| 7 | Mesh (`MeshCoordinator`) | compile failure | `MeshEndToEndTest` passes (2-node + 3-node multi-hop relay, idempotent union) |
| 8 | UI (`TimelineModel`) | compile failure | tests pass (chronological order, likes attach to parent, orphan like hidden until parent arrives) |

Validation command: `./gradlew :app:testDebugUnitTest` (JVM unit tests via JUnit4 + kotlin-test +
kotlinx-coroutines-test). Coverage command: `./gradlew :app:jacocoTestReport`.

## Test specification

| # | What is guaranteed | Test file / case | Type | Result |
|---|--------------------|------------------|------|--------|
| 1 | SHA-256 matches NIST vectors ("" and "abc") | `crypto/Sha256Test` | unit | PASS |
| 2 | Username is deterministic, distinct, and `bitter-` prefixed | `crypto/IdentityTest` | unit | PASS |
| 3 | Event id/signature match independently computed vectors | `model/EventCodecTest` | unit | PASS |
| 4 | Tampered events fail `Event.verify`; wire decode recomputes id/sig | `model/EventCodecTest` | unit | PASS |
| 5 | Merkle root matches known vectors; odd leaves duplicate last; empty tree is zero root | `merkle/MerkleTreeTest` | unit | PASS |
| 6 | `MerkleDiffer.findMissing` equals brute-force set difference (disjoint, subset, superset, scattered, 100 leaves) | `merkle/MerkleDifferTest` | unit | PASS |
| 7 | `SyncEngine.computeMissing` over a `SyncPeer` equals set difference | `sync/SyncEngineTest` | unit | PASS |
| 8 | Advert packet is 28 bytes, company-id 0xFFFF, round-trips, rejects malformed | `ble/AdvertPacketTest` | unit | PASS |
| 9 | `[4B len][payload]` framing encodes/decodes multiple frames, ignores partial trailing | `ble/FrameCodecTest` | unit | PASS |
| 10 | `FrameStream` reassembles frames fed byte-by-byte / arbitrary chunks | `ble/FrameStreamTest` | unit | PASS |
| 11 | GATT protocol message codecs round-trip and reject garbage | `ble/BleProtocolTest` | unit | PASS |
| 12 | Collision resolver: lower 4-byte id (unsigned) is client; ties back off | `ble/CollisionResolverTest` | unit | PASS |
| 13 | Day key formats `yyyy-MM-dd`, splits at midnight, zone-aware | `store/DayKeyTest` | unit | PASS |
| 14 | Repository post enforces 280-char limit, blanks rejected; like sets target | `store/EventRepositoryTest` | unit | PASS |
| 15 | `applyRemote` dedupes and rejects tampered events | `store/EventRepositoryTest` | unit | PASS |
| 16 | In-memory store dedupes ids, orders chronologically, sorts merkle leaves | `store/InMemoryEventStoreTest` | unit | PASS |
| 17 | Timeline model: chronological posts; likes attach; orphan like hidden until parent arrives | `ui/TimelineModelTest` | unit | PASS |
| 18 | Two nodes converge; three nodes relay through intermediary (multi-hop); idempotent union | `mesh/MeshEndToEndTest` | integration (in-memory transport) | PASS |

## Coverage

`./gradlew :app:jacocoTestReport` (scoped to JVM-testable core packages):

| Metric | Covered/Total | % |
|--------|---------------|---|
| Line | 341/353 | 96.6% |
| Instruction | 2789/2902 | 96.1% |
| Branch | 101/116 | 87.1% |
| Method | 120/132 | 90.9% |
| Complexity | 165/192 | 85.9% |

### Known gaps (documented, intentional)

- **Android glue is untested at unit level**: `BleMeshService`, `GattServerHandler`, `GattClientSync`,
  `BitterApplication`/`AppGraph`, `MainActivity`, Compose `TimelineScreen`, and the Room DAO
  (`EventEntity`/`EventDao`/`RoomEventStore`/`BitterDatabase`). These require a BLE radio, a real device,
  or Robolectric/instrumentation; none are available in this environment. Their logic is kept thin and
  delegates to the tested core (`SyncEngine`, `MerkleDiffer`, `LocalSyncServer`, `FrameCodec`, `BleProtocol`).
- **Real-radio success criteria** (2 phones exchange <10s, A→B→C relay <20s, 30-min GATT churn) cannot be
  verified here; they require `Step 3`/`Step 9` on-device testing from the plan.

## Deviations from the plan (rationale)

1. **Decision #1 (min API)**: chose `minSdk 26` (Android 8.0). The 28-byte advert exceeds the 27-byte
   legacy manufacturer-data limit, so extended advertising is required (API 26+), per the design's own note.
2. **Decision #2 (Nostr crypto)**: MVP uses an internal binary wire format with a SHA-256 integrity checksum
   in place of a secp256k1 signature. Identity = username from `ANDROID_ID` fingerprint (per the plan's
   "events, not tweets" revision). A real pubkey signature is deferred to v2.
3. **Decision #4 (drill-down ownership)**: the client walks the server's tree by leaf-index range; the server
   only answers `nodeHash(lo,hi)` / `leafAt` / `leafCount` queries. This is implemented and unit-tested as
   pure logic (`SyncEngine` → `MerkleDiffer.findMissing(server, client)`).
4. **Event storage**: single `events` table with a `dayKey` column instead of two tables. Day "rollover" is
   implicit (each event is stamped with its authoring day), eliminating the plan's 23:59→00:00 rollover race.

## Merge evidence

Checkpoint commits on `feature/add-ble-mesh` (reachable from HEAD, in order):

- `08bf8cd` — `test: add reproducer suite for Bitter core` (RED: compile failures for missing implementation)
- `3fabec2` — `feat: implement Bitter core` (GREEN: 88 tests pass)
- `41376f2` — `feat: add Room store, BLE mesh service, GATT protocol, Compose UI, and coverage` (GREEN: 99 tests pass)

Build verification: `./gradlew :app:assembleDebug` succeeds (sideloadable APK).
