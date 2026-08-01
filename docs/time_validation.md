# Multi-Layer Time Validation Engine

## The Problem
Time skew or clock jumps (backward/forward) cause severe **bank settlement rejections** (BCA, Mandiri, BNI, BRI, Bank DKI, Nobu, QRIS) and break SAM module APDU authentication. Since validators operate offline and in moving buses, cellular time (NITZ) is notoriously unreliable, and malicious tampering of the device clock is a major threat.

## 4-Layer Cryptographic & Monotonic Time Engine

To guarantee settlement time compliance and protect public funds, the validator implements a 4-Layer Time Validation Engine.

### Layer 1: Multi-Source Time Synchronization Pipeline
The device constantly seeks the true UTC time from multiple redundant sources:
1. **Source A (GPS NMEA Atomic Time):** Extracts absolute UTC atomic time directly from raw GPS NMEA sentences (`$GPRMC` / `$GPZDA`). This works 100% offline without cellular data as long as there is an open sky.
2. **Source B (NTP Stratum-1/2 Pools):** Queries reliable time servers (`pool.ntp.org` / `time.google.com`) via SNTP protocol when a network connection is available.
3. **Source C (NITZ):** Intercepts cell tower NITZ broadcast time from the SIM card provider (used as a fallback).
4. **Source D (Hardware RTC Node):** Reads hardware RTC `/dev/rtc0` via JNI/Root for low-level verification.

### Layer 2: Monotonic Drift Guard (`MonotonicTimeGuard`)
Android's `SystemClock.elapsedRealtimeNanos()` never decreases and cannot be tampered with by the user or network settings. We use it to compute true elapsed time velocity.
- Calculation: $\Delta t_{real} = \text{elapsedRealtimeNanos}() - \text{lastReferenceNanos}$
- If the system wall clock (`System.currentTimeMillis()`) diverges from $\Delta t_{real}$ by more than $\pm 5$ seconds, a **Clock Drift Anomaly** is flagged.

### Layer 3: Persisted Monotonic Time Checkpoint Ledger
Every transaction and encrypted log commit writes a time checkpoint (`last_valid_utc_timestamp`) to the Room Database.
- Upon device boot or app startup, if `CurrentSystemTime < LastPersistedTimestamp`, the engine definitively detects **Backward Time Tampering** or an RTC battery failure.

### Layer 4: Time Confidence Transaction Gate (`TimeConfidenceGate`)
This gatekeeper controls whether payment transactions are allowed based on the aggregated time state.

- **SECURE_SYNCED:** Verified recently by GPS, NTP, or NITZ. Full card/QR transactions allowed.
- **MONOTONIC_VALIDATED:** Verified by the Monotonic Hardware offset since the last secure sync. Full transactions allowed.
- **TIME_UNTRUSTED:** Detected a backward jump, an unverified clock at boot, or a skew > 30 seconds.
  - **Action:** All payment transactions are **INSTANTLY SUSPENDED**.
  - **UI State:** Displays `UNTRUSTED_SYSTEM_TIME` with a warning sound and red LED.
  - **Auto-Recovery:** The system silently executes a root command (`su -c date -s ...`) to force update the Android system clock from GPS NMEA atomic time or NTP, restoring service automatically.
