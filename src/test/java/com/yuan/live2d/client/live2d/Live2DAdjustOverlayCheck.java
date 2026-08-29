package com.yuan.live2d.client.live2d;

import java.nio.file.Files;
import java.nio.file.Path;

public final class Live2DAdjustOverlayCheck {
    private Live2DAdjustOverlayCheck() {}

    public static void main(String[] args) throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/yuan/live2d/client/live2d/Live2DAdjustOverlay.java"));
        assert source.contains("public static void render(") : "overlay must expose render";
        assert source.contains("Live2DAdjustLayout.bar(") : "overlay must draw the action bar";
        assert source.contains("drawWindow") : "overlay must draw the window frame";
        assert source.contains("confirmPending") : "overlay must draw the unsaved dialog";
        assert source.contains("drawGuides") : "overlay must draw snap guides";
        assert source.contains("Live2DAdjustMath.BAR_HEIGHT")
                : "bottom snap guide must avoid the action bar";
        assert source.contains("modelStatus()") : "overlay must show model errors";
        String hud = Files.readString(Path.of("src/main/java/com/yuan/live2d/client/live2d/Live2DHudRenderer.java"));
        assert hud.contains("Live2DAdjustOverlay.render(") : "renderer must call the overlay";
    }
}
