package com.yuan.live2d.client.gui;

public final class Live2DConfigLayoutCheck {
    private Live2DConfigLayoutCheck() {}

    public static void main(String[] args) {
        check();
    }

    public static void check() {
        Live2DConfigLayout layout = Live2DConfigLayout.of(854, 480, 0);
        assert layout.header().top() == 0 && layout.header().height() == 28;
        assert layout.leftPanel().left() >= 8 && layout.leftPanel().top() >= 34;
        assert layout.preview().right() <= 854 && layout.preview().bottom() <= 480;
        assert !layout.leftPanel().intersect(layout.preview()).hasArea() : "panels must not overlap";
        assert layout.sliders().size() == Live2DConfigLayout.SLIDER_COUNT;
        assert layout.toggles().size() == Live2DConfigLayout.TOGGLE_COUNT;
        assert layout.presets().size() == Live2DConfigLayout.PRESET_COUNT;
        assert layout.performancePolicy().size() == Live2DConfigLayout.PERFORMANCE_POLICY_COUNT;
        assert layout.performanceRows().size() == Live2DConfigLayout.PERFORMANCE_ROW_COUNT;
        assert layout.performanceCustomButtons().size() == Live2DConfigLayout.PERFORMANCE_ROW_COUNT;
        assert layout.visibilityButtons().size() == Live2DConfigLayout.VISIBILITY_COUNT;
        assert layout.resetPosition().bottom() <= layout.leftPanel().bottom();
        assert !layout.resetPosition().intersect(layout.sliders().get(Live2DConfigLayout.SLIDER_COUNT - 1)).hasArea();
        assert !layout.resetPosition().intersect(layout.toggles().get(0)).hasArea();
        assert !layout.performanceRows().get(0).intersect(layout.performanceRows().get(1)).hasArea();
        assert !layout.performancePolicy().get(0).intersect(layout.performanceRows().get(0)).hasArea();
        assert Live2DConfigLayout.valueAt(0f, 1f, .01f, 100, 100, 150) == .5f;
        assert Live2DConfigLayout.valueAt(-4096f, 4096f, 1f, 100, 100, 150) == 0f;
        assert Live2DConfigLayout.ratio(0f, 1f, .5f) == .5f;
        assert Live2DConfigLayout.valueAt(0f, 1f, .01f, 100, 100, 100) == 0f;
        assert Live2DConfigLayout.valueAt(0f, 1f, .01f, 100, 100, 200) == 1f;

        Live2DConfigLayout stacked = Live2DConfigLayout.of(427, 240, 0);
        assert stacked.leftPanel().right() <= 427 && stacked.preview().bottom() <= 240;
        assert !stacked.leftPanel().intersect(stacked.preview()).hasArea();

        Live2DConfigLayout scrolled = Live2DConfigLayout.of(854, 480, 1000);
        assert scrolled.modelList().right() <= 854 && scrolled.modelActions().right() <= 854
                && scrolled.modelStatus().right() <= 854;
        assert scrolled.modelStatus().bottom() <= scrolled.leftPanel().bottom();
        assert scrolled.performanceRows().get(Live2DConfigLayout.PERFORMANCE_ROW_COUNT - 1).bottom() <= scrolled.leftPanel().bottom();
        assert !scrolled.modelList().intersect(scrolled.modelActions()).hasArea();
        assert !scrolled.modelActions().intersect(scrolled.modelStatus()).hasArea();
        assert layout.interactionSection() != null && layout.physicsSection() != null
                && layout.renderSection() != null : "new config sections must exist";
        assert !layout.interactionSection().intersect(layout.physicsSection()).hasArea()
                : "interaction and physics sections must not overlap";
        assert !layout.physicsSection().intersect(layout.renderSection()).hasArea()
                : "physics and render sections must not overlap";
        assert !layout.interactionSection().intersect(
                layout.performanceRows().get(Live2DConfigLayout.PERFORMANCE_ROW_COUNT - 1)).hasArea()
                : "interaction section must sit below performance";
        assert !layout.renderSection().intersect(layout.modelList()).hasArea()
                : "render section must sit above the model list";
        assert layout.interactionToggles().size() == Live2DConfigLayout.INTERACTION_TOGGLE_COUNT;
        assert layout.interactionSliders().size() == Live2DConfigLayout.INTERACTION_SLIDER_COUNT;
        assert layout.interactionToggles().get(1).top() == layout.interactionToggles().get(0).top()
                : "view-follow toggle must share the first toggle row";
        assert layout.interactionSliders().get(1).top() > layout.interactionSliders().get(0).bottom()
                : "sliders must stack below the toggles";
        assert layout.interactionToggles().get(Live2DConfigLayout.INTERACTION_TOGGLE_COUNT - 1).bottom()
                <= layout.interactionSection().bottom() : "all interaction toggles must stay in the section";
        assert layout.interactionSliders().get(Live2DConfigLayout.INTERACTION_SLIDER_COUNT - 1).bottom()
                <= layout.interactionSection().bottom() : "all interaction sliders must stay in the section";
        assert layout.physicsToggles().size() == Live2DConfigLayout.PHYSICS_TOGGLE_COUNT;
        assert layout.physicsSliders().size() == Live2DConfigLayout.PHYSICS_SLIDER_COUNT;
        assert layout.renderToggles().size() == Live2DConfigLayout.RENDER_TOGGLE_COUNT;
        assert layout.renderSliders().size() == Live2DConfigLayout.RENDER_SLIDER_COUNT;
        assert scrolled.renderSection().bottom() <= scrolled.leftPanel().bottom()
                : "render section must be reachable by scroll";
    }
}
