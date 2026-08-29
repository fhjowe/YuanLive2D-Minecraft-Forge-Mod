package com.yuan.live2d.client.live2d;

public final class Live2DPhysicsMathCheck {
    private Live2DPhysicsMathCheck() {}

    public static void main(String[] args) {
        check();
    }

    public static void check() {
        Live2DOverlayPhysics physics = new Live2DOverlayPhysics();
        physics.kickBounce(1f);
        assert Live2DOverlayPhysics.evadeAllowed(false)
                && !Live2DOverlayPhysics.evadeAllowed(true)
                : "cursor evade must only run while the mouse is not grabbed";
        assert physics.vy < 0f && physics.bounceScale == 1.06f;
        float startY = physics.dy;
        for (int i = 0; i < 120; i++) physics.tick(1f / 60f, 1f);
        assert Math.abs(physics.dy) < .5f && Math.abs(physics.dx) < .5f
                : "spring must settle back to the saved position";
        physics.decay(5f);
        assert Math.abs(physics.bounceScale - 1f) < .01f
                && Math.abs(physics.squashX - 1f) < .01f
                && Math.abs(physics.squashY - 1f) < .01f
                : "effects must decay to identity";

        Live2DOverlayPhysics evadePhysics = new Live2DOverlayPhysics();
        Live2DClientState.ModelBounds near = new Live2DClientState.ModelBounds(100, 100, 200, 200);
        evadePhysics.evade(140, 140, near, 1f, 854, 480);   // 鼠标在本体边缘内不推离
        assert Math.abs(evadePhysics.dx) < 1f && Math.abs(evadePhysics.dy) < 1f;
        Live2DOverlayPhysics outside = new Live2DOverlayPhysics();
        outside.evade(30, 150, near, 1f, 854, 480);          // 鼠标在外扩区内左侧
        assert outside.vx > 0f : "nearby cursor must push the model away";
        Live2DOverlayPhysics squash = new Live2DOverlayPhysics();
        squash.edgeSquash(new Live2DClientState.ModelBounds(-5, 100, 100, 200), 854, 480, true);
        assert squash.squashX == .92f && squash.squashY == 1.08f
                : "touching the left edge must squash";
        Live2DOverlayPhysics disabled = new Live2DOverlayPhysics();
        disabled.edgeSquash(new Live2DClientState.ModelBounds(-5, 100, 100, 200), 854, 480, false);
        assert disabled.squashX == 1f && disabled.squashY == 1f;

        Live2DOverlayPhysics disabledSettle = new Live2DOverlayPhysics();
        disabledSettle.kickBounce(1f);
        disabledSettle.evade(30, 150, near, 1f, 854, 480);
        for (int i = 0; i < 240; i++) disabledSettle.settle(1f / 60f);
        assert Math.abs(disabledSettle.dx) < .5f && Math.abs(disabledSettle.dy) < .5f
                : "disabled settle must return displacement to the saved position";

        Live2DOverlayPhysics edgeDecay = new Live2DOverlayPhysics();
        edgeDecay.edgeSquash(new Live2DClientState.ModelBounds(-5, 100, 100, 200), 854, 480, true);
        assert edgeDecay.squashX == .92f && edgeDecay.squashY == 1.08f;
        edgeDecay.edgeSquash(new Live2DClientState.ModelBounds(100, 100, 200, 200), 854, 480, true);
        assert edgeDecay.squashX == .92f && edgeDecay.squashY == 1.08f
                : "leaving the edge must not snap squash back to identity";
        edgeDecay.decay(1f / 60f);
        assert edgeDecay.squashX > .92f && edgeDecay.squashX < 1f
                && edgeDecay.squashY > 1f && edgeDecay.squashY < 1.08f
                : "squash must decay exponentially instead of snapping";
        for (int i = 0; i < 240; i++) edgeDecay.decay(1f / 60f);
        assert Math.abs(edgeDecay.squashX - 1f) < .01f && Math.abs(edgeDecay.squashY - 1f) < .01f
                : "squash must settle back to identity after decay";

    }
}
