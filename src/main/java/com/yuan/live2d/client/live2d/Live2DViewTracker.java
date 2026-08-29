package com.yuan.live2d.client.live2d;

/**
 * Accumulates the player view rotation deltas into a clamped look direction
 * that drives the model head/body parameters.
 */
final class Live2DViewTracker {
    private static final float MAX_YAW_DEG = 30f;
    private static final float MAX_PITCH_DEG = 20f;

    private float lookDegX;
    private float lookDegY;

    void update(float yawDeltaDeg, float pitchDeltaDeg, float strength, float deltaSeconds) {
        float seconds = Math.max(0f, deltaSeconds);
        float decay = (float) Math.pow(0.985d, seconds * 60d);
        lookDegX *= decay;
        lookDegY *= decay;
        if (strength > 0f && (yawDeltaDeg != 0f || pitchDeltaDeg != 0f)) {
            lookDegX += yawDeltaDeg * strength;
            lookDegY += pitchDeltaDeg * strength;
            lookDegX = clamp(lookDegX, -MAX_YAW_DEG, MAX_YAW_DEG);
            lookDegY = clamp(lookDegY, -MAX_PITCH_DEG, MAX_PITCH_DEG);
        }
    }

    void reset() {
        lookDegX = 0f;
        lookDegY = 0f;
    }

    float lookX() {
        return lookDegX / MAX_YAW_DEG;
    }

    float lookY() {
        return -lookDegY / MAX_PITCH_DEG;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(value, max));
    }
}
