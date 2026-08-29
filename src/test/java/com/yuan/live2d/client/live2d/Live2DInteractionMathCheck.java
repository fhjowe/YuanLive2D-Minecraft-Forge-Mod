package com.yuan.live2d.client.live2d;

public final class Live2DInteractionMathCheck {
    private Live2DInteractionMathCheck() {}

    public static void main(String[] args) {
        check();
    }

    public static void check() {
        Live2DClientState.ModelBounds bounds = new Live2DClientState.ModelBounds(100, 80, 300, 240);
        assert Live2DInteractionMath.hitTest(200, 160, bounds)
                && !Live2DInteractionMath.hitTest(50, 50, bounds)
                && !Live2DInteractionMath.hitTest(300, 240, bounds)
                : "hit test must use the content rectangle";
        assert !Live2DInteractionMath.hitTest(100, 80, bounds) && !Live2DInteractionMath.hitTest(300, 240, bounds)
                : "hit test must exclude the far edge";
        java.util.Random random = new java.util.Random(3);
        int hits = 0;
        for (int i = 0; i < 10_000; i++) if (Live2DInteractionMath.rollChance(.6f, random)) hits++;
        assert hits > 5_500 && hits < 6_500 : "probability roll must approximate the configured chance";
    }
}
