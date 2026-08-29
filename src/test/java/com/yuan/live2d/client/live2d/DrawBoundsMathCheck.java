package com.yuan.live2d.client.live2d;

public final class DrawBoundsMathCheck {
    private DrawBoundsMathCheck() {}

    public static void main(String[] args) {
        check();
    }

    public static void check() {
        // modelInsideGui_keepsRawBounds
        {
            var b = Live2DHudRenderer.buildDrawBounds(854, 480, 200f, 100f, 400f, 300f);
            assert b != null : "expected non-null DrawBounds";
            assert b.left() == 200 : "left: " + b.left();
            assert b.top() == 100 : "top: " + b.top();
            assert b.right() == 400 : "right: " + b.right();
            assert b.bottom() == 300 : "bottom: " + b.bottom();
        }

        // modelLeftOfGui_clampsToZero
        {
            var b = Live2DHudRenderer.buildDrawBounds(854, 480, -50f, 100f, 200f, 300f);
            assert b != null;
            assert b.left() == 0;
            assert b.top() == 100;
            assert b.right() == 200;
            assert b.bottom() == 300;
        }

        // modelRightOfGui_clampsToGuiWidth
        {
            var b = Live2DHudRenderer.buildDrawBounds(854, 480, 700f, 100f, 1200f, 300f);
            assert b != null;
            assert b.left() == 700;
            assert b.right() == 854;
        }

        // modelAboveGui_clampsToZero
        {
            var b = Live2DHudRenderer.buildDrawBounds(854, 480, 200f, -50f, 400f, 100f);
            assert b != null;
            assert b.top() == 0;
            assert b.bottom() == 100;
        }

        // modelBelowGui_clampsToGuiHeight
        {
            var b = Live2DHudRenderer.buildDrawBounds(854, 480, 200f, 400f, 400f, 700f);
            assert b != null;
            assert b.top() == 400;
            assert b.bottom() == 480;
        }

        // uvsAreNormalizedToGuiDimensions
        {
            var b = Live2DHudRenderer.buildDrawBounds(854, 480, 0f, 0f, 854f, 480f);
            assert b != null;
            assert Math.abs(b.u0() - 0.0f) < 1e-6f : "u0: " + b.u0();
            assert Math.abs(b.v0() - 0.0f) < 1e-6f : "v0: " + b.v0();
            assert Math.abs(b.u1() - 1.0f) < 1e-6f : "u1: " + b.u1();
            assert Math.abs(b.v1() - 1.0f) < 1e-6f : "v1: " + b.v1();
        }

        // modelEntirelyOffScreen_returnsNull
        // guiScale2_uvsSampleTheMatchingFramebufferRegion
        {
            var b = Live2DHudRenderer.buildDrawBounds(427, 240, 100f, 50f, 300f, 150f);
            assert b != null;
            assert b.left() == 100 && b.top() == 50 && b.right() == 300 && b.bottom() == 150;
            assert Math.abs(b.u0() - 100f / 427f) < 1e-6f : "u0: " + b.u0();
            assert Math.abs(b.v0() - 50f / 240f) < 1e-6f : "v0: " + b.v0();
            assert Math.abs(b.u1() - 300f / 427f) < 1e-6f : "u1: " + b.u1();
            assert Math.abs(b.v1() - 150f / 240f) < 1e-6f : "v1: " + b.v1();
        }
        assert Live2DHudRenderer.buildDrawBounds(854, 480, 1000f, 100f, 1200f, 300f) == null : "right of GUI should be null";
        assert Live2DHudRenderer.buildDrawBounds(854, 480, -500f, 100f, -100f, 300f) == null : "left of GUI should be null";
        assert Live2DHudRenderer.buildDrawBounds(854, 480, 200f, -500f, 400f, -100f) == null : "above GUI should be null";
        assert Live2DHudRenderer.buildDrawBounds(854, 480, 200f, 600f, 400f, 900f) == null : "below GUI should be null";

        // modelTouchingGuiEdge_isValid
        {
            var b = Live2DHudRenderer.buildDrawBounds(854, 480, 0f, 0f, 854f, 480f);
            assert b != null;
            assert b.left() == 0;
            assert b.top() == 0;
            assert b.right() == 854;
            assert b.bottom() == 480;
            assert b.u0() < b.u1();
            assert b.v0() < b.v1();
        }
    }
}
