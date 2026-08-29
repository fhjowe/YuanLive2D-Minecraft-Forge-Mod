package com.yuan.live2d.client.live2d;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

public final class Live2DConfigCheck {
    private Live2DConfigCheck() {}

    public static void main(String[] args) throws Exception {
        check();
    }

    public static void check() throws Exception {
        Live2DConfig config = Live2DConfig.defaults();
        assert config.format == 2 : "config must be v2";
        assert config.global != null && config.hud != null && config.performance != null;
        assert config.global.enabled && config.global.visibility == Live2DConfig.Visibility.ALWAYS;
        assert Live2DConfig.Visibility.values().length == 5 : "visibility enum must be generic";
        for (String legacy : java.util.List.of("ANY_YUAN_ITEM", "YUAN_ARMOR", "FULL_YUAN_ARMOR", "UNKNOWN")) {
            Live2DConfig migratedVisibility = Live2DConfigStore.migrateVisibilityForTest(legacy);
            assert migratedVisibility.global.visibility == Live2DConfig.Visibility.ALWAYS;
        }
        assert config.hud.preset == Live2DConfig.HudPreset.CLEAN;
        assert config.performance.policy == Live2DConfig.PerformancePolicy.AUTO_DEGRADE;
        Live2DConfig.Performance performance = new Live2DConfig.Performance();
        performance.maxVisibleInstances = 9999;
        performance.textureMemoryBudgetMiB = 999999;
        performance.sanitize();
        assert performance.maxVisibleInstances == 4096 : "custom instance cap must clamp to 4096";
        assert performance.textureMemoryBudgetMiB == 262144 : "custom budget cap must clamp to 262144";
        performance.maxVisibleInstances = 0;
        performance.textureMemoryBudgetMiB = 1;
        performance.sanitize();
        assert performance.maxVisibleInstances == 1 : "instance floor must clamp to 1";
        assert performance.textureMemoryBudgetMiB == 16 : "budget floor must clamp to 16";
        assert config.modelOverrides != null && config.modelOverrides.isEmpty();

        Live2DConfig copy = config.copy();
        assert copy.sameSettings(config);
        copy.global.scale = 1.5f;
        assert !copy.sameSettings(config);
        copy.global.scale = config.global.scale;
        copy.hud.crosshair = true;
        assert !copy.sameSettings(config);

        config.modelOverrides = new HashMap<>();
        config.modelOverrides.put("m", new Live2DConfig.ModelOverride());
        config.modelOverrides.get("m").scale = 2f;
        Live2DConfig sanitized = config.copy();
        sanitized.sanitize();
        assert sanitized.modelOverrides.get("m").scale == 2f;
        sanitized.modelOverrides.put("bad", null);
        sanitized.sanitize();
        assert !sanitized.modelOverrides.containsKey("bad");

        Live2DConfig empty = config.copy();
        empty.modelOverrides.put("empty", new Live2DConfig.ModelOverride());
        empty.sanitize();
        assert !empty.modelOverrides.containsKey("empty") : "empty override must be removed";

        Live2DConfig overridden = config.copy();
        assert overridden.sameSettings(config) : "deep copy of overrides must compare equal";
        overridden.modelOverrides.get("m").scale = 3f;
        assert !overridden.sameSettings(config) : "override value change must break sameSettings";

        Live2DConfig.Global effective = config.effectiveGlobal("m");
        assert effective.scale == 2f && effective.anchorX == config.global.anchorX
                && effective.opacity == config.global.opacity : "effectiveGlobal must merge field-level overrides";
        Live2DConfig.Global inherited = config.effectiveGlobal("other");
        assert inherited.scale == config.global.scale && inherited.anchorX == config.global.anchorX
                : "missing override must inherit global";

        Live2DConfig.ModelOverride partial = new Live2DConfig.ModelOverride();
        partial.opacity = .25f;
        Live2DConfig partialConfig = config.copy();
        partialConfig.modelOverrides.put("p", partial);
        Live2DConfig.Global partialEffective = partialConfig.effectiveGlobal("p");
        assert partialEffective.opacity == .25f && partialEffective.scale == partialConfig.global.scale
                : "null override fields must inherit global";

        Live2DConfig.ModelOverride clamp = new Live2DConfig.ModelOverride();
        clamp.scale = 99f;
        clamp.offsetX = 1_000_000f;
        Live2DConfig clamped = config.copy();
        clamped.modelOverrides.put("c", clamp);
        clamped.sanitize();
        assert clamped.modelOverrides.get("c").scale == 5f : "override scale must clamp to global range";
        assert clamped.modelOverrides.get("c").offsetX == 16384f : "override offset must clamp to global range";

        Live2DConfig.Hud hud = new Live2DConfig.Hud();
        hud.applyPreset(Live2DConfig.HudPreset.ALL);
        assert hud.crosshair && hud.hotbar && hud.chat && hud.thirdParty;

        Path temp = Files.createTempDirectory("yuan-config-v2-");
        try {
            Files.writeString(temp.resolve("config.json"),
                    "{\"format\":1,\"enabled\":true,\"selectedModelId\":\"m1\",\"anchorX\":0.8,\"anchorY\":0.55,"
                            + "\"offsetX\":-24,\"offsetY\":-24,\"scale\":0.45,\"opacity\":1,\"visibility\":\"ALWAYS\","
                            + "\"hideDelayTicks\":5,\"performancePolicy\":\"AUTO_DEGRADE\",\"maxVisibleInstances\":4,"
                            + "\"textureMemoryBudgetMiB\":768}");
            Path uiJson = temp.resolveSibling("ui.json");
            Files.createDirectories(temp.getParent());
            Files.writeString(uiJson, "{\"live2dAdjustHud\":{\"preset\":\"VANILLA\",\"crosshair\":true,\"hotbar\":true}}");
            Live2DConfigStore store = new Live2DConfigStore(temp);
            Live2DConfig migrated = store.load();
            assert migrated.format == 2 && "m1".equals(migrated.global.selectedModelId);
            assert migrated.performance.textureMemoryBudgetMiB == 768;
            assert migrated.hud.preset == Live2DConfig.HudPreset.VANILLA && migrated.hud.crosshair && migrated.hud.hotbar;
            try (var files = Files.list(temp)) {
                assert files.anyMatch(p -> p.getFileName().toString().startsWith("config.legacy-"));
            }
            assert !Files.readString(uiJson).contains("live2dAdjustHud");
            assert store.load().format == 2;
            assert Files.readString(temp.resolve("config.json")).contains("\"format\": 2");
        } finally {
            deleteTree(temp);
            Files.deleteIfExists(temp.resolveSibling("ui.json"));
        }

        Path legacyTemp = Files.createTempDirectory("yuan-config-legacy-");
        try {
            Files.writeString(legacyTemp.resolve("config.json"),
                    "{\"format\":2,\"global\":{\"visibility\":\"FULL_YUAN_ARMOR\"}}");
            Live2DConfig legacyMigrated = new Live2DConfigStore(legacyTemp).load();
            assert legacyMigrated.global.visibility == Live2DConfig.Visibility.ALWAYS;

            Path overrideTemp = Files.createTempDirectory("yuan-config-override-");
            try {
                Files.writeString(overrideTemp.resolve("config.json"),
                        "{\"format\":2,\"global\":{\"selectedModelId\":\"first\",\"scale\":0.45},"
                                + "\"modelOverrides\":{\"first\":{\"scale\":2.0,\"visibility\":\"FULL_YUAN_ARMOR\",\"opacity\":0.5},"
                                + "\"legacy\":{\"visibility\":\"ANY_YUAN_ITEM\"},"
                                + "\"nonString\":{\"visibility\":42},"
                                + "\"missing\":{\"scale\":0.7},"
                                + "\"unknown\":{\"visibility\":\"UNKNOWN\"}}}");
                Live2DConfig overrideMigrated = new Live2DConfigStore(overrideTemp).load();
                assert overrideMigrated.modelOverrides.get("first").scale == 2f;
                assert overrideMigrated.modelOverrides.get("first").opacity == .5f;
                assert overrideMigrated.modelOverrides.get("first").visibility == Live2DConfig.Visibility.ALWAYS
                        : "override legacy visibility must map to ALWAYS";
                assert overrideMigrated.modelOverrides.get("legacy").visibility == Live2DConfig.Visibility.ALWAYS;
                assert overrideMigrated.modelOverrides.get("unknown").visibility == Live2DConfig.Visibility.ALWAYS
                        : "unknown override visibility must map to ALWAYS";
                assert overrideMigrated.modelOverrides.get("missing").scale == .7f
                        : "override without visibility must keep other fields";
                assert !overrideMigrated.modelOverrides.containsKey("nonString")
                        : "non-string override visibility must be dropped and empty override removed";
                assert overrideMigrated.global.enabled : "legacy override visibility must not corrupt the config";
                Live2DConfig writeTest = overrideMigrated.copy();
                writeTest.modelOverrides.put("only", new Live2DConfig.ModelOverride());
                writeTest.modelOverrides.get("only").scale = 1.5f;
                writeTest.modelOverrides.put("empty", new Live2DConfig.ModelOverride());
                new Live2DConfigStore(overrideTemp).save(writeTest);
                JsonObject written = JsonParser.parseString(Files.readString(overrideTemp.resolve("config.json"))).getAsJsonObject();
                JsonObject writtenOverrides = written.getAsJsonObject("modelOverrides");
                assert writtenOverrides.has("only") && writtenOverrides.getAsJsonObject("only").size() == 1
                        && writtenOverrides.getAsJsonObject("only").has("scale")
                        : "only the overridden scale field must be serialized";
                assert !writtenOverrides.has("empty") : "empty override must not be serialized";
                assert written.has("global") && written.getAsJsonObject("global").has("enabled")
                        && written.getAsJsonObject("global").get("enabled").getAsBoolean()
                        : "global enabled must be serialized";
            } finally {
                deleteTree(overrideTemp);
            }
        } finally {
            deleteTree(legacyTemp);
        }
        checkNewSections();
    }

