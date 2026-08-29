package com.yuan.live2d.client.live2d;

import java.nio.file.Files;
import java.nio.file.Path;

public final class Live2DHudRendererCheck {
    private Live2DHudRendererCheck() {}

    public static void main(String[] args) throws Exception {
        check();
    }

    public static void check() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/yuan/live2d/client/live2d/Live2DHudRenderer.java"));
        assert source.contains("InputEvent.MouseButton.Pre")
                && source.contains("Live2DInteractionMath.hitTest(")
                && source.contains("Live2DInteractionMath.rollChance(")
                && source.contains("Live2DInteractionScheduler.FLAG_CLICK_REACTION")
                && source.contains("clickReactionEnabled")
                : "click reaction must be wired with hit-test and probability";
        assert source.contains("if (!interaction.randomMotionEnabled) flags &= ~Live2DInteractionScheduler.FLAG_RANDOM_MOTION;")
                && source.contains("if (!interaction.randomExpressionEnabled) flags &= ~Live2DInteractionScheduler.FLAG_RANDOM_EXPRESSION;")
                : "random toggles must gate scheduler flags before world control";
        assert source.contains("Live2DViewTracker") && source.contains("getViewYRot(")
                && source.contains("viewFollowEnabled")
                : "view follow must be wired with the view tracker";
        assert source.contains("getDeltaFrameTime()")
                : "view tracker must receive real frame seconds";
        assert !source.contains("updateFollow(")
                : "position follow code must be removed from the renderer";
    }
}
