package com.yuan.live2d.client.live2d;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;

public final class Live2DConfigStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path root;

    public Live2DConfigStore(Path root) { this.root = root.toAbsolutePath().normalize(); }

    public Live2DConfig load() {
        Path file = root.resolve("config.json");
        try {
            if (!Files.exists(file)) return Live2DConfig.defaults();
            String raw = Files.readString(file);
            boolean migrating = isV1(raw);
            if (migrating) backupLegacy(file);
            Live2DConfig config = parse(raw, uiJson());
            if (migrating) {
                writeConfig(config);
                removeMigratedHud(uiJson());
            }
            return config;
        } catch (JsonParseException | IllegalArgumentException error) {
            preserveCorrupt(file);
            return loadBackup();
        } catch (IOException | RuntimeException error) {
            return loadBackup();
        }
    }

    public void save(Live2DConfig config) throws IOException {
        writeConfig(config);
    }

    private void writeConfig(Live2DConfig config) throws IOException {
        Files.createDirectories(root);
        Path file = root.resolve("config.json");
        Path temp = root.resolve("config.json.tmp");
        String json = GSON.toJson(config.sanitize());
        parse(json, uiJson());
        Files.writeString(temp, json);
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
        Files.copy(file, root.resolve("config.json.bak"), StandardCopyOption.REPLACE_EXISTING);
    }

    private void removeMigratedHud(Path uiJson) {
        try {
            if (uiJson == null || !Files.exists(uiJson)) return;
            JsonObject ui = GSON.fromJson(Files.readString(uiJson), JsonObject.class);
            if (ui == null) return;
            boolean removed = ui.remove("live2dAdjustHud") != null;
            removed |= ui.remove("live2dAutoSave") != null;
            removed |= ui.remove("live2dShowWorldHudDuringPreview") != null;
            removed |= ui.remove("live2dAdjustPauseSingleplayer") != null;
            removed |= ui.remove("live2dAdjustLastParameter") != null;
            if (removed) Files.writeString(uiJson, GSON.toJson(ui));
        } catch (Exception ignored) {
        }
    }

    private Path uiJson() {
        return root.resolveSibling("ui.json");
    }

    private Live2DConfig loadBackup() {
        try {
            Path backup = root.resolve("config.json.bak");
            if (Files.exists(backup)) return parse(Files.readString(backup), uiJson());
        } catch (Exception ignored) {
        }
        Live2DConfig disabled = Live2DConfig.defaults();
        disabled.global.enabled = false;
        return disabled;
    }

    private static Live2DConfig parse(String json, Path uiJson) {
        JsonObject object = GSON.fromJson(json, JsonObject.class);
        if (object == null) throw new IllegalArgumentException("Empty Live2D config");
        int format = object.has("format") ? object.get("format").getAsInt() : 1;
        JsonObject source = object;
        if (format != Live2DConfig.FORMAT) {
            source = migrateV1(object, uiJson);
        }
        normalizeVisibility(source);
        normalizeOverrideVisibility(source);
        normalizeInteraction(source);
        Live2DConfig config = GSON.fromJson(source, Live2DConfig.class);
        if (config == null) throw new IllegalArgumentException("Unsupported Live2D config format");
        return config.sanitize();
    }

    private static JsonObject migrateV1(JsonObject v1, Path uiJson) {
        JsonObject out = new JsonObject();
        out.addProperty("format", Live2DConfig.FORMAT);

        JsonObject global = new JsonObject();
        copyIfPresent(v1, global, "enabled");
        copyIfPresent(v1, global, "selectedModelId");
        copyIfPresent(v1, global, "visibility");
        copyIfPresent(v1, global, "anchorX");
        copyIfPresent(v1, global, "anchorY");
        copyIfPresent(v1, global, "offsetX");
        copyIfPresent(v1, global, "offsetY");
        copyIfPresent(v1, global, "scale");
        copyIfPresent(v1, global, "opacity");
        copyIfPresent(v1, global, "hideDelayTicks");
        out.add("global", global);

        JsonObject hud = new JsonObject();
        try {
            if (uiJson != null && Files.exists(uiJson)) {
                JsonObject ui = GSON.fromJson(Files.readString(uiJson), JsonObject.class);
                if (ui != null && ui.has("live2dAdjustHud") && ui.get("live2dAdjustHud").isJsonObject()) {
                    JsonObject oldHud = ui.getAsJsonObject("live2dAdjustHud");
                    copyIfPresent(oldHud, hud, "preset");
                    copyIfPresent(oldHud, hud, "crosshair");
                    copyIfPresent(oldHud, hud, "hotbar");
                    copyIfPresent(oldHud, hud, "status");
                    copyIfPresent(oldHud, hud, "experience");
                    copyIfPresent(oldHud, hud, "effects");
                    copyIfPresent(oldHud, hud, "bossBars");
                    copyIfPresent(oldHud, hud, "scoreboard");
                    copyIfPresent(oldHud, hud, "chat");
                    copyIfPresent(oldHud, hud, "playerList");
                    copyIfPresent(oldHud, hud, "messages");
                    copyIfPresent(oldHud, hud, "thirdParty");
                }
            }
        } catch (Exception ignored) {
        }
        out.add("hud", hud);

        JsonObject performance = new JsonObject();
        copyIfPresent(v1, performance, "performancePolicy");
        copyIfPresent(v1, performance, "maxVisibleInstances");
        copyIfPresent(v1, performance, "textureMemoryBudgetMiB");
        out.add("performance", performance);

        out.add("modelOverrides", new JsonObject());
        return out;
    }

    private static void copyIfPresent(JsonObject from, JsonObject to, String name) {
        JsonElement value = from.get(name);
        if (value != null) to.add(name, value);
    }


    private static void normalizeVisibility(JsonObject source) {
        if (source == null || !source.has("global")) return;
        JsonElement global = source.get("global");
        if (!(global instanceof JsonObject globalObject) || !globalObject.has("visibility")) return;
        JsonElement visibility = globalObject.get("visibility");
        if (!visibility.isJsonPrimitive() || !visibility.getAsJsonPrimitive().isString()) {
            globalObject.remove("visibility");
            return;
        }
        globalObject.addProperty("visibility", normalizeVisibility(visibility.getAsString()));
    }

    private static void normalizeOverrideVisibility(JsonObject source) {
        if (source == null || !source.has("modelOverrides")) return;
        JsonElement overrides = source.get("modelOverrides");
        if (!(overrides instanceof JsonObject map)) return;
        for (var entry : map.entrySet()) {
            JsonElement value = entry.getValue();
            if (!(value instanceof JsonObject modelOverride) || !modelOverride.has("visibility")) continue;
            JsonElement visibility = modelOverride.get("visibility");
            if (!visibility.isJsonPrimitive() || !visibility.getAsJsonPrimitive().isString()) {
                modelOverride.remove("visibility");
                continue;
            }
            modelOverride.addProperty("visibility", normalizeVisibility(visibility.getAsString()));
        }
    }

    private static void normalizeInteraction(JsonObject source) {
        if (source == null || !source.has("interaction")) return;
        JsonElement interaction = source.get("interaction");
        if (!(interaction instanceof JsonObject interactionObject)) return;
        boolean legacy = interactionObject.has("mouseFollowEnabled")
                || interactionObject.has("mouseFollowStrength")
                || interactionObject.has("modelFollowEnabled")
                || interactionObject.has("modelFollowStrength");
        if (!legacy) return;
        Boolean mouse = legacyBoolean(interactionObject, "mouseFollowEnabled");
        Boolean model = legacyBoolean(interactionObject, "modelFollowEnabled");
        if (mouse != null || model != null) {
            interactionObject.addProperty("viewFollowEnabled",
                    Boolean.TRUE.equals(mouse) || Boolean.TRUE.equals(model));
        }
        if (!interactionObject.has("viewFollowStrength")) {
            JsonElement strength = interactionObject.has("mouseFollowStrength")
                    ? interactionObject.get("mouseFollowStrength")
                    : interactionObject.get("modelFollowStrength");
            if (strength != null && strength.isJsonPrimitive() && strength.getAsJsonPrimitive().isNumber()) {
                interactionObject.addProperty("viewFollowStrength", strength.getAsFloat());
            }
        }
        interactionObject.remove("mouseFollowEnabled");
        interactionObject.remove("mouseFollowStrength");
        interactionObject.remove("modelFollowEnabled");
        interactionObject.remove("modelFollowStrength");
    }

    private static Boolean legacyBoolean(JsonObject object, String name) {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) return null;
        return value.getAsBoolean();
    }

    private static String normalizeVisibility(String value) {
        return switch (value == null ? "" : value) {
            case "ANY_YUAN_ITEM", "YUAN_ARMOR", "FULL_YUAN_ARMOR" -> "ALWAYS";
            case "MAIN_HAND", "EITHER_HAND", "HOTBAR", "INVENTORY", "ALWAYS" -> value;
            default -> "ALWAYS";
        };
    }

    public static Live2DConfig migrateVisibilityForTest(String value) {
        Live2DConfig config = Live2DConfig.defaults();
        config.global.visibility = Live2DConfig.Visibility.valueOf(normalizeVisibility(value));
        return config;
    }

    private static boolean isV1(String json) {
        try {
            JsonObject object = GSON.fromJson(json, JsonObject.class);
            return object != null && (!object.has("format") || object.get("format").getAsInt() != Live2DConfig.FORMAT);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private void backupLegacy(Path file) {
        try {
            String suffix = Long.toString(Instant.now().toEpochMilli());
            Files.copy(file, root.resolve("config.legacy-" + suffix + ".json"),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ignored) {
        }
    }

    private static void preserveCorrupt(Path file) {
        try {
            String suffix = Long.toString(Instant.now().toEpochMilli());
            Files.move(file, file.resolveSibling("config.corrupt-" + suffix + ".json"));
        } catch (Exception ignored) {
        }
    }
}
