# libaom-ffm

Panama FFM (jextract) bindings for libaom with bundled, from-source natives.
One Maven coordinate, no JNI and no user-installed codec library.

| Artifact | Contents | License |
|---|---|---|
| `io.github.ghosthack:libaom-ffm` | generated FFM stubs, portable wrappers, and runtime loader (JDK 22+) | MIT |
| `io.github.ghosthack:libaom-ffm-natives` with a platform classifier | libaom 3.14.1 shared library | BSD-2-Clause plus the AOM patent license |

The curated surface covers AV1 encoding, decoding, image allocation and
metadata, stream inspection, external decoder frame buffers, encoder packets,
named codec options, and typed integer/pointer codec controls. Supported native
classifiers are `macos-arm64`, `windows-x64`, and `linux-x64`.

## Use

```xml
<dependency>
  <groupId>io.github.ghosthack</groupId>
  <artifactId>libaom-ffm</artifactId>
  <version>3.14.1-0.1.0</version>
</dependency>
<dependency>
  <groupId>io.github.ghosthack</groupId>
  <artifactId>libaom-ffm-natives</artifactId>
  <version>3.14.1-0.1.0</version>
  <classifier>macos-arm64</classifier> <!-- or windows-x64 / linux-x64 -->
  <scope>runtime</scope>
</dependency>
```

On the module path, declare `requires libaom.ffm;` and run with
`--enable-native-access=libaom.ffm`. On the class path, use
`--enable-native-access=ALL-UNNAMED`.

```java
import io.github.ghosthack.libaomffm.Libaom;
import io.github.ghosthack.libaomffm.aom.Aom;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

System.out.println(Libaom.string(Aom.aom_codec_version_str()));

try (Arena arena = Arena.ofConfined()) {
    MemorySegment decoder = Libaom.allocateCodecContext(arena);
    Libaom.check(Libaom.decoderInit(decoder, Aom.aom_codec_av1_dx(),
            MemorySegment.NULL, 0), decoder, "decoder init");
    try {
        // Aom.aom_codec_decode(...), then Aom.aom_codec_get_frame(...)
    } finally {
        Aom.aom_codec_destroy(decoder);
    }
}
```

The complete encode→packet→decode example is in
`core/src/test/java/io/github/ghosthack/libaomffm/SmokeTest.java`.

### Why some calls live in `Libaom`

libaom exposes C `long` in a few signatures and packet fields. C `long` is
64-bit on macOS/Linux but 32-bit on Windows. `Libaom` and
`AomCodecCxPacket` select the correct native layouts at runtime so the same
Java artifact is ABI-safe on all three platforms. Use these wrappers for
context allocation, encoder/decoder initialization, encoding, capabilities,
codec controls, and encoded packet fields. All other calls remain direct in
the generated `Aom` class.

### Generated array accessors

jextract 22's indexed array-field accessors read relative to the struct start
for nonzero-offset fields. Use the field-slice form:

```java
MemorySegment y = aom_image.planes(image)
        .getAtIndex(ValueLayout.ADDRESS, Aom.AOM_PLANE_Y());
int stride = aom_image.stride(image)
        .getAtIndex(ValueLayout.JAVA_INT, Aom.AOM_PLANE_Y());
```

### Library resolution

1. `-Dlibaomffm.libdir=<dir>` or `LIBAOM_FFM_LIBDIR` uses an existing shared
   libaom from that directory.
2. The matching classifier jar is extracted once to
   `~/.cache/libaom-ffm/<version>-<platform>/`.
3. Libraries already loaded by the host and the platform default lookup are
   searched last.

## Build

Build each native classifier on its matching platform:

```sh
mkdir -p build
git clone --depth 1 --branch v3.14.1 \
  https://aomedia.googlesource.com/aom build/aom-3.14.1
build-natives/macos-arm64.sh

# Optional regeneration; set JEXTRACT or install tools/jextract-22.
jextract/gen-bindings.sh

mvn clean install
```

The Linux and Windows scripts follow the same flow. Staged native resources are
build outputs and are not committed. CI builds libaom from the official tag,
checks relocatability, and runs the encode/decode tests on every platform.

