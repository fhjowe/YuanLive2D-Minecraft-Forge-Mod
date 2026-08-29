package com.yuan.live2d.client.gui;

import com.yuan.live2d.client.live2d.Live2DClientState;
import com.yuan.live2d.client.live2d.Live2DConfig;
import com.yuan.live2d.client.live2d.Live2DConfigStore;
import com.yuan.live2d.client.live2d.Live2DAdjustMode;
import com.yuan.live2d.client.live2d.Live2DHudRenderer;
import com.yuan.live2d.client.live2d.Live2DModelManifest;
import com.yuan.live2d.client.live2d.Live2DModelManager;
import com.yuan.live2d.client.live2d.Live2DModelRegistry;
import com.yuan.live2d.client.live2d.Live2DPaths;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.awt.FileDialog;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class Live2DConfigScreen extends Screen {
    private static final int GOLD = 0xFFE1B65A;
    private static final int TEXT = 0xFFE5E0E8;
    private static final int MUTED = 0xFF948A9E;
    private static final int PANEL = 0xE6121018;
    private static final int PANEL_ALT = 0xE61A1620;
    private static final int BORDER = 0xFF3A3042;

    private static final String[] SLIDER_LABELS = {"水平位置", "垂直位置", "水平偏移", "垂直偏移", "缩放", "透明度"};
    private static final float[] SLIDER_MIN = {0, 0, -4096, -4096, .05f, 0};
    private static final float[] SLIDER_MAX = {1, 1, 4096, 4096, 2, 1};
    private static final float[] SLIDER_STEP = {.01f, .01f, 1, 1, .01f, .01f};
    private static final String[] HUD_LABELS = {"准星", "快捷栏", "状态", "经验", "效果", "Boss 条",
            "计分板", "聊天", "玩家列表", "提示消息", "第三方"};
    private static final String[] PRESET_LABELS = {"纯净", "准星", "原版", "全部"};
    private static final String[] PERFORMANCE_POLICY_LABELS = {"自动降级", "仅警告", "严格限制"};
    private static final String[] PERFORMANCE_ROW_LABELS = {"最大同时显示", "纹理内存预算"};
    private static final String[] VISIBILITY_LABELS = {"始终显示", "主手", "主/副手", "快捷栏",
            "物品栏"};
    private static final int[] PERFORMANCE_SLIDER_MIN = {1, 64};
    private static final int[] PERFORMANCE_SLIDER_MAX = {64, 16384};
    private static final int[] PERFORMANCE_SLIDER_STEP = {1, 64};
    private static final int[] PERFORMANCE_CUSTOM_MIN = {1, 16};
    private static final int[] PERFORMANCE_CUSTOM_MAX = {4096, 262144};
    private static final String[] INTERACTION_TOGGLE_LABELS = {"视角跟随", "随机动作", "随机表情", "点击反应"};
    private static final String[] INTERACTION_SLIDER_LABELS = {"视角强度", "随机间隔", "反应概率"};
    private static final float[] INTERACTION_SLIDER_MIN = {0, 5, 0};
    private static final float[] INTERACTION_SLIDER_MAX = {1, 60, 1};
    private static final float[] INTERACTION_SLIDER_STEP = {.01f, 1f, .01f};
    private static final String[] PHYSICS_TOGGLE_LABELS = {"轻量物理", "边缘挤压"};
    private static final String[] PHYSICS_SLIDER_LABELS = {"物理幅度", "物理强度"};
    private static final float[] PHYSICS_SLIDER_MIN = {0, 0};
    private static final float[] PHYSICS_SLIDER_MAX = {2, 1};
    private static final float[] PHYSICS_SLIDER_STEP = {.01f, .01f};
    private static final String[] RENDER_TOGGLE_LABELS = {"柔和投影"};
    private static final String[] RENDER_SLIDER_LABELS = {"切换淡入"};
    private static final float[] RENDER_SLIDER_MIN = {0};
    private static final float[] RENDER_SLIDER_MAX = {20};
    private static final float[] RENDER_SLIDER_STEP = {1};

    private final Live2DConfigStore store = new Live2DConfigStore(Live2DPaths.root());
    private final Live2DModelRegistry registry = new Live2DModelRegistry(Live2DPaths.models());
    private final Live2DModelManager modelManager = new Live2DModelManager();
    private Live2DConfig config;
    private Live2DConfigLayout layout;
    private List<Live2DModelManifest> installed = List.of();
    private int scroll;
    private int modelScroll;
    private long[] modelEstimates = new long[0];
    private List<String> estimatedModelIds = List.of();
    private int draggingSlider = -1;
    private String error = "";
    private String modelStatus = "";
    private String pendingDeleteId = "";
    private boolean modelBusy;
    private final boolean[] performanceCustom = new boolean[Live2DConfigLayout.PERFORMANCE_ROW_COUNT];
    private final String[] performanceText = new String[Live2DConfigLayout.PERFORMANCE_ROW_COUNT];
    private int performanceEditing = -1;
    private int performanceDragging = -1;
    private int interactionDragging = -1;
    private int physicsDragging = -1;
    private int renderDragging = -1;

    public Live2DConfigScreen() {
        super(Component.literal("Live2D 配置"));
    }

    @Override
    protected void init() {
        Live2DAdjustMode.exitQuietly();
        config = store.load();
        refreshModels();
        Live2DHudRenderer.beginPreview(false);
        Live2DHudRenderer.reloadConfig();
        performanceText[0] = Integer.toString(config.performance.maxVisibleInstances);
        performanceText[1] = Integer.toString(config.performance.textureMemoryBudgetMiB);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        layout = Live2DConfigLayout.of(width, height, scroll);
        scroll = layout.scroll();
        modelScroll = Math.max(0, Math.min(modelScroll, maxModelScroll()));
        renderShell(g);
        renderSliders(g, font, mouseX, mouseY);
        renderHudControls(g, font, mouseX, mouseY);
        renderVisibility(g, font);
        renderPerformance(g, font);
        renderInteraction(g, font);
        renderPhysics(g, font);
        renderRender(g, font);
        renderModels(g, font);
        renderPreviewPanel(g, font);
        renderNativePreview(g);
        renderStatus(g);
    }

    private void renderShell(GuiGraphics g) {
        g.fill(0, 0, width, 28, PANEL);
        g.drawString(font, "Live2D 配置", 12, 9, GOLD);
        String close = "Esc 关闭";
        g.drawString(font, close, width - font.width(close) - 12, 9, MUTED);
    }

    private void renderSliders(GuiGraphics g, Font font, int mouseX, int mouseY) {
        List<Live2DConfigLayout.Bounds> bounds = layout.sliders();
        float[] values = sliderValues();
        for (int i = 0; i < bounds.size(); i++) {
            Live2DConfigLayout.Bounds b = bounds.get(i);
            g.fill(b.left(), b.top(), b.right(), b.bottom(), PANEL_ALT);
            g.drawString(font, SLIDER_LABELS[i], b.left() + 8, b.top() + 6, TEXT);
            float ratio = Live2DConfigLayout.ratio(SLIDER_MIN[i], SLIDER_MAX[i], values[i]);
            int trackLeft = b.left() + 8;
            int trackRight = b.right() - 8;
            int trackY = b.bottom() - 14;
            g.fill(trackLeft, trackY, trackRight, trackY + 3, 0xFF3A3042);
            int knobX = trackLeft + (int) (ratio * (trackRight - trackLeft));
            g.fill(knobX - 3, trackY - 3, knobX + 3, trackY + 6, GOLD);
            String valueText = formatValue(i, values[i]);
            g.drawString(font, valueText, trackRight - font.width(valueText), b.top() + 6, GOLD);
        }
        Live2DConfigLayout.Bounds reset = layout.resetPosition();
        g.fill(reset.left(), reset.top(), reset.right(), reset.bottom(), PANEL_ALT);
        g.drawCenteredString(font, "重置位置", (reset.left() + reset.right()) / 2, reset.top() + 6, TEXT);
    }

    private void renderHudControls(GuiGraphics g, Font font, int mouseX, int mouseY) {
        List<Live2DConfigLayout.Bounds> toggles = layout.toggles();
        for (int i = 0; i < Math.min(toggles.size(), Live2DConfigLayout.TOGGLE_COUNT); i++) {
            Live2DConfigLayout.Bounds b = toggles.get(i);
            boolean on = hudValue(i);
            g.fill(b.left(), b.top(), b.right(), b.bottom(), on ? 0xFF4B3820 : PANEL_ALT);
            g.drawString(font, (on ? "■ " : "□ ") + HUD_LABELS[i], b.left() + 4, b.top() + 3,
                    on ? GOLD : MUTED);
        }
        List<Live2DConfigLayout.Bounds> presets = layout.presets();
        for (int i = 0; i < Math.min(presets.size(), Live2DConfigLayout.PRESET_COUNT); i++) {
            Live2DConfigLayout.Bounds b = presets.get(i);
            g.fill(b.left(), b.top(), b.right(), b.bottom(), PANEL_ALT);
            g.drawString(font, PRESET_LABELS[i], b.left() + 4, b.top() + 5, TEXT);
        }
    }

    private void renderVisibility(GuiGraphics g, Font font) {
        List<Live2DConfigLayout.Bounds> buttons = layout.visibilityButtons();
        if (buttons.isEmpty()) return;
        g.drawString(font, "可见性", buttons.get(0).left(), buttons.get(0).top() - 16, GOLD);
        Live2DConfig.Visibility current = config.global.visibility;
        for (int i = 0; i < Math.min(buttons.size(), Live2DConfigLayout.VISIBILITY_COUNT); i++) {
            Live2DConfigLayout.Bounds b = buttons.get(i);
            boolean active = current == Live2DConfig.Visibility.values()[i];
            g.fill(b.left(), b.top(), b.right(), b.bottom(), active ? 0xFF4B3820 : PANEL_ALT);
            g.drawString(font, VISIBILITY_LABELS[i], b.left() + 4, b.top() + 3, active ? GOLD : MUTED);
        }
    }

    private void renderPerformance(GuiGraphics g, Font font) {
        List<Live2DConfigLayout.Bounds> policy = layout.performancePolicy();
        if (policy.isEmpty()) return;
        g.drawString(font, "性能", policy.get(0).left(), policy.get(0).top() - 16, GOLD);
        Live2DConfig.PerformancePolicy current = config.performance.policy;
        for (int i = 0; i < Math.min(policy.size(), Live2DConfigLayout.PERFORMANCE_POLICY_COUNT); i++) {
            Live2DConfigLayout.Bounds b = policy.get(i);
            boolean active = current == Live2DConfig.PerformancePolicy.values()[i];
            g.fill(b.left(), b.top(), b.right(), b.bottom(), active ? 0xFF4B3820 : PANEL_ALT);
            g.drawString(font, PERFORMANCE_POLICY_LABELS[i], b.left() + 4, b.top() + 5, active ? GOLD : TEXT);
        }
        List<Live2DConfigLayout.Bounds> rows = layout.performanceRows();
        for (int i = 0; i < Math.min(rows.size(), Live2DConfigLayout.PERFORMANCE_ROW_COUNT); i++) {
            Live2DConfigLayout.Bounds row = rows.get(i);
            g.fill(row.left(), row.top(), row.right(), row.bottom(), PANEL_ALT);
            g.drawString(font, PERFORMANCE_ROW_LABELS[i], row.left() + 8, row.top() + 4, TEXT);
            Live2DConfigLayout.Bounds custom = layout.performanceCustomButtons().get(i);
            g.fill(custom.left(), custom.top(), custom.right(), custom.bottom(), PANEL_ALT);
            String customLabel = performanceCustom[i] ? "滑块" : "自定义";
            g.drawCenteredString(font, customLabel, (custom.left() + custom.right()) / 2, custom.top() + 4, TEXT);
            if (performanceCustom[i]) {
                Live2DConfigLayout.Bounds input = performanceInputBounds(i);
                g.fill(input.left(), input.top(), input.right(), input.bottom(), 0xFF0F0E16);
                g.fill(input.left(), input.top(), input.right(), input.top() + 1, performanceEditing == i ? GOLD : BORDER);
                g.fill(input.left(), input.bottom() - 1, input.right(), input.bottom(), performanceEditing == i ? GOLD : BORDER);
                g.fill(input.left(), input.top(), input.left() + 1, input.bottom(), performanceEditing == i ? GOLD : BORDER);
                g.fill(input.right() - 1, input.top(), input.right(), input.bottom(), performanceEditing == i ? GOLD : BORDER);
                g.drawString(font, performanceText[i], input.left() + 6, input.top() + 5, TEXT);
            } else {
                int value = performanceValue(i);
                int trackLeft = row.left() + 8;
                int trackRight = custom.left() - 8;
                int trackY = row.bottom() - 14;
                g.fill(trackLeft, trackY, trackRight, trackY + 3, 0xFF3A3042);
                float ratio = Live2DConfigLayout.ratio(PERFORMANCE_SLIDER_MIN[i], PERFORMANCE_SLIDER_MAX[i], value);
                int knobX = trackLeft + (int) (ratio * (trackRight - trackLeft));
                g.fill(knobX - 3, trackY - 3, knobX + 3, trackY + 6, GOLD);
                String valueText = Integer.toString(value);
                g.drawString(font, valueText, trackRight - font.width(valueText), row.top() + 4, GOLD);
            }
        }
    }
    private void renderInteraction(GuiGraphics g, Font font) {
        Live2DConfigLayout.Bounds section = layout.interactionSection();
        g.drawString(font, "互动", section.left(), section.top(), GOLD);
        List<Live2DConfigLayout.Bounds> toggles = layout.interactionToggles();
        boolean[] values = {config.interaction.viewFollowEnabled,
                config.interaction.randomMotionEnabled, config.interaction.randomExpressionEnabled,
                config.interaction.clickReactionEnabled};
        for (int i = 0; i < Math.min(toggles.size(), Live2DConfigLayout.INTERACTION_TOGGLE_COUNT); i++) {
            renderSectionToggle(g, font, toggles.get(i), INTERACTION_TOGGLE_LABELS[i], values[i]);
        }
        List<Live2DConfigLayout.Bounds> sliders = layout.interactionSliders();
        float[] sliderValues = {config.interaction.viewFollowStrength,
                config.interaction.randomMotionIntervalSeconds, config.interaction.clickReactionChance};
        for (int i = 0; i < Math.min(sliders.size(), Live2DConfigLayout.INTERACTION_SLIDER_COUNT); i++) {
            renderSectionSlider(g, font, sliders.get(i), INTERACTION_SLIDER_LABELS[i],
                    INTERACTION_SLIDER_MIN[i], INTERACTION_SLIDER_MAX[i], INTERACTION_SLIDER_STEP[i],
                    sliderValues[i]);
        }
    }

    private void renderPhysics(GuiGraphics g, Font font) {
        Live2DConfigLayout.Bounds section = layout.physicsSection();
        g.drawString(font, "物理", section.left(), section.top(), GOLD);
        List<Live2DConfigLayout.Bounds> toggles = layout.physicsToggles();
        boolean[] values = {config.physics.interactionEnabled, config.physics.edgeSquash};
        for (int i = 0; i < Math.min(toggles.size(), Live2DConfigLayout.PHYSICS_TOGGLE_COUNT); i++) {
            renderSectionToggle(g, font, toggles.get(i), PHYSICS_TOGGLE_LABELS[i], values[i]);
        }
        List<Live2DConfigLayout.Bounds> sliders = layout.physicsSliders();
        float[] sliderValues = {config.physics.amplitude, config.physics.strength};
        for (int i = 0; i < Math.min(sliders.size(), Live2DConfigLayout.PHYSICS_SLIDER_COUNT); i++) {
            renderSectionSlider(g, font, sliders.get(i), PHYSICS_SLIDER_LABELS[i],
                    PHYSICS_SLIDER_MIN[i], PHYSICS_SLIDER_MAX[i], PHYSICS_SLIDER_STEP[i], sliderValues[i]);
        }
    }

    private void renderRender(GuiGraphics g, Font font) {
        Live2DConfigLayout.Bounds section = layout.renderSection();
        g.drawString(font, "渲染", section.left(), section.top(), GOLD);
        List<Live2DConfigLayout.Bounds> toggles = layout.renderToggles();
        for (int i = 0; i < Math.min(toggles.size(), Live2DConfigLayout.RENDER_TOGGLE_COUNT); i++) {
            renderSectionToggle(g, font, toggles.get(i), RENDER_TOGGLE_LABELS[i], config.render.shadowEnabled);
        }
        List<Live2DConfigLayout.Bounds> sliders = layout.renderSliders();
        for (int i = 0; i < Math.min(sliders.size(), Live2DConfigLayout.RENDER_SLIDER_COUNT); i++) {
            renderSectionSlider(g, font, sliders.get(i), RENDER_SLIDER_LABELS[i],
                    RENDER_SLIDER_MIN[i], RENDER_SLIDER_MAX[i], RENDER_SLIDER_STEP[i],
                    config.render.switchFadeTicks);
        }
    }

    private void renderSectionToggle(GuiGraphics g, Font font, Live2DConfigLayout.Bounds b, String label, boolean on) {
        g.fill(b.left(), b.top(), b.right(), b.bottom(), on ? 0xFF4B3820 : PANEL_ALT);
        g.drawString(font, (on ? "■ " : "□ ") + label, b.left() + 4, b.top() + 3, on ? GOLD : MUTED);
    }

    private void renderSectionSlider(GuiGraphics g, Font font, Live2DConfigLayout.Bounds b, String label,
            float min, float max, float step, float value) {
        g.fill(b.left(), b.top(), b.right(), b.bottom(), PANEL_ALT);
        g.drawString(font, label, b.left() + 8, b.top() + 4, TEXT);
        float ratio = Live2DConfigLayout.ratio(min, max, value);
        int trackLeft = b.left() + 8;
        int trackRight = b.right() - 8;
        int trackY = b.bottom() - 14;
        g.fill(trackLeft, trackY, trackRight, trackY + 3, 0xFF3A3042);
        int knobX = trackLeft + (int) (ratio * (trackRight - trackLeft));
        g.fill(knobX - 3, trackY - 3, knobX + 3, trackY + 6, GOLD);
        String valueText = step >= 1f ? Integer.toString(Math.round(value))
                : String.format(Locale.ROOT, "%.2f", value);
        g.drawString(font, valueText, trackRight - font.width(valueText), b.top() + 4, GOLD);
    }
    private void renderModels(GuiGraphics g, Font font) {
        Live2DConfigLayout.Bounds list = layout.modelList();
        g.drawString(font, "模型 (" + installed.size() + ")", list.left(), list.top() - 20, GOLD);
        if (installed.isEmpty()) {
            g.drawString(font, "未安装模型", list.left() + 6, list.top() + 6, MUTED);
        } else {
            int rowHeight = 22;
            int visible = visibleModelRows();
            int from = Math.max(0, Math.min(modelScroll, installed.size() - visible));
            for (int i = 0; i < visible && from + i < installed.size(); i++) {
                Live2DModelManifest model = installed.get(from + i);
                boolean selected = model.id().equals(config.global.selectedModelId);
                int top = list.top() + i * rowHeight;
                int bottom = Math.min(list.bottom(), top + rowHeight);
                g.fill(list.left(), top, list.right(), bottom, selected ? 0xFF4B3820 : PANEL_ALT);
                String name = model.displayName();
                String prefix = selected ? "■ " : "□ ";
                String estimateText = "";
                boolean overBudget = false;
                if (modelEstimates.length == installed.size() && modelEstimates[from + i] > 0) {
                    long miB = Math.max(1, modelEstimates[from + i] >> 20);
                    overBudget = modelEstimates[from + i] > ((long) config.performance.textureMemoryBudgetMiB) << 20;
                    estimateText = "≈" + miB + " MiB" + (overBudget ? " 超预算" : "");
                }
                int estimateWidth = font.width(estimateText);
                int maxNameWidth = Math.max(1, list.right() - 4 - list.left()
                        - (estimateWidth == 0 ? 0 : estimateWidth + 8) - font.width(prefix));
                String clipped = name;
                if (font.width(name) > maxNameWidth) clipped = font.plainSubstrByWidth(name, maxNameWidth);
                g.drawString(font, prefix + clipped, list.left() + 4, top + 3,
                        selected ? GOLD : MUTED);
                if (estimateWidth > 0) {
                    g.drawString(font, estimateText, list.right() - 4 - estimateWidth, top + 3,
                            overBudget ? 0xFFFF8E78 : GOLD);
                }
            }
        }

        Live2DConfigLayout.Bounds actions = layout.modelActions();
        int seg1 = actionSegment(actions, 1, 4);
        int seg2 = actionSegment(actions, 2, 4);
        int seg3 = actionSegment(actions, 3, 4);
        g.fill(actions.left(), actions.top(), seg1, actions.bottom(), PANEL_ALT);
        g.drawCenteredString(font, "导入模型", (actions.left() + seg1) / 2, actions.top() + 5, TEXT);
        g.fill(seg1, actions.top(), seg2, actions.bottom(), PANEL_ALT);
        g.drawCenteredString(font, "覆盖", (seg1 + seg2) / 2, actions.top() + 5, TEXT);
        g.fill(seg2, actions.top(), seg3, actions.bottom(), PANEL_ALT);
        g.drawCenteredString(font, "调整", (seg2 + seg3) / 2, actions.top() + 5, TEXT);
        g.fill(seg3, actions.top(), actions.right(), actions.bottom(), PANEL_ALT);
        String deleteLabel = pendingDeleteId.isEmpty() ? "删除" : "确认删除";
        g.drawCenteredString(font, deleteLabel, (seg3 + actions.right()) / 2, actions.top() + 5,
                pendingDeleteId.isEmpty() ? TEXT : 0xFFFF8E78);

        Live2DConfigLayout.Bounds status = layout.modelStatus();
        String line = modelBusy ? "处理中..." : modelStatus;
        g.drawString(font, line, status.left() + 4, status.top() + 3,
                line.startsWith("导入失败") || line.startsWith("删除失败") ? 0xFFFF8E78 : MUTED);
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
        if (!status.error().isEmpty()) {
            String errorText = status.error();
            int maxWidth = preview.width() - 12;
            if (font.width(errorText) > maxWidth) {
                errorText = font.plainSubstrByWidth(errorText, Math.max(1, maxWidth - font.width("..."))) + "...";
            }
            g.drawString(font, errorText, preview.left() + 6,
                    (preview.top() + preview.bottom()) / 2 - 6, 0xFFFF8E78);
        } else if (status.activeModelId() == null || status.activeModelId().isEmpty()) {
            g.drawCenteredString(font, "未选择模型", (preview.left() + preview.right()) / 2,
                    (preview.top() + preview.bottom()) / 2 - 6, MUTED);
        }
    }

    private void renderNativePreview(GuiGraphics g) {
        Live2DConfigLayout.Bounds preview = layout.preview();
        Minecraft minecraft = Minecraft.getInstance();
        int framebufferWidth = minecraft.getWindow().getWidth();
        int framebufferHeight = minecraft.getWindow().getHeight();
        if (preview.width() <= 1 || preview.height() <= 1 || framebufferWidth <= 0 || framebufferHeight <= 0) return;
        Live2DConfig.Global effective = config.effectiveGlobal(config.global.selectedModelId);
        PreviewPosition position = previewPosition(effective.anchorX, effective.anchorY,
                effective.offsetX, effective.offsetY, preview.width(), preview.height(), width, height);
        float centerX = preview.left() + position.x();
        float centerY = preview.top() + position.y();
        float scale = preview.height() * .82f * effective.scale / .45f;
        g.enableScissor(preview.left(), preview.top(), preview.right(), preview.bottom());
        try {
            Live2DHudRenderer.renderPreview(g, new Live2DClientState.PreviewFrame(
                    config, framebufferWidth, framebufferHeight,
                    centerX * framebufferWidth / Math.max(1f, width),
                    centerY * framebufferHeight / Math.max(1f, height),
                    scale * framebufferHeight / Math.max(1f, height),
                    effective.opacity));
        } finally {
            g.disableScissor();
        }
    }

    private void renderStatus(GuiGraphics g) {
        String line = error.isEmpty() ? "即时保存已开启" : error;
        g.drawString(font, line, 12, height - 14, error.isEmpty() ? GOLD : 0xFFFF8E78);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        for (int i = 0; i < layout.sliders().size(); i++) {
            if (layout.sliders().get(i).contains(mouseX, mouseY)) {
                draggingSlider = i;
                setSliderFromMouse(i, mouseX, mouseY);
                return true;
            }
        }
        if (layout.resetPosition().contains(mouseX, mouseY)) {
            resetPosition();
            return true;
        }
        for (int i = 0; i < layout.toggles().size(); i++) {
            if (layout.toggles().get(i).contains(mouseX, mouseY)) {
                toggleHud(i);
                return true;
            }
        }
        for (int i = 0; i < layout.presets().size(); i++) {
            if (layout.presets().get(i).contains(mouseX, mouseY)) {
                applyPreset(i);
                return true;
            }
        }
        for (int i = 0; i < layout.visibilityButtons().size(); i++) {
            if (layout.visibilityButtons().get(i).contains(mouseX, mouseY)) {
                config.global.visibility = Live2DConfig.Visibility.values()[i];
                saveConfigAndPreview();
                return true;
            }
        }
        if (performanceEditing >= 0) {
            commitPerformanceText(performanceEditing);
            performanceEditing = -1;
        }
        for (int i = 0; i < layout.performancePolicy().size(); i++) {
            if (layout.performancePolicy().get(i).contains(mouseX, mouseY)) {
                config.performance.policy = Live2DConfig.PerformancePolicy.values()[i];
                saveConfigAndPreview();
                return true;
            }
        }
        for (int i = 0; i < layout.performanceCustomButtons().size(); i++) {
            if (layout.performanceCustomButtons().get(i).contains(mouseX, mouseY)) {
                togglePerformanceCustom(i);
                return true;
            }
        }
        for (int i = 0; i < layout.performanceRows().size(); i++) {
            if (layout.performanceRows().get(i).contains(mouseX, mouseY)) {
                if (performanceCustom[i]) {
                    performanceEditing = i;
                } else {
                    performanceDragging = i;
                    setPerformanceSliderFromMouse(i, mouseX);
                }
                return true;
            }
        }
        if (handleInteractionClick(mouseX, mouseY)) return true;
        if (handlePhysicsClick(mouseX, mouseY)) return true;
        if (handleRenderClick(mouseX, mouseY)) return true;
        if (handleModelClick(mouseX, mouseY)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingSlider >= 0) {
            setSliderFromMouse(draggingSlider, mouseX, mouseY);
            return true;
        }
        if (performanceDragging >= 0) {
            setPerformanceSliderFromMouse(performanceDragging, mouseX);
            return true;
        }
        if (interactionDragging >= 0) {
            setInteractionSliderFromMouse(interactionDragging, mouseX);
            return true;
        }
        if (physicsDragging >= 0) {
            setPhysicsSliderFromMouse(physicsDragging, mouseX);
            return true;
        }
        if (renderDragging >= 0) {
            setRenderSliderFromMouse(renderDragging, mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingSlider = -1;
        performanceDragging = -1;
        interactionDragging = -1;
        physicsDragging = -1;
        renderDragging = -1;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (performanceEditing >= 0) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                commitPerformanceText(performanceEditing);
                performanceEditing = -1;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                performanceText[performanceEditing] = Integer.toString(performanceValue(performanceEditing));
                performanceEditing = -1;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !performanceText[performanceEditing].isEmpty()) {
                performanceText[performanceEditing] = performanceText[performanceEditing].substring(0,
                        performanceText[performanceEditing].length() - 1);
                updatePerformanceFromText(performanceEditing);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (performanceEditing >= 0 && Character.isDigit(codePoint)) {
            String next = performanceText[performanceEditing] + codePoint;
            if (next.length() <= 7) {
                performanceText[performanceEditing] = next;
                updatePerformanceFromText(performanceEditing);
            }
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (layout.modelList().contains(mouseX, mouseY)) {
            modelScroll = Math.max(0, Math.min(modelScroll - (int) delta * 3, maxModelScroll()));
            return true;
        }
        scroll = Math.max(0, Math.min(scroll - (int) delta * 10, layout.maxScroll()));
        return true;
    }

    @Override
    public void onClose() {
        Live2DHudRenderer.endPreview();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private boolean handleModelClick(double mouseX, double mouseY) {
        Live2DConfigLayout.Bounds actions = layout.modelActions();
        if (actions.contains(mouseX, mouseY)) {
            int seg1 = actionSegment(actions, 1, 4);
            int seg2 = actionSegment(actions, 2, 4);
            int seg3 = actionSegment(actions, 3, 4);
            if (mouseX < seg1) importModel();
            else if (mouseX < seg2) openOverride();
            else if (mouseX < seg3) openAdjust();
            else deleteModel();
            return true;
        }
        Live2DConfigLayout.Bounds list = layout.modelList();
        if (!list.contains(mouseX, mouseY) || installed.isEmpty()) return false;
        int row = (int) ((mouseY - list.top()) / 22);
        int index = Math.max(0, Math.min(modelScroll, installed.size() - visibleModelRows())) + row;
        if (index >= 0 && index < installed.size()) selectModel(installed.get(index));
        return true;
    }

    private void selectModel(Live2DModelManifest model) {
        config.global.selectedModelId = model.id();
        registry.selectById(model.id());
        pendingDeleteId = "";
        saveConfigAndPreview();
    }

    private void openOverride() {
        if (modelBusy || registry.selected() == null) return;
        Minecraft.getInstance().setScreen(new Live2DOverrideScreen(config, store, registry.selected()));
    }

    private void openAdjust() {
        if (modelBusy || registry.selected() == null) return;
        Live2DHudRenderer.endPreview();
        Minecraft.getInstance().setScreen(null);
        Live2DAdjustMode.enter(config, registry.selected(), store);
    }

    private static int actionSegment(Live2DConfigLayout.Bounds actions, int index, int count) {
        return actions.left() + actions.width() * index / count;
    }

    private void importModel() {
        if (modelBusy) return;
        modelBusy = true;
        modelStatus = "选择模型包...";
        CompletableFuture<Live2DModelManager.ImportResult> future =
                modelManager.submitDialog(this::chooseAndImport);
        future.whenComplete((result, failure) ->
                Minecraft.getInstance().execute(() -> completeImport(result, failure)));
    }

    private Live2DModelManager.ImportResult chooseAndImport() throws Exception {
        Path source = chooseModelZip();
        if (source == null) return null;
        return modelManager.importSources(List.of(source));
    }

    private void completeImport(Live2DModelManager.ImportResult result, Throwable failure) {
        modelBusy = false;
        if (failure != null) {
            modelStatus = "导入失败: " + message(failure);
            return;
        }
        if (result == null) {
            modelStatus = "已取消";
            return;
        }
        if (!result.imported().isEmpty()) {
            config.global.selectedModelId = result.imported().get(0).id();
            modelStatus = "已导入 " + result.imported().size() + " 个模型";
        } else if (!result.errors().isEmpty()) {
            modelStatus = "导入失败: " + result.errors().get(0).message();
        } else {
            modelStatus = "没有导入模型";
        }
        registry.invalidate();
        refreshModels();
        saveConfigAndPreview();
    }

    private void deleteModel() {
        if (modelBusy) return;
        if (pendingDeleteId.isEmpty()) {
            Live2DModelManifest selected = registry.selected();
            if (selected == null) {
                modelStatus = "未选择模型";
                return;
            }
            pendingDeleteId = selected.id();
            modelStatus = "再次点击确认删除: " + selected.displayName();
            return;
        }
        String deletedId = pendingDeleteId;
        modelBusy = true;
        modelStatus = "删除中...";
        modelManager.deletePackageAsync(deletedId, Set::of)
                .whenComplete((result, failure) ->
                        Minecraft.getInstance().execute(() -> completeDelete(deletedId, failure)));
    }

    private void completeDelete(String deletedId, Throwable failure) {
        modelBusy = false;
        pendingDeleteId = "";
        if (failure != null) {
            modelStatus = "删除失败: " + message(failure);
            return;
        }
        registry.invalidate();
        refreshModels();
        if (deletedId.equals(config.global.selectedModelId) && !installed.isEmpty()) {
            config.global.selectedModelId = installed.get(0).id();
            registry.selectById(config.global.selectedModelId);
        }
        config.modelOverrides.remove(deletedId);
        modelStatus = "已删除";
        saveConfigAndPreview();
    }

    private void refreshModels() {
        installed = registry.list();
        List<String> ids = installed.stream().map(Live2DModelManifest::id).toList();
        if (!ids.equals(estimatedModelIds)) {
            estimatedModelIds = ids;
            modelEstimates = new long[installed.size()];
            for (int i = 0; i < installed.size(); i++) {
                modelEstimates[i] = Live2DHudRenderer.estimateTextures(installed.get(i));
            }
        }
        if (installed.isEmpty()) {
            if (!registry.lastError().isEmpty()) modelStatus = registry.lastError();
            config.global.selectedModelId = "";
            registry.selectById("");
            return;
        }
        registry.selectById(config.global.selectedModelId);
        if (registry.selected() == null) {
            config.global.selectedModelId = installed.get(0).id();
            registry.selectById(config.global.selectedModelId);
        }
    }

    private int visibleModelRows() {
        return layout == null ? 1 : Math.max(1, layout.modelList().height() / 22);
    }

    private int maxModelScroll() {
        return Math.max(0, installed.size() - visibleModelRows());
    }

    private static Path chooseModelZip() {
        FileDialog dialog = new FileDialog((java.awt.Frame) null, "选择 Live2D 模型 ZIP", FileDialog.LOAD);
        dialog.setFilenameFilter((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".zip"));
        dialog.setVisible(true);
        String directory = dialog.getDirectory();
        String file = dialog.getFile();
        if (directory == null || file == null) return null;
        return Path.of(directory, file);
    }

    private static String message(Throwable failure) {
        String value = failure.getMessage();
        return value == null || value.isBlank() ? String.valueOf(failure) : value;
    }

    private void setSliderFromMouse(int index, double mouseX, double mouseY) {
        Live2DConfigLayout.Bounds b = layout.sliders().get(index);
        float value = Live2DConfigLayout.valueAt(SLIDER_MIN[index], SLIDER_MAX[index], SLIDER_STEP[index],
                b.left() + 8, b.width() - 16, mouseX);
        if (Float.compare(value, sliderValue(index)) != 0) {
            applySliderValue(index, value);
            saveConfigAndPreview();
        }
    }

    private void applySliderValue(int index, float value) {
        switch (index) {
            case 0 -> config.global.anchorX = value;
            case 1 -> config.global.anchorY = value;
            case 2 -> config.global.offsetX = value;
            case 3 -> config.global.offsetY = value;
            case 4 -> config.global.scale = value;
            default -> config.global.opacity = value;
        }
    }

    private float sliderValue(int index) {
        return switch (index) {
            case 0 -> config.global.anchorX;
            case 1 -> config.global.anchorY;
            case 2 -> config.global.offsetX;
            case 3 -> config.global.offsetY;
            case 4 -> config.global.scale;
            default -> config.global.opacity;
        };
    }

    private float[] sliderValues() {
        return new float[]{config.global.anchorX, config.global.anchorY, config.global.offsetX, config.global.offsetY,
                config.global.scale, config.global.opacity};
    }

    private String formatValue(int index, float value) {
        return switch (index) {
            case 2, 3 -> Integer.toString(Math.round(value));
            default -> String.format(Locale.ROOT, "%.2f", value);
        };
    }

    private boolean hudValue(int index) {
        return switch (index) {
            case 0 -> config.hud.crosshair;
            case 1 -> config.hud.hotbar;
            case 2 -> config.hud.status;
            case 3 -> config.hud.experience;
            case 4 -> config.hud.effects;
            case 5 -> config.hud.bossBars;
            case 6 -> config.hud.scoreboard;
            case 7 -> config.hud.chat;
            case 8 -> config.hud.playerList;
            case 9 -> config.hud.messages;
            default -> config.hud.thirdParty;
        };
    }

    private void toggleHud(int index) {
        switch (index) {
            case 0 -> config.hud.crosshair = !config.hud.crosshair;
            case 1 -> config.hud.hotbar = !config.hud.hotbar;
            case 2 -> config.hud.status = !config.hud.status;
            case 3 -> config.hud.experience = !config.hud.experience;
            case 4 -> config.hud.effects = !config.hud.effects;
            case 5 -> config.hud.bossBars = !config.hud.bossBars;
            case 6 -> config.hud.scoreboard = !config.hud.scoreboard;
            case 7 -> config.hud.chat = !config.hud.chat;
            case 8 -> config.hud.playerList = !config.hud.playerList;
            case 9 -> config.hud.messages = !config.hud.messages;
            default -> config.hud.thirdParty = !config.hud.thirdParty;
        }
        saveConfigAndPreview();
    }

    private void applyPreset(int index) {
        config.hud.applyPreset(Live2DConfig.HudPreset.values()[index]);
        saveConfigAndPreview();
    }

    private void resetPosition() {
        config.global.anchorX = .8f;
        config.global.anchorY = .55f;
        config.global.offsetX = -24f;
        config.global.offsetY = -24f;
        saveConfigAndPreview();
    }

    private int performanceValue(int index) {
        return switch (index) {
            case 0 -> config.performance.maxVisibleInstances;
            default -> config.performance.textureMemoryBudgetMiB;
        };
    }

    private void setPerformanceValue(int index, int value) {
        if (index == 0) config.performance.maxVisibleInstances = value;
        else config.performance.textureMemoryBudgetMiB = value;
    }

    private Live2DConfigLayout.Bounds performanceInputBounds(int index) {
        Live2DConfigLayout.Bounds row = layout.performanceRows().get(index);
        return new Live2DConfigLayout.Bounds(row.left() + 8, row.top() + 20, row.right() - 8, row.top() + 38);
    }

    private void setPerformanceSliderFromMouse(int index, double mouseX) {
        Live2DConfigLayout.Bounds row = layout.performanceRows().get(index);
        Live2DConfigLayout.Bounds custom = layout.performanceCustomButtons().get(index);
        int left = row.left() + 8;
        int width = Math.max(1, custom.left() - 8 - left);
        float value = Live2DConfigLayout.valueAt(PERFORMANCE_SLIDER_MIN[index], PERFORMANCE_SLIDER_MAX[index],
                PERFORMANCE_SLIDER_STEP[index], left, width, mouseX);
        int rounded = Math.round(value);
        if (rounded != performanceValue(index)) {
            setPerformanceValue(index, rounded);
            performanceText[index] = Integer.toString(rounded);
            saveConfigAndPreview();
        }
    }

    private void updatePerformanceFromText(int index) {
        try {
            int value = Integer.parseInt(performanceText[index]);
            value = Math.max(PERFORMANCE_CUSTOM_MIN[index], Math.min(PERFORMANCE_CUSTOM_MAX[index], value));
            if (value != performanceValue(index)) {
                setPerformanceValue(index, value);
                saveConfigAndPreview();
            }
        } catch (NumberFormatException ignored) {
        }
    }

    private void commitPerformanceText(int index) {
        try {
            int value = Integer.parseInt(performanceText[index].trim());
            value = Math.max(PERFORMANCE_CUSTOM_MIN[index], Math.min(PERFORMANCE_CUSTOM_MAX[index], value));
            setPerformanceValue(index, value);
            performanceText[index] = Integer.toString(value);
            saveConfigAndPreview();
        } catch (NumberFormatException ignored) {
            performanceText[index] = Integer.toString(performanceValue(index));
        }
    }

    private void togglePerformanceCustom(int index) {
        performanceCustom[index] = !performanceCustom[index];
        performanceText[index] = Integer.toString(performanceValue(index));
        performanceEditing = performanceCustom[index] ? index : -1;
    }

    private boolean handleInteractionClick(double mouseX, double mouseY) {
        List<Live2DConfigLayout.Bounds> toggles = layout.interactionToggles();
        for (int i = 0; i < toggles.size(); i++) {
            if (toggles.get(i).contains(mouseX, mouseY)) {
                toggleInteraction(i);
                return true;
            }
        }
        List<Live2DConfigLayout.Bounds> sliders = layout.interactionSliders();
        for (int i = 0; i < sliders.size(); i++) {
            if (sliders.get(i).contains(mouseX, mouseY)) {
                interactionDragging = i;
                setInteractionSliderFromMouse(i, mouseX);
                return true;
            }
        }
        return false;
    }

    private boolean handlePhysicsClick(double mouseX, double mouseY) {
        List<Live2DConfigLayout.Bounds> toggles = layout.physicsToggles();
        for (int i = 0; i < toggles.size(); i++) {
            if (toggles.get(i).contains(mouseX, mouseY)) {
                togglePhysics(i);
                return true;
            }
        }
        List<Live2DConfigLayout.Bounds> sliders = layout.physicsSliders();
        for (int i = 0; i < sliders.size(); i++) {
            if (sliders.get(i).contains(mouseX, mouseY)) {
                physicsDragging = i;
                setPhysicsSliderFromMouse(i, mouseX);
                return true;
            }
        }
        return false;
    }

    private boolean handleRenderClick(double mouseX, double mouseY) {
        List<Live2DConfigLayout.Bounds> toggles = layout.renderToggles();
        for (int i = 0; i < toggles.size(); i++) {
            if (toggles.get(i).contains(mouseX, mouseY)) {
                toggleRender(i);
                return true;
            }
        }
        List<Live2DConfigLayout.Bounds> sliders = layout.renderSliders();
        for (int i = 0; i < sliders.size(); i++) {
            if (sliders.get(i).contains(mouseX, mouseY)) {
                renderDragging = i;
                setRenderSliderFromMouse(i, mouseX);
                return true;
            }
        }
        return false;
    }

    private void toggleInteraction(int index) {
        switch (index) {
            case 0 -> config.interaction.viewFollowEnabled = !config.interaction.viewFollowEnabled;
            case 1 -> config.interaction.randomMotionEnabled = !config.interaction.randomMotionEnabled;
            case 2 -> config.interaction.randomExpressionEnabled = !config.interaction.randomExpressionEnabled;
            default -> config.interaction.clickReactionEnabled = !config.interaction.clickReactionEnabled;
        }
        saveConfigAndPreview();
    }

    private void togglePhysics(int index) {
        if (index == 0) config.physics.interactionEnabled = !config.physics.interactionEnabled;
        else config.physics.edgeSquash = !config.physics.edgeSquash;
        saveConfigAndPreview();
    }

    private void toggleRender(int index) {
        config.render.shadowEnabled = !config.render.shadowEnabled;
        saveConfigAndPreview();
    }

    private float interactionSliderValue(int index) {
        return switch (index) {
            case 0 -> config.interaction.viewFollowStrength;
            case 1 -> config.interaction.randomMotionIntervalSeconds;
            default -> config.interaction.clickReactionChance;
        };
    }

    private void applyInteractionSliderValue(int index, float value) {
        switch (index) {
            case 0 -> config.interaction.viewFollowStrength = value;
            case 1 -> config.interaction.randomMotionIntervalSeconds = value;
            default -> config.interaction.clickReactionChance = value;
        }
    }

    private void setInteractionSliderFromMouse(int index, double mouseX) {
        Live2DConfigLayout.Bounds b = layout.interactionSliders().get(index);
        float value = Live2DConfigLayout.valueAt(INTERACTION_SLIDER_MIN[index], INTERACTION_SLIDER_MAX[index],
                INTERACTION_SLIDER_STEP[index], b.left() + 8, b.width() - 16, mouseX);
        if (Float.compare(value, interactionSliderValue(index)) != 0) {
            applyInteractionSliderValue(index, value);
            saveConfigAndPreview();
        }
    }

    private float physicsSliderValue(int index) {
        return index == 0 ? config.physics.amplitude : config.physics.strength;
    }

    private void applyPhysicsSliderValue(int index, float value) {
        if (index == 0) config.physics.amplitude = value;
        else config.physics.strength = value;
    }

    private void setPhysicsSliderFromMouse(int index, double mouseX) {
        Live2DConfigLayout.Bounds b = layout.physicsSliders().get(index);
        float value = Live2DConfigLayout.valueAt(PHYSICS_SLIDER_MIN[index], PHYSICS_SLIDER_MAX[index],
                PHYSICS_SLIDER_STEP[index], b.left() + 8, b.width() - 16, mouseX);
        if (Float.compare(value, physicsSliderValue(index)) != 0) {
            applyPhysicsSliderValue(index, value);
            saveConfigAndPreview();
        }
    }

    private void setRenderSliderFromMouse(int index, double mouseX) {
        Live2DConfigLayout.Bounds b = layout.renderSliders().get(index);
        float value = Live2DConfigLayout.valueAt(RENDER_SLIDER_MIN[index], RENDER_SLIDER_MAX[index],
                RENDER_SLIDER_STEP[index], b.left() + 8, b.width() - 16, mouseX);
        int rounded = Math.round(value);
        if (rounded != config.render.switchFadeTicks) {
            config.render.switchFadeTicks = rounded;
            saveConfigAndPreview();
        }
    }
    private void saveConfigAndPreview() {
        try {
            store.save(config);
            Live2DHudRenderer.previewConfig(config);
            error = "";
        } catch (IOException failure) {
            error = "配置保存失败: " + failure.getMessage();
        }
    }

    public static PreviewPosition previewPosition(float anchorX, float anchorY,
            float offsetX, float offsetY, int previewWidth, int previewHeight,
            int worldReferenceWidth, int worldReferenceHeight) {
        return new PreviewPosition(anchorX * previewWidth
                + offsetX * previewWidth / Math.max(1f, worldReferenceWidth),
                anchorY * previewHeight + offsetY * previewHeight / Math.max(1f, worldReferenceHeight));
    }

    public record PreviewPosition(float x, float y) {}
}
