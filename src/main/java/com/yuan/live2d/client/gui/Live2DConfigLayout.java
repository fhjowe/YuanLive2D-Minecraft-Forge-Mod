package com.yuan.live2d.client.gui;

import java.util.ArrayList;
import java.util.List;

public final class Live2DConfigLayout {
    public static final int SLIDER_COUNT = 6;
    public static final int TOGGLE_COUNT = 11;
    public static final int PRESET_COUNT = 4;
    public static final int PERFORMANCE_POLICY_COUNT = 3;
    public static final int PERFORMANCE_ROW_COUNT = 2;
    public static final int VISIBILITY_COUNT = 5;
    public static final int INTERACTION_TOGGLE_COUNT = 4;
    public static final int INTERACTION_SLIDER_COUNT = 3;
    public static final int PHYSICS_TOGGLE_COUNT = 2;
    public static final int PHYSICS_SLIDER_COUNT = 2;
    public static final int RENDER_TOGGLE_COUNT = 1;
    public static final int RENDER_SLIDER_COUNT = 1;

    public record Bounds(int left, int top, int right, int bottom) {
        public int width() { return right - left; }
        public int height() { return bottom - top; }
        public boolean contains(double x, double y) {
            return x >= left && x < right && y >= top && y < bottom;
        }
        public Bounds intersect(Bounds other) {
            return new Bounds(Math.max(left, other.left), Math.max(top, other.top),
                    Math.min(right, other.right), Math.min(bottom, other.bottom));
        }
        public boolean hasArea() { return right > left && bottom > top; }
        public Bounds offset(int dy) { return new Bounds(left, top - dy, right, bottom - dy); }
    }

    private final Bounds header;
    private final Bounds leftPanel;
    private final Bounds hudPanel;
    private final Bounds preview;
    private final List<Bounds> sliders = new ArrayList<>();
    private final List<Bounds> toggles = new ArrayList<>();
    private final List<Bounds> presets = new ArrayList<>();
    private final List<Bounds> visibilityButtons = new ArrayList<>();
    private final List<Bounds> performancePolicy = new ArrayList<>();
    private final List<Bounds> performanceRows = new ArrayList<>();
    private final List<Bounds> performanceCustomButtons = new ArrayList<>();
    private final List<Bounds> interactionToggles = new ArrayList<>();
    private final List<Bounds> interactionSliders = new ArrayList<>();
    private final List<Bounds> physicsToggles = new ArrayList<>();
    private final List<Bounds> physicsSliders = new ArrayList<>();
    private final List<Bounds> renderToggles = new ArrayList<>();
    private final List<Bounds> renderSliders = new ArrayList<>();
    private final Bounds interactionSection;
    private final Bounds physicsSection;
    private final Bounds renderSection;
    private final Bounds resetPosition;
    private final Bounds modelList;
    private final Bounds modelActions;
    private final Bounds modelStatus;
    private final int scroll;
    private final int maxScroll;