    private static void checkNewSections() throws Exception {
        Live2DConfig config = Live2DConfig.defaults();
        assert config.interaction.randomMotionIntervalSeconds == 12f
                && config.interaction.clickReactionChance == .6f;
        assert config.physics.amplitude == 1f && config.physics.strength == .3f && config.physics.edgeSquash;
        assert config.render.shadowEnabled && config.render.switchFadeTicks == 5;
        config.interaction.randomMotionIntervalSeconds = 1f;
        config.interaction.clickReactionChance = -1f;
        config.physics.amplitude = 5f;
        config.physics.strength = 9f;
        config.render.switchFadeTicks = 999;
        config.sanitize();
        assert config.interaction.randomMotionIntervalSeconds == 5f
                && config.interaction.clickReactionChance == 0f;
        assert config.physics.amplitude == 2f && config.physics.strength == 1f
                && config.render.switchFadeTicks == 20;
        Live2DConfig copy = config.copy();
        assert copy.interaction.sameSettings(config.interaction)
                && copy.physics.sameSettings(config.physics)
                && copy.render.sameSettings(config.render);
        assert config.sameSettings(copy);

        assert config.interaction.viewFollowEnabled && config.interaction.viewFollowStrength == .6f;
        config.interaction.viewFollowStrength = 3f;
        config.sanitize();
        assert config.interaction.viewFollowStrength == 1f;
        Live2DConfig viewCopy = config.copy();
        assert viewCopy.interaction.sameSettings(config.interaction);
        // modelFollow fields are removed; the compile-time guarantee is that no reference can exist.

        Path interactionTemp = Files.createTempDirectory("yuan-config-interaction-");
        try {
            Files.writeString(interactionTemp.resolve("config.json"),
                    "{\"format\":2,\"interaction\":{\"mouseFollowEnabled\":true,\"mouseFollowStrength\":1.0,"
                            + "\"modelFollowEnabled\":false,\"modelFollowStrength\":0.22}}");
            Live2DConfig migrated = new Live2DConfigStore(interactionTemp).load();
            assert migrated.interaction.viewFollowEnabled : "legacy follow flags must enable view follow";
            assert migrated.interaction.viewFollowStrength == 1f : "legacy mouse strength must migrate to view follow";
            new Live2DConfigStore(interactionTemp).save(migrated);
            String written = Files.readString(interactionTemp.resolve("config.json"));
            assert !written.contains("mouseFollowEnabled") && !written.contains("modelFollowEnabled")
                    : "legacy follow fields must be dropped on save";
            assert written.contains("viewFollowEnabled");

            Files.writeString(interactionTemp.resolve("config.json"),
                    "{\"format\":2,\"interaction\":{\"mouseFollowEnabled\":false,\"modelFollowEnabled\":true}}");
            assert new Live2DConfigStore(interactionTemp).load().interaction.viewFollowEnabled
                    : "mixed legacy follow flags must enable view follow";

            Files.writeString(interactionTemp.resolve("config.json"),
                    "{\"format\":2,\"interaction\":{\"mouseFollowEnabled\":false,\"modelFollowEnabled\":false}}");
            assert !new Live2DConfigStore(interactionTemp).load().interaction.viewFollowEnabled
                    : "both legacy follow flags off must disable view follow";
        } finally {
            deleteTree(interactionTemp);
        }
    }

    private static void deleteTree(Path root) throws Exception {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }
}
