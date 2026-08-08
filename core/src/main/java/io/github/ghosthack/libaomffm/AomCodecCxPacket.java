package io.github.ghosthack.libaomffm;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/** Portable accessors for {@code aom_codec_cx_pkt_t}'s encoded-frame variant. */
public final class AomCodecCxPacket {
    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");
    private static final long DATA = 8;

    private AomCodecCxPacket() {}

    public static int kind(MemorySegment packet) {
        return view(packet).get(ValueLayout.JAVA_INT, 0);
    }

    public static MemorySegment buffer(MemorySegment packet) {
        return view(packet).get(ValueLayout.ADDRESS, DATA);
    }

    public static long size(MemorySegment packet) {
        return view(packet).get(ValueLayout.JAVA_LONG, DATA + 8);
    }

    public static long pts(MemorySegment packet) {
        return view(packet).get(ValueLayout.JAVA_LONG, DATA + 16);
    }

    public static long duration(MemorySegment packet) {
        return WINDOWS
                ? Integer.toUnsignedLong(view(packet).get(ValueLayout.JAVA_INT, DATA + 24))
                : view(packet).get(ValueLayout.JAVA_LONG, DATA + 24);
    }

    public static long flags(MemorySegment packet) {
        return Integer.toUnsignedLong(view(packet).get(ValueLayout.JAVA_INT,
                DATA + (WINDOWS ? 28 : 32)));
    }

    public static int partitionId(MemorySegment packet) {
        return view(packet).get(ValueLayout.JAVA_INT, DATA + (WINDOWS ? 32 : 36));
    }

    public static long visibleFrameSize(MemorySegment packet) {
        return view(packet).get(ValueLayout.JAVA_LONG, DATA + 40);
    }

    private static MemorySegment view(MemorySegment packet) {
        return packet.reinterpret(56);
    }
}
