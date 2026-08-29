package com.yuan.live2d.client.live2d;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class Live2DConfig {
    public static final int FORMAT = 2;

    public enum Visibility { ALWAYS, MAIN_HAND, EITHER_HAND, HOTBAR, INVENTORY }
    public enum PerformancePolicy { WARN_ONLY, AUTO_DEGRADE, STRICT_LIMIT }
    public enum HudPreset { CLEAN, CROSSHAIR, VANILLA, ALL }

    public static final class ModelOverride {
        public Boolean enabled;
        public Visibility visibility;
        public Float anchorX;
        public Float anchorY;
        public Float offsetX;
        public Float offsetY;
        public Float scale;
        public Float opacity;
        public Integer hideDelayTicks;

        public boolean hasAny() {
            return enabled != null || visibility != null || anchorX != null || anchorY != null
                    || offsetX != null || offsetY != null || scale != null || opacity != null
                    || hideDelayTicks != null;
        }

        public ModelOverride copy() {
            ModelOverride copy = new ModelOverride();
            copy.enabled = enabled;
            copy.visibility = visibility;
            copy.anchorX = anchorX;
            copy.anchorY = anchorY;
            copy.offsetX = offsetX;
            copy.offsetY = offsetY;
            copy.scale = scale;
            copy.opacity = opacity;
            copy.hideDelayTicks = hideDelayTicks;
            return copy;
        }

        public boolean sameSettings(ModelOverride other) {
            return other != null
                    && Objects.equals(enabled, other.enabled)
                    && visibility == other.visibility
                    && Objects.equals(anchorX, other.anchorX)
                    && Objects.equals(anchorY, other.anchorY)
                    && Objects.equals(offsetX, other.offsetX)
                    && Objects.equals(offsetY, other.offsetY)
                    && Objects.equals(scale, other.scale)
                    && Objects.equals(opacity, other.opacity)
                    && Objects.equals(hideDelayTicks, other.hideDelayTicks);
        }

        public void sanitize() {
            anchorX = clampNullable(anchorX, 0, 1);
            anchorY = clampNullable(anchorY, 0, 1);
            offsetX = clampNullable(offsetX, -16384, 16384);
            offsetY = clampNullable(offsetY, -16384, 16384);
            scale = clampNullable(scale, .05f, 5);
            opacity = clampNullable(opacity, 0, 1);
            if (hideDelayTicks != null) hideDelayTicks = Math.max(0, Math.min(hideDelayTicks, 1200));
        }

        private static Float clampNullable(Float value, float min, float max) {
            if (value == null) return null;
            return Float.isFinite(value) ? Math.max(min, Math.min(value, max)) : min;
        }
    }

    public static final class Global {
        public boolean enabled = true;
        public String selectedModelId = "";
        public Visibility visibility = Visibility.ALWAYS;
        public float anchorX = .8f;
        public float anchorY = .55f;
        public float offsetX = -24;
        public float offsetY = -24;
        public float scale = .45f;
        public float opacity = 1;
        public int hideDelayTicks = 5;

        public Global copy() {
            Global copy = new Global();
            copy.enabled = enabled;
            copy.selectedModelId = selectedModelId;
            copy.visibility = visibility;
            copy.anchorX = anchorX;
            copy.anchorY = anchorY;
            copy.offsetX = offsetX;
            copy.offsetY = offsetY;
            copy.scale = scale;
            copy.opacity = opacity;
            copy.hideDelayTicks = hideDelayTicks;
            return copy;
        }

        public boolean sameSettings(Global other) {
            return other != null && enabled == other.enabled
                    && Objects.equals(selectedModelId, other.selectedModelId)
                    && visibility == other.visibility
                    && Float.compare(anchorX, other.anchorX) == 0
                    && Float.compare(anchorY, other.anchorY) == 0
                    && Float.compare(offsetX, other.offsetX) == 0
                    && Float.compare(offsetY, other.offsetY) == 0
                    && Float.compare(scale, other.scale) == 0
                    && Float.compare(opacity, other.opacity) == 0
                    && hideDelayTicks == other.hideDelayTicks;
        }

        public void sanitize() {
            if (selectedModelId == null) selectedModelId = "";
            if (visibility == null) visibility = Visibility.ALWAYS;
            anchorX = clamp(anchorX, 0, 1);
            anchorY = clamp(anchorY, 0, 1);
            offsetX = clamp(offsetX, -16384, 16384);
            offsetY = clamp(offsetY, -16384, 16384);
            scale = clamp(scale, .05f, 5);
            opacity = clamp(opacity, 0, 1);
            hideDelayTicks = Math.max(0, Math.min(hideDelayTicks, 1200));
        }
    }

    public static final class Hud {
        public HudPreset preset = HudPreset.CLEAN;
        public boolean crosshair;
        public boolean hotbar;
        public boolean status;
        public boolean experience;
        public boolean effects;
        public boolean bossBars;
        public boolean scoreboard;
        public boolean chat;
        public boolean playerList;
        public boolean messages;
        public boolean thirdParty;

        public Hud copy() {
            Hud copy = new Hud();
            copy.preset = preset;
            copy.crosshair = crosshair;
            copy.hotbar = hotbar;
            copy.status = status;
            copy.experience = experience;
            copy.effects = effects;
            copy.bossBars = bossBars;
            copy.scoreboard = scoreboard;
            copy.chat = chat;
            copy.playerList = playerList;
            copy.messages = messages;
            copy.thirdParty = thirdParty;
            return copy;
        }

        public boolean sameSettings(Hud other) {
            return other != null && preset == other.preset && crosshair == other.crosshair
                    && hotbar == other.hotbar && status == other.status && experience == other.experience
                    && effects == other.effects && bossBars == other.bossBars && scoreboard == other.scoreboard
                    && chat == other.chat && playerList == other.playerList && messages == other.messages
                    && thirdParty == other.thirdParty;
        }

        public void applyPreset(HudPreset preset) {
            this.preset = preset;
            crosshair = preset != HudPreset.CLEAN;
            hotbar = preset == HudPreset.VANILLA || preset == HudPreset.ALL;
            status = hotbar;
            experience = hotbar;
            effects = hotbar;
            bossBars = hotbar;
            scoreboard = hotbar;
            chat = preset == HudPreset.ALL;
            playerList = hotbar;
            messages = hotbar;
            thirdParty = preset == HudPreset.ALL;
        }

        public void sanitize() {
            if (preset == null) preset = HudPreset.CLEAN;
        }
    }

    public static final class Performance {
        public PerformancePolicy policy = PerformancePolicy.AUTO_DEGRADE;
        public int maxVisibleInstances = 4;
        public int textureMemoryBudgetMiB = 768;

        public Performance copy() {
            Performance copy = new Performance();
            copy.policy = policy;
            copy.maxVisibleInstances = maxVisibleInstances;
            copy.textureMemoryBudgetMiB = textureMemoryBudgetMiB;
            return copy;
        }

        public boolean sameSettings(Performance other) {
            return other != null && policy == other.policy
                    && maxVisibleInstances == other.maxVisibleInstances
                    && textureMemoryBudgetMiB == other.textureMemoryBudgetMiB;
        }

        public void sanitize() {
            if (policy == null) policy = PerformancePolicy.AUTO_DEGRADE;
            maxVisibleInstances = Math.max(1, Math.min(maxVisibleInstances, 4096));
            textureMemoryBudgetMiB = Math.max(16, Math.min(textureMemoryBudgetMiB, 262144));
        }
    }

    public static final class Interaction {
        public boolean viewFollowEnabled = true;
        public float viewFollowStrength = .6f;
        public boolean randomMotionEnabled = true;
        public float randomMotionIntervalSeconds = 12f;
        public boolean randomExpressionEnabled = true;
        public boolean clickReactionEnabled = true;
        public float clickReactionChance = .6f;

        public Interaction copy() {
            Interaction copy = new Interaction();
            copy.viewFollowEnabled = viewFollowEnabled;
            copy.viewFollowStrength = viewFollowStrength;
            copy.randomMotionEnabled = randomMotionEnabled;
            copy.randomMotionIntervalSeconds = randomMotionIntervalSeconds;
            copy.randomExpressionEnabled = randomExpressionEnabled;
            copy.clickReactionEnabled = clickReactionEnabled;
            copy.clickReactionChance = clickReactionChance;
            return copy;
        }

        public boolean sameSettings(Interaction other) {
            return other != null
                    && viewFollowEnabled == other.viewFollowEnabled
                    && Float.compare(viewFollowStrength, other.viewFollowStrength) == 0
                    && randomMotionEnabled == other.randomMotionEnabled
                    && Float.compare(randomMotionIntervalSeconds, other.randomMotionIntervalSeconds) == 0
                    && randomExpressionEnabled == other.randomExpressionEnabled
                    && clickReactionEnabled == other.clickReactionEnabled
                    && Float.compare(clickReactionChance, other.clickReactionChance) == 0;
        }

        public void sanitize() {
            viewFollowStrength = clamp(viewFollowStrength, 0f, 1f);
            randomMotionIntervalSeconds = clamp(randomMotionIntervalSeconds, 5f, 60f);
            clickReactionChance = clamp(clickReactionChance, 0f, 1f);
        }
    }

    public static final class Physics {
        public float amplitude = 1f;
        public boolean interactionEnabled = true;
        public float strength = .3f;
        public boolean edgeSquash = true;

        public Physics copy() {
            Physics copy = new Physics();
            copy.amplitude = amplitude;
            copy.interactionEnabled = interactionEnabled;
            copy.strength = strength;
            copy.edgeSquash = edgeSquash;
            return copy;
        }

        public boolean sameSettings(Physics other) {
            return other != null
                    && Float.compare(amplitude, other.amplitude) == 0
                    && interactionEnabled == other.interactionEnabled
                    && Float.compare(strength, other.strength) == 0
                    && edgeSquash == other.edgeSquash;
        }

        public void sanitize() {
            amplitude = clamp(amplitude, 0f, 2f);
            strength = clamp(strength, 0f, 1f);
        }
    }

    public static final class Render {
        public boolean shadowEnabled = true;
        public int switchFadeTicks = 5;

        public Render copy() {
            Render copy = new Render();
            copy.shadowEnabled = shadowEnabled;
            copy.switchFadeTicks = switchFadeTicks;
            return copy;
        }

        public boolean sameSettings(Render other) {
            return other != null && shadowEnabled == other.shadowEnabled
                    && switchFadeTicks == other.switchFadeTicks;
        }

        public void sanitize() {
            switchFadeTicks = Math.max(0, Math.min(switchFadeTicks, 20));
        }
    }

    public int format = FORMAT;
    public Global global = new Global();
    public Hud hud = new Hud();
    public Performance performance = new Performance();
    public Interaction interaction = new Interaction();
    public Physics physics = new Physics();
    public Render render = new Render();
    public Map<String, ModelOverride> modelOverrides = new HashMap<>();

    public static Live2DConfig defaults() { return new Live2DConfig(); }

    public Live2DConfig copy() {
        Live2DConfig copy = new Live2DConfig();
        copy.format = format;
        copy.global = global == null ? new Global() : global.copy();
        copy.hud = hud == null ? new Hud() : hud.copy();
        copy.performance = performance == null ? new Performance() : performance.copy();
        copy.interaction = interaction == null ? new Interaction() : interaction.copy();
        copy.physics = physics == null ? new Physics() : physics.copy();
        copy.render = render == null ? new Render() : render.copy();
        copy.modelOverrides = new HashMap<>();
        if (modelOverrides != null) for (var entry : modelOverrides.entrySet()) {
            copy.modelOverrides.put(entry.getKey(), entry.getValue() == null ? null : entry.getValue().copy());
        }
        return copy;
    }

    public Global effectiveGlobal(String modelId) {
        Global effective = global.copy();
        if (modelId == null || modelId.isEmpty()) return effective;
        ModelOverride override = modelOverrides == null ? null : modelOverrides.get(modelId);
        if (override == null) return effective;
        if (override.enabled != null) effective.enabled = override.enabled;
        if (override.visibility != null) effective.visibility = override.visibility;
        if (override.anchorX != null) effective.anchorX = override.anchorX;
        if (override.anchorY != null) effective.anchorY = override.anchorY;
        if (override.offsetX != null) effective.offsetX = override.offsetX;
        if (override.offsetY != null) effective.offsetY = override.offsetY;
        if (override.scale != null) effective.scale = override.scale;
        if (override.opacity != null) effective.opacity = override.opacity;
        if (override.hideDelayTicks != null) effective.hideDelayTicks = override.hideDelayTicks;
        effective.sanitize();
        return effective;
    }

    public boolean sameSettings(Live2DConfig other) {
        if (other == null) return false;
        if (!mapsEqual(modelOverrides, other.modelOverrides)) return false;
        return format == other.format && global.sameSettings(other.global) && hud.sameSettings(other.hud)
                && performance.sameSettings(other.performance)
                && interaction.sameSettings(other.interaction) && physics.sameSettings(other.physics)
                && render.sameSettings(other.render);
    }

    private static boolean mapsEqual(Map<String, ModelOverride> left, Map<String, ModelOverride> right) {
        if (left == null || right == null) return left == right;
        if (left.size() != right.size()) return false;
        for (var entry : left.entrySet()) {
            ModelOverride other = right.get(entry.getKey());
            if (other == null || entry.getValue() == null || !entry.getValue().sameSettings(other)) return false;
        }
        return true;
    }

    public Live2DConfig sanitize() {
        format = FORMAT;
        if (global == null) global = new Global();
        if (hud == null) hud = new Hud();
        if (performance == null) performance = new Performance();
        if (interaction == null) interaction = new Interaction();
        if (physics == null) physics = new Physics();
        if (render == null) render = new Render();
        global.sanitize();
        hud.sanitize();
        performance.sanitize();
        interaction.sanitize();
        physics.sanitize();
        render.sanitize();
        if (modelOverrides == null) modelOverrides = new HashMap<>();
        modelOverrides.values().removeIf(value -> value == null || !value.hasAny());
        modelOverrides.values().forEach(ModelOverride::sanitize);
        return this;
    }

    private static float clamp(float value, float min, float max) {
        return Float.isFinite(value) ? Math.max(min, Math.min(value, max)) : min;
    }

    public String toJsonPreview() {
        return String.format(Locale.ROOT, "v%d %s %.2f/%.2f", format,
                global == null ? "" : global.selectedModelId,
                global == null ? 0 : global.scale,
                global == null ? 0 : global.opacity);
    }
}
