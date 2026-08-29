package com.yuan.live2d.client.live2d;

public final class Live2DAdjustMathCheck {
    private Live2DAdjustMathCheck() {}

    public static void main(String[] args) {
        check();
    }

    public static void check() {
        assert Live2DAdjustMath.clampScale(3f) == Live2DAdjustMath.SCALE_MAX;
        assert Live2DAdjustMath.clampScale(0.01f) == Live2DAdjustMath.SCALE_MIN;
        assert Live2DAdjustMath.clampScale(Float.NaN) == Live2DAdjustMath.SCALE_MIN;

        Live2DAdjustMath.Bounds window = new Live2DAdjustMath.Bounds(100, 80, 300, 240);
        assert window.width() == 200 && window.height() == 160;
        assert Live2DAdjustMath.hit(300, 160, window) == Live2DAdjustMath.Handle.RIGHT;
        assert Live2DAdjustMath.hit(100, 80, window) == Live2DAdjustMath.Handle.TOP_LEFT;
        assert Live2DAdjustMath.hit(200, 160, window) == Live2DAdjustMath.Handle.BODY
                : "window interior must be a body drag";
        assert Live2DAdjustMath.hit(101, 160, window) == Live2DAdjustMath.Handle.MOVE
                : "window border must be a move handle";
        assert Live2DAdjustMath.hit(500, 500, window) == Live2DAdjustMath.Handle.NONE;

        assert Math.abs(Live2DAdjustMath.freeResize(1f, 100f) - 1.2f) < 1e-4f;
        assert Live2DAdjustMath.freeResize(1.99f, 100f) == Live2DAdjustMath.SCALE_MAX;
        assert Math.abs(Live2DAdjustMath.aspectResize(1f, 100f, 150f) - 1.5f) < 1e-4f;
        assert Live2DAdjustMath.aspectResize(1.5f, 100f, 200f) == Live2DAdjustMath.SCALE_MAX;
        assert Live2DAdjustMath.nudgePosition(1f, 10f) == 11f;

        Live2DAdjustMath.SnapResult snap = Live2DAdjustMath.snap(window, 854, 480);
        assert !snap.x() : "window 100px from left must not snap";
        Live2DAdjustMath.Bounds nearLeftWindow = new Live2DAdjustMath.Bounds(5, 80, 205, 240);
        Live2DAdjustMath.SnapResult nearSnap = Live2DAdjustMath.snap(nearLeftWindow, 854, 480);
        assert nearSnap.x() && Math.abs(nearSnap.dx() - (-5f)) < 1e-4f && nearSnap.guideX() == 0f
                : "window within threshold must snap to screen left";
        Live2DAdjustMath.Bounds rightWindow = new Live2DAdjustMath.Bounds(760, 100, 854, 200);
        Live2DAdjustMath.SnapResult rightSnap = Live2DAdjustMath.snap(rightWindow, 854, 480);
        assert rightSnap.x() && Math.abs(rightSnap.dx() - 0f) < 1e-4f
                : "right edge already flush must stay flush";
        Live2DAdjustMath.Bounds centerWindow = new Live2DAdjustMath.Bounds(420, 220, 434, 260);
        Live2DAdjustMath.SnapResult centerSnap = Live2DAdjustMath.snap(centerWindow, 854, 480);
        assert centerSnap.x() && centerSnap.y()
                : "window near both center lines must snap on both axes";

        Live2DAdjustMath.Bounds insideWindow = new Live2DAdjustMath.Bounds(100, 80, 300, 240);
        Live2DAdjustMath.Bounds kept = Live2DAdjustMath.clampToArea(insideWindow, 854, 450);
        assert kept.left() == 100f && kept.top() == 80f && kept.right() == 300f && kept.bottom() == 240f
                : "window fully inside the area must stay put";
        Live2DAdjustMath.Bounds overflow = new Live2DAdjustMath.Bounds(-40, -20, 300, 240);
        Live2DAdjustMath.Bounds clamped = Live2DAdjustMath.clampToArea(overflow, 854, 450);
        assert clamped.left() == 0f && clamped.top() == 0f
                : "window overflowing the top-left must clamp to the area origin";
        Live2DAdjustMath.Bounds below = new Live2DAdjustMath.Bounds(100, 400, 300, 560);
        Live2DAdjustMath.Bounds clampedBelow = Live2DAdjustMath.clampToArea(below, 854, 450);
        assert clampedBelow.bottom() == 450f
                : "window overflowing the bottom must clamp to the area";
        Live2DAdjustMath.Bounds huge = new Live2DAdjustMath.Bounds(100, 100, 2000, 900);
        Live2DAdjustMath.Bounds clampedHuge = Live2DAdjustMath.clampToArea(huge, 854, 450);
        assert clampedHuge.left() <= 0f && clampedHuge.right() >= 854f
                && clampedHuge.top() <= 0f && clampedHuge.bottom() >= 450f
                : "oversized window must keep covering the area";

        Live2DAdjustLayout.Bar bar = Live2DAdjustLayout.bar(854, 480);
        assert bar.bar().bottom() == 480;
        assert bar.target().left() >= 0 && bar.back().right() <= 854;
        assert !bar.target().intersect(bar.save())
                && !bar.save().intersect(bar.apply())
                && !bar.apply().intersect(bar.back())
                : "bar buttons must not overlap";
        Live2DAdjustMath.Bounds dialog = Live2DAdjustLayout.confirmDialog(854, 480);
        assert dialog.contains(427, 240) && dialog.width() == 320 && dialog.height() == 120;
    }
}
