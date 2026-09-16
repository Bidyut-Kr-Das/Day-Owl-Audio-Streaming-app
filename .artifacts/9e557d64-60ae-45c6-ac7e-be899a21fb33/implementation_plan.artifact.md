# Opus Codec Integration Plan

This plan details the integration of the Opus audio codec into the Day Owl audio pipeline to enable measurement and comparison between Raw PCM and Opus compression for low-latency LAN audio broadcasting.

## User Review Required

> [!IMPORTANT]
> **Dependency Selection**: I have selected `eu.buney.kopus:kopus-android:1.6.1.3`. It is a modern, actively maintained Kotlin Multiplatform wrapper for the official `libopus` (version 1.5.2+). It is published to Maven Central and supports the latest Opus features including low-latency modes.

## Proposed Changes

### [Audio Core]

#### [NEW] [AudioCodec.kt](file:///C:/Users/Bidyut Kr. Das/AndroidStudioProjects/DayOwl/app/src/main/java/com/example/dayowl/audio/AudioCodec.kt)
Defines the `AudioCodec` interface and `AudioCodecMode` enum.

#### [NEW] [RawPcmCodec.kt](file:///C:/Users/Bidyut Kr. Das/AndroidStudioProjects/DayOwl/app/src/main/java/com/example/dayowl/audio/RawPcmCodec.kt)
Pass-through implementation for Raw PCM mode.

#### [NEW] [OpusCodec.kt](file:///C:/Users/Bidyut Kr. Das/AndroidStudioProjects/DayOwl/app/src/main/java/com/example/dayowl/audio/OpusCodec.kt)
Implementation of the Opus encoder and decoder using the `kopus` library.

#### [MODIFY] [AudioConfig.kt](file:///C:/Users/Bidyut Kr. Das/AndroidStudioProjects/DayOwl/app/src/main/java/com/example/dayowl/audio/AudioConfig.kt)
Add Opus-specific configuration (bitrate, complexity, frame duration) and a global `ACTIVE_CODEC` switch.

---

### [Audio Pipeline]

#### [MODIFY] [AudioCaptureEngine.kt](file:///C:/Users/Bidyut Kr. Das/AndroidStudioProjects/DayOwl/app/src/main/java/com/example/dayowl/audio/AudioCaptureEngine.kt)
Integrate `AudioCodec` into the capture loop. Implement PCM accumulation to ensure exact frame sizes are passed to the encoder. Add instrumentation for capture and encoding duration.

#### [MODIFY] [AudioPlayer.kt](file:///C:/Users/Bidyut Kr. Das/AndroidStudioProjects/DayOwl/app/src/main/java/com/example/dayowl/audio/AudioPlayer.kt)
Integrate `AudioCodec` into the playback loop. Decodes frames before writing to `AudioTrack`. Add instrumentation for decoding duration.

---

### [Network]

#### [MODIFY] [Models.kt](file:///C:/Users/Bidyut Kr. Das/AndroidStudioProjects/DayOwl/app/src/main/java/com/example/dayowl/model/Models.kt)
Update `AudioFrame` to include codec information and high-precision timing fields.

#### [MODIFY] [AudioPacketizer.kt](file:///C:/Users/Bidyut Kr. Das/AndroidStudioProjects/DayOwl/app/src/main/java/com/example/dayowl/network/AudioPacketizer.kt)
Update the binary protocol to include the codec type in the header.

---

### [Instrumentation]

#### [MODIFY] [StatsManager.kt](file:///C:/Users/Bidyut Kr. Das/AndroidStudioProjects/DayOwl/app/src/main/java/com/example/dayowl/util/StatsManager.kt)
Expand metrics tracking for encode/decode durations, bitrate, and total end-to-end processing latency.

## Verification Plan

### Automated Tests
- Run `gradle build` to ensure dependency resolution and compilation.
- I will create a scratch script to verify PCM -> Opus -> PCM roundtrip consistency.

### Manual Verification
- Deploy to two Android devices.
- Toggle between PCM and OPUS modes in `AudioConfig`.
- Observe Logcat metrics from `StatsManager` diagnostic report.
- Compare "Processing Latency" and "Bitrate" in both modes.
