package com.yuan.live2d.client.live2d;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public final class Live2DModelRegistryCheck {
    private Live2DModelRegistryCheck() {}

    public static void main(String[] args) throws Exception {
        check();
    }

    public static void check() throws Exception {
        Path temp = Files.createTempDirectory("yuan-live2d-registry-");
        try {
            Path models = temp.resolve("models");
            model(models.resolve("first"), "first");
            model(models.resolve("second"), "second");
            Live2DModelRegistry registry = new Live2DModelRegistry(models);
            assert registry.list().size() == 2;
            assert registry.selected() == null;
            String firstId = registry.list().get(0).id();
            String secondId = registry.list().get(1).id();
            assert registry.selectById(firstId);
            assert registry.selected() != null && registry.selected().id().equals(firstId);
            assert registry.selectById(secondId);
            assert registry.selectById("missing") == false;
            assert registry.selected() == null;

            model(models.resolve("third"), "third");
            assert registry.list().size() == 2 : "registry must cache until invalidated";
            registry.invalidate();
            assert registry.list().size() == 3;
            String thirdId = registry.list().get(2).id();
            assert registry.selectById(thirdId) && registry.selected().id().equals(thirdId);
            assert registry.lastError().isEmpty();

            Path blocked = temp.resolve("blocked");
            Files.writeString(blocked, "not a directory");
            Live2DModelRegistry broken = new Live2DModelRegistry(blocked);
            assert broken.list().isEmpty();
            assert !broken.lastError().isEmpty();
            assert broken.selectById("anything") == false;
            Files.delete(blocked);
            Files.createDirectories(blocked);
            model(blocked.resolve("recovered"), "recovered");
            assert broken.list().size() == 1 : "failed scan must retry on the next list call";
            assert broken.lastError().isEmpty();
        } finally {
            deleteTree(temp);
        }
    }

    private static void model(Path root, String name) throws Exception {
        Files.createDirectories(root);
        Files.write(root.resolve(name + ".moc3"), new byte[] {1});
        Files.write(root.resolve(name + ".png"), new byte[] {1});
        Files.writeString(root.resolve(name + ".model3.json"), """
                {"Version":3,"FileReferences":{"Moc":"%s.moc3","Textures":["%s.png"]}}
                """.formatted(name, name));
    }

    private static void deleteTree(Path root) throws Exception {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }
}
