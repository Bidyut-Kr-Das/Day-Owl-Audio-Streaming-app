# Day Owl

[![Kotlin, Android Studio, Gradle, GitHub Actions, Git](https://skillicons.dev/icons?i=kotlin,androidstudio,gradle,githubactions,git)](https://skillicons.dev)

Share whatever your phone is playing with everyone in the room. One device hosts; the others join
over the local WiFi network and hear it in near real time — no internet, no account, no pairing.

Built for small groups (2–5 listeners): a shared playlist on a speaker-less trip, a film on a laptop
with several sets of headphones, a late-night listen that shouldn't wake the house.

---

## How it works

The host captures **system audio** (via `MediaProjection` playback capture — whatever app is playing,
not the microphone), encodes it with Opus and unicasts it over UDP to each joiner. Joiners find the
host by broadcast, so nobody types an IP address.

```
HOST                                          JOINER
AudioRecord ─> Opus ─┐                  ┌──> FrameRing ─> Opus ─> AudioTrack
  (10ms)             │                  │     (jitter)            (speaker)
                     └── UDP ───────────┘
                  ~340-byte datagrams (frame N + frame N-1),
                  to the port the joiner asked for

              ◄── UDP :5000 (control) ──►   DISCOVER probe, join, heartbeat
              ─── UDP :5002 (announce) ──►  host HOST_INFO every 1 s
```

**48 kHz mono Opus, 10 ms frames, 128 kbps** ([Concentus](https://github.com/plasmoapp/concentus),
a pure-Java Opus port — no NDK). `RESTRICTED_LOWDELAY` mode adds ~5 ms of codec delay. Every packet
carries its own frame **and the previous one**, so any single lost datagram is recovered bit-exact;
only two consecutive losses need concealment, and that is Opus's own PLC, not a repeated frame.

**Nothing paces the stream except the hardware.** On the host, `AudioRecord.read()` returns once per
10 ms of real audio and the send happens inline on the same thread — no queue, no dispatcher hop.
On the joiner, `AudioTrack.write(WRITE_BLOCKING)` blocks until the audio device accepts the frame,
which clocks the play-out loop at exactly the DAC rate. Both audio threads run at
`THREAD_PRIORITY_URGENT_AUDIO`.

**The jitter buffer is a 64-slot ring** indexed by `sequence and 63`, with each slot's sequence
number published through an `AtomicLongArray` — a stale slot left by a wrap simply reads as a miss.
Depth is adaptive: it starts at 3 frames (30 ms). An underrun holds play-out in place (Opus conceals
one frame) so the buffer deepens, and raises the drop threshold by one frame, up to 150 ms. Ten
clean seconds give a frame back. The sender's clock is reconciled against the speaker's by dropping
a single frame when depth runs past that threshold.

**Discovery works in both directions.** The joiner sends DISCOVER to every subnet's own broadcast
address (not just `255.255.255.255`, which the hotspot phone routes out of cellular), and the host
announces HOST_INFO to every subnet on port 5002. Either one getting through is enough.

Typical end-to-end latency is roughly **40–80 ms** on a clean link, dominated by the jitter buffer and the device's
own audio buffers. More importantly it stays flat: there is no unbounded queue anywhere in the path.

### Connection lifecycle

The joiner opens its audio socket before it joins and puts that port in the `JOIN_REQUEST`, so the
host always sends to a port something is listening on. A joiner re-sends `JOIN_REQUEST` every 2 s and the host answers every one, so the same traffic doubles
as a two-way heartbeat. The host evicts a client it hasn't heard from in 6 s; the joiner tears down
if the host stops answering for 8 s. A join that never gets accepted gives up after ~5 s rather than
hanging on "Connecting…" forever.

---

## Requirements

- **Android 10 (API 29) or newer**, both devices. Playback capture does not exist before that.
- Both devices on the **same WiFi network**, with client isolation off. A phone hotspot works, with
  or without mobile data; most home routers are fine; many public and campus networks block
  device-to-device traffic entirely.

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
| `network/DiscoveryManager.kt` | Broadcast probe, host list built from reply source addresses |
| `network/WifiNetwork.kt` | Pins every socket to the WiFi network, not the default one |
| `service/` | Foreground services that own the host and joiner sessions |
| `ui/` | Compose screens, Koin view models |

## Known limits

- **Not encrypted.** Anyone on the network can receive the stream if they know the port. Fine for a
  living room, not for anything sensitive.
- **Screen-off hosting degrades.** The low-latency WiFi lock is only honoured in the foreground with
  the screen on; audio keeps flowing but jitter rises.
- **Mono.** Stereo is a channel-count change; Opus makes the bandwidth cost small.
- **Not compatible with 1.1.x.** Packet format v2 (Opus); old builds ignore the stream.
