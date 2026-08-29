package com.yuan.live2d.client.live2d;

public final class Live2DViewTrackerCheck {
    private Live2DViewTrackerCheck() {}

    public static void main(String[] args) {
        check();
    }

    public static void check() {
        Live2DViewTracker tracker = new Live2DViewTracker();
        for (int i = 0; i < 60; i++) tracker.update(-8f, 0f, .6f, 1f / 60f); // 每帧左转 8°
        assert tracker.lookX() < -0.9f : "look must accumulate toward the left";
        for (int i = 0; i < 60; i++) tracker.update(0f, 6f, .6f, 1f / 60f);
        assert tracker.lookY() < -0.9f : "pitch up must turn the head up";
        for (int i = 0; i < 240; i++) tracker.update(0f, 0f, .6f, 1f / 60f);
        assert Math.abs(tracker.lookX()) < .05f && Math.abs(tracker.lookY()) < .05f
                : "still view must return the head to center";

        Live2DViewTracker zeroStrength = new Live2DViewTracker();
        for (int i = 0; i < 60; i++) zeroStrength.update(-8f, 0f, .6f, 1f / 60f);
        assert zeroStrength.lookX() < -0.9f : "look must accumulate before strength drops to zero";
        for (int i = 0; i < 60; i++) zeroStrength.update(-8f, 0f, 0f, 1f / 60f);
        assert Math.abs(zeroStrength.lookX()) < .9f
                : "zero strength must still decay the look back to center";

        Live2DViewTracker timeBase = new Live2DViewTracker();
        for (int i = 0; i < 60; i++) timeBase.update(-8f, 0f, .6f, 1f / 60f);
        float lookBeforeDecay = timeBase.lookX();
        timeBase.update(0f, 0f, 0f, 1f / 60f);
        float decayOneFrame = timeBase.lookX() - lookBeforeDecay;
        timeBase.update(0f, 0f, 0f, 1f);
        float decayOneSecond = timeBase.lookX() - lookBeforeDecay;
        assert decayOneSecond > decayOneFrame * 10f
                : "delta seconds must drive decay instead of treating partialTick as seconds";
    }
}
