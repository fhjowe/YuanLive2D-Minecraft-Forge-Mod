package com.yuan.live2d.client.live2d;

import com.yuan.live2d.YuanLive2D;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;

public final class Live2DResourceInstaller {
    private static final String RUNTIME_INDEX = "/runtime-index.txt";
    private static final String MODEL_INDEX = "/model-index.txt";

    private Live2DResourceInstaller() {}

    public static void install(Path root) throws IOException {
        Path installRoot = root.toAbsolutePath().normalize();
        Files.createDirectories(installRoot);
        // Runtime is mod-owned and must stay in sync with the jar's JNI surface; models/config below are user data.
        extractListed(RUNTIME_INDEX, "runtime/windows-x86_64/", installRoot.resolve("runtime/windows-x86_64"));
        if (!Files.isRegularFile(installRoot.resolve("models/Haru/Haru.model3.json"))) {
            extractListed(MODEL_INDEX, "models/", installRoot.resolve("models"));
        }
        migrateLegacy(installRoot);
    }

    static void extractListed(String indexResource, String stripPrefix, Path target) throws IOException {
        Path targetRoot = target.toAbsolutePath().normalize();
        try (InputStream indexIn = YuanLive2D.class.getResourceAsStream(indexResource)) {
            if (indexIn == null) throw new IOException("Missing bundled resource index: " + indexResource);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(indexIn, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("\uFEFF")) line = line.substring(1);
                    if (line.isBlank()) continue;
                    String relative = line.replace('\\', '/').trim();
                    if (relative.isBlank()) continue;
                    Path relativePath;
                    try {
                        relativePath = Path.of(relative);
                    } catch (InvalidPathException invalid) {
                        throw new IOException("Invalid path in bundled resource index: " + indexResource, invalid);
                    }
                    Path output = targetRoot.resolve(relativePath).normalize();
                    if (relativePath.isAbsolute() || !output.startsWith(targetRoot)) {
                        throw new IOException("Resource escapes target: " + stripPrefix + relative);
                    }
                    String resource = stripPrefix + relative;
                    Path parent = output.getParent();
                    if (parent != null) Files.createDirectories(parent);
                    try (InputStream resourceIn = YuanLive2D.class.getResourceAsStream("/" + resource)) {
                        if (resourceIn == null) throw new IOException("Missing bundled resource: " + resource);
                        Files.copy(resourceIn, output, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
    }

    private static void migrateLegacy(Path root) throws IOException {
        Path legacy = root.getParent().resolve("yuan/live2d");
        Path legacyConfig = legacy.resolve("config.json");
        if (!Files.isRegularFile(legacyConfig, LinkOption.NOFOLLOW_LINKS)) return;
        Files.createDirectories(root);
        backupLegacy(legacyConfig, root);
        if (!Files.exists(root.resolve("config.json"), LinkOption.NOFOLLOW_LINKS)) {
            Files.copy(legacyConfig, root.resolve("config.json"));
        }
        migrateLegacyModels(legacy.resolve("models"), root.resolve("models"));
    }

    private static Path backupLegacy(Path legacyConfig, Path root) throws IOException {
        Path backup = root.resolve("config.legacy-" + System.currentTimeMillis() + ".json");
        int attempt = 0;
        while (Files.exists(backup, LinkOption.NOFOLLOW_LINKS)) {
            attempt++;
            backup = root.resolve("config.legacy-" + System.currentTimeMillis() + "-" + attempt + ".json");
        }
        Files.copy(legacyConfig, backup);
        return backup;
    }

    private static void migrateLegacyModels(Path legacyModels, Path modelsRoot) throws IOException {
        if (!Files.isDirectory(legacyModels, LinkOption.NOFOLLOW_LINKS)) return;
        Files.createDirectories(modelsRoot);
        List<Path> sources;
        try (Stream<Path> paths = Files.walk(legacyModels)) {
            sources = paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)).toList();
        }
        Path normalizedModels = modelsRoot.toAbsolutePath().normalize();
        for (Path source : sources) {
            Path relative = legacyModels.relativize(source);
            Path destination = normalizedModels.resolve(relative).normalize();
            if (!destination.startsWith(normalizedModels)) {
                throw new IOException("Legacy model path escapes target: " + relative);
            }
            Files.createDirectories(destination.getParent());
            if (!Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
                Files.copy(source, destination);
            }
        }
    }
}
