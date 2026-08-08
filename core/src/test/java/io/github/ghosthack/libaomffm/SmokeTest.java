package io.github.ghosthack.libaomffm;

import io.github.ghosthack.libaomffm.aom.Aom;
import io.github.ghosthack.libaomffm.aom.aom_codec_enc_cfg;
import io.github.ghosthack.libaomffm.aom.aom_image;
import io.github.ghosthack.libaomffm.aom.aom_rational;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmokeTest {
    @Test
    void bundledNativeReportsExpectedVersionAndCapabilities() {
        assertEquals(3, Aom.aom_codec_version() >> 16);
        assertTrue(Libaom.string(Aom.aom_codec_version_str()).startsWith("3.14.1"));
        assertNotEquals(MemorySegment.NULL, Aom.aom_codec_av1_cx());
        assertNotEquals(MemorySegment.NULL, Aom.aom_codec_av1_dx());
        assertTrue((Libaom.codecCapabilities(Aom.aom_codec_av1_cx()) & Aom.AOM_CODEC_CAP_ENCODER()) != 0);
        assertTrue((Libaom.codecCapabilities(Aom.aom_codec_av1_dx()) & Aom.AOM_CODEC_CAP_DECODER()) != 0);
    }

    @Test
    void encodesAndDecodesOneAv1Frame() {
        byte[] compressed;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment encoder = Libaom.allocateCodecContext(arena);
            MemorySegment iface = Aom.aom_codec_av1_cx();
            MemorySegment config = aom_codec_enc_cfg.allocate(arena);
            check(Aom.aom_codec_enc_config_default(iface, config, Aom.AOM_USAGE_REALTIME()),
                    MemorySegment.NULL, "encoder config defaults");
            aom_codec_enc_cfg.g_w(config, 64);
            aom_codec_enc_cfg.g_h(config, 64);
            aom_codec_enc_cfg.g_threads(config, 1);
            aom_codec_enc_cfg.g_lag_in_frames(config, 0);
            aom_codec_enc_cfg.rc_target_bitrate(config, 100);
            MemorySegment timebase = aom_codec_enc_cfg.g_timebase(config);
            aom_rational.num(timebase, 1);
            aom_rational.den(timebase, 30);

            check(Libaom.encoderInit(encoder, iface, config, 0), encoder, "encoder init");
            try {
                try (Arena strings = Arena.ofConfined()) {
                    check(Aom.aom_codec_set_option(encoder, strings.allocateFrom("cpu-used"),
                            strings.allocateFrom("8")), encoder, "set cpu-used");
                }
                check(Libaom.codecControl(encoder, Aom.AOME_SET_CPUUSED(), 8),
                        encoder, "control cpu-used");
                MemorySegment imageAddress = Aom.aom_img_alloc(MemorySegment.NULL,
                        Aom.AOM_IMG_FMT_I420(), 64, 64, 1);
                assertNotEquals(MemorySegment.NULL, imageAddress);
                MemorySegment image = imageAddress.reinterpret(aom_image.sizeof());
                try {
                    fillPlane(image, Aom.AOM_PLANE_Y(), 96);
                    fillPlane(image, Aom.AOM_PLANE_U(), 110);
                    fillPlane(image, Aom.AOM_PLANE_V(), 140);
                    check(Libaom.encode(encoder, image, 0, 1, Aom.AOM_EFLAG_FORCE_KF()),
                            encoder, "encode");
                    compressed = collectFramePackets(encoder, arena);
                    assertTrue(compressed.length > 0, "encoder emitted an AV1 frame packet");
                } finally {
                    Aom.aom_img_free(imageAddress);
                }
            } finally {
                check(Aom.aom_codec_destroy(encoder), encoder, "encoder destroy");
            }
        }

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment decoder = Libaom.allocateCodecContext(arena);
            check(Libaom.decoderInit(decoder, Aom.aom_codec_av1_dx(), MemorySegment.NULL, 0),
                    decoder, "decoder init");
            try {
                MemorySegment encoded = arena.allocate(compressed.length);
                MemorySegment.copy(MemorySegment.ofArray(compressed), 0, encoded, 0, compressed.length);
                check(Aom.aom_codec_decode(decoder, encoded, compressed.length, MemorySegment.NULL),
                        decoder, "decode");
                MemorySegment iterator = arena.allocate(ValueLayout.ADDRESS);
                iterator.set(ValueLayout.ADDRESS, 0, MemorySegment.NULL);
                MemorySegment decodedAddress = Aom.aom_codec_get_frame(decoder, iterator);
                assertNotEquals(MemorySegment.NULL, decodedAddress, "decoder returned a frame");
                MemorySegment decoded = decodedAddress.reinterpret(aom_image.sizeof());
                assertEquals(64, aom_image.d_w(decoded));
                assertEquals(64, aom_image.d_h(decoded));
                MemorySegment frameFlags = arena.allocate(ValueLayout.JAVA_INT);
                check(Libaom.codecControl(decoder, Aom.AOMD_GET_FRAME_FLAGS(), frameFlags),
                        decoder, "get decoded frame flags");
                assertTrue((frameFlags.get(ValueLayout.JAVA_INT, 0) & Aom.AOM_FRAME_IS_KEY()) != 0);
                MemorySegment lumaAddress = aom_image.planes(decoded)
                        .getAtIndex(ValueLayout.ADDRESS, Aom.AOM_PLANE_Y());
                int stride = aom_image.stride(decoded)
                        .getAtIndex(ValueLayout.JAVA_INT, Aom.AOM_PLANE_Y());
                MemorySegment luma = lumaAddress.reinterpret((long) stride * 64);
                assertFalse(luma.equals(MemorySegment.NULL));
                assertTrue(Byte.toUnsignedInt(luma.get(ValueLayout.JAVA_BYTE, 0)) > 0);
            } finally {
                check(Aom.aom_codec_destroy(decoder), decoder, "decoder destroy");
            }
        }
    }

    private static void fillPlane(MemorySegment image, int plane, int value) {
        MemorySegment address = aom_image.planes(image).getAtIndex(ValueLayout.ADDRESS, plane);
        int stride = aom_image.stride(image).getAtIndex(ValueLayout.JAVA_INT, plane);
        int width = Aom.aom_img_plane_width(image, plane);
        int height = Aom.aom_img_plane_height(image, plane);
        MemorySegment data = address.reinterpret((long) stride * height);
        for (int y = 0; y < height; y++) {
            data.asSlice((long) y * stride, width).fill((byte) value);
        }
    }

    private static byte[] collectFramePackets(MemorySegment encoder, Arena arena) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        MemorySegment iterator = arena.allocate(ValueLayout.ADDRESS);
        iterator.set(ValueLayout.ADDRESS, 0, MemorySegment.NULL);
        MemorySegment packet;
        while (!(packet = Aom.aom_codec_get_cx_data(encoder, iterator)).equals(MemorySegment.NULL)) {
            if (AomCodecCxPacket.kind(packet) == Aom.AOM_CODEC_CX_FRAME_PKT()) {
                long size = AomCodecCxPacket.size(packet);
                assertEquals(0, AomCodecCxPacket.pts(packet));
                assertEquals(1, AomCodecCxPacket.duration(packet));
                assertTrue((AomCodecCxPacket.flags(packet) & Aom.AOM_FRAME_IS_KEY()) != 0);
                assertTrue(AomCodecCxPacket.visibleFrameSize(packet) > 0);
                assertTrue(AomCodecCxPacket.visibleFrameSize(packet) <= size);
                MemorySegment data = AomCodecCxPacket.buffer(packet).reinterpret(size);
                bytes.writeBytes(data.toArray(ValueLayout.JAVA_BYTE));
            }
        }
        return bytes.toByteArray();
    }

    private static void check(int result, MemorySegment context, String operation) {
        Libaom.check(result, context, operation);
    }
}
