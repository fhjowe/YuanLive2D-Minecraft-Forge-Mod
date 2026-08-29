package com.yuan.live2d.client.live2d;

import com.yuan.live2d.client.live2d.Live2DConfig;
import com.yuan.live2d.client.live2d.Live2DConfigStore;
import com.yuan.live2d.client.live2d.Live2DManifestReader;
import com.yuan.live2d.client.live2d.Live2DModelImporter;
import com.yuan.live2d.client.live2d.Live2DModelManagerCheck;
import com.yuan.live2d.client.live2d.Live2DModelManifest;
import com.yuan.live2d.client.live2d.Live2DModelRepository;
import com.yuan.live2d.client.live2d.Live2DVisibility;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class Live2DFoundationCheck {
    public static void main(String[] args) throws Exception {
        Live2DModelManagerCheck.check();
        Path temp = Files.createTempDirectory("yuan-live2d-check-");
        try {
            Live2DConfigStore store = new Live2DConfigStore(temp);
            Live2DConfig defaults = store.load();
            assert defaults.format == 2;
            assert defaults.global.enabled;
            assert defaults.global.visibility == Live2DConfig.Visibility.ALWAYS;

            defaults.global.scale = 99;
            defaults.global.opacity = -1;
            defaults.global.hideDelayTicks = -10;
            store.save(defaults);

            Live2DConfig loaded = store.load();
            assert loaded.global.scale == 5;
            assert loaded.global.opacity == 0;
            assert loaded.global.hideDelayTicks == 0;
            assert loaded.performance.textureMemoryBudgetMiB == 768;
            assert Files.exists(temp.resolve("config.json"));
            assert Files.exists(temp.resolve("config.json.bak"));

            Path source = temp.resolve("source-model");
            Files.createDirectories(source.resolve("tex"));
            Files.write(source.resolve("avatar.moc3"), new byte[] {1});
            Files.write(source.resolve("tex/texture_00.png"), new byte[] {1});
            Files.writeString(source.resolve("smile.exp3.json"), "{}");
            Files.writeString(source.resolve("idle.motion3.json"), "{}");
            Files.writeString(source.resolve("avatar.model3.json"), """
                    {"Version":3,"FileReferences":{"Moc":"avatar.moc3","Textures":["tex/texture_00.png"]}}
                    """);

            Live2DModelManifest manifest = Live2DManifestReader.read(source, source.resolve("avatar.model3.json"));
            assert manifest.version() == 3;
            assert manifest.moc().equals(source.resolve("avatar.moc3"));
            assert manifest.textures().size() == 1;
            assert manifest.expressions().size() == 1;
            assert manifest.motions().size() == 1;

            Path outside = temp.resolve("outside-model");
            Files.createDirectories(outside);
            Files.write(outside.resolve("outside.moc3"), new byte[] {1});
            Files.write(outside.resolve("outside.png"), new byte[] {1});
            Path escapeManifest = source.resolve("escape.model3.json");
            Files.writeString(escapeManifest, """
                    {"Version":3,"FileReferences":{"Moc":"../outside-model/outside.moc3","Textures":["../outside-model/outside.png"]}}
                    """);
            boolean escaped = false;
            try { Live2DManifestReader.read(source, escapeManifest); }
            catch (IllegalArgumentException expected) { escaped = expected.getMessage().contains("escapes root"); }
            assert escaped : "model references must not escape the model root";
            Files.delete(escapeManifest);

            Path linkedMoc = source.resolve("linked.moc3");
            boolean symlinksSupported = true;
            try {
                Files.createSymbolicLink(linkedMoc, outside.resolve("outside.moc3"));
            } catch (UnsupportedOperationException | IOException | SecurityException ignored) {
                symlinksSupported = false;
                Files.deleteIfExists(linkedMoc);
            }
            if (symlinksSupported) {
                Path linkedResourceManifest = source.resolve("linked-resource.model3.json");
                Files.writeString(linkedResourceManifest, """
                        {"Version":3,"FileReferences":{"Moc":"linked.moc3","Textures":["tex/texture_00.png"]}}
                        """);
                boolean linkedResourceRejected = false;
                try { Live2DManifestReader.read(source, linkedResourceManifest); }
                catch (IllegalArgumentException expected) {
                    linkedResourceRejected = expected.getMessage().contains("Symbolic links are not allowed");
                }
                assert linkedResourceRejected : "symbolic-link resources must be rejected";
                Files.delete(linkedResourceManifest);
                Files.delete(linkedMoc);

                Files.writeString(outside.resolve("outside.model3.json"), """
                        {"Version":3,"FileReferences":{"Moc":"avatar.moc3","Textures":["tex/texture_00.png"]}}
                        """);
                Path linkedModelJson = source.resolve("linked.model3.json");
                Files.createSymbolicLink(linkedModelJson, outside.resolve("outside.model3.json"));
                boolean linkedModelRejected = false;
                try { Live2DManifestReader.read(source, linkedModelJson); }
                catch (IllegalArgumentException expected) {
                    linkedModelRejected = expected.getMessage().contains("Symbolic links are not allowed");
                }
                assert linkedModelRejected : "symbolic-link model JSON must be rejected";
                Files.delete(linkedModelJson);
            }

            List<Path> mutableTextures = new ArrayList<>(List.of(source.resolve("tex/texture_00.png")));
            List<Path> mutableExpressions = new ArrayList<>(List.of(source.resolve("smile.exp3.json")));
            List<Path> mutableMotions = new ArrayList<>(List.of(source.resolve("idle.motion3.json")));
            Live2DModelManifest copied = new Live2DModelManifest("avatar", "avatar", 3, source,
                    source.resolve("avatar.model3.json"), source.resolve("avatar.moc3"), mutableTextures, null,
                    mutableExpressions, mutableMotions);
            mutableTextures.clear();
            mutableExpressions.clear();
            mutableMotions.clear();
            assert copied.textures().size() == 1;
            assert copied.expressions().size() == 1;
            assert copied.motions().size() == 1;

            Path models = temp.resolve("models");
            List<Live2DModelManifest> directoryImport = Live2DModelImporter.importSource(source, models);
            assert directoryImport.size() == 1;
            assert directoryImport.get(0).root().startsWith(models.toAbsolutePath());
            assert !directoryImport.get(0).root().equals(source);
            assert Files.exists(directoryImport.get(0).modelJson());

            Path zip = temp.resolve("flagged-utf8.zip");
            writeModelZip(zip, java.nio.charset.StandardCharsets.UTF_8, "模型");
            List<Live2DModelManifest> zipImport = Live2DModelImporter.importSource(zip, models);
            assert zipImport.size() == 1;
            assert zipImport.get(0).root().startsWith(models.toAbsolutePath());
            assert zipImport.get(0).modelJson().getParent().getFileName().toString().equals("模型");

            Path unflaggedUtf8 = temp.resolve("unflagged-utf8.zip");
            writeModelZip(unflaggedUtf8, java.nio.charset.StandardCharsets.UTF_8, "模型");
            clearUtf8Flags(unflaggedUtf8);
            Live2DModelManifest unflaggedUtf8Import = Live2DModelImporter.importSource(unflaggedUtf8, models).get(0);
            assert unflaggedUtf8Import.modelJson().getParent().getFileName().toString().equals("模型");

            Path legacyZip = temp.resolve("unflagged-gbk.zip");
            writeModelZip(legacyZip, java.nio.charset.Charset.forName("GBK"), "模型");
            Live2DModelManifest legacyImport = Live2DModelImporter.importSource(legacyZip, models).get(0);
            assert legacyImport.modelJson().getParent().getFileName().toString().equals("模型");

            Path nestedManifest = zipImport.get(0).modelJson();
            Path brokenManifest = nestedManifest.resolveSibling("broken.model3.json");
            Files.writeString(brokenManifest, "{}");
            Files.write(models.resolve("loose.moc3"), new byte[] {1});
            Files.write(models.resolve("loose.png"), new byte[] {1});
            Files.writeString(models.resolve("loose.model3.json"), """
                    {"Version":3,"FileReferences":{"Moc":"loose.moc3","Textures":["loose.png"]}}
                    """);
            if (symlinksSupported)
                Files.createSymbolicLink(models.resolve("linked.model3.json"), source.resolve("avatar.model3.json"));
            Live2DModelRepository repository = new Live2DModelRepository(models);
            List<Live2DModelManifest> scanned = repository.scan();
            assert scanned.size() == 4 : "only manifests under direct import directories are managed";
            assert repository.errors().size() == 1;
            assert repository.errors().containsKey(brokenManifest);
            assert scanned.stream().anyMatch(model -> model.modelJson().equals(nestedManifest))
                    : "a valid nested sibling must survive a malformed manifest in the same import";
            assert repository.find(scanned.get(0).id()).isPresent();

            Path staleStaging = models.resolve(".import-stale");
            copyValidModel(source, staleStaging);
            assert repository.scan().size() == 4 : "stale staging directories must not be managed imports";
            deleteTree(staleStaging);

            Path stableModels = temp.resolve("stable-models");
            Path laterSource = temp.resolve("z-existing");
            copyValidModel(source, laterSource);
            Live2DModelManifest importedExisting = Live2DModelImporter.importSource(laterSource, stableModels).get(0);
            Live2DModelRepository stableRepository = new Live2DModelRepository(stableModels);
            Live2DModelManifest scannedExisting = stableRepository.scan().get(0);
            assert importedExisting.id().equals(scannedExisting.id())
                    : "importer and repository must derive the same managed model ID";
            Path earlierSource = temp.resolve("a-earlier");
            copyValidModel(source, earlierSource);
            Live2DModelImporter.importSource(earlierSource, stableModels);
            String existingId = stableRepository.scan().stream()
                    .filter(model -> model.root().getFileName().toString().equals("z-existing"))
                    .findFirst().orElseThrow().id();
            assert existingId.equals(scannedExisting.id())
                    : "a lexically earlier duplicate filename import must not change an existing model ID";

            Live2DVisibility.InventorySnapshot inventory = new Live2DVisibility.InventorySnapshot(
                    false, true, true, true);
            assert !Live2DVisibility.matches(Live2DConfig.Visibility.MAIN_HAND, inventory);
            assert Live2DVisibility.matches(Live2DConfig.Visibility.EITHER_HAND, inventory);
            assert Live2DVisibility.matches(Live2DConfig.Visibility.HOTBAR, inventory);
            assert Live2DVisibility.matches(Live2DConfig.Visibility.INVENTORY, inventory);
            assert Live2DVisibility.matches(Live2DConfig.Visibility.ALWAYS, inventory);
            assert !Live2DVisibility.matches(null, inventory);
            assert !Live2DVisibility.matches(Live2DConfig.Visibility.ALWAYS, null);

            Live2DVisibility.InventorySnapshot mainHandOnly = new Live2DVisibility.InventorySnapshot(
                    true, false, false, false);
            Live2DVisibility.InventorySnapshot offHandOnly = new Live2DVisibility.InventorySnapshot(
                    false, true, false, false);
            Live2DVisibility.InventorySnapshot hotbarOnly = new Live2DVisibility.InventorySnapshot(
                    false, false, true, false);
            Live2DVisibility.InventorySnapshot inventoryOnly = new Live2DVisibility.InventorySnapshot(
                    false, false, false, true);
            assert Live2DVisibility.matches(Live2DConfig.Visibility.MAIN_HAND, mainHandOnly);
            assert Live2DVisibility.matches(Live2DConfig.Visibility.EITHER_HAND, offHandOnly);
            assert Live2DVisibility.matches(Live2DConfig.Visibility.HOTBAR, hotbarOnly);
            assert Live2DVisibility.matches(Live2DConfig.Visibility.INVENTORY, inventoryOnly);
            assert !Live2DVisibility.matches(Live2DConfig.Visibility.INVENTORY, hotbarOnly);
            assert !Live2DVisibility.matches(Live2DConfig.Visibility.HOTBAR, inventoryOnly);

            Path malicious = temp.resolve("malicious.zip");
            try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(malicious))) {
                addZip(out, "../escape.txt", new byte[] {1});
            }
            assertImportRejected(malicious, models);
            assert !Files.exists(temp.resolve("escape.txt"));
            assertNoStaging(models);

            Path duplicate = temp.resolve("duplicate.zip");
            try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(duplicate))) {
                addZip(out, "A/file.txt", new byte[] {1});
                addZip(out, "a/FILE.txt", new byte[] {2});
            }
            assertImportRejected(duplicate, models, "Duplicate ZIP output path");
            assertNoStaging(models);

            Path quota = temp.resolve("quota.zip");
            try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(quota))) {
                addZip(out, "large.bin", new byte[] {1, 2, 3, 4, 5});
            }
            boolean quotaRejected = false;
            try { Live2DModelImporter.importSource(quota, models, new Live2DModelImporter.Limits(10, 4, 16)); }
            catch (IllegalArgumentException expected) { quotaRejected = expected.getMessage().contains("entry size"); }
            assert quotaRejected : "streaming per-entry quota must reject oversized expansion";
            assertNoStaging(models);

            Path absolute = temp.resolve("absolute.zip");
            try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(absolute))) {
                addZip(out, "/absolute.txt", new byte[] {1});
            }
            assertImportRejected(absolute, models);
            assertNoStaging(models);

            if (symlinksSupported) {
                Path linkedSource = temp.resolve("linked-source");
                Files.createDirectories(linkedSource.resolve("tex"));
                Files.write(linkedSource.resolve("avatar.moc3"), new byte[] {1});
                Files.write(linkedSource.resolve("tex/texture.png"), new byte[] {1});
                Files.writeString(linkedSource.resolve("avatar.model3.json"), """
                        {"Version":3,"FileReferences":{"Moc":"avatar.moc3","Textures":["tex/texture.png"]}}
                        """);
                Files.createSymbolicLink(linkedSource.resolve("outside"), outside);
                assertImportRejected(linkedSource, models, "Symbolic links are not allowed");
                assertNoStaging(models);

                Path realModels = temp.resolve("real-models");
                Files.createDirectories(realModels);
                Path linkedModels = temp.resolve("linked-models");
                Files.createSymbolicLink(linkedModels, realModels);
                assertImportRejected(source, linkedModels, "Symbolic links are not allowed");
                assertNoStaging(realModels);

                Path realParent = temp.resolve("real-parent");
                Files.createDirectories(realParent.resolve("models"));
                Path linkedParent = temp.resolve("linked-parent");
                Files.createSymbolicLink(linkedParent, realParent);
                assertImportRejected(source, linkedParent.resolve("models"), "Symbolic links are not allowed");
                assertNoStaging(realParent.resolve("models"));

                assertRepositoryRejected(linkedModels, "Symbolic links are not allowed");
                assertRepositoryRejected(linkedParent.resolve("models"), "Symbolic links are not allowed");
            }

            assertImportRejected(source, source, "overlap");
            Path sourceContainedRoot = source.resolve("managed-models");
            assertImportRejected(source, sourceContainedRoot, "overlap");
            assert !Files.exists(sourceContainedRoot) : "overlap rejection must precede root creation";
            Path rootContainingSource = temp.resolve("root-containing-source");
            Path nestedSource = rootContainingSource.resolve("nested-source");
            copyValidModel(source, nestedSource);
            assertImportRejected(nestedSource, rootContainingSource, "overlap");
            if (symlinksSupported) {
                Path rootAlias = temp.resolve("root-alias");
                Files.createSymbolicLink(rootAlias, rootContainingSource);
                assertImportRejected(rootAlias.resolve("nested-source"), rootContainingSource, "overlap");
            }

            Files.writeString(temp.resolve("config.json"), "not json");
            Live2DConfig recovered = new Live2DConfigStore(temp).load();
            assert recovered.format == 2;
            try (var files = Files.list(temp)) {
                assert files.anyMatch(path -> path.getFileName().toString().startsWith("config.corrupt-"));
            }

            Live2DConfig backupConfig = Live2DConfig.defaults();
            backupConfig.global.opacity = .25f;
            Live2DConfigStore backupStore = new Live2DConfigStore(temp);
            backupStore.save(backupConfig);
            Files.writeString(temp.resolve("config.json"), "not json again");
            assert backupStore.load().global.opacity == .25f : "schema corruption must recover the last-known-good backup";

            Path closedZip = temp.resolve("closed.zip");
            Live2DConfigStore closedStore;
            try (FileSystem fileSystem = FileSystems.newFileSystem(
                    URI.create("jar:" + closedZip.toUri()), Map.of("create", "true"))) {
                closedStore = new Live2DConfigStore(fileSystem.getPath("/config"));
            }
            assert closedStore.load().format == 2 : "filesystem runtime failures must recover to defaults";

            long[] clock = {0};
            int[] scans = {0};
            Live2DModelRepository throttled = new Live2DModelRepository(stableModels, () -> clock[0], () -> scans[0]++);
            throttled.scan();
            throttled.scan();
            assert scans[0] == 1 : "repository rescans must be throttled";
            throttled.invalidate();
            throttled.scan();
            assert scans[0] == 2 : "explicit reload must invalidate repository cache";

            Path incomplete = temp.resolve("incomplete");
            Files.createDirectories(incomplete);
            Files.writeString(incomplete.resolve("bad.model3.json"), """
                    {"Version":3,"FileReferences":{"Moc":"missing.moc3","Textures":["missing.png"]}}
                    """);
            long stagingBefore;
            try (var entries = Files.list(models)) {
                stagingBefore = entries.filter(path -> path.getFileName().toString().startsWith(".import-")).count();
            }
            boolean incompleteRejected = false;
            try { Live2DModelImporter.importSource(incomplete, models); }
            catch (IllegalArgumentException expected) { incompleteRejected = true; }
            assert incompleteRejected;
            try (var entries = Files.list(models)) {
                assert entries.filter(path -> path.getFileName().toString().startsWith(".import-")).count() == stagingBefore;
            }
        } finally {
            deleteTree(temp);
        }
    }

    private static void addZip(ZipOutputStream out, String name, byte[] value) throws Exception {
        out.putNextEntry(new ZipEntry(name));
        out.write(value);
        out.closeEntry();
    }

    private static void writeModelZip(Path zip, java.nio.charset.Charset charset, String directory) throws Exception {
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip), charset)) {
            addZip(out, directory + "/avatar.moc3", new byte[] {1});
            addZip(out, directory + "/texture.png", new byte[] {1});
            addZip(out, directory + "/avatar.model3.json", """
                    {"Version":3,"FileReferences":{"Moc":"avatar.moc3","Textures":["texture.png"]}}
                    """.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private static void clearUtf8Flags(Path zip) throws Exception {
        byte[] bytes = Files.readAllBytes(zip);
        for (int offset = 0; offset + 10 < bytes.length; offset++) {
            boolean local = bytes[offset] == 0x50 && bytes[offset + 1] == 0x4b
                    && bytes[offset + 2] == 0x03 && bytes[offset + 3] == 0x04;
            boolean central = bytes[offset] == 0x50 && bytes[offset + 1] == 0x4b
                    && bytes[offset + 2] == 0x01 && bytes[offset + 3] == 0x02;
            if (local || central) {
                int flag = offset + (local ? 6 : 8);
                bytes[flag + 1] &= (byte) ~0x08;
            }
        }
        Files.write(zip, bytes);
    }

    private static void assertImportRejected(Path source, Path modelsRoot) throws Exception {
        assertImportRejected(source, modelsRoot, null);
    }

    private static void assertImportRejected(Path source, Path modelsRoot, String message) throws Exception {
        boolean rejected = false;
        try { Live2DModelImporter.importSource(source, modelsRoot); }
        catch (IllegalArgumentException expected) {
            rejected = message == null || expected.getMessage().contains(message);
        }
        assert rejected : "unsafe or invalid import must be rejected: " + source;
    }

    private static void copyValidModel(Path source, Path target) throws Exception {
        Files.createDirectories(target.resolve("tex"));
        Files.copy(source.resolve("avatar.moc3"), target.resolve("avatar.moc3"));
        Files.copy(source.resolve("tex/texture_00.png"), target.resolve("tex/texture_00.png"));
        Files.copy(source.resolve("avatar.model3.json"), target.resolve("avatar.model3.json"));
    }

    private static void assertRepositoryRejected(Path modelsRoot, String message) throws Exception {
        boolean rejected = false;
        try { new Live2DModelRepository(modelsRoot).scan(); }
        catch (IllegalArgumentException expected) { rejected = expected.getMessage().contains(message); }
        assert rejected : "unsafe repository root must be rejected: " + modelsRoot;
    }

    private static void assertNoStaging(Path modelsRoot) throws Exception {
        try (var paths = Files.list(modelsRoot)) {
            assert paths.noneMatch(path -> path.getFileName().toString().startsWith(".import-"))
                    : "failed import left a staging directory";
        }
    }

    private static void deleteTree(Path root) throws Exception {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }
}
