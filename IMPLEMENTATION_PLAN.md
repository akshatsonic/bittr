# Implementation Plan — Bitter (Offline BLE Mesh Timeline)

Source: `akshatsonic-akshatsonic-palembang-design-20260811-225037.md` (DRAFT, revised — Merkle tree per calendar day)

## Chosen approach

Approach A — Merkle root hash broadcast + GATT drill-down sync. One sync mechanism. Per-calendar-day tree. Foreground Service from Day 1. Kotlin + Compose, Android-first, one public room, no encryption, sideload APK.

## Functional requirements (MVP)

1. **Events, not tweets.** Single event model carries all activity. Every event
   is stored locally and rendered by type. Examples:
   - `POST:<content>:<meta>` — a message post (≤280 chars).
   - `LIKE:<target_event_id>:<meta>` — a like on another event.
   - Extensible: more kinds added without changing transport.
2. **Identity = username from device fingerprint.** First launch derives a
   username from a device fingerprint; user can edit it once. Every post/like
   is authored under this username and displayed in the timeline.
3. **Post** — create a POST event, appears on timeline.
4. **Like** — react to any event; rendered in a separate like queue below the
   parent event. If the parent event is not yet present, the like count is
   hidden; it renders only once the parent arrives.
5. **Timeline** — render all event kinds in chronological order, auto-refresh.
6. **BLE mesh** — phones exchange events via BLE, no Wi-Fi/server.
7. **Sync** — Merkle root + GATT drill-down, pull only missing events.
8. **Foreground Service** — mesh alive when backgrounded/locked.
9. **One public room** — no encryption, no accounts.
10. **Local store** — today's events + archive (Room/SQLite).

## Open decisions (must lock before their step)

| # | Decision | Blocks | Options |
|---|----------|--------|---------|
| 1 | Min Android API | Step 1, 6 | legacy 31B advert (API 21) vs extended 251B (API 26) |
| 2 | Nostr crypto Day 1 vs later | Step 4 | Full Nostr event + secp256k1 now vs internal JSON + crypto later |
| 3 | Multi-hop 20s feasibility | Step 9 | validate timing in Step 3 prototype before committing |
| 4 | GATT drill-down ownership (client walks vs server walks tree) | Step 7 | nail exact protocol before coding |

## Steps

### Step 1 — Project skeleton (Week 1)
- New Android project: Kotlin, Gradle, Compose.
- `minSdk` = oldest team device (Decision #1).
- Single MainActivity stub.

### Step 2 — Foreground Service shell (Week 1)
- `BleMeshService` foreground service, persistent notification.
- `foregroundServiceType="connectedDevice"`.
- Start on launch; log lifecycle. Non-negotiable per premise #3.

### Step 3 — BLE hello-world + timing prototype (Week 1-2)
- Advertise fixed UUID; scan on 2nd phone; log discovery.
- Verify simultaneous scan+advertise on real team devices (premise #1).
- Prototype multi-hop timing A→B→C; validate ~20s (Decision #3).

### Step 4 — Data layer (Week 3-4)
- Room DB: `events_today`, `events_archive`.
- Event model (one schema, many kinds): `id`, `kind` (POST/LIKE/…), `author`
  (username), `content`, `target_event_id` (nullable, for LIKE), `created_at`,
  `meta`, `signature`. Wire format: Nostr (pubkey 32B, ts 8B, content ≤280
  chars, sig 64B) or internal JSON (Decision #2).
- Identity: derive username from device fingerprint at first launch (editable
  once), store locally. Keypair generated same time, stored in Android Keystore.
- Day-rollover worker: move `events_today`→`events_archive`, reset tree.

### Step 5 — Merkle tree (Week 3-4)
- Leaves: `SHA256(event_id)`, sorted lexicographically; odd → dup last.
- Internal: `SHA256(left || right)`. Root truncated to 20B.
- Rebuild on post. Unit-test against known vectors.

### Step 6 — Advert packet (Week 3-4)
- `[2B company 0xFFFF][1B version][1B room][4B device ID][20B root]` = 28B.
- 250ms interval (tune later). Device ID = random 4B, stored locally.
- Size depends on Decision #1 (legacy vs extended).

### Step 7 — GATT sync protocol (Week 5-6)
- One service, three chars: `MERKLE_QUERY` (write+notify), `EVENT_FETCH` (notify), `IDENTITY` (read).
- `requestMtu(512)`; `[4B len][payload]` framing.
- Level-by-level drill-down to locate missing leaves (resolve Decision #4 first).
- Collision rule: lower 4B device ID = GATT client; tie → backoff 100ms.
- Stop-and-wait: client pauses scan; server keeps advertising.

### Step 8 — Timeline UI (Week 7-8)
- Compose: input + post button + scrollable timeline.
- Render events by `kind`: POST as message card; LIKE goes to a per-parent like
  queue shown below the parent event.
- Like queue rule: if parent event absent, hide its like count/queue; reveal
  when parent syncs in.
- Observe Room; auto-refresh on sync.

### Step 9 — Real-device test (Week 9-10)
- 2 → 3 → 5 phones. Tune advert interval.
- Validate multi-hop + 30-min GATT stability (no crash).

### Step 10 — Polish + rollout (Week 11-12)
- Nickname edit UI (username already derived from fingerprint in Step 4). Crash fixes.
- `./gradlew assembleDebug`; sideload APK to team.

## Risks
- BLE debugging ~2x expected effort (premise #4).
- Day-rollover race (event at 23:59:59 arrives at 00:00:03 — today vs archive).
- Multi-hop 20s target unproven until Step 3.
- Battery drain +10-15% in background.
- LIKE referencing an event not yet synced — hold in per-parent like queue; hide count until parent arrives.

## Success criteria (from doc)
- 2 phones, no Wi-Fi/data, exchange events <10s in range.
- A→B→C relay <20s.
- Foreground Service keeps mesh alive backgrounded.
- Timeline renders POST and LIKE events chronologically, auto-refresh.
- 5+ people same room, 30-min GATT churn no crash.