    private Live2DConfigLayout(int width, int height, int scroll) {
        header = new Bounds(0, 0, width, 28);
        boolean stacked = width < 760;
        int margin = 10;
        if (stacked) {
            int leftTop = 38;
            int leftHeight = Math.max(120, height - leftTop - margin - 190);
            leftPanel = new Bounds(margin, leftTop, width - margin, Math.min(height - margin, leftTop + leftHeight));
            int previewTop = leftPanel.bottom() + margin;
            preview = new Bounds(margin, previewTop, width - margin, height - margin);
        } else {
            int leftWidth = Math.min(360, Math.max(280, width * 4 / 10));
            leftPanel = new Bounds(margin, 38, margin + leftWidth, height - margin);
            preview = new Bounds(leftPanel.right() + margin, 38, width - margin, height - margin);
        }

        int innerTop = leftPanel.top() + 30;
        int innerLeft = leftPanel.left() + 12;
        int innerWidth = leftPanel.width() - 24;
        int sliderHeight = 46;
        int gap = 8;
        List<Bounds> rawSliders = new ArrayList<>();
        for (int i = 0; i < SLIDER_COUNT; i++) {
            int top = innerTop + i * (sliderHeight + gap);
            rawSliders.add(new Bounds(innerLeft, top, innerLeft + innerWidth, top + sliderHeight));
        }
        Bounds rawResetPosition = new Bounds(innerLeft, rawSliders.get(SLIDER_COUNT - 1).bottom() + 8,
                innerLeft + innerWidth, rawSliders.get(SLIDER_COUNT - 1).bottom() + 30);
        int toggleTop = rawResetPosition.bottom() + 12;
        int toggleHeight = 18;
        List<Bounds> rawToggles = new ArrayList<>();
        for (int i = 0; i < TOGGLE_COUNT; i++) {
            int column = i / 6;
            int row = i % 6;
            int toggleWidth = (innerWidth - 8) / 2;
            int top = toggleTop + row * (toggleHeight + 6);
            rawToggles.add(new Bounds(innerLeft + column * (toggleWidth + 8), top,
                    innerLeft + column * (toggleWidth + 8) + toggleWidth, top + toggleHeight));
        }
        Bounds rawHudPanel = new Bounds(leftPanel.left(), toggleTop - 12, leftPanel.right(),
                rawToggles.get(TOGGLE_COUNT - 1).bottom() + 10);
        int presetTop = rawHudPanel.bottom() + 10;
        int presetWidth = (innerWidth - 18) / PRESET_COUNT;
        List<Bounds> rawPresets = new ArrayList<>();
        for (int i = 0; i < PRESET_COUNT; i++) {
            int left = innerLeft + i * (presetWidth + 6);
            rawPresets.add(new Bounds(left, presetTop, left + presetWidth, presetTop + 20));
        }

        int visibilityTop = rawPresets.get(PRESET_COUNT - 1).bottom() + 12;
        int visibilityWidth = (innerWidth - 8) / 2;
        List<Bounds> rawVisibilityButtons = new ArrayList<>();
        for (int i = 0; i < VISIBILITY_COUNT; i++) {
            int column = i / 4;
            int row = i % 4;
            int top = visibilityTop + row * 24;
            rawVisibilityButtons.add(new Bounds(innerLeft + column * (visibilityWidth + 8), top,
                    innerLeft + column * (visibilityWidth + 8) + visibilityWidth, top + 18));
        }
        int performanceTop = rawVisibilityButtons.get(VISIBILITY_COUNT - 1).bottom() + 12;
        int policyWidth = (innerWidth - 12) / PERFORMANCE_POLICY_COUNT;
        List<Bounds> rawPerformancePolicy = new ArrayList<>();
        for (int i = 0; i < PERFORMANCE_POLICY_COUNT; i++) {
            int left = innerLeft + i * (policyWidth + 6);
            rawPerformancePolicy.add(new Bounds(left, performanceTop, left + policyWidth, performanceTop + 20));
        }
        int rowTop = performanceTop + 26;
        int rowHeight = 42;
        List<Bounds> rawPerformanceRows = new ArrayList<>();
        List<Bounds> rawPerformanceCustomButtons = new ArrayList<>();
        for (int i = 0; i < PERFORMANCE_ROW_COUNT; i++) {
            int top = rowTop + i * (rowHeight + gap);
            Bounds row = new Bounds(innerLeft, top, innerLeft + innerWidth, top + rowHeight);
            rawPerformanceRows.add(row);
            rawPerformanceCustomButtons.add(
                    new Bounds(row.right() - 54, row.top() + 2, row.right() - 4, row.top() + 18));
        }
        int performanceBottom = rawPerformanceRows.get(PERFORMANCE_ROW_COUNT - 1).bottom();

        int sectionTitleHeight = 18;
        int sectionToggleHeight = 18;
        int sectionToggleGap = 6;
        int sectionGap = 12;
        int interactionTop = performanceBottom + sectionGap;
        int interactionTitleBottom = interactionTop + sectionTitleHeight;
        int interactionToggleTop = interactionTitleBottom + 4;
        List<Bounds> rawInteractionToggles = new ArrayList<>();
        for (int i = 0; i < INTERACTION_TOGGLE_COUNT; i++) {
            int column = i % 2;
            int row = i / 2;
            int toggleWidth = (innerWidth - 8) / 2;
            int top = interactionToggleTop + row * (sectionToggleHeight + sectionToggleGap);
            rawInteractionToggles.add(new Bounds(innerLeft + column * (toggleWidth + 8), top,
                    innerLeft + column * (toggleWidth + 8) + toggleWidth, top + sectionToggleHeight));
        }
        int interactionSliderTop = rawInteractionToggles.get(INTERACTION_TOGGLE_COUNT - 1).bottom() + 10;
        List<Bounds> rawInteractionSliders = new ArrayList<>();
        for (int i = 0; i < INTERACTION_SLIDER_COUNT; i++) {
            int top = interactionSliderTop + i * (rowHeight + gap);
            rawInteractionSliders.add(new Bounds(innerLeft, top, innerLeft + innerWidth, top + rowHeight));
        }
        Bounds rawInteractionSection = new Bounds(innerLeft, interactionTop, innerLeft + innerWidth,
                rawInteractionSliders.get(INTERACTION_SLIDER_COUNT - 1).bottom());

        int physicsTop = rawInteractionSection.bottom() + sectionGap;
        int physicsTitleBottom = physicsTop + sectionTitleHeight;
        int physicsToggleTop = physicsTitleBottom + 4;
        int physicsToggleWidth = (innerWidth - 8) / 2;
        List<Bounds> rawPhysicsToggles = new ArrayList<>();
        for (int i = 0; i < PHYSICS_TOGGLE_COUNT; i++) {
            int column = i % 2;
            int top = physicsToggleTop;
            rawPhysicsToggles.add(new Bounds(innerLeft + column * (physicsToggleWidth + 8), top,
                    innerLeft + column * (physicsToggleWidth + 8) + physicsToggleWidth,
                    top + sectionToggleHeight));
        }
        int physicsSliderTop = rawPhysicsToggles.get(PHYSICS_TOGGLE_COUNT - 1).bottom() + 10;
        List<Bounds> rawPhysicsSliders = new ArrayList<>();
        for (int i = 0; i < PHYSICS_SLIDER_COUNT; i++) {
            int top = physicsSliderTop + i * (rowHeight + gap);
            rawPhysicsSliders.add(new Bounds(innerLeft, top, innerLeft + innerWidth, top + rowHeight));
        }
        Bounds rawPhysicsSection = new Bounds(innerLeft, physicsTop, innerLeft + innerWidth,
                rawPhysicsSliders.get(PHYSICS_SLIDER_COUNT - 1).bottom());

        int renderTop = rawPhysicsSection.bottom() + sectionGap;
        int renderTitleBottom = renderTop + sectionTitleHeight;
        int renderToggleTop = renderTitleBottom + 4;
        int renderToggleWidth = (innerWidth - 8) / 2;
        List<Bounds> rawRenderToggles = new ArrayList<>();
        rawRenderToggles.add(new Bounds(innerLeft, renderToggleTop, innerLeft + renderToggleWidth,
                renderToggleTop + sectionToggleHeight));
        int renderSliderTop = rawRenderToggles.get(RENDER_TOGGLE_COUNT - 1).bottom() + 10;
        List<Bounds> rawRenderSliders = new ArrayList<>();
        rawRenderSliders.add(new Bounds(innerLeft, renderSliderTop, innerLeft + innerWidth,
                renderSliderTop + rowHeight));
        Bounds rawRenderSection = new Bounds(innerLeft, renderTop, innerLeft + innerWidth,
                rawRenderSliders.get(RENDER_SLIDER_COUNT - 1).bottom());

        int modelListTop = rawRenderSection.bottom() + 12;
        int available = leftPanel.height() - (modelListTop - leftPanel.top()) - 64;
        int modelListHeight = Math.max(80, Math.min(160, available));
        Bounds rawModelList = new Bounds(innerLeft, modelListTop, innerLeft + innerWidth,
                modelListTop + modelListHeight);
        Bounds rawModelActions = new Bounds(innerLeft, rawModelList.bottom() + 8, innerLeft + innerWidth,
                rawModelList.bottom() + 30);
        Bounds rawModelStatus = new Bounds(innerLeft, rawModelActions.bottom() + 4, innerLeft + innerWidth,
                rawModelActions.bottom() + 22);

        maxScroll = Math.max(0, rawModelStatus.bottom() + 8 - leftPanel.bottom());
        this.scroll = Math.max(0, Math.min(scroll, maxScroll));
        int offset = this.scroll;
        rawSliders.forEach(bounds -> sliders.add(bounds.offset(offset)));
        rawToggles.forEach(bounds -> toggles.add(bounds.offset(offset)));
        rawPresets.forEach(bounds -> presets.add(bounds.offset(offset)));
        rawVisibilityButtons.forEach(bounds -> visibilityButtons.add(bounds.offset(offset)));
        rawPerformancePolicy.forEach(bounds -> performancePolicy.add(bounds.offset(offset)));
        rawPerformanceRows.forEach(bounds -> performanceRows.add(bounds.offset(offset)));
        rawPerformanceCustomButtons.forEach(bounds -> performanceCustomButtons.add(bounds.offset(offset)));
        rawInteractionToggles.forEach(bounds -> interactionToggles.add(bounds.offset(offset)));
        rawInteractionSliders.forEach(bounds -> interactionSliders.add(bounds.offset(offset)));
        rawPhysicsToggles.forEach(bounds -> physicsToggles.add(bounds.offset(offset)));
        rawPhysicsSliders.forEach(bounds -> physicsSliders.add(bounds.offset(offset)));
        rawRenderToggles.forEach(bounds -> renderToggles.add(bounds.offset(offset)));
        rawRenderSliders.forEach(bounds -> renderSliders.add(bounds.offset(offset)));
        resetPosition = rawResetPosition.offset(offset);
        hudPanel = rawHudPanel.offset(offset);
        interactionSection = rawInteractionSection.offset(offset);
        physicsSection = rawPhysicsSection.offset(offset);
        renderSection = rawRenderSection.offset(offset);
        modelList = rawModelList.offset(offset);
        modelActions = rawModelActions.offset(offset);
        modelStatus = rawModelStatus.offset(offset);
    }

