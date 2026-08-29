package com.yuan.live2d.client.gui;

public final class Live2DOverrideLayoutCheck {
    private Live2DOverrideLayoutCheck() {}

    public static void main(String[] args) {
        check();
    }

    public static void check() {
        Live2DOverrideLayout layout = Live2DOverrideLayout.of(854, 480, 0);
        assert layout.header().top() == 0 && layout.header().height() == 28;
        assert layout.leftPanel().top() >= 34;
        assert !layout.leftPanel().intersect(layout.preview()).hasArea() : "panels must not overlap";
        assert layout.rows().size() == Live2DOverrideLayout.FIELD_COUNT;
        assert layout.modeButtons().size() == Live2DOverrideLayout.FIELD_COUNT;
        assert layout.controls().size() == Live2DOverrideLayout.FIELD_COUNT;
        assert layout.visibilityButtons().size() == Live2DOverrideLayout.VISIBILITY_COUNT;
        for (int i = 0; i < Live2DOverrideLayout.FIELD_COUNT; i++) {
            assert layout.rows().get(i).contains(layout.modeButtons().get(i).left(), layout.modeButtons().get(i).top());
            assert layout.controls().get(i).right() <= layout.rows().get(i).right();
            assert !layout.modeButtons().get(i).intersect(layout.controls().get(i)).hasArea();
        }
        assert !layout.copyFromGlobal().intersect(layout.clearOverride()).hasArea();
        assert !layout.clearOverride().intersect(layout.back()).hasArea();
        assert layout.status().bottom() <= layout.leftPanel().bottom();

        Live2DOverrideLayout stacked = Live2DOverrideLayout.of(427, 240, 0);
        assert stacked.leftPanel().right() <= 427 && stacked.preview().bottom() <= 240;
        assert !stacked.leftPanel().intersect(stacked.preview()).hasArea();

        Live2DOverrideLayout scrolled = Live2DOverrideLayout.of(854, 320, 1000);
        assert scrolled.status().bottom() <= scrolled.leftPanel().bottom();
        assert scrolled.visibilityButtons().get(Live2DOverrideLayout.VISIBILITY_COUNT - 1).right() <= 854;
    }
}
