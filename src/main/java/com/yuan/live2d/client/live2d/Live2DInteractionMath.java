package com.yuan.live2d.client.live2d;

import java.util.Random;

final class Live2DInteractionMath {
    private Live2DInteractionMath() {}

    static boolean hitTest(double mouseX, double mouseY, Live2DClientState.ModelBounds bounds) {
        return mouseX > bounds.left() && mouseX < bounds.right()
                && mouseY > bounds.top() && mouseY < bounds.bottom();
    }

    static boolean rollChance(double chance, Random random) {
        return random.nextFloat() < chance;
    }
}
