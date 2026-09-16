from pathlib import Path

root = Path(__file__).resolve().parent
manager = root / "app/src/main/java/com/threerdi/dfamstyle/BleMidiSyncManager.java"
s = manager.read_text()

old_fields = '''    private volatile double avgClockIntervalMs = 0.0;
    private volatile int lastRemoteTimestamp13 = -1;
    private volatile long incomingClockCount = 0L;
    private volatile long syncRevision = 0L;'''
new_fields = '''    private static final int PLL_WINDOW_PULSES = 48;
    private volatile double avgClockIntervalMs = 0.0;
    private volatile int lastRemoteTimestamp13 = -1;
    private volatile long remoteUnwrappedMs = Long.MIN_VALUE;
    private volatile long pllWindowStartMs = Long.MIN_VALUE;
    private volatile long pllWindowStartClock = 0L;
    private volatile float lockedBpm = 0f;
    private volatile long incomingClockCount = 0L;
    private volatile long syncRevision = 0L;
    private volatile int lastPacketHash = 0;
    private volatile long lastPacketArrivalNs = 0L;'''
if old_fields not in s:
    raise RuntimeError("Could not find v2.6 BLE clock fields")
s = s.replace(old_fields, new_fields, 1)

old_reset = '''        avgClockIntervalMs = 0.0;
        lastRemoteTimestamp13 = -1;
        incomingClockCount = 0L;'''
new_reset = '''        avgClockIntervalMs = 0.0;
        lastRemoteTimestamp13 = -1;
        remoteUnwrappedMs = Long.MIN_VALUE;
        pllWindowStartMs = Long.MIN_VALUE;
        pllWindowStartClock = 0L;
        lockedBpm = 0f;
        incomingClockCount = 0L;
        lastPacketHash = 0;
        lastPacketArrivalNs = 0L;'''
if old_reset not in s:
    raise RuntimeError("Could not find v2.6 clock reset")
s = s.replace(old_reset, new_reset, 1)

old_start_reset = '''            incomingClockCount = 0L;
            lastRemoteTimestamp13 = -1;
            avgClockIntervalMs = 0.0;
            syncRevision++;'''
new_start_reset = '''            incomingClockCount = 0L;
            lastRemoteTimestamp13 = -1;
            remoteUnwrappedMs = Long.MIN_VALUE;
            pllWindowStartMs = Long.MIN_VALUE;
            pllWindowStartClock = 0L;
            lockedBpm = 0f;
            avgClockIntervalMs = 0.0;
            syncRevision++;'''
if old_start_reset not in s:
    raise RuntimeError("Could not find MIDI Start reset")
s = s.replace(old_start_reset, new_start_reset, 1)

old_parser_head = '''    private void parseBleMidi(byte[] value) {
        if (value == null || value.length == 0) return;

        int index = 0;'''
new_parser_head = '''    private synchronized void parseBleMidi(byte[] value) {
        if (value == null || value.length == 0) return;

        // Some Android BLE stacks can deliver the same notification through
        // both characteristic-changed callback overloads. Drop only identical
        // packets that arrive within 3 ms so duplicate callbacks cannot double
        // the logical MIDI clock count.
        long packetNowNs = System.nanoTime();
        int packetHash = Arrays.hashCode(value);
        if (packetHash == lastPacketHash
                && lastPacketArrivalNs != 0L
                && packetNowNs - lastPacketArrivalNs < 3_000_000L) {
            return;
        }
        lastPacketHash = packetHash;
        lastPacketArrivalNs = packetNowNs;

        int index = 0;'''
if old_parser_head not in s:
    raise RuntimeError("Could not find v2.6 BLE parser head")
s = s.replace(old_parser_head, new_parser_head, 1)

