package com.yuan.live2d.client.gui;

import java.nio.file.Files;
import java.nio.file.Path;

public final class Live2DOverrideScreenCheck {
    private Live2DOverrideScreenCheck() {}

    public static void main(String[] args) throws Exception {
        check();
    }

    public static void check() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/yuan/live2d/client/gui/Live2DOverrideScreen.java"));
        assert source.contains("public final class Live2DOverrideScreen extends Screen") : "must be an independent Screen";
        assert source.contains("Live2DConfig.ModelOverride") : "must edit field-level overrides";
        assert source.contains("config.modelOverrides.get(model.id())") : "must edit the selected model entry";
        assert source.contains("store.save(config)") : "changes must save immediately";
        assert source.contains("Live2DHudRenderer.previewConfig(config)") : "preview must follow config";
        assert source.contains("Live2DHudRenderer.renderPreview") : "screen must render a live preview";
        assert source.contains("copyFromGlobal") : "copy-from-global action must exist";
        assert source.contains("clearOverride") : "clear-override action must exist";
        assert source.contains("effectiveGlobal") : "preview must use effective settings";
        assert source.contains("Live2DOverrideLayout.of(") : "screen must use the override layout";
        assert source.contains("hideDelay") : "hide delay must be editable";
        assert source.contains("VISIBILITY_LABELS") : "visibility buttons must exist";
        assert source.contains("GLFW.GLFW_KEY_ESCAPE") : "Esc must return to the config screen";
        assert source.contains("layout.status()") : "status must use layout bounds";
        assert source.contains("leftPanel().contains(mouseX, mouseY)")
                : "clicks outside the left panel must not hit hidden controls";
    }
}
