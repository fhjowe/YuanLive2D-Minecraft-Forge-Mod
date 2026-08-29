package com.yuan.live2d.client.live2d;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

public final class Live2DInstallCheck {
    public static void main(String[] args) throws Exception {
        Path fresh = Files.createTempDirectory("yuan-live2d-install-");
        try {
            Live2DResourceInstaller.install(fresh);
            assertBundled(fresh);
            assertHaruScans(fresh);
            Live2DResourceInstaller.install(fresh);
            assertBundled(fresh);
            Files.delete(fresh.resolve("runtime/windows-x86_64/yuan_live2d.dll"));
            Live2DResourceInstaller.install(fresh);
            assertBundled(fresh);
            byte[] staleMarker = "stale".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            Files.write(fresh.resolve("runtime/windows-x86_64/yuan_live2d.dll"), staleMarker);
            Live2DResourceInstaller.install(fresh);
            assert !java.util.Arrays.equals(
                    Files.readAllBytes(fresh.resolve("runtime/windows-x86_64/yuan_live2d.dll")), staleMarker)
                    : "stale runtime must be refreshed from the jar";
            assertBundled(fresh);
            traversalCheck();
            legacyCopiesWhenMissing();
            legacyNeverOverwrites();
            System.out.println("LIVE2D_INSTALL_CHECK pass=true runtime=true model=Haru legacy=true traversalRejected=true");
        } finally {
            deleteTree(fresh);
        }
    }

    private static void assertBundled(Path root) throws IOException {
        assert Files.isRegularFile(root.resolve("runtime/windows-x86_64/yuan_live2d.dll"))
                : "Missing bundled yuan_live2d.dll";
        assert Files.isRegularFile(root.resolve("runtime/windows-x86_64/Live2DCubismCore.dll"))
                : "Missing bundled Live2DCubismCore.dll";
        assert Files.isRegularFile(root.resolve("models/Haru/Haru.model3.json"))
                : "Missing bundled Haru.model3.json";
        assert Files.isRegularFile(root.resolve("models/Haru/Haru.moc3"))
                : "Missing bundled Haru.moc3";
        assert Files.isRegularFile(root.resolve("models/Haru/Haru.2048/texture_00.png"))
                : "Missing bundled Haru texture";
    }

    private static void assertHaruScans(Path root) throws Exception {
        List<Live2DModelManifest> models = new Live2DModelRepository(root.resolve("models")).scan();
        assert models.size() == 1 : "Expected exactly one bundled model, found " + models.size();
        assert models.get(0).modelJson().getFileName().toString().equals("Haru.model3.json")
                : "Bundled model manifest is not Haru";
    }

    private static void traversalCheck() throws Exception {
        Path target = Files.createTempDirectory("yuan-live2d-traversal-");
        try {
            try {
                Live2DResourceInstaller.extractListed("/install-index-traversal.txt", "runtime/", target);
                throw new AssertionError("Path traversal entry was accepted");
            } catch (IOException expected) {
                assert expected.getMessage().contains("escapes target") : expected.getMessage();
            }
            assert !Files.exists(target.getParent().resolve("escape.txt"))
                    : "Traversal test escaped the extraction target";
        } finally {
            deleteTree(target);
        }
    }

    private static void legacyCopiesWhenMissing() throws Exception {
        Path parent = Files.createTempDirectory("yuan-live2d-legacy-copy-");
        try {
            Path root = parent.resolve("yuan_live2d");
            Path legacy = parent.resolve("yuan/live2d");
            Path legacyConfig = legacy.resolve("config.json");
            String oldConfig = "{\"format\":1,\"global\":{\"selectedModelId\":\"User\"}}";
            Files.createDirectories(legacy.resolve("models/User"));
            Files.writeString(legacyConfig, oldConfig);
            Files.writeString(legacy.resolve("models/User/User.model3.json"), "old-user-model");

            Live2DResourceInstaller.install(root);

            assert Files.readString(root.resolve("config.json")).equals(oldConfig)
                    : "Legacy config was not copied into the new root";
            assert legacyBackups(root) == 1 : "Legacy config backup is missing";
            assert Files.readString(legacy.resolve("models/User/User.model3.json")).equals("old-user-model")
                    : "Legacy source was modified";
            assert Files.isRegularFile(root.resolve("models/User/User.model3.json"))
                    : "Legacy user model was not copied into the new root";
        } finally {
            deleteTree(parent);
        }
    }

    private static void legacyNeverOverwrites() throws Exception {
        Path parent = Files.createTempDirectory("yuan-live2d-legacy-keep-");
        try {
            Path root = parent.resolve("yuan_live2d");
            Path legacy = parent.resolve("yuan/live2d");
            String keptConfig = "{\"format\":2,\"global\":{\"selectedModelId\":\"Haru\"}}";
            Files.createDirectories(root.resolve("models/Existing"));
            Files.writeString(root.resolve("config.json"), keptConfig);
            Files.writeString(root.resolve("models/Existing/keep.txt"), "new-file");
            Files.createDirectories(legacy.resolve("models/Existing"));
            Files.createDirectories(legacy.resolve("models/User"));
            Files.writeString(legacy.resolve("config.json"), "{\"format\":1}");
            Files.writeString(legacy.resolve("models/Existing/keep.txt"), "old-file");
            Files.writeString(legacy.resolve("models/User/User.model3.json"), "old-user-model");

            Live2DResourceInstaller.install(root);

            assert Files.readString(root.resolve("config.json")).equals(keptConfig)
                    : "Existing new config was overwritten";
            assert Files.readString(root.resolve("models/Existing/keep.txt")).equals("new-file")
                    : "Existing new model file was overwritten";
            assert Files.isRegularFile(root.resolve("models/User/User.model3.json"))
                    : "Legacy user model was not copied alongside existing files";
            assert legacyBackups(root) == 1 : "Legacy config backup is missing";
        } finally {
            deleteTree(parent);
        }
    }

    private static long legacyBackups(Path root) throws IOException {
        try (var paths = Files.list(root)) {
            return paths.map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith("config.legacy-") && name.endsWith(".json"))
                    .count();
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }
}