start = s.index('    private synchronized void handleClock(int timestamp13) {')
end = s.index('    private static float clampBpm(float bpm) {', start)
new_handle = '''    private synchronized void handleClock(int timestamp13) {
        lastIncomingClockNs = System.nanoTime();
        if (timestamp13 < 0) {
            // Standard timestamped BLE-MIDI is required for a stable PLL.
            // Keep the connection alive but do not derive tempo from callback
            // arrival jitter.
            incomingClockCount++;
            syncRevision++;
            return;
        }

        int pulseSpan = 1;
        int lastTs = lastRemoteTimestamp13;
        if (lastTs < 0) {
            lastRemoteTimestamp13 = timestamp13;
            remoteUnwrappedMs = timestamp13;
            incomingClockCount++;
            pllWindowStartMs = remoteUnwrappedMs;
            pllWindowStartClock = incomingClockCount;
            syncRevision++;
            return;
        }

        int deltaMs = timestamp13 - lastTs;
        if (deltaMs < 0) deltaMs += TIMESTAMP_MODULO;
        if (deltaMs == 0) {
            // Same remote timestamp means duplicate clock data. At supported
            // tempos two legitimate clocks can never share a 1 ms timestamp.
            return;
        }
        if (deltaMs >= 1000) {
            // Discontinuity/reconnect: re-acquire instead of contaminating PLL.
            lastRemoteTimestamp13 = timestamp13;
            remoteUnwrappedMs = timestamp13;
            avgClockIntervalMs = 0.0;
            pllWindowStartMs = remoteUnwrappedMs;
            pllWindowStartClock = incomingClockCount;
            lockedBpm = 0f;
            incomingBpm = 0f;
            syncRevision++;
            return;
        }

        if (avgClockIntervalMs > 0.0) {
            pulseSpan = (int) Math.round(deltaMs / avgClockIntervalMs);
            pulseSpan = Math.max(1, Math.min(24, pulseSpan));
            double perPulseMs = deltaMs / (double) pulseSpan;
            double ratio = perPulseMs / avgClockIntervalMs;
            if (ratio >= 0.72 && ratio <= 1.38) {
                avgClockIntervalMs = avgClockIntervalMs * 0.94 + perPulseMs * 0.06;
            }
        } else if (deltaMs >= 5 && deltaMs <= 125) {
            avgClockIntervalMs = deltaMs;
        }

        lastRemoteTimestamp13 = timestamp13;
        if (remoteUnwrappedMs == Long.MIN_VALUE) remoteUnwrappedMs = timestamp13;
        else remoteUnwrappedMs += deltaMs;
        incomingClockCount += pulseSpan;

        if (pllWindowStartMs == Long.MIN_VALUE) {
            pllWindowStartMs = remoteUnwrappedMs;
            pllWindowStartClock = incomingClockCount;
        } else {
            long logicalPulses = incomingClockCount - pllWindowStartClock;
            long elapsedMs = remoteUnwrappedMs - pllWindowStartMs;
            if (logicalPulses >= PLL_WINDOW_PULSES && elapsedMs > 0L) {
                float candidate = (float) (60_000.0 * logicalPulses
                        / (elapsedMs * 24.0));
                if (candidate >= 20f && candidate <= 300f) {
                    // Long-window PLL removes 1 ms BLE timestamp quantisation.
                    // Static tempos remain visually locked instead of following
                    // individual 18/19/20 ms timestamp steps.
                    if (lockedBpm <= 0f) {
                        lockedBpm = candidate;
                    } else {
                        float error = candidate - lockedBpm;
                        float gain = Math.abs(error) > 2.0f ? 0.45f : 0.12f;
                        lockedBpm += error * gain;
                    }

                    float quantized = Math.round(lockedBpm * 10f) / 10f;
                    if (incomingBpm <= 0f
                            || Math.abs(quantized - incomingBpm) >= 0.2f) {
                        incomingBpm = quantized;
                    }
                }

                // Non-overlapping 48-clock windows: ~0.9 s at 133 BPM. This
                // trades a small amount of lock time for much better stability.
                pllWindowStartMs = remoteUnwrappedMs;
                pllWindowStartClock = incomingClockCount;
                notifySync();
            }
        }

        syncRevision++;
    }

'''
s = s[:start] + new_handle + s[end:]

manager.write_text(s)
print("Applied v2.7 long-window BLE MIDI PLL stability fix")