    public static Live2DConfigLayout of(int width, int height, int scroll) {
        return new Live2DConfigLayout(width, height, scroll);
    }

    public Bounds header() { return header; }
    public Bounds leftPanel() { return leftPanel; }
    public Bounds hudPanel() { return hudPanel; }
    public Bounds preview() { return preview; }
    public List<Bounds> sliders() { return sliders; }
    public List<Bounds> toggles() { return toggles; }
    public List<Bounds> presets() { return presets; }
    public List<Bounds> visibilityButtons() { return visibilityButtons; }
    public List<Bounds> performancePolicy() { return performancePolicy; }
    public List<Bounds> performanceRows() { return performanceRows; }
    public List<Bounds> performanceCustomButtons() { return performanceCustomButtons; }
    public Bounds interactionSection() { return interactionSection; }
    public Bounds physicsSection() { return physicsSection; }
    public Bounds renderSection() { return renderSection; }
    public List<Bounds> interactionToggles() { return interactionToggles; }
    public List<Bounds> interactionSliders() { return interactionSliders; }
    public List<Bounds> physicsToggles() { return physicsToggles; }
    public List<Bounds> physicsSliders() { return physicsSliders; }
    public List<Bounds> renderToggles() { return renderToggles; }
    public List<Bounds> renderSliders() { return renderSliders; }
    public Bounds resetPosition() { return resetPosition; }
    public Bounds modelList() { return modelList; }
    public Bounds modelActions() { return modelActions; }
    public Bounds modelStatus() { return modelStatus; }
    public int scroll() { return scroll; }
    public int maxScroll() { return maxScroll; }

    public static float valueAt(float min, float max, float step, int left, int width, double mouseX) {
        float ratio = (float) Math.max(0, Math.min(1, (mouseX - left) / Math.max(1, width)));
        float value = min + ratio * (max - min);
        if (step <= 0) return value;
        return Math.round(value / step) * step;
    }

    public static float ratio(float min, float max, float value) {
        return (value - min) / Math.max(1e-6f, max - min);
    }
}
