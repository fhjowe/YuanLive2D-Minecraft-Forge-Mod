package com.yuan.live2d.client.live2d;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongSupplier;

public final class Live2DModelRepository {
    private final Path root;
    private final Map<String, Live2DModelManifest> models = new LinkedHashMap<>();
    private final Map<Path, String> errors = new LinkedHashMap<>();
    private final LongSupplier nanoTime;
    private final Runnable scanObserver;
    private long lastScanNanos;
    private boolean scanned;

    public Live2DModelRepository(Path modelsRoot) {
        this(modelsRoot, System::nanoTime, () -> {});
    }

    public Live2DModelRepository(Path modelsRoot, LongSupplier nanoTime, Runnable scanObserver) {
        root = modelsRoot.toAbsolutePath().normalize();
        this.nanoTime = nanoTime;
        this.scanObserver = scanObserver;
    }

    public List<Live2DModelManifest> scan() throws IOException {
        synchronized (Live2DModelManager.FILESYSTEM_LOCK) {
        long now = nanoTime.getAsLong();
        long elapsed = now - lastScanNanos;
        if (scanned && elapsed >= 0 && elapsed < 1_000_000_000L) return List.copyOf(models.values());
        scanned = true;
        lastScanNanos = now;
        scanObserver.run();
        models.clear();
        errors.clear();
        rejectSymbolicLinks(root);
        Files.createDirectories(root);
        rejectSymbolicLinks(root);
        Path realRoot = root.toRealPath();
        List<Path> imports;
        try (var paths = Files.list(realRoot)) {
            imports = paths.filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> !path.getFileName().toString().startsWith(".import-"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
        for (Path imported : imports) scanImport(imported);
        return List.copyOf(models.values());
        }
    }

    public void invalidate() {
        synchronized (Live2DModelManager.FILESYSTEM_LOCK) { scanned = false; }
    }

    public Optional<Live2DModelManifest> find(String id) {
        synchronized (Live2DModelManager.FILESYSTEM_LOCK) { return Optional.ofNullable(models.get(id)); }
    }

    public Map<Path, String> errors() {
        synchronized (Live2DModelManager.FILESYSTEM_LOCK) { return Map.copyOf(errors); }
    }

    private void scanImport(Path imported) {
        try {
            List<Path> manifests;
            try (var paths = Files.walk(imported)) {
                manifests = paths.filter(Files::isRegularFile)
                        .filter(Live2DModelRepository::isManifest)
                        .sorted(Comparator.comparing(Path::toString))
                        .toList();
            }
            for (Path modelJson : manifests) read(imported, modelJson);
        } catch (Exception error) {
            errors.put(imported, message(error));
        }
    }

    private void read(Path imported, Path modelJson) {
        try {
            Live2DModelManifest manifest = Live2DManifestReader.read(imported, modelJson);
            String id = Live2DModelIds.id(imported, modelJson);
            models.put(id, new Live2DModelManifest(id,
                    manifest.displayName(), manifest.version(), manifest.root(), manifest.modelJson(), manifest.moc(),
                    manifest.textures(), manifest.physics(), manifest.expressions(), manifest.motions()));
        } catch (Exception error) {
            errors.put(modelJson, message(error));
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

    private static boolean isManifest(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".model3.json");
    }

    private static String message(Exception error) {
        return error.getMessage() == null ? error.toString() : error.getMessage();
    }
}
