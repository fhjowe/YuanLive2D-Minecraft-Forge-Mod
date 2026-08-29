package com.yuan.live2d.client.live2d;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class Live2DManifestReader {
    private Live2DManifestReader() {}

    public static Live2DModelManifest read(Path modelRoot, Path modelJson) throws IOException {
        Path root = modelRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(root) || Files.isSymbolicLink(root))
            throw new IllegalArgumentException("Invalid model root: " + modelRoot);
        Path realRoot = root.toRealPath();
        Path jsonPath = requireFileInside(root, realRoot, modelJson, modelJson.toString());
        JsonObject json = JsonParser.parseString(Files.readString(jsonPath)).getAsJsonObject();
        int version = json.get("Version").getAsInt();
        if (version != 3) throw new IllegalArgumentException("Unsupported Cubism model version: " + version);
        JsonObject references = json.getAsJsonObject("FileReferences");
        if (references == null) throw new IllegalArgumentException("Missing FileReferences");

        Path base = jsonPath.getParent();
        Path moc = resolveRequired(root, realRoot, base, references.get("Moc").getAsString());
        JsonArray textureJson = references.getAsJsonArray("Textures");
        if (textureJson == null || textureJson.isEmpty()) throw new IllegalArgumentException("Model has no textures");
        List<Path> textures = new ArrayList<>();
        textureJson.forEach(value -> textures.add(resolveRequired(root, realRoot, base, value.getAsString())));

        Path physics = null;
        if (references.has("Physics"))
            physics = resolveRequired(root, realRoot, base, references.get("Physics").getAsString());

        String filename = jsonPath.getFileName().toString();
        String displayName = filename.substring(0, filename.length() - ".model3.json".length());
        String id = slug(displayName);
        return new Live2DModelManifest(id, displayName, version, base, jsonPath, moc,
                List.copyOf(textures), physics, discover(base, ".exp3.json"), discover(base, ".motion3.json"));
    }

    private static Path resolveRequired(Path root, Path realRoot, Path base, String relative) {
        return requireFileInside(root, realRoot, base.resolve(relative), relative);
    }

    private static Path requireFileInside(Path root, Path realRoot, Path candidate, String label) {
        Path path = candidate.toAbsolutePath().normalize();
        if (!path.startsWith(root)) throw new IllegalArgumentException("Model path escapes root: " + candidate);
        for (Path current = root; current != null && current.startsWith(root); current = next(current, path)) {
            if (Files.isSymbolicLink(current)) throw new IllegalArgumentException("Symbolic links are not allowed: " + candidate);
            if (current.equals(path)) break;
        }
        if (!Files.isRegularFile(path) || !Files.isReadable(path))
            throw new IllegalArgumentException("Missing model resource: " + label);
        try {
            if (!path.toRealPath().startsWith(realRoot))
                throw new IllegalArgumentException("Model path escapes root: " + candidate);
        } catch (IOException error) {
            throw new IllegalArgumentException("Missing model resource: " + label, error);
        }
        return path;
    }

    private static Path next(Path current, Path target) {
        int index = current.getNameCount();
        return index < target.getNameCount() ? current.resolve(target.getName(index)) : null;
    }

    private static List<Path> discover(Path root, String suffix) throws IOException {
        try (var paths = Files.walk(root)) {
            return paths.filter(path -> {
                        if (Files.isSymbolicLink(path))
                            throw new IllegalArgumentException("Symbolic links are not allowed: " + path);
                        return Files.isRegularFile(path);
                    })
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(suffix))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
    }

    private static String slug(String value) {
        String slug = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "-").replaceAll("^-+|-+$", "");
        return slug.isEmpty() ? "model" : slug;
    }
}
