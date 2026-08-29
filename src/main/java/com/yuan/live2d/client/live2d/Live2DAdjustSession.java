package com.yuan.live2d.client.live2d;

import java.io.IOException;
import java.util.Objects;

public final class Live2DAdjustSession {
    public enum Target { GLOBAL, OVERRIDE }

    @FunctionalInterface
    public interface Saver {
        void save(Live2DConfig config) throws IOException;
    }

    private final Live2DConfig config;
    private final Live2DModelManifest model;
    private final Saver saver;
    private final Target entryTarget;
    private float entryOffsetX;
    private float entryOffsetY;
    private final float entryScale;

    private Target target;
    private float offsetX;
    private float offsetY;
    private float scale;
    private float appliedOffsetX;
    private float appliedOffsetY;
    private float appliedScale;
    private boolean dirty;
    private boolean confirmPending;
    private String error = "";

    public Live2DAdjustSession(Live2DConfig config, Live2DModelManifest model, Saver saver) {
        this.config = Objects.requireNonNull(config).copy().sanitize();
        this.model = Objects.requireNonNull(model);
        this.saver = Objects.requireNonNull(saver);
        this.entryTarget = this.config.modelOverrides.containsKey(model.id())
                ? Target.OVERRIDE : Target.GLOBAL;
        float[] initial = read(entryTarget);
        this.entryOffsetX = initial[0];
        this.entryOffsetY = initial[1];
        this.entryScale = initial[2];
        this.target = entryTarget;
        this.offsetX = entryOffsetX;
        this.offsetY = entryOffsetY;
        this.scale = entryScale;
        this.appliedOffsetX = entryOffsetX;
        this.appliedOffsetY = entryOffsetY;
        this.appliedScale = entryScale;
    }

    public Target target() { return target; }
    public float offsetX() { return offsetX; }
    public float offsetY() { return offsetY; }
    public float scale() { return scale; }
    public boolean isDirty() { return dirty; }
    public boolean confirmPending() { return confirmPending; }
    public String error() { return error; }
    public String modelId() { return model.id(); }

    public void setTarget(Target value) {
        if (value == null || value == target) return;
        target = value;
        refreshDirty();
    }

    public void setOffsetX(float value) { offsetX = value; refreshDirty(); }
    public void setOffsetY(float value) { offsetY = value; refreshDirty(); }
    public void setScale(float value) {
        scale = Live2DAdjustMath.clampScale(value);
        refreshDirty();
    }

    public void nudge(float dx, float dy, float dScale) {
        offsetX += dx;
        offsetY += dy;
        scale = Live2DAdjustMath.clampScale(scale + dScale);
        refreshDirty();
    }

    public void resetToEntry() {
        offsetX = entryOffsetX;
        offsetY = entryOffsetY;
        scale = entryScale;
        refreshDirty();
    }

    public void shiftEntry(float dx, float dy) {
        entryOffsetX += dx;
        entryOffsetY += dy;
        offsetX += dx;
        offsetY += dy;
        appliedOffsetX += dx;
        appliedOffsetY += dy;
    }

    public boolean apply() {
        try {
            write(target);
            saver.save(config);
            appliedOffsetX = offsetX;
            appliedOffsetY = offsetY;
            appliedScale = scale;
            confirmPending = false;
            error = "";
            refreshDirty();
            return true;
        } catch (IOException | RuntimeException failure) {
            error = "配置保存失败: " + failure.getMessage();
            return false;
        }
    }

    public boolean save() {
        return apply();
    }

    public boolean requestBack() {
        if (dirty) {
            confirmPending = true;
            return false;
        }
        return true;
    }

    public void confirmDiscard() {
        confirmPending = false;
    }

    public void cancelConfirm() {
        confirmPending = false;
    }

    public Live2DConfig draftConfig() {
        Live2DConfig draft = config.copy().sanitize();
        if (target == Target.OVERRIDE) {
            Live2DConfig.ModelOverride override = draft.modelOverrides
                    .computeIfAbsent(model.id(), key -> new Live2DConfig.ModelOverride());
            override.offsetX = offsetX;
            override.offsetY = offsetY;
            override.scale = scale;
        } else {
            Live2DConfig.ModelOverride override = draft.modelOverrides.get(model.id());
            if (override != null) {
                override.offsetX = null;
                override.offsetY = null;
                override.scale = null;
                if (!override.hasAny()) draft.modelOverrides.remove(model.id());
            }
            draft.global.offsetX = offsetX;
            draft.global.offsetY = offsetY;
            draft.global.scale = scale;
        }
        return draft;
    }

    private float[] read(Target source) {
        if (source == Target.OVERRIDE) {
            Live2DConfig.ModelOverride override = config.modelOverrides.get(model.id());
            if (override != null) {
                return new float[] {
                        override.offsetX == null ? config.global.offsetX : override.offsetX,
                        override.offsetY == null ? config.global.offsetY : override.offsetY,
                        override.scale == null ? config.global.scale : override.scale
                };
            }
        }
        return new float[] {config.global.offsetX, config.global.offsetY, config.global.scale};
    }

    private void write(Target destination) {
        if (destination == Target.OVERRIDE) {
            Live2DConfig.ModelOverride override = config.modelOverrides
                    .computeIfAbsent(model.id(), key -> new Live2DConfig.ModelOverride());
            override.offsetX = offsetX;
            override.offsetY = offsetY;
            override.scale = scale;
            if (!override.hasAny()) config.modelOverrides.remove(model.id());
        } else {
            Live2DConfig.ModelOverride override = config.modelOverrides.get(model.id());
            if (override != null) {
                override.offsetX = null;
                override.offsetY = null;
                override.scale = null;
                if (!override.hasAny()) config.modelOverrides.remove(model.id());
            }
            config.global.offsetX = offsetX;
            config.global.offsetY = offsetY;
            config.global.scale = scale;
        }
        config.sanitize();
    }

    private void refreshDirty() {
        dirty = Float.compare(offsetX, appliedOffsetX) != 0
                || Float.compare(offsetY, appliedOffsetY) != 0
                || Float.compare(scale, appliedScale) != 0;
    }
}
