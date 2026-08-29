package com.yuan.live2d.client.live2d;

final class Live2DOverlayPhysics {
    float dx;
    float dy;
    float vx;
    float vy;
    float bounceScale = 1f;
    float squashX = 1f;
    float squashY = 1f;

    static boolean evadeAllowed(boolean mouseGrabbed) {
        return !mouseGrabbed;
    }

    void tick(float deltaSeconds, float strength) {
        float impulse = .08f * strength * 60f;
        vx -= dx * impulse;
        vy -= dy * impulse;
        float damping = (float) Math.pow(.92d, deltaSeconds * 60d);
        vx *= damping;
        vy *= damping;
        dx += vx * deltaSeconds;
        dy += vy * deltaSeconds;
    }

    void settle(float deltaSeconds) {
        tick(deltaSeconds, 1f);
    }

    void kickBounce(float strength) {
        vy -= 26f * strength;
        bounceScale = 1.06f;
    }

    void evade(float mouseX, float mouseY, Live2DClientState.ModelBounds bounds,
               float strength, int guiWidth, int guiHeight) {
        float expansion = Math.max(24f, Math.min(guiWidth, guiHeight) * .15f);
        double nearLeft = bounds.left() - expansion;
        double nearTop = bounds.top() - expansion;
        double nearRight = bounds.right() + expansion;
        double nearBottom = bounds.bottom() + expansion;
        boolean near = mouseX >= nearLeft && mouseX < nearRight
                && mouseY >= nearTop && mouseY < nearBottom;
        if (!near || Live2DInteractionMath.hitTest(mouseX, mouseY, bounds)) return;
        float push = 160f * strength;
        double leftDistance = mouseX - bounds.left();
        double rightDistance = bounds.right() - mouseX;
        double topDistance = mouseY - bounds.top();
        double bottomDistance = bounds.bottom() - mouseY;
        if (leftDistance < rightDistance) vx += push;
        else vx -= push;
        if (topDistance < bottomDistance) vy += push;
        else vy -= push;
    }

    void edgeSquash(Live2DClientState.ModelBounds bounds, int guiWidth, int guiHeight, boolean enabled) {
        if (!enabled) {
            squashX = 1f;
            squashY = 1f;
            return;
        }
        boolean touches = bounds.left() <= 0f || bounds.top() <= 0f
                || bounds.right() >= guiWidth || bounds.bottom() >= guiHeight;
        if (touches) {
            squashX = .92f;
            squashY = 1.08f;
        }
    }

    void decay(float deltaSeconds) {
        float factor = (float) Math.pow(.85d, deltaSeconds * 60d);
        bounceScale = 1f + (bounceScale - 1f) * factor;
        squashX = 1f + (squashX - 1f) * factor;
        squashY = 1f + (squashY - 1f) * factor;
    }

}
