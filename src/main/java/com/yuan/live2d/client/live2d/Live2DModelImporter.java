package com.yuan.live2d.client.live2d;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.charset.Charset;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class Live2DModelImporter {
    private static final Limits DEFAULT_LIMITS = new Limits(4096, 512L << 20, 2L << 30);
    public record Limits(int entries, long entryBytes, long totalBytes) {
        public Limits {
            if (entries <= 0 || entryBytes <= 0 || totalBytes <= 0) throw new IllegalArgumentException("ZIP limits must be positive");
        }
    }
    private Live2DModelImporter() {}

    public static List<Live2DModelManifest> importSource(Path source, Path modelsRoot) throws IOException {
        return importSource(source, modelsRoot, DEFAULT_LIMITS);
    }

    public static List<Live2DModelManifest> importSource(Path source, Path modelsRoot, Limits limits) throws IOException {
        Path input = source.toAbsolutePath().normalize();
        Path root = modelsRoot.toAbsolutePath().normalize();
        rejectOverlap(input, root);
        rejectSymbolicLinks(root);
        Files.createDirectories(root);
        rejectSymbolicLinks(root);
        root = root.toRealPath();
        rejectOverlap(input, root);
        Path destination = null;
        boolean published = false;
        Path staging = null;
        try {
            if (Files.isSymbolicLink(input)) throw new IllegalArgumentException("Symbolic links are not allowed: " + source);
            if (Files.isDirectory(input, LinkOption.NOFOLLOW_LINKS)) {
                staging = createStaging(root);
                copyDirectory(input, staging);
            } else if (Files.isRegularFile(input, LinkOption.NOFOLLOW_LINKS)
                    && input.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip")) {
                try {
                    staging = createStaging(root);
                    unzip(input, staging, StandardCharsets.UTF_8, limits);
                } catch (IllegalArgumentException error) {
                    if (!isZipNameEncodingFailure(error)) throw error;
                    deleteTree(staging);
                    staging = createStaging(root);
                    unzip(input, staging, Charset.forName("GBK"), limits);
                }
            } else throw new IllegalArgumentException("Live2D import source must be a ZIP or directory");

            List<Path> modelFiles = findModels(staging);
            if (modelFiles.isEmpty()) throw new IllegalArgumentException("No .model3.json found");
            for (Path model : modelFiles) Live2DManifestReader.read(staging, model);

            destination = uniqueDestination(root, baseName(input));
            Files.move(staging, destination);
            published = true;
            List<Live2DModelManifest> imported = new ArrayList<>();
            for (Path model : findModels(destination))
                imported.add(withId(Live2DManifestReader.read(destination, model), Live2DModelIds.id(destination, model)));
            return List.copyOf(imported);
        } catch (IOException | RuntimeException error) {
            if (published) deleteTree(destination);
            throw error;
        } finally {
            if (staging != null) deleteTree(staging);
        }
    }

    private static void rejectSymbolicLinks(Path path) {
        Path current = path.getRoot();
        for (Path part : path) {
            current = current == null ? part : current.resolve(part);
            if (Files.isSymbolicLink(current))
                throw new IllegalArgumentException("Symbolic links are not allowed: " + path);
        }
    }

    private static void rejectOverlap(Path input, Path root) throws IOException {
        if (input.startsWith(root) || root.startsWith(input))
            throw new IllegalArgumentException("Import source and models root overlap");
        if (Files.exists(input, LinkOption.NOFOLLOW_LINKS)) {
            Path realInput = input.toRealPath();
            if (realInput.startsWith(root) || root.startsWith(realInput))
                throw new IllegalArgumentException("Import source and models root overlap");
        }
    }

    private static void copyDirectory(Path source, Path target) throws IOException {
        try (var paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                if (Files.isSymbolicLink(path)) throw new IllegalArgumentException("Symbolic links are not allowed: " + path);
                Path destination = target.resolve(source.relativize(path)).normalize();
                if (!destination.startsWith(target)) throw new IllegalArgumentException("Import path escapes staging directory");
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) Files.createDirectories(destination);
                else Files.copy(path, destination, StandardCopyOption.COPY_ATTRIBUTES);
            }
        }
    }

    private static Path createStaging(Path root) throws IOException {
        Path staging = root.resolve(".import-" + UUID.randomUUID());
        Files.createDirectory(staging);
        return staging;
    }

    private static boolean isZipNameEncodingFailure(IllegalArgumentException error) {
        return error.getCause() instanceof CharacterCodingException;
    }

    private static void unzip(Path archive, Path target, Charset charset, Limits limits) throws IOException {
        Set<String> outputs = new HashSet<>();
        long total = 0;
        int entries = 0;
        try (InputStream raw = Files.newInputStream(archive);
             ZipInputStream zip = new ZipInputStream(raw, charset)) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null; zip.closeEntry()) {
                if (++entries > limits.entries()) throw new IllegalArgumentException("ZIP entry count exceeds limit");
                Path entryPath = Path.of(entry.getName());
                if (entryPath.isAbsolute())
                    throw new IllegalArgumentException("ZIP entry uses an absolute path: " + entry.getName());
                Path destination = target.resolve(entryPath).normalize();
                if (!destination.startsWith(target))
                    throw new IllegalArgumentException("ZIP entry escapes import directory: " + entry.getName());
                String output = target.relativize(destination).toString().replace('\\', '/').toLowerCase(Locale.ROOT);
                if (!outputs.add(output)) throw new IllegalArgumentException("Duplicate ZIP output path: " + entry.getName());
                if (entry.isDirectory()) Files.createDirectories(destination);
                else {
                    Files.createDirectories(destination.getParent());
                    try (var outputStream = Files.newOutputStream(destination)) {
                        byte[] buffer = new byte[8192];
                        long entryBytes = 0;
                        for (int read; (read = zip.read(buffer)) != -1;) {
                            if (entryBytes > limits.entryBytes() - read)
                                throw new IllegalArgumentException("ZIP entry size exceeds limit: " + entry.getName());
                            if (total > limits.totalBytes() - read)
                                throw new IllegalArgumentException("ZIP expanded size exceeds limit");
                            entryBytes += read;
                            total += read;
                            outputStream.write(buffer, 0, read);
                        }
                    }
                }
            }
        }
    }

    private static List<Path> findModels(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".model3.json"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
    }

    private static Path uniqueDestination(Path root, String base) {
        Path destination = root.resolve(base);
        for (int suffix = 2; Files.exists(destination); suffix++) destination = root.resolve(base + "-" + suffix);
        return destination;
    }

    private static String baseName(Path source) {
        String name = source.getFileName().toString().replaceFirst("(?i)\\.zip$", "");
        String clean = name.replaceAll("[<>:\"/\\\\|?*\\p{Cntrl}]", "-").replaceAll("[. ]+$", "").trim();
        return clean.isEmpty() ? "model" : clean;
    }

    private static Live2DModelManifest withId(Live2DModelManifest manifest, String id) {
        return new Live2DModelManifest(id, manifest.displayName(), manifest.version(), manifest.root(),
                manifest.modelJson(), manifest.moc(), manifest.textures(), manifest.physics(),
                manifest.expressions(), manifest.motions());
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }
}
