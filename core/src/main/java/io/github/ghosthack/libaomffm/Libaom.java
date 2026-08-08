package io.github.ghosthack.libaomffm;

import io.github.ghosthack.libaomffm.aom.Aom;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * Portable wrappers for the handful of libaom calls whose ABI contains C
 * {@code long}. C long is 64-bit on macOS/Linux and 32-bit on Windows, so
 * jextract output generated on one platform cannot safely model these calls on
 * every supported platform. The rest of the API is available directly in
 * {@link Aom}.
 */
public final class Libaom {
    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");
    private static final ValueLayout C_LONG = WINDOWS ? JAVA_INT : JAVA_LONG;
    private static final Linker LINKER = Linker.nativeLinker();

    private static final MethodHandle DECODER_INIT = downcall("aom_codec_dec_init_ver",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, C_LONG, JAVA_INT));
    private static final MethodHandle ENCODER_INIT = downcall("aom_codec_enc_init_ver",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, C_LONG, JAVA_INT));
    private static final MethodHandle ENCODE = downcall("aom_codec_encode",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_LONG, C_LONG, C_LONG));
    private static final MethodHandle GET_CAPS = downcall("aom_codec_get_caps",
            FunctionDescriptor.of(C_LONG, ADDRESS));
    private static final MethodHandle CONTROL_INT = downcallVariadic("aom_codec_control",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT));
    private static final MethodHandle CONTROL_ADDRESS = downcallVariadic("aom_codec_control",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS));

    private Libaom() {}

    /** Allocate a zero-filled, pointer-aligned {@code aom_codec_ctx_t}. */
    public static MemorySegment allocateCodecContext(Arena arena) {
        // The public context is 56 bytes on all supported 64-bit ABIs. Its
        // init_flags member differs in width, but padding keeps later pointers
        // at the same offsets; callers should treat the context as opaque.
        return arena.allocate(56, 8).fill((byte) 0);
    }

    public static int decoderInit(MemorySegment context, MemorySegment iface,
                                  MemorySegment config, long flags) {
        try {
            return WINDOWS
                    ? (int) DECODER_INIT.invokeExact(context, iface, config, (int) flags,
                            Aom.AOM_DECODER_ABI_VERSION())
                    : (int) DECODER_INIT.invokeExact(context, iface, config, flags,
                            Aom.AOM_DECODER_ABI_VERSION());
        } catch (Throwable throwable) {
            throw rethrow(throwable);
        }
    }

    public static int encoderInit(MemorySegment context, MemorySegment iface,
                                  MemorySegment config, long flags) {
        try {
            return WINDOWS
                    ? (int) ENCODER_INIT.invokeExact(context, iface, config, (int) flags,
                            Aom.AOM_ENCODER_ABI_VERSION())
                    : (int) ENCODER_INIT.invokeExact(context, iface, config, flags,
                            Aom.AOM_ENCODER_ABI_VERSION());
        } catch (Throwable throwable) {
            throw rethrow(throwable);
        }
    }

    public static int encode(MemorySegment context, MemorySegment image, long pts,
                             long duration, long flags) {
        try {
            return WINDOWS
                    ? (int) ENCODE.invokeExact(context, image, pts, (int) duration, (int) flags)
                    : (int) ENCODE.invokeExact(context, image, pts, duration, flags);
        } catch (Throwable throwable) {
            throw rethrow(throwable);
        }
    }

    public static long codecCapabilities(MemorySegment iface) {
        try {
            return WINDOWS ? Integer.toUnsignedLong((int) GET_CAPS.invokeExact(iface))
                    : (long) GET_CAPS.invokeExact(iface);
        } catch (Throwable throwable) {
            throw rethrow(throwable);
        }
    }

    /** Invoke an integer-valued codec control. */
    public static int codecControl(MemorySegment context, int controlId, int value) {
        try {
            return (int) CONTROL_INT.invokeExact(context, controlId, value);
        } catch (Throwable throwable) {
            throw rethrow(throwable);
        }
    }

    /** Invoke a pointer-valued codec control. */
    public static int codecControl(MemorySegment context, int controlId, MemorySegment value) {
        try {
            return (int) CONTROL_ADDRESS.invokeExact(context, controlId, value);
        } catch (Throwable throwable) {
            throw rethrow(throwable);
        }
    }

    /** Decode a zero-terminated native UTF-8 string returned by libaom. */
    public static String string(MemorySegment address) {
        return address.equals(MemorySegment.NULL) ? null : address.reinterpret(4096).getString(0);
    }

    /** Throw an exception carrying libaom's error text for a nonzero result. */
    public static void check(int result, MemorySegment context, String operation) {
        if (result == Aom.AOM_CODEC_OK()) return;
        String message = context.equals(MemorySegment.NULL)
                ? string(Aom.aom_codec_err_to_string(result))
                : string(Aom.aom_codec_error(context));
        String detail = context.equals(MemorySegment.NULL) ? null : string(Aom.aom_codec_error_detail(context));
        throw new IllegalStateException(operation + " failed (" + result + "): " + message
                + (detail == null || detail.isBlank() ? "" : " — " + detail));
    }

    private static MethodHandle downcall(String symbol, FunctionDescriptor descriptor) {
        MemorySegment address = LibaomLibs.lookup().find(symbol)
                .orElseThrow(() -> new UnsatisfiedLinkError("unresolved libaom symbol: " + symbol));
        return LINKER.downcallHandle(address, descriptor);
    }

    private static MethodHandle downcallVariadic(String symbol, FunctionDescriptor descriptor) {
        MemorySegment address = LibaomLibs.lookup().find(symbol)
                .orElseThrow(() -> new UnsatisfiedLinkError("unresolved libaom symbol: " + symbol));
        return LINKER.downcallHandle(address, descriptor, Linker.Option.firstVariadicArg(2));
    }

    private static RuntimeException rethrow(Throwable throwable) {
        if (throwable instanceof RuntimeException runtime) return runtime;
        if (throwable instanceof Error error) throw error;
        return new IllegalStateException("libaom downcall failed", throwable);
    }
}

