package com.yuan.live2d.client.gui;

import com.yuan.live2d.client.live2d.Live2DClientState;
import com.yuan.live2d.client.live2d.Live2DConfig;
import com.yuan.live2d.client.live2d.Live2DConfigStore;
import com.yuan.live2d.client.live2d.Live2DHudRenderer;
import com.yuan.live2d.client.live2d.Live2DModelManifest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public final class Live2DOverrideScreen extends Screen {
    private static final int GOLD = 0xFFE1B65A;
    private static final int TEXT = 0xFFE5E0E8;
    private static final int MUTED = 0xFF948A9E;
    private static final int PANEL = 0xE6121018;
    private static final int PANEL_ALT = 0xE61A1620;
    private static final int BORDER = 0xFF3A3042;

    private static final int FIELD_ENABLED = 0;
    private static final int FIELD_VISIBILITY = 1;
    private static final int FIELD_ANCHOR_X = 2;
    private static final int FIELD_ANCHOR_Y = 3;
    private static final int FIELD_OFFSET_X = 4;
    private static final int FIELD_OFFSET_Y = 5;
    private static final int FIELD_SCALE = 6;
    private static final int FIELD_OPACITY = 7;
    private static final int FIELD_HIDE_DELAY = 8;

    private static final String[] FIELD_LABELS = {"启用", "可见性", "水平位置", "垂直位置",
            "水平偏移", "垂直偏移", "缩放", "透明度", "隐藏延迟"};
    private static final int[] SLIDER_FIELDS = {FIELD_ANCHOR_X, FIELD_ANCHOR_Y, FIELD_OFFSET_X,
            FIELD_OFFSET_Y, FIELD_SCALE, FIELD_OPACITY};
    private static final float[] SLIDER_MIN = {0, 0, -4096, -4096, .05f, 0};
    private static final float[] SLIDER_MAX = {1, 1, 4096, 4096, 2, 1};
    private static final float[] SLIDER_STEP = {.01f, .01f, 1, 1, .01f, .01f};
    private static final String[] VISIBILITY_LABELS = {"始终", "主手", "主/副手", "快捷栏", "物品栏"};

    private final Live2DConfig config;
    private final Live2DConfigStore store;
    private final Live2DModelManifest model;
    private Live2DOverrideLayout layout;
    private int scroll;
    private int draggingSlider = -1;
    private boolean hideDelayEditing;
    private String hideDelayText = "";
    private String error = "";

    public Live2DOverrideScreen(Live2DConfig config, Live2DConfigStore store, Live2DModelManifest model) {
        super(Component.literal("模型覆盖"));
        this.config = config;
        this.store = store;
        this.model = model;
    }

    @Override
    protected void init() {
        hideDelayText = Integer.toString(effectiveHideDelay());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        layout = Live2DOverrideLayout.of(width, height, scroll);
        scroll = layout.scroll();
        renderShell(g);
        Live2DConfigLayout.Bounds panel = layout.leftPanel();
        g.enableScissor(panel.left(), panel.top(), panel.right(), panel.bottom());
        try {
            renderRows(g, font);
            renderActions(g, font);
            renderStatus(g);
        } finally {
            g.disableScissor();
        }
        renderPreviewPanel(g, font);
        renderNativePreview(g);
    }

    private void renderShell(GuiGraphics g) {
        g.fill(0, 0, width, 28, PANEL);
        g.drawString(font, "模型覆盖", 12, 9, GOLD);
        String close = "Esc 返回配置";
        g.drawString(font, close, width - font.width(close) - 12, 9, MUTED);
    }

    private void renderRows(GuiGraphics g, Font font) {
        List<Live2DConfigLayout.Bounds> rows = layout.rows();
        for (int field = 0; field < rows.size(); field++) {
            Live2DConfigLayout.Bounds row = rows.get(field);
            g.fill(row.left(), row.top(), row.right(), row.bottom(), PANEL_ALT);
            Live2DConfigLayout.Bounds mode = layout.modeButtons().get(field);
            boolean overridden = isOverridden(field);
            g.fill(mode.left(), mode.top(), mode.right(), mode.bottom(), overridden ? 0xFF4B3820 : PANEL_ALT);
            g.drawCenteredString(font, overridden ? "模型" : "全局", (mode.left() + mode.right()) / 2, mode.top() + 3,
                    overridden ? GOLD : MUTED);
            g.drawString(font, FIELD_LABELS[field], mode.right() + 6, row.top() + 7, TEXT);
            renderControl(g, font, field);
        }
    }

    private void renderControl(GuiGraphics g, Font font, int field) {
        Live2DConfigLayout.Bounds control = layout.controls().get(field);
        if (field == FIELD_ENABLED) {
            boolean enabled = effectiveEnabled();
            g.fill(control.left(), control.top(), control.right(), control.bottom(),
                    enabled ? 0xFF4B3820 : PANEL_ALT);
            g.drawString(font, (enabled ? "■ 启用" : "□ 禁用"), control.left() + 4, control.top() + 3,
                    enabled ? GOLD : MUTED);
            return;
        }
        if (field == FIELD_VISIBILITY) {
            Live2DConfig.Visibility current = effectiveVisibility();
            for (int i = 0; i < layout.visibilityButtons().size(); i++) {
                Live2DConfigLayout.Bounds button = layout.visibilityButtons().get(i);
                boolean active = current == Live2DConfig.Visibility.values()[i];
                g.fill(button.left(), button.top(), button.right(), button.bottom(), active ? 0xFF4B3820 : PANEL_ALT);
                g.drawCenteredString(font, VISIBILITY_LABELS[i], (button.left() + button.right()) / 2,
                        button.top() + 2, active ? GOLD : MUTED);
            }
            return;
        }
        if (field == FIELD_HIDE_DELAY) {
            g.fill(control.left(), control.top(), control.right(), control.bottom(), 0xFF0F0E16);
            g.fill(control.left(), control.top(), control.right(), control.top() + 1,
                    hideDelayEditing ? GOLD : BORDER);
            g.fill(control.left(), control.bottom() - 1, control.right(), control.bottom(),
                    hideDelayEditing ? GOLD : BORDER);
            g.fill(control.left(), control.top(), control.left() + 1, control.bottom(),
                    hideDelayEditing ? GOLD : BORDER);
            g.fill(control.right() - 1, control.top(), control.right(), control.bottom(),
                    hideDelayEditing ? GOLD : BORDER);
            g.drawString(font, hideDelayText, control.left() + 6, control.top() + 5, TEXT);
            return;
        }
        int slider = sliderIndex(field);
        float value = effectiveFloat(field);
        int trackLeft = control.left() + 4;
        int trackRight = control.right() - 4;
        int trackY = control.bottom() - 8;
        g.fill(trackLeft, trackY, trackRight, trackY + 3, 0xFF3A3042);
        float ratio = Live2DConfigLayout.ratio(SLIDER_MIN[slider], SLIDER_MAX[slider], value);
        int knobX = trackLeft + (int) (ratio * (trackRight - trackLeft));
        g.fill(knobX - 3, trackY - 3, knobX + 3, trackY + 6, GOLD);
        String valueText = formatValue(slider, value);
        g.drawString(font, valueText, trackRight - font.width(valueText), control.top() + 1, GOLD);
    }

    private void renderActions(GuiGraphics g, Font font) {
        drawAction(g, font, layout.copyFromGlobal(), "从全局复制");
        drawAction(g, font, layout.clearOverride(), "清除覆盖");
        drawAction(g, font, layout.back(), "返回");
    }

    private void drawAction(GuiGraphics g, Font font, Live2DConfigLayout.Bounds bounds, String label) {
        g.fill(bounds.left(), bounds.top(), bounds.right(), bounds.bottom(), PANEL_ALT);
        g.drawCenteredString(font, label, (bounds.left() + bounds.right()) / 2, bounds.top() + 5, TEXT);
    }

    private void renderPreviewPanel(GuiGraphics g, Font font) {
        Live2DConfigLayout.Bounds preview = layout.preview();
        g.drawString(font, "实时预览", preview.left(), preview.top() - 24, GOLD);
        g.fillGradient(preview.left(), preview.top(), preview.right(), preview.bottom(), 0xFF17131D, 0xFF0B0910);
        g.fill(preview.left(), preview.top(), preview.right(), preview.top() + 1, BORDER);
        g.fill(preview.left(), preview.bottom() - 1, preview.right(), preview.bottom(), BORDER);
        g.fill(preview.left(), preview.top(), preview.left() + 1, preview.bottom(), BORDER);
        g.fill(preview.right() - 1, preview.top(), preview.right(), preview.bottom(), BORDER);
        for (int x = preview.left() + 24; x < preview.right(); x += 24)
            g.fill(x, preview.top(), x + 1, preview.bottom(), 0x10FFFFFF);
        for (int y = preview.top() + 24; y < preview.bottom(); y += 24)
            g.fill(preview.left(), y, preview.right(), y + 1, 0x10FFFFFF);
        g.fill((preview.left() + preview.right()) / 2, preview.top(), (preview.left() + preview.right()) / 2 + 1,
                preview.bottom(), 0x22E1B65A);
        g.fill(preview.left(), (preview.top() + preview.bottom()) / 2, preview.right(),
                (preview.top() + preview.bottom()) / 2 + 1, 0x22E1B65A);
        Live2DClientState.ModelStatus status = Live2DHudRenderer.modelStatus();
        if (status.activeModelId() == null || status.activeModelId().isEmpty()) {
            g.drawCenteredString(font, "未选择模型", (preview.left() + preview.right()) / 2,
                    (preview.top() + preview.bottom()) / 2 - 6, MUTED);
        } else if (!status.error().isEmpty()) {
            g.drawCenteredString(font, "模型加载失败", (preview.left() + preview.right()) / 2,
                    (preview.top() + preview.bottom()) / 2 - 6, 0xFFFF8E78);
        }
    }

    private void renderNativePreview(GuiGraphics g) {
        Live2DConfigLayout.Bounds preview = layout.preview();
        Minecraft minecraft = Minecraft.getInstance();
        int framebufferWidth = minecraft.getWindow().getWidth();
        int framebufferHeight = minecraft.getWindow().getHeight();
        if (preview.width() <= 1 || preview.height() <= 1 || framebufferWidth <= 0 || framebufferHeight <= 0) return;
        Live2DConfig previewConfig = config.copy();
        previewConfig.global.selectedModelId = model.id();
        Live2DConfig.Global effective = previewConfig.effectiveGlobal(model.id());
        Live2DConfigScreen.PreviewPosition position = Live2DConfigScreen.previewPosition(
                effective.anchorX, effective.anchorY, effective.offsetX, effective.offsetY,
                preview.width(), preview.height(), width, height);
        float centerX = preview.left() + position.x();
        float centerY = preview.top() + position.y();
        float scale = preview.height() * .82f * effective.scale / .45f;
        g.enableScissor(preview.left(), preview.top(), preview.right(), preview.bottom());
        try {
            Live2DHudRenderer.renderPreview(g, new Live2DClientState.PreviewFrame(
                    previewConfig, framebufferWidth, framebufferHeight,
                    centerX * framebufferWidth / Math.max(1f, width),
                    centerY * framebufferHeight / Math.max(1f, height),
                    scale * framebufferHeight / Math.max(1f, height),
                    effective.opacity));
        } finally {
            g.disableScissor();
        }
    }

    private void renderStatus(GuiGraphics g) {
        Live2DConfigLayout.Bounds status = layout.status();
        boolean hasOverride = config.modelOverrides.containsKey(model.id());
        String statusText = model.displayName() + (hasOverride ? "  ·  有覆盖" : "  ·  全部继承全局");
        if (!error.isEmpty()) statusText = error;
        g.drawString(font, statusText, status.left() + 4, status.top() + 2,
                error.isEmpty() ? GOLD : 0xFFFF8E78);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        if (!layout.leftPanel().contains(mouseX, mouseY)) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (layout.copyFromGlobal().contains(mouseX, mouseY)) { copyFromGlobal(); return true; }
        if (layout.clearOverride().contains(mouseX, mouseY)) { clearOverride(); return true; }
        if (layout.back().contains(mouseX, mouseY)) { backToConfig(); return true; }
        for (int field = 0; field < Live2DOverrideLayout.FIELD_COUNT; field++) {
            if (layout.modeButtons().get(field).contains(mouseX, mouseY)) { toggleMode(field); return true; }
            if (!layout.controls().get(field).contains(mouseX, mouseY)) continue;
            if (field == FIELD_ENABLED) {
                setOverrideValue(field, !effectiveEnabled());
                saveConfigAndPreview();
                return true;
            }
            if (field == FIELD_VISIBILITY) {
                for (int i = 0; i < layout.visibilityButtons().size(); i++) {
                    if (layout.visibilityButtons().get(i).contains(mouseX, mouseY)) {
                        setOverrideValue(field, Live2DConfig.Visibility.values()[i]);
                        saveConfigAndPreview();
                        return true;
                    }
                }
                return true;
            }
            if (field == FIELD_HIDE_DELAY) { hideDelayEditing = true; return true; }
            draggingSlider = sliderIndex(field);
            setSliderFromMouse(draggingSlider, mouseX);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingSlider >= 0) {
            setSliderFromMouse(draggingSlider, mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingSlider = -1;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scroll = Math.max(0, Math.min(scroll - (int) delta * 10, layout.maxScroll()));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (hideDelayEditing) {
                hideDelayText = Integer.toString(effectiveHideDelay());
                hideDelayEditing = false;
            } else {
                backToConfig();
            }
            return true;
        }
        if (hideDelayEditing) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                commitHideDelay();
                hideDelayEditing = false;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !hideDelayText.isEmpty()) {
                hideDelayText = hideDelayText.substring(0, hideDelayText.length() - 1);
                updateHideDelayFromText();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (hideDelayEditing && Character.isDigit(codePoint)) {
            String next = hideDelayText + codePoint;
            if (next.length() <= 5) {
                hideDelayText = next;
                updateHideDelayFromText();
            }
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    private boolean isOverridden(int field) { return overrideValue(field) != null; }

    private Object overrideValue(int field) {
        Live2DConfig.ModelOverride override = config.modelOverrides.get(model.id());
        if (override == null) return null;
        return switch (field) {
            case FIELD_ENABLED -> override.enabled;
            case FIELD_VISIBILITY -> override.visibility;
            case FIELD_ANCHOR_X -> override.anchorX;
            case FIELD_ANCHOR_Y -> override.anchorY;
            case FIELD_OFFSET_X -> override.offsetX;
            case FIELD_OFFSET_Y -> override.offsetY;
            case FIELD_SCALE -> override.scale;
            case FIELD_OPACITY -> override.opacity;
            default -> override.hideDelayTicks;
        };
    }

    private Object globalValue(int field) {
        return switch (field) {
            case FIELD_ENABLED -> config.global.enabled;
            case FIELD_VISIBILITY -> config.global.visibility;
            case FIELD_ANCHOR_X -> config.global.anchorX;
            case FIELD_ANCHOR_Y -> config.global.anchorY;
            case FIELD_OFFSET_X -> config.global.offsetX;
            case FIELD_OFFSET_Y -> config.global.offsetY;
            case FIELD_SCALE -> config.global.scale;
            case FIELD_OPACITY -> config.global.opacity;
            default -> config.global.hideDelayTicks;
        };
    }

    private void setOverrideValue(int field, Object value) {
        Live2DConfig.ModelOverride override = config.modelOverrides.get(model.id());
        if (override == null) {
            if (value == null) return;
            override = new Live2DConfig.ModelOverride();
            config.modelOverrides.put(model.id(), override);
        }
        switch (field) {
            case FIELD_ENABLED -> override.enabled = (Boolean) value;
            case FIELD_VISIBILITY -> override.visibility = (Live2DConfig.Visibility) value;
            case FIELD_ANCHOR_X -> override.anchorX = (Float) value;
            case FIELD_ANCHOR_Y -> override.anchorY = (Float) value;
            case FIELD_OFFSET_X -> override.offsetX = (Float) value;
            case FIELD_OFFSET_Y -> override.offsetY = (Float) value;
            case FIELD_SCALE -> override.scale = (Float) value;
            case FIELD_OPACITY -> override.opacity = (Float) value;
            default -> override.hideDelayTicks = (Integer) value;
        }
        if (!override.hasAny()) config.modelOverrides.remove(model.id());
    }

    private void toggleMode(int field) {
        if (isOverridden(field)) setOverrideValue(field, null);
        else setOverrideValue(field, globalValue(field));
        if (field == FIELD_HIDE_DELAY) hideDelayText = Integer.toString(effectiveHideDelay());
        saveConfigAndPreview();
    }

    private void copyFromGlobal() {
        for (int field = 0; field < Live2DOverrideLayout.FIELD_COUNT; field++) {
            setOverrideValue(field, globalValue(field));
        }
        hideDelayText = Integer.toString(effectiveHideDelay());
        saveConfigAndPreview();
    }

    private void clearOverride() {
        config.modelOverrides.remove(model.id());
        hideDelayText = Integer.toString(config.global.hideDelayTicks);
        if (saveConfigAndPreview()) backToConfig();
    }

    private void backToConfig() {
        Minecraft.getInstance().setScreen(new Live2DConfigScreen());
    }

    private boolean effectiveEnabled() {
        return isOverridden(FIELD_ENABLED) ? (Boolean) overrideValue(FIELD_ENABLED) : config.global.enabled;
    }

    private Live2DConfig.Visibility effectiveVisibility() {
        return isOverridden(FIELD_VISIBILITY)
                ? (Live2DConfig.Visibility) overrideValue(FIELD_VISIBILITY) : config.global.visibility;
    }

    private float effectiveFloat(int field) {
        return isOverridden(field) ? (Float) overrideValue(field) : globalFloat(field);
    }

    private int effectiveHideDelay() {
        return isOverridden(FIELD_HIDE_DELAY)
                ? (Integer) overrideValue(FIELD_HIDE_DELAY) : config.global.hideDelayTicks;
    }

    private float globalFloat(int field) {
        return switch (field) {
            case FIELD_ANCHOR_X -> config.global.anchorX;
            case FIELD_ANCHOR_Y -> config.global.anchorY;
            case FIELD_OFFSET_X -> config.global.offsetX;
            case FIELD_OFFSET_Y -> config.global.offsetY;
            case FIELD_SCALE -> config.global.scale;
            default -> config.global.opacity;
        };
    }

    private int sliderIndex(int field) {
        for (int i = 0; i < SLIDER_FIELDS.length; i++) {
            if (SLIDER_FIELDS[i] == field) return i;
        }
        throw new IllegalArgumentException("Not a slider field: " + field);
    }

    private void setSliderFromMouse(int slider, double mouseX) {
        Live2DConfigLayout.Bounds control = layout.controls().get(SLIDER_FIELDS[slider]);
        float value = Live2DConfigLayout.valueAt(SLIDER_MIN[slider], SLIDER_MAX[slider], SLIDER_STEP[slider],
                control.left() + 4, Math.max(1, control.width() - 8), mouseX);
        float effective = effectiveFloat(SLIDER_FIELDS[slider]);
        if (Float.compare(value, effective) != 0) {
            setOverrideValue(SLIDER_FIELDS[slider], value);
            saveConfigAndPreview();
        }
    }

    private void updateHideDelayFromText() {
        try {
            int value = Integer.parseInt(hideDelayText);
            value = Math.max(0, Math.min(value, 1200));
            setOverrideValue(FIELD_HIDE_DELAY, value);
            saveConfigAndPreview();
        } catch (NumberFormatException ignored) {
        }
    }

    private void commitHideDelay() {
        try {
            int value = Integer.parseInt(hideDelayText.trim());
            value = Math.max(0, Math.min(value, 1200));
            setOverrideValue(FIELD_HIDE_DELAY, value);
            hideDelayText = Integer.toString(value);
            saveConfigAndPreview();
        } catch (NumberFormatException ignored) {
            hideDelayText = Integer.toString(effectiveHideDelay());
        }
        }

    private boolean saveConfigAndPreview() {
        try {
            store.save(config);
            Live2DHudRenderer.previewConfig(config);
            error = "";
            return true;
        } catch (IOException failure) {
            error = "配置保存失败: " + failure.getMessage();
            return false;
        }
    }

    private String formatValue(int slider, float value) {
        return switch (SLIDER_FIELDS[slider]) {
            case FIELD_OFFSET_X, FIELD_OFFSET_Y -> Integer.toString(Math.round(value));
            default -> String.format(Locale.ROOT, "%.2f", value);
        };
    }
}
