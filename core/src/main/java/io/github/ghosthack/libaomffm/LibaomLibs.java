package io.github.ghosthack.libaomffm;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.foreign.Arena;
import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/** Resolves the libaom shared library used by the generated bindings. */
public final class LibaomLibs {
    private static final Arena LIBRARY_ARENA = Arena.ofAuto();
    private static volatile SymbolLookup lookup;

    private LibaomLibs() {}

    public static SymbolLookup lookup() {
        SymbolLookup result = lookup;
        if (result == null) {
            synchronized (LibaomLibs.class) {
                result = lookup;
                if (result == null) lookup = result = resolve();
            }
        }
        return result;
    }

    private static SymbolLookup resolve() {
        String override = System.getProperty("libaomffm.libdir", System.getenv("LIBAOM_FFM_LIBDIR"));
        SymbolLookup resolved = override != null ? fromDirectory(Path.of(override)) : fromBundle();
        SymbolLookup fallback = SymbolLookup.loaderLookup().or(Linker.nativeLinker().defaultLookup());
        return resolved != null ? resolved.or(fallback) : fallback;
    }

    private static SymbolLookup fromDirectory(Path directory) {
        if (!Files.isDirectory(directory)) {
            throw new IllegalStateException("libaomffm.libdir does not exist: " + directory);
        }
        List<Path> libraries = new ArrayList<>();
        try (var paths = Files.list(directory)) {
            paths.filter(LibaomLibs::isLibaomLibrary).sorted().forEach(libraries::add);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (libraries.isEmpty()) {
            throw new IllegalStateException("no libaom shared library found in " + directory);
        }
        return chain(libraries);
    }

    private static boolean isLibaomLibrary(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        boolean extension = name.endsWith(".dylib") || name.endsWith(".dll") || name.contains(".so");
        return extension && (name.startsWith("libaom") || name.startsWith("aom"));
    }

    private static SymbolLookup fromBundle() {
        String classifier = classifier();
        String base = "natives/" + classifier + "/";
        List<String> manifest = readManifest(base);
        if (manifest == null) return null;
        Path cache = Path.of(System.getProperty("user.home"), ".cache", "libaom-ffm",
                Version.VERSION + "-" + classifier);
        try {
            Files.createDirectories(cache);
            List<Path> libraries = new ArrayList<>();
            for (String name : manifest) libraries.add(extract(base + name, cache.resolve(name)));
            return chain(libraries);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to extract bundled libaom natives to " + cache, e);
        }
    }

    private static List<String> readManifest(String base) {
        try (InputStream in = LibaomLibs.class.getClassLoader().getResourceAsStream(base + "manifest.txt")) {
            if (in == null) return null;
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .map(String::trim).filter(line -> !line.isEmpty()).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path extract(String resource, Path target) throws IOException {
        try (InputStream in = LibaomLibs.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) throw new IOException("missing classpath resource " + resource);
            byte[] bytes = in.readAllBytes();
            if (Files.exists(target) && Files.size(target) == bytes.length) return target;
            Path temporary = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
            Files.write(temporary, bytes);
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return target;
        }
    }

    private static SymbolLookup chain(List<Path> libraries) {
        SymbolLookup result = null;
        for (Path library : libraries) {
            SymbolLookup next = SymbolLookup.libraryLookup(library.toAbsolutePath().toString(), LIBRARY_ARENA);
            result = result == null ? next : result.or(next);
        }
        return result;
    }

    public static String classifier() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();
        String osPart = os.contains("mac") ? "macos" : os.contains("win") ? "windows" : "linux";
        String archPart = arch.equals("aarch64") || arch.equals("arm64") ? "arm64" : "x64";
        return osPart + "-" + archPart;
    }
}

