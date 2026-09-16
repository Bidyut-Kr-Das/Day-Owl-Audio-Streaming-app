# Day Owl

Share whatever your phone is playing with everyone in the room. One device hosts; the others join
over the local WiFi network and hear it in near real time — no internet, no account, no pairing.

Built for small groups (2–5 listeners): a shared playlist on a speaker-less trip, a film on a laptop
with several sets of headphones, a late-night listen that shouldn't wake the house.

---

## How it works

The host captures **system audio** (via `MediaProjection` playback capture — whatever app is playing,
not the microphone) and unicasts it as raw PCM over UDP to each joiner. Joiners find the host through
mDNS/NSD, so nobody types an IP address.

```
HOST                                          JOINER
AudioRecord ──┐                          ┌──> FrameRing ──> AudioTrack
  (10ms)      │                          │     (jitter)      (speaker)
              └── UDP :5001 ─────────────┘
                  980-byte datagrams

              ◄── UDP :5000 (control) ──►
                  NSD discovery, join, heartbeat
```

**48 kHz mono PCM16, 10 ms frames.** 960 bytes of audio plus a 20-byte header is a 980-byte datagram
— under the 1500-byte MTU, so packets never fragment. Uncompressed, so one listener costs ~768 kbps;
five cost under 4 Mbps, which local WiFi absorbs easily. No codec means no encode/decode delay.

**Nothing paces the stream except the hardware.** On the host, `AudioRecord.read()` returns once per
10 ms of real audio and the send happens inline on the same thread — no queue, no dispatcher hop.
On the joiner, `AudioTrack.write(WRITE_BLOCKING)` blocks until the audio device accepts the frame,
which clocks the play-out loop at exactly the DAC rate. Both audio threads run at
`THREAD_PRIORITY_URGENT_AUDIO`.

**The jitter buffer is a 64-slot ring** indexed by `sequence and 63`, with each slot's sequence
number published through an `AtomicLongArray` — a stale slot left by a wrap simply reads as a miss.
Play-out targets 5 frames (50 ms) of depth and reconciles the sender's clock against the speaker's
by dropping a single frame when it runs long. A missing frame is concealed by repeating the last
good one up to 3 times, then silence.

Typical end-to-end latency is roughly **60–100 ms**, dominated by the jitter buffer and the device's
own audio buffers. More importantly it stays flat: there is no unbounded queue anywhere in the path.

### Connection lifecycle

A joiner re-sends `JOIN_REQUEST` every 2 s and the host answers every one, so the same traffic doubles
as a two-way heartbeat. The host evicts a client it hasn't heard from in 6 s; the joiner tears down
if the host stops answering for 8 s. A join that never gets accepted gives up after ~5 s rather than
hanging on "Connecting…" forever.

---

## Requirements

- **Android 10 (API 29) or newer**, both devices. Playback capture does not exist before that.
- Both devices on the **same WiFi network**, with client isolation off. Most home routers are fine;
  many public and campus networks block device-to-device traffic entirely.

Apps can opt out of being captured (`ALLOW_CAPTURE_BY_NONE`). Most streaming services do, so DRM-
protected audio will come through silent — that restriction is enforced by Android, not by this app.

## Using it

1. **Host:** open the app, start a session, and grant the screen-capture prompt (this is how Android
   gates audio capture — nothing is recorded or sent anywhere but to your joiners).
2. **Joiners:** open the app, pick the host from the discovered list, tap Join.
3. Play audio on the host. Keep the host app in the foreground with the screen on for the lowest
   jitter — `WIFI_MODE_FULL_LOW_LATENCY` only applies under those conditions.

## Building

```bash
./gradlew assembleDebug          # APK at app/build/outputs/apk/debug/
./gradlew testDebugUnitTest      # packet format + jitter-buffer play-out logic
```

JDK 21 (AGP 9.3.1 / Gradle 9.5). Pushing a `v*` tag builds and publishes a GitHub release with the
APK attached — see `.github/workflows/ci.yml`.

## Layout

| Path | What lives there |
|---|---|
| `audio/AudioCaptureEngine.kt` | The whole host path: capture → packetize → fan out, one thread |
| `audio/AudioPlayer.kt` | The whole joiner path: an RX thread and a play-out thread |
| `audio/FrameRing.kt` | Jitter ring and play-out control. Pure JVM, so it is unit-tested |
| `network/UdpEndpoint.kt` | One socket that both sends and receives |
| `network/AudioPacketizer.kt` | Wire format, written in place — the hot path allocates nothing |
| `network/SessionManager.kt` | Joiner control plane and connection state machine |
| `network/DiscoveryManager.kt` | mDNS/NSD advertise and discover |
| `service/` | Foreground services that own the host and joiner sessions |
| `ui/` | Compose screens, Koin view models |

## Known limits

- **Not encrypted.** Anyone on the network can receive the stream if they know the port. Fine for a
  living room, not for anything sensitive.
- **Screen-off hosting degrades.** The low-latency WiFi lock is only honoured in the foreground with
  the screen on; audio keeps flowing but jitter rises.
- **Loss concealment is frame-repeat.** Audible as a short stutter under heavy packet loss. Opus with
  in-band FEC is the proper fix and is the intended next step if listener counts grow past ~8.
