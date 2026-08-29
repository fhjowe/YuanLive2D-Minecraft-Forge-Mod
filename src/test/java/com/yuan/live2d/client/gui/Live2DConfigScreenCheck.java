package com.yuan.live2d.client.gui;

import java.nio.file.Files;
import java.nio.file.Path;

public final class Live2DConfigScreenCheck {
    private Live2DConfigScreenCheck() {}

    public static void main(String[] args) throws Exception {
        check();
    }

    public static void check() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/yuan/live2d/client/gui/Live2DConfigScreen.java"));
        assert source.contains("public final class Live2DConfigScreen extends Screen")
                : "must be an independent Screen";
        assert source.contains("Live2DHudRenderer.beginPreview(false)") : "preview must start on init";
        assert source.contains("Live2DHudRenderer.reloadConfig()")
                : "init must reload runtime config so world-adjust saves take effect";
        assert source.contains("Live2DHudRenderer.endPreview()") : "preview must stop on close";
        assert source.contains("new Live2DConfigStore(Live2DPaths.root())") : "must own a config store";
        assert source.contains("store.save(config)") : "config changes must save immediately";
        assert source.contains("Live2DHudRenderer.previewConfig(config)") : "preview must follow config";
        assert source.contains("new Live2DClientState.PreviewFrame(") : "preview must render a frame";
        assert source.contains("Live2DConfigLayout.of(") : "screen must use the standalone layout";
        assert source.contains("Live2DModelRegistry") : "screen must own a model registry";
        assert source.contains("Live2DModelManager") : "screen must own the model manager";
        assert source.contains("config.global.") : "sliders must edit v2 global config";
        assert source.contains("config.global.visibility") : "visibility buttons must edit v2 global visibility";
        assert source.contains("Live2DConfig.Visibility.values()") : "visibility buttons must map enum order";
        assert source.contains("resetPosition") : "reset position button must be wired";
        assert source.contains("config.hud.") : "HUD toggles must edit v2 HUD config";
        assert source.contains("config.performance.policy") : "policy buttons must edit performance policy";
        assert source.contains("config.performance.maxVisibleInstances") : "max instances must be editable";
        assert source.contains("config.performance.textureMemoryBudgetMiB") : "texture budget must be editable";
        assert source.contains("Live2DConfig.PerformancePolicy.values()") : "policy buttons must map enum order";
        assert source.contains("performanceCustom") && source.contains("charTyped")
                : "custom numeric input must be editable";
        assert source.contains("registry.invalidate()") : "model mutations must invalidate the registry";
        assert source.contains("importSources") : "import action must use the model manager";
        assert source.contains("deletePackageAsync") : "delete action must use the model manager";
        assert source.contains("覆盖") : "override entry button must exist";
        assert source.contains("new Live2DOverrideScreen(") : "override button must open the override screen";
        assert source.contains("config.modelOverrides.remove(deletedId)")
               : "deleting a model must clear its override";
        assert source.contains("调整") : "adjust entry button must exist";
        assert source.contains("Live2DAdjustMode.enter(") : "adjust button must enter adjust mode";
        assert source.contains("Live2DAdjustMode.exitQuietly()")
                : "opening the config screen must leave adjust mode";
        assert source.contains("actionSegment(") : "model actions must share segment math";
        assert source.contains("estimateTextures") : "screen must compute texture estimates";
        assert source.contains("超预算") : "over-budget hint must be rendered";
        assert source.contains("plainSubstrByWidth") : "long model names must be truncated";
        assert source.indexOf("!status.error().isEmpty()") < source.indexOf("未选择模型")
                : "load errors must be shown before the no-model placeholder";
        assert source.contains("font.width(prefix)")
                : "name truncation must reserve the selection prefix width";
        assert source.contains("estimatedModelIds")
                : "estimates must not be recomputed on every resize";
        assert source.contains("if (modelBusy || registry.selected() == null)")
                : "override entry must be gated while model operations are busy";
        assert source.contains("applyPreset") : "preset buttons must be wired";
        assert source.contains("Live2DConfigLayout.TOGGLE_COUNT") || source.contains("TOGGLE_COUNT")
                : "screen must use layout counts";
        assert source.contains("Live2DConfigLayout.PRESET_COUNT") || source.contains("PRESET_COUNT")
                : "screen must use layout counts";
        assert source.contains("modelStatus()") : "preview must show model status";
        assert source.contains("renderBackground(g)") : "screen must paint a full background";
        assert source.contains("config.global.selectedModelId = \"\";")
                : "deleting the last model must clear the selected id";
        assert source.contains("g.enableScissor(preview.left(), preview.top(), preview.right(), preview.bottom())")
                : "native preview must be scissored to the preview panel";
        assert source.contains("previewPosition(") : "preview placement math must be present";
        assert source.contains("effectiveGlobal") : "screen preview must use effective per-model settings";
        assert !source.contains("Live2DConsolePage") && !source.contains("Live2DConsoleLayout")
                : "old console classes must not be referenced";
        assert !source.contains("live2dAdjustHud") && !source.contains("YuanClientPreferences")
                : "HUD preferences must migrate into Live2DConfig";
        assert source.contains("config.interaction.viewFollowEnabled")
                && source.contains("config.interaction.viewFollowStrength")
                && source.contains("config.interaction.randomMotionIntervalSeconds")
                && source.contains("config.interaction.clickReactionChance")
                : "interaction section must be wired";
        assert !source.contains("mouseFollow")
                : "dead mouse-follow controls must be removed from the interaction section";
        assert !source.contains("modelFollow")
                : "position-follow controls must be removed from the interaction section";
        assert source.contains("config.physics.amplitude")
                && source.contains("config.physics.strength")
                && source.contains("config.physics.edgeSquash")
                : "physics section must be wired";
        assert source.contains("config.render.shadowEnabled")
                && source.contains("config.render.switchFadeTicks")
                : "render section must be wired";
    }
}
