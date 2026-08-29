package com.yuan.live2d.client.live2d;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public final class Live2DClientStateCheck {
    private Live2DClientStateCheck() {}

    public static void check() throws Exception {
        Live2DModelManifest first = model("first");
        Live2DModelManifest second = model("second");
        assert Live2DClientState.select("second", List.of(first, second)) == second;
        assert Live2DClientState.select("missing", List.of(first, second)) == first;
        assert Live2DClientState.select("", List.of(first, second)) == first;
        assert Live2DClientState.select(null, List.of()) == null;

        Live2DClientState.Placement placement = Live2DClientState.placement(
                .8f, .55f, -24, -24, .45f, 960, 540, 1920, 1080);
        assert placement.x() == 1488 && placement.y() == 546 && placement.scale() == 486
                : "Live2D scale is a fraction of framebuffer height, not raw pixels";

        Live2DConfig defaults = Live2DConfig.defaults();
        assert defaults.global.anchorX == .8f && defaults.global.anchorY == .55f && defaults.global.scale == .45f;

        fallbackSaveRetries(first);
        emptyRepositoryRecovers(first);
        createRetriesAfterDelay(first);
        runtimeInitializeRetries(first);
        retryHandlesClockResetAndWrap(first);
        successfulReplacement(first, second);
        replacementAndCloseAreTransactional(first, second);
        failedReplacementCleanupIsTracked(first, second);
        pendingCloseRunsOnRenderThread(first);
        failedCloseRetries(first);
        thrownCloseIsContained(first);
        lifecycleGatesCreation();
        lifecycleRetriesCleanupOnNextFrame(first);
        closeLinearizesWithFrameAdmission();
        closePublishesPendingBeforeEndFrame(first);
        renderArgumentsAndTime(first);
        renderClampsOffscreenPlacement(first);
        modelBoundsRoutesAndScales(first);
        previewLoadsAndSharesUpdate(first);
        rejectsInvalidNativeFrames(first);
        overflowIsIsolatedByRenderPath(first);
        updateCommitsOnlyAfterSuccessfulDraw(first);
        updateThenFailCommitsOwnership(first, second);
        previewFailureDoesNotDisableWorld(first);
        replacementForcesOwnZeroDeltaUpdate(first, second);
        directPreviewIsIsolated(first);
        previewAdmissionSurvivesLogout();
        threadOwnership(first);
        reloadsAuthoritativeConfig(first);
        previewRetainsHandleForLayoutChanges(first, second);
        previewFallbackDoesNotPersist(first);
        disabledAndHiddenFramesReuseHandle(first);
        failureIsolation(first);
        cleanupFailuresAreContained(first, second);
        firstFrameSuccessResetsOnSwap(first, second);

        Live2DVisibility.InventorySnapshot snapshot = Live2DClientState.snapshot(false, true,
                true, true);
        assert snapshot.offHand() && snapshot.hotbar() && snapshot.inventory();
        assert !snapshot.mainHand();
        assert Live2DVisibility.matches(Live2DConfig.Visibility.EITHER_HAND, snapshot);
        assert Live2DVisibility.matches(Live2DConfig.Visibility.HOTBAR, snapshot);
        assert Live2DVisibility.matches(Live2DConfig.Visibility.INVENTORY, snapshot);
        assert !Live2DVisibility.matches(Live2DConfig.Visibility.MAIN_HAND, snapshot);
        overridesAffectRenderArguments(first);
        estimateRoutesThroughNative(first);
        controlRoutesToNative(first);
    }

    private static void fallbackSaveRetries(Live2DModelManifest model) {
        Live2DConfig config = Live2DConfig.defaults();
        FakeConfig configs = new FakeConfig(config);
        configs.failSaves = 1;
        FakeNative nativeOps = new FakeNative();
        Sequence clock = new Sequence(1_000_000_000L, 1_100_000_000L);
        Live2DClientState state = state(configs, () -> List.of(model), nativeOps, clock);
        state.render(frame(config));
        assert config.global.selectedModelId.isEmpty() : "failed save must not mutate live config";
        assert configs.saves == 1;
        state.render(frame(config));
        assert configs.saves == 2 && config.global.selectedModelId.isEmpty()
                && state.config().global.selectedModelId.equals("first")
                : "successful fallback must advance authoritative state without mutating caller config";
    }

    private static void emptyRepositoryRecovers(Live2DModelManifest model) {
        Live2DConfig config = Live2DConfig.defaults();
        List<Live2DModelManifest> models = new ArrayList<>();
        FakeNative nativeOps = new FakeNative();
        Live2DClientState state = state(new FakeConfig(config), () -> List.copyOf(models), nativeOps,
                new Sequence(1_000_000_000L, 2_000_000_000L));
        state.render(frame(config));
        assert nativeOps.created.isEmpty();
        models.add(model);
        state.render(frame(config));
        assert nativeOps.created.equals(List.of("first"));
    }

    private static void createRetriesAfterDelay(Live2DModelManifest model) {
        Live2DConfig config = Live2DConfig.defaults();
        config.global.selectedModelId = model.id();
        FakeNative nativeOps = new FakeNative();
        nativeOps.createResults.add(0L);
        nativeOps.createResults.add(11L);
        Sequence clock = new Sequence(1_000_000_000L, 1_500_000_000L, 2_000_000_000L, 2_100_000_000L);
        Live2DClientState state = state(new FakeConfig(config), () -> List.of(model), nativeOps, clock);
        state.render(frame(config));
        state.render(frame(config));
        assert nativeOps.created.size() == 1 : "retry delay must suppress frame spam";
        state.render(frame(config));
        assert nativeOps.created.size() == 2;
    }

    private static void runtimeInitializeRetries(Live2DModelManifest model) {
        Live2DConfig config = Live2DConfig.defaults();
        config.global.selectedModelId = model.id();
        FakeNative nativeOps = new FakeNative();
        nativeOps.initializeResults.add(false);
        nativeOps.initializeResults.add(true);
        Live2DClientState state = state(new FakeConfig(config), () -> List.of(model), nativeOps,
                new Sequence(1_000_000_000L, 1_500_000_000L, 2_000_000_000L));
        state.render(frame(config));
        state.render(frame(config));
        assert nativeOps.initializes == 1;
        state.render(frame(config));
        assert nativeOps.initializes == 2 && nativeOps.created.size() == 1;
    }

    private static void retryHandlesClockResetAndWrap(Live2DModelManifest model) {
        Live2DConfig backwards = Live2DConfig.defaults();
        backwards.global.selectedModelId = model.id();
        FakeNative backwardsNative = new FakeNative();
        backwardsNative.createResults.add(0L);
        backwardsNative.createResults.add(11L);
        Live2DClientState backwardsState = state(new FakeConfig(backwards), () -> List.of(model), backwardsNative,
                new Sequence(5_000_000_000L, 4_000_000_000L));
        backwardsState.render(frame(backwards));
        backwardsState.render(frame(backwards));
        assert backwardsNative.created.size() == 2 : "backwards clock must reset retry timing";

        Live2DConfig wrapped = Live2DConfig.defaults();
        wrapped.global.selectedModelId = model.id();
        FakeNative wrappedNative = new FakeNative();
        wrappedNative.createResults.add(0L);
        wrappedNative.createResults.add(11L);
        Live2DClientState wrappedState = state(new FakeConfig(wrapped), () -> List.of(model), wrappedNative,
                new Sequence(Long.MAX_VALUE - 500_000_000L, Long.MIN_VALUE + 600_000_000L));
        wrappedState.render(frame(wrapped));
        wrappedState.render(frame(wrapped));
        assert wrappedNative.created.size() == 2 : "nanoTime wrap must use elapsed subtraction";
    }

    private static void successfulReplacement(Live2DModelManifest first, Live2DModelManifest second) {
        Live2DConfig config = Live2DConfig.defaults();
        config.global.selectedModelId = first.id();
        FakeNative nativeOps = new FakeNative();
        nativeOps.createResults.add(11L);
        nativeOps.createResults.add(22L);
        Live2DClientState state = state(new FakeConfig(config), () -> List.of(first, second), nativeOps,
                new Sequence(1_000_000_000L, 2_000_000_000L));
        state.render(frame(config));
        assert state.activeModelId().equals(first.id());
        config.global.selectedModelId = second.id();
        state.render(frame(config));
        assert state.activeModelId().equals(second.id());
        assert nativeOps.destroyed.equals(List.of(11L));
        assert nativeOps.rendered.get(nativeOps.rendered.size() - 1).handle == 22L;
    }

    private static void replacementAndCloseAreTransactional(Live2DModelManifest first,
                                                             Live2DModelManifest second) {
        Live2DConfig config = Live2DConfig.defaults();
        config.global.selectedModelId = first.id();
        FakeNative nativeOps = new FakeNative();
        nativeOps.createResults.add(11L);
        nativeOps.createResults.add(22L);
        nativeOps.destroyResults.add(false);
        nativeOps.destroyResults.add(true);
        Live2DClientState state = state(new FakeConfig(config), () -> List.of(first, second), nativeOps,
                new Sequence(1_000_000_000L, 2_000_000_000L));
        state.render(frame(config));
        config.global.selectedModelId = second.id();
        state.render(frame(config));
        assert nativeOps.destroyed.equals(List.of(11L, 22L));
        assert nativeOps.rendered.get(nativeOps.rendered.size() - 1).handle == 11L
                : "old handle must remain active when old destruction fails";

        nativeOps.destroyResults.add(false);
        state.closeModel();
        assert state.lastError().contains("destroy failed");
        nativeOps.destroyResults.add(true);
        state.closeModel();
        assert nativeOps.destroyed.get(nativeOps.destroyed.size() - 1) == 11L
                : "failed close must retain the owned handle for retry";
    }

    private static void failedReplacementCleanupIsTracked(Live2DModelManifest first,
                                                            Live2DModelManifest second) {
        Live2DConfig config = Live2DConfig.defaults();
        config.global.selectedModelId = first.id();
        FakeNative nativeOps = new FakeNative();
        nativeOps.createResults.add(11L);
        nativeOps.createResults.add(22L);
        nativeOps.destroyResults.add(false);
        nativeOps.destroyResults.add(false);
        Live2DClientState state = state(new FakeConfig(config), () -> List.of(first, second), nativeOps,
                new Sequence(1_000_000_000L, 2_000_000_000L));
        state.render(frame(config));
        config.global.selectedModelId = second.id();
        state.render(frame(config));
        assert state.sessionDisabled();
        assert state.lastError().contains("cleanup");
        nativeOps.destroyResults.add(true);
        nativeOps.destroyResults.add(true);
        state.closeModel();
        assert nativeOps.destroyed.subList(nativeOps.destroyed.size() - 2, nativeOps.destroyed.size())
                .containsAll(List.of(11L, 22L)) : "both retained handles must be retried";
    }

    private static void pendingCloseRunsOnRenderThread(Live2DModelManifest model) throws Exception {
        Live2DConfig config = Live2DConfig.defaults();
        FakeNative nativeOps = new FakeNative();
        Live2DClientState state = state(new FakeConfig(config), () -> List.of(model), nativeOps,
                new Sequence(1_000_000_000L));
        state.render(frame(config));
        Thread requester = new Thread(state::requestClose);
        requester.start();
        requester.join();
        assert nativeOps.destroyed.isEmpty() : "requestClose must not invoke native code";
        state.processPendingClose();
        assert nativeOps.destroyed.equals(List.of(11L));
    }

    private static void failedCloseRetries(Live2DModelManifest model) {
        Live2DConfig config = Live2DConfig.defaults();
        FakeNative nativeOps = new FakeNative();
        nativeOps.destroyResults.add(false);
        nativeOps.destroyResults.add(true);
        Live2DClientState state = state(new FakeConfig(config), () -> List.of(model), nativeOps,
                new Sequence(1_000_000_000L));
        state.render(frame(config));
        state.requestClose();
        assert state.processPendingClose() && state.closePending();
        assert state.processPendingClose() && !state.closePending();
        assert nativeOps.destroyed.equals(List.of(11L, 11L));
    }

    private static void thrownCloseIsContained(Live2DModelManifest model) {
        Live2DConfig config = Live2DConfig.defaults();
        FakeNative nativeOps = new FakeNative();
        nativeOps.destroyFailure = new IllegalStateException("destroy exploded");
        Live2DClientState state = state(new FakeConfig(config), () -> List.of(model), nativeOps,
                new Sequence(1_000_000_000L));
        state.render(frame(config));
        state.requestClose();
        assert state.processPendingClose();
        assert state.closePending() && state.lastError().contains("destroy exploded");
        nativeOps.destroyFailure = null;
        assert state.processPendingClose() && !state.closePending();
        assert nativeOps.destroyed.equals(List.of(11L, 11L));
    }

    private static void lifecycleGatesCreation() {
        Live2DLifecycle<String> lifecycle = new Live2DLifecycle<>();
        int[] creates = {0};
        lifecycle.close(false, ignored -> {});
        assert lifecycle.beginFrame(() -> { creates[0]++; return "late"; }) == null;
        assert creates[0] == 0 : "close before lazy init must prevent construction";

        lifecycle.login();
        Live2DLifecycle.Admission<String> open = lifecycle.beginFrame(() -> { creates[0]++; return "open"; });
        assert open != null && "open".equals(open.state());
        assert lifecycle.endFrame(open) == null;
        lifecycle.close(false, ignored -> {});
        lifecycle.cleaned("open");
        assert lifecycle.beginFrame(() -> { creates[0]++; return "recreated"; }) == null;
        assert creates[0] == 1 : "successful cleanup must remain closed";

        lifecycle.login();
        Live2DLifecycle.Admission<String> admitted = lifecycle.beginFrame(() -> { creates[0]++; return "admitted"; });
        assert admitted != null;
        Live2DLifecycle.Close<String> close = lifecycle.close(false, ignored -> {});
        assert close.state().equals("admitted") && !close.cleanupNow();
        assert lifecycle.endFrame(admitted).equals("admitted") : "close during frame must defer cleanup to frame end";

        lifecycle.login();
        assert lifecycle.isOpen();
        lifecycle.close(true, ignored -> {});
        lifecycle.login();
        assert !lifecycle.isOpen() : "login must not reopen terminal shutdown";
    }

    private static void lifecycleRetriesCleanupOnNextFrame(Live2DModelManifest model) {
        Live2DConfig config = Live2DConfig.defaults();
        FakeNative nativeOps = new FakeNative();
        nativeOps.destroyResults.add(false);
        nativeOps.destroyResults.add(true);
        Live2DClientState state = state(new FakeConfig(config), () -> List.of(model), nativeOps,
                new Sequence(1_000_000_000L));
        Live2DLifecycle<Live2DClientState> lifecycle = new Live2DLifecycle<>();
        int[] creates = {0};
        Live2DHudRenderer.frame(lifecycle, () -> { creates[0]++; return state; }, value -> {
            value.render(frame(config));
            Live2DLifecycle.Close<Live2DClientState> close = lifecycle.close(false, Live2DClientState::requestClose);
            assert !close.cleanupNow();
        });
        assert state.closePending() : "failed frame-end cleanup must remain pending";

        Live2DHudRenderer.frame(lifecycle, () -> { creates[0]++; return state; }, value -> value.render(frame(config)));
        assert lifecycle.existing() == null : "successful frame retry must clear retained state";
        Live2DHudRenderer.frame(lifecycle, () -> { creates[0]++; return state; }, value -> value.render(frame(config)));
        assert creates[0] == 1 : "closed lifecycle must not recreate after cleanup";

        lifecycle.login();
        Live2DHudRenderer.frame(lifecycle, () -> { creates[0]++; return state; }, value -> {});
        assert creates[0] == 2 : "login must restore creation admission";
    }

    private static void closeLinearizesWithFrameAdmission() {
        Live2DLifecycle<String> lifecycle = new Live2DLifecycle<>();
        Live2DLifecycle.Admission<String> admission = lifecycle.beginFrame(() -> "state");
        assert admission != null;
        Live2DLifecycle.Close<String> close = lifecycle.close(false, ignored -> {});
        assert !close.cleanupNow() : "close must not clean concurrently with an admitted frame";
        assert lifecycle.beginFrame(() -> "late") == null : "close must block later native admission";
        assert lifecycle.endFrame(admission).equals("state") : "admitted frame owns deferred cleanup";

        Live2DLifecycle<String> closedFirst = new Live2DLifecycle<>();
        closedFirst.close(false, ignored -> {});
        assert closedFirst.beginFrame(() -> "never") == null : "close before admission must prevent native work";
    }

    private static void closePublishesPendingBeforeEndFrame(Live2DModelManifest model) {
        Live2DConfig config = Live2DConfig.defaults();
        FakeNative nativeOps = new FakeNative();
        Live2DClientState state = state(new FakeConfig(config), () -> List.of(model), nativeOps,
                new Sequence(1_000_000_000L));
        state.render(frame(config));
        Live2DLifecycle<Live2DClientState> lifecycle = new Live2DLifecycle<>();
        Live2DLifecycle.Admission<Live2DClientState> admission = lifecycle.beginFrame(() -> state);
        assert admission != null;

        Live2DLifecycle.Close<Live2DClientState> close = lifecycle.close(false, Live2DClientState::requestClose);
        assert !close.cleanupNow() && state.closePending();
        Live2DClientState cleanup = lifecycle.endFrame(admission);
        assert cleanup == state && cleanup.closePending()
                : "endFrame must observe closed retained state with pending close published";
        assert cleanup.processPendingClose() && !cleanup.closePending();
        lifecycle.cleaned(cleanup);
        assert lifecycle.existing() == null && nativeOps.destroyed.equals(List.of(11L));
    }

    private static void renderArgumentsAndTime(Live2DModelManifest model) {
        Live2DConfig config = Live2DConfig.defaults();
        config.global.selectedModelId = model.id();
        config.global.anchorX = .25f;
        config.global.anchorY = .5f;
        config.global.offsetX = 10;
        config.global.offsetY = -20;
        config.global.scale = 1.5f;
        config.global.opacity = .4f;
        FakeNative nativeOps = new FakeNative();
        Live2DClientState state = state(new FakeConfig(config), () -> List.of(model), nativeOps,
                new Sequence(1_000_000_000L, 1_050_000_000L, 5_000_000_000L));
        state.render(new Live2DClientState.Frame(config, true, 1000, 500, 2000, 1000));
        state.render(new Live2DClientState.Frame(config, true, 1000, 500, 2000, 1000));
        state.render(new Live2DClientState.Frame(config, false, 1000, 500, 2000, 1000));
        state.render(new Live2DClientState.Frame(config, true, 1000, 500, 2000, 1000));
        RenderCall first = nativeOps.rendered.get(0);
        RenderCall second = nativeOps.rendered.get(1);
        RenderCall afterHidden = nativeOps.rendered.get(2);
        assert first.delta == 0 && second.delta == .05f && afterHidden.delta == 0;
        assert first.width == 2000 && first.height == 1000;
        assert first.x == 520 && first.y == 460 && first.scale == 1500 && first.opacity == .4f;
    }

    private static void renderClampsOffscreenPlacement(Live2DModelManifest model) {
        Live2DConfig config = Live2DConfig.defaults();
        config.global.selectedModelId = model.id();
        config.global.offsetX = -5000f;
        config.global.offsetY = -5000f;
        FakeNative nativeOps = new FakeNative();
        nativeOps.relativeBounds = true;
        Live2DClientState state = state(new FakeConfig(config), () -> List.of(model), nativeOps,
                new Sequence(1_000_000_000L));
        state.render(new Live2DClientState.Frame(config, true, 960, 540, 1920, 1080));
        RenderCall call = nativeOps.rendered.get(0);
        assert call.x == 100 && call.y == 200
                : "offscreen model must be clamped into the framebuffer";
    }

    private static void modelBoundsRoutesAndScales(Live2DModelManifest model) {
        Live2DConfig config = Live2DConfig.defaults();
        FakeNative nativeOps = new FakeNative();
        Live2DClientState state = state(new FakeConfig(config), () -> List.of(model), nativeOps,
                new Sequence(1_000_000_000L));
        assert state.modelBounds(854, 480, 854, 480).isEmpty();
        state.render(new Live2DClientState.Frame(config, true, 854, 480, 854, 480));
        assert state.modelBounds(854, 480, 854, 480).orElseThrow().right() == 110;
        assert state.modelBounds(427, 240, 854, 480).orElseThrow().right() == 55;
        nativeOps.bounds = new float[]{1, 2, 3};
        assert state.modelBounds(854, 480, 854, 480).isEmpty();
        nativeOps.bounds = new float[]{Float.NaN, 2, 3, 4};
        assert state.modelBounds(854, 480, 854, 480).isEmpty();
        nativeOps.bounds = new float[]{3, 4, 1, 2};
        assert state.modelBounds(854, 480, 854, 480).isEmpty();
    }

    private static void previewLoadsAndSharesUpdate(Live2DModelManifest model) {
        Live2DConfig config = Live2DConfig.defaults();
        config.global.selectedModelId = model.id();
        FakeNative nativeOps = new FakeNative();
        Live2DClientState state = state(new FakeConfig(config), () -> List.of(model), nativeOps,
                new Sequence(1_000_000_000L, 1_050_000_000L));
        Live2DClientState.PreviewFrame preview = new Live2DClientState.PreviewFrame(
                config, 1920, 1080, 100, 200, .5f, .75f);
        nativeOps.renderResults.add(new long[]{2, 41, 1920, 1080});
        nativeOps.renderResults.add(new long[]{1, 42, 1920, 1080});
        Live2DClientState.DrawResult first = state.renderPreview(preview, true);
        Live2DClientState.DrawResult second = state.renderPreview(preview, false);
        assert first.textureId() == 41 && first.textureWidth() == 1920 && first.textureHeight() == 1080;
        assert first.hasTexture();
        assert second.textureId() == 42 && second.status() == Live2DClientState.DrawStatus.DRAWN_NO_UPDATE;
        assert nativeOps.created.size() == 1 : "preview must create through the shared state";
        assert nativeOps.updates == 1 && nativeOps.rendered.size() == 2
                : "one visual frame must update once and draw both views";
    }

    private static void rejectsInvalidNativeFrames(Live2DModelManifest model) {
        long[][] invalidFrames = {
                null,
                new long[0],
                new long[]{2, 41, 1920},
                new long[]{Long.MAX_VALUE, 41, 1920, 1080},
                new long[]{2, Long.MAX_VALUE, 1920, 1080},
                new long[]{2, 41, Long.MAX_VALUE, 1080},
                new long[]{2, 41, 1920, Long.MAX_VALUE},
                new long[]{4, 41, 1920, 1080},
                new long[]{2, 0, 1920, 1080},
                new long[]{2, 41, 0, 1080},
                new long[]{2, 41, 1920, 0}
        };
        for (long[] invalidFrame : invalidFrames) {
            Live2DConfig config = Live2DConfig.defaults();
            config.global.selectedModelId = model.id();
            FakeNative nativeOps = new FakeNative();
            nativeOps.renderResults.add(invalidFrame);
            Live2DClientState state = state(new FakeConfig(config), () -> List.of(model), nativeOps,
                    new Sequence(1_000_000_000L));
            Live2DClientState.DrawResult result = state.renderWorld(frame(config), true);
            assert result.status() == Live2DClientState.DrawStatus.SKIPPED;
            assert !result.hasTexture();
            assert !state.lastError().isEmpty();
        }

        Live2DConfig config = Live2DConfig.defaults();
        config.global.selectedModelId = model.id();
        FakeNative nativeOps = new FakeNative();
        nativeOps.renderResults.add(new long[]{2, 41, 1920});
        Live2DClientState state = state(new FakeConfig(config), () -> List.of(model), nativeOps,
                new Sequence(1_000_000_000L, 1_050_000_000L));
        assert state.renderPreview(preview(config), true).status() == Live2DClientState.DrawStatus.SKIPPED;
        assert !state.previewError().isEmpty() && state.lastError().isEmpty() && !state.sessionDisabled();
        assert state.renderWorld(frame(config), true).status() == Live2DClientState.DrawStatus.DRAWN_UPDATED;
    }

    private static void overflowIsIsolatedByRenderPath(Live2DModelManifest model) {
        Live2DConfig worldConfig = Live2DConfig.defaults();
        worldConfig.global.selectedModelId = model.id();
        FakeNative worldNative = new FakeNative();
        worldNative.renderResults.add(new long[]{2, Long.MAX_VALUE, 1920, 1080});
        Live2DClientState worldState = state(new FakeConfig(worldConfig), () -> List.of(model), worldNative,
                new Sequence(1_000_000_000L, 1_050_000_000L));
        assert worldState.renderWorld(frame(worldConfig), true).status() == Live2DClientState.DrawStatus.SKIPPED;
        assert worldState.lastError().contains("invalid frame descriptor");
        assert !worldState.sessionDisabled() && worldState.activeHandle() == 11L && worldNative.destroyed.isEmpty();
        assert worldState.renderPreview(preview(worldConfig), true).status() == Live2DClientState.DrawStatus.DRAWN_UPDATED;

        Live2DConfig previewConfig = Live2DConfig.defaults();
        previewConfig.global.selectedModelId = model.id();
        FakeNative previewNative = new FakeNative();
        previewNative.renderResults.add(new long[]{Long.MAX_VALUE, 41, 1920, 1080});
        Live2DClientState previewState = state(new FakeConfig(previewConfig), () -> List.of(model), previewNative,
                new Sequence(2_000_000_000L, 2_050_000_000L));
        assert previewState.renderPreview(preview(previewConfig), true).status() == Live2DClientState.DrawStatus.SKIPPED;
        assert !previewState.previewError().isEmpty() && previewState.lastError().isEmpty();
        assert !previewState.sessionDisabled() && previewState.activeHandle() == 11L && previewNative.destroyed.isEmpty();
        assert previewState.renderWorld(frame(previewConfig), true).status() == Live2DClientState.DrawStatus.DRAWN_UPDATED;
    }

    private static void updateCommitsOnlyAfterSuccessfulDraw(Live2DModelManifest model) {
        Live2DConfig config = Live2DConfig.defaults();
        config.global.selectedModelId = model.id();
        FakeNative nativeOps = new FakeNative();
        Live2DClientState state = state(new FakeConfig(config), () -> List.of(model), nativeOps,
                new Sequence(1_000_000_000L, 1_050_000_000L));
        Live2DHudRenderer.UpdateTracker tracker = new Live2DHudRenderer.UpdateTracker();
        long frame = 7;

        Live2DClientState.DrawResult hidden = state.renderWorld(
                new Live2DClientState.Frame(config, false, 960, 540, 1920, 1080), tracker.shouldUpdate(frame, state.activeHandle()));
        tracker.commit(frame, hidden);
        Live2DClientState.DrawResult preview = state.renderPreview(preview(config), tracker.shouldUpdate(frame, state.activeHandle()));
        tracker.commit(frame, preview);
        assert hidden.status() == Live2DClientState.DrawStatus.SKIPPED;
        assert preview.status() == Live2DClientState.DrawStatus.DRAWN_UPDATED;

        Live2DClientState.DrawResult invalid = state.renderPreview(
                new Live2DClientState.PreviewFrame(config, 1920, 1080, 100, 200, .5f, 0),
                tracker.shouldUpdate(frame + 1, state.activeHandle()));
        tracker.commit(frame + 1, invalid);
        Live2DClientState.DrawResult world = state.renderWorld(frame(config), tracker.shouldUpdate(frame + 1, state.activeHandle()));
        tracker.commit(frame + 1, world);
        assert invalid.status() == Live2DClientState.DrawStatus.SKIPPED;
        assert world.status() == Live2DClientState.DrawStatus.DRAWN_UPDATED;

        FakeNative falseNative = new FakeNative();
        falseNative.renderResults.add(new long[]{0, 0, 0, 0});
        Live2DClientState failed = state(new FakeConfig(config), () -> List.of(model), falseNative,
                new Sequence(2_000_000_000L, 2_050_000_000L));
        Live2DHudRenderer.UpdateTracker failedTracker = new Live2DHudRenderer.UpdateTracker();
        Live2DClientState.DrawResult falseResult = failed.renderWorld(frame(config), true);
        failedTracker.commit(9, falseResult);
        assert falseResult.status() == Live2DClientState.DrawStatus.SKIPPED;
        assert failedTracker.shouldUpdate(9, failed.activeHandle()) : "native false must not commit frame update";
        Live2DClientState.DrawResult retry = failed.renderPreview(preview(config),
                failedTracker.shouldUpdate(9, failed.activeHandle()));
        failedTracker.commit(9, retry);
        assert retry.status() == Live2DClientState.DrawStatus.DRAWN_UPDATED
                : "second claimant must update after native false";
    }

    private static void replacementForcesOwnZeroDeltaUpdate(Live2DModelManifest first, Live2DModelManifest second) {
        Live2DConfig firstConfig = Live2DConfig.defaults();
        firstConfig.global.selectedModelId = first.id();
        FakeNative nativeOps = new FakeNative();
        nativeOps.createResults.add(11L);
        nativeOps.createResults.add(22L);
        Live2DClientState state = state(new FakeConfig(firstConfig), () -> List.of(first, second), nativeOps,
                new Sequence(1_000_000_000L, 1_050_000_000L));
        Live2DHudRenderer.UpdateTracker tracker = new Live2DHudRenderer.UpdateTracker();
        Live2DClientState.DrawResult firstDraw = state.renderWorld(frame(firstConfig), tracker.shouldUpdate(3, state.activeHandle()));
        tracker.commit(3, firstDraw);
        Live2DConfig secondConfig = firstConfig.copy();
        secondConfig.global.selectedModelId = second.id();
        Live2DClientState.DrawResult replacement = state.renderPreview(preview(secondConfig),
                tracker.shouldUpdate(3, state.activeHandle()));
        tracker.commit(3, replacement);
        RenderCall call = nativeOps.rendered.get(nativeOps.rendered.size() - 1);
        assert replacement.status() == Live2DClientState.DrawStatus.DRAWN_UPDATED && replacement.handle() == 22L;
        assert call.handle == 22L && call.delta == 0 : "replacement must update with zero delta before first draw";
    }

    private static void updateThenFailCommitsOwnership(Live2DModelManifest model, Live2DModelManifest second) {
        Live2DConfig config = Live2DConfig.defaults();
        config.global.selectedModelId = model.id();
        FakeNative nativeOps = new FakeNative();
        nativeOps.renderResults.add(new long[]{3, 0, 0, 0});
        Live2DClientState state = state(new FakeConfig(config), () -> List.of(model, second), nativeOps,
                new Sequence(1_000_000_000L));
        Live2DHudRenderer.UpdateTracker tracker = new Live2DHudRenderer.UpdateTracker();
        Live2DClientState.DrawResult failed = state.renderWorld(frame(config), true);
        tracker.commit(12, failed);
        assert failed.status() == Live2DClientState.DrawStatus.FAILED_UPDATED;
        assert !state.sessionDisabled() : "world post-update draw failure must keep the session alive";
        assert state.activeHandle() == 11L : "world post-update draw failure must keep the handle until user switches";
        assert !tracker.shouldUpdate(12, state.activeHandle()) : "code 3 must keep update ownership";
        Live2DClientState.DrawResult retried = state.renderPreview(preview(config),
                tracker.shouldUpdate(12, state.activeHandle()));
        assert retried.status() == Live2DClientState.DrawStatus.SKIPPED
                : "failed model must not auto-reload without user action";
        Live2DConfig switched = config.copy();
        switched.global.selectedModelId = second.id();
        state.previewConfig(switched);
        Live2DClientState.DrawResult reloaded = state.renderPreview(preview(switched),
                tracker.shouldUpdate(12, state.activeHandle()));
        assert reloaded.status() == Live2DClientState.DrawStatus.DRAWN_UPDATED
                : "switching models must reload after a post-update failure";
        assert nativeOps.updates == 2 : "reload must update the recreated model";

        FakeNative previewNative = new FakeNative();
        previewNative.renderResults.add(new long[]{2, 1, 1920, 1080});
        previewNative.renderResults.add(new long[]{3, 0, 0, 0});
        Live2DClientState previewState = state(new FakeConfig(config), () -> List.of(model, second), previewNative,
                new Sequence(1_000_000_000L, 1_050_000_000L, 1_200_000_000L));
        previewState.renderWorld(frame(config), true);
        assert previewState.renderPreview(preview(config), true).status() == Live2DClientState.DrawStatus.FAILED_UPDATED;
        previewState.renderWorld(frame(config), true);
        assert previewState.activeHandle() == 11L && previewNative.rendered.size() == 1
                : "suppressed reload must not render";
        Live2DConfig switched2 = config.copy();
        switched2.global.selectedModelId = second.id();
        previewState.previewConfig(switched2);
        previewState.renderWorld(frame(config), true);
        assert previewNative.rendered.size() == 2 && previewNative.rendered.get(1).delta == 0f
                : "model switch after status 3 must reload with reset timing";
    }

    private static void previewFailureDoesNotDisableWorld(Live2DModelManifest model) {
        Live2DConfig config = Live2DConfig.defaults();
        config.global.selectedModelId = model.id();
        FakeNative nativeOps = new FakeNative();
        nativeOps.renderResults.add(new long[]{0, 0, 0, 0});
        Live2DClientState state = state(new FakeConfig(config), () -> List.of(model), nativeOps,
                new Sequence(1_000_000_000L, 1_050_000_000L));
        Live2DClientState.DrawResult previewFailure = state.renderPreview(preview(config), true);
        assert previewFailure.status() == Live2DClientState.DrawStatus.SKIPPED;
        assert !state.sessionDisabled() && state.activeHandle() == 11L;
        assert !state.previewError().isEmpty();
        Live2DClientState.DrawResult world = state.renderWorld(frame(config), true);
        assert world.status() == Live2DClientState.DrawStatus.DRAWN_UPDATED
                : "world render must recover after a preview-only failure";

        FakeNative updatedFailure = new FakeNative();
        updatedFailure.renderResults.add(new long[]{3, 0, 0, 0});
        Live2DClientState updatedState = state(new FakeConfig(config), () -> List.of(model), updatedFailure,
                new Sequence(2_000_000_000L, 2_050_000_000L));
        Live2DClientState.DrawResult updated = updatedState.renderPreview(preview(config), true);
        assert updated.status() == Live2DClientState.DrawStatus.FAILED_UPDATED;
        assert !updatedState.sessionDisabled() && updatedState.activeHandle() == 11L;
        assert updatedState.renderWorld(frame(config), false).status() == Live2DClientState.DrawStatus.SKIPPED
                : "failed preview model must not auto-reload into the world";
    }

    private static void directPreviewIsIsolated(Live2DModelManifest model) {
        Live2DConfig authoritative = Live2DConfig.defaults();
        authoritative.global.selectedModelId = model.id();
        authoritative.performance.textureMemoryBudgetMiB = 768;
        FakeConfig configs = new FakeConfig(authoritative);
        FakeNative nativeOps = new FakeNative();
        Live2DClientState state = state(configs, () -> List.of(model), nativeOps, new Sequence(1_000_000_000L));
        Live2DConfig preview = authoritative.copy();
        preview.global.selectedModelId = "missing";
        preview.performance.textureMemoryBudgetMiB = 96;
        state.renderPreview(preview(preview), true);
        assert configs.saves == 0 : "direct PreviewFrame fallback must never persist";
        assert state.config().global.selectedModelId.equals(model.id()) : "direct PreviewFrame must not mutate authoritative config";
        assert nativeOps.textureBudgets.equals(List.of(96L << 20)) : "preview must use its supplied texture budget";
    }

    private static void previewAdmissionSurvivesLogout() {
        Live2DLifecycle<String> lifecycle = new Live2DLifecycle<>();
        lifecycle.logout(ignored -> {});
        assert lifecycle.beginFrame(() -> "world") == null;
        assert lifecycle.openPreview();
        Live2DLifecycle.Admission<String> preview = lifecycle.beginPreviewFrame(() -> "preview");
        assert preview != null && preview.state().equals("preview");
        assert lifecycle.endFrame(preview) == null;
        Live2DLifecycle.Close<String> close = lifecycle.closePreview(ignored -> {});
        assert close.state().equals("preview") && close.cleanupNow();
        lifecycle.cleaned("preview");
        lifecycle.close(true, ignored -> {});
        assert !lifecycle.openPreview() : "terminal shutdown must reject preview admission";
    }

    private static Live2DClientState.PreviewFrame preview(Live2DConfig config) {
        return new Live2DClientState.PreviewFrame(config, 1920, 1080, 100, 200, .5f, .75f);
    }

    private static void threadOwnership(Live2DModelManifest model) throws Exception {
        Live2DConfig config = Live2DConfig.defaults();
        FakeNative nativeOps = new FakeNative();
        Live2DClientState state = state(new FakeConfig(config), () -> List.of(model), nativeOps,
                new Sequence(1_000_000_000L));
        state.closeModel();
        state.render(frame(config));
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try { state.closeModel(); } catch (Throwable error) { failure.set(error); }
        });
        thread.start();
        thread.join();
        assert failure.get() instanceof IllegalStateException;
        assert nativeOps.destroyed.isEmpty() : "wrong thread must be rejected before native mutation";
    }

    private static void reloadsAuthoritativeConfig(Live2DModelManifest model) {
        Live2DConfig initial = Live2DConfig.defaults();
        FakeConfig configs = new FakeConfig(initial);
        FakeNative nativeOps = new FakeNative();
        Live2DClientState state = state(configs, () -> List.of(model), nativeOps,
                new Sequence(1_000_000_000L, 2_000_000_000L));
        Live2DConfig replacement = Live2DConfig.defaults();
        replacement.global.selectedModelId = "missing";
        replacement.global.opacity = .25f;
        configs.loaded = replacement;
        state.reloadConfig();
        assert state.config() != replacement && state.config().sameSettings(replacement);
        Live2DConfig exposed = state.config();
        exposed.global.opacity = 1;
        assert state.config().global.opacity == .25f : "config accessor must return a defensive copy";
        state.render(new Live2DClientState.Frame(null, true, 960, 540, 1920, 1080));
        assert replacement.global.selectedModelId.equals("missing") : "loaded config must be defensively copied";
        assert state.config().global.selectedModelId.equals(model.id()) : "invalid reloaded ID must persist fallback";
        assert nativeOps.rendered.get(0).opacity == .25f : "render must use reloaded config";
    }

    private static void previewFallbackDoesNotPersist(Live2DModelManifest model) {
        Live2DConfig initial = Live2DConfig.defaults();
        initial.global.selectedModelId = model.id();
        FakeConfig configs = new FakeConfig(initial);
        FakeNative nativeOps = new FakeNative();
        Live2DClientState state = state(configs, () -> List.of(model), nativeOps,
                new Sequence(1_000_000_000L));
        Live2DConfig preview = initial.copy();
        preview.global.selectedModelId = "missing";
        state.previewConfig(preview);
        state.render(new Live2DClientState.Frame(null, true, 960, 540, 1920, 1080));
        assert configs.saves == 0 : "preview fallback must not persist";
        assert preview.global.selectedModelId.equals("missing") && state.config().global.selectedModelId.equals("missing")
                : "preview fallback must not mutate draft or preview config";
    }

    private static void disabledAndHiddenFramesReuseHandle(Live2DModelManifest model) {
        Live2DConfig config = Live2DConfig.defaults();
        config.global.selectedModelId = model.id();
        FakeNative nativeOps = new FakeNative();
        Live2DClientState state = state(new FakeConfig(config), () -> List.of(model), nativeOps,
                new Sequence(1_000_000_000L, 2_000_000_000L));
        state.render(frame(config));
        Live2DConfig disabled = config.copy();
        disabled.global.enabled = false;
        state.previewConfig(disabled);
        state.render(new Live2DClientState.Frame(null, true, 960, 540, 1920, 1080));
        state.render(new Live2DClientState.Frame(null, false, 960, 540, 1920, 1080));
        assert nativeOps.rendered.size() == 1 && nativeOps.created.size() == 1 && nativeOps.destroyed.isEmpty();
        state.previewConfig(config);
        state.render(new Live2DClientState.Frame(null, true, 960, 540, 1920, 1080));
        assert nativeOps.rendered.size() == 2 && nativeOps.created.size() == 1
                : "disabled and hidden frames must reuse the existing handle";
    }

    private static void previewRetainsHandleForLayoutChanges(Live2DModelManifest first,
                                                               Live2DModelManifest second) {
        Live2DConfig config = Live2DConfig.defaults();
        config.global.selectedModelId = first.id();
        FakeNative nativeOps = new FakeNative();
        nativeOps.createResults.add(11L);
        nativeOps.createResults.add(22L);
        Live2DClientState state = state(new FakeConfig(config), () -> List.of(first, second), nativeOps,
                new Sequence(1_000_000_000L, 2_000_000_000L, 3_000_000_000L));
        state.render(frame(config));

        Live2DConfig layout = config.copy();
        layout.global.scale = .7f;
        state.previewConfig(layout);
        state.render(new Live2DClientState.Frame(null, true, 960, 540, 1920, 1080));
        assert nativeOps.created.equals(List.of("first")) : "layout preview must retain the native handle";

        Live2DConfig replacement = layout.copy();
        replacement.global.selectedModelId = second.id();
        state.previewConfig(replacement);
        state.render(new Live2DClientState.Frame(null, true, 960, 540, 1920, 1080));
        assert nativeOps.created.equals(List.of("first", "second"));
    }

    private static void failureIsolation(Live2DModelManifest model) {
        FakeNative failing = new FakeNative();
        failing.renderFailure = new AssertionError("x".repeat(2048));
        failing.destroyResults.add(false);
        Live2DClientState isolated = state(new FakeConfig(Live2DConfig.defaults()), () -> List.of(model), failing,
                new Sequence(1_000_000_000L));
        isolated.render(frame(Live2DConfig.defaults()));
        assert isolated.sessionDisabled() && isolated.lastError().length() == 1024;
        assert isolated.lastError().contains("cleanup") && isolated.lastError().contains("destroy failed");

        FakeNative fatal = new FakeNative();
        fatal.renderFailure = new OutOfMemoryError("fatal");
        Live2DClientState fatalState = state(new FakeConfig(Live2DConfig.defaults()), () -> List.of(model), fatal,
                new Sequence(1_000_000_000L));
        boolean rethrown = false;
        try { fatalState.render(frame(Live2DConfig.defaults())); }
        catch (OutOfMemoryError expected) { rethrown = true; }
        assert rethrown;
    }

    private static void cleanupFailuresAreContained(Live2DModelManifest first, Live2DModelManifest second) {
        Live2DConfig config = Live2DConfig.defaults();
        config.global.selectedModelId = first.id();
        FakeNative nativeOps = new FakeNative();
        nativeOps.createResults.add(11L);
        nativeOps.createResults.add(22L);
        Live2DClientState state = state(new FakeConfig(config), () -> List.of(first, second), nativeOps,
                new Sequence(1_000_000_000L, 2_000_000_000L));
        state.render(frame(config));
        config.global.selectedModelId = second.id();
        nativeOps.destroyFailure = new AssertionError("cleanup exploded");
        state.render(frame(config));
        assert state.sessionDisabled() && state.lastError().contains("cleanup exploded");
        nativeOps.destroyFailure = null;
        state.closeModel();
        assert nativeOps.destroyed.containsAll(List.of(11L, 22L)) : "inconclusive cleanup must retain both handles";
    }

    private static void firstFrameSuccessResetsOnSwap(Live2DModelManifest first, Live2DModelManifest second) {
        Live2DConfig config = Live2DConfig.defaults();
        config.global.selectedModelId = first.id();
        FakeNative nativeOps = new FakeNative();
        nativeOps.createResults.add(11L);
        nativeOps.createResults.add(22L);
        Live2DClientState state = state(new FakeConfig(config), () -> List.of(first, second), nativeOps,
                new Sequence(1_000_000_000L, 2_000_000_000L));
        state.render(frame(config));
        assert state.firstFrameLoggedForTest();
        config.global.selectedModelId = second.id();
        state.render(frame(config));
        assert state.firstFrameLoggedForTest() : "replacement first successful frame must be recorded";
        assert nativeOps.rendered.size() == 2;
    }

    private static void overridesAffectRenderArguments(Live2DModelManifest model) {
        Live2DConfig config = Live2DConfig.defaults();
        config.global.selectedModelId = model.id();
        config.global.anchorY = .55f;
        config.global.offsetY = -24f;
        config.global.scale = .45f;
        config.global.opacity = 1f;
        Live2DConfig.ModelOverride override = new Live2DConfig.ModelOverride();
        override.anchorX = .3f;
        override.offsetX = 7f;
        override.offsetY = -20f;
        override.scale = 1.2f;
        override.opacity = .55f;
        override.visibility = Live2DConfig.Visibility.HOTBAR;
        override.enabled = true;
        config.modelOverrides.put(model.id(), override);

        FakeNative nativeOps = new FakeNative();
        Live2DClientState state = state(new FakeConfig(config), () -> List.of(model), nativeOps,
                new Sequence(1_000_000_000L, 1_050_000_000L));
        Live2DClientState.Frame display = new Live2DClientState.Frame(config, true, 1000, 500, 2000, 1000);
        state.render(display);
        assert nativeOps.rendered.size() == 1;
        RenderCall call = nativeOps.rendered.get(0);
        assert call.x == 614 && call.y == 510 && call.scale == 1200 && call.opacity == .55f
                : "render must apply field-level model overrides";

        Live2DConfig hidden = config.copy();
        hidden.modelOverrides.get(model.id()).enabled = false;
        state.previewConfig(hidden);
        state.render(frame(hidden));
        assert nativeOps.rendered.size() == 1 : "disabled override must skip rendering";

        Live2DConfig faded = config.copy();
        faded.modelOverrides.get(model.id()).opacity = 0f;
        assert state.renderPreview(preview(faded), true).status() == Live2DClientState.DrawStatus.SKIPPED
                : "zero opacity override must hide preview";
    }

    private static void estimateRoutesThroughNative(Live2DModelManifest model) {
        Live2DConfig config = Live2DConfig.defaults();
        config.performance.textureMemoryBudgetMiB = 768;
        FakeNative nativeOps = new FakeNative();
        nativeOps.estimateResults.add(700L << 20);
        Live2DClientState state = state(new FakeConfig(config), () -> List.of(model), nativeOps,
                new Sequence(1_000_000_000L));
        assert state.estimateTextures(model) == 700L << 20;
        assert nativeOps.estimates.size() == 1;
        assert nativeOps.estimates.get(0).root.equals(model.root().toString());
        assert nativeOps.estimates.get(0).modelJson.equals(model.modelJson().toString());
        assert nativeOps.estimates.get(0).shaders.equals("shaders")
                : "estimate must receive the configured shader root";

        FakeNative unavailable = new FakeNative();
        unavailable.initializeResults.add(false);
        Live2DClientState unavailableState = state(new FakeConfig(config), () -> List.of(model), unavailable,
                new Sequence(1_000_000_000L));
        assert unavailableState.estimateTextures(model) == 0 && unavailable.estimates.isEmpty()
                : "runtime unavailable must return unknown without native estimate";

        FakeNative unknown = new FakeNative();
        Live2DClientState unknownState = state(new FakeConfig(config), () -> List.of(model), unknown,
                new Sequence(1_000_000_000L));
        assert unknownState.estimateTextures(model) == 0 : "native zero must map to unknown";

        FakeNative staleNative = new FakeNative();
        staleNative.estimateFailure = new UnsatisfiedLinkError("estimate");
        Live2DClientState staleState = state(new FakeConfig(config), () -> List.of(model), staleNative,
                new Sequence(1_000_000_000L));
        assert staleState.estimateTextures(model) == 0 : "linkage failure must degrade to unknown";
    }

    private static void controlRoutesToNative(Live2DModelManifest model) {
        Live2DConfig config = Live2DConfig.defaults();
        config.global.selectedModelId = model.id();
        FakeNative nativeOps = new FakeNative();
        Live2DClientState state = state(new FakeConfig(config), () -> List.of(model), nativeOps,
                new Sequence(1_000_000_000L));
        state.render(frame(config));
        state.control(.5f, -.25f, 1f, Live2DInteractionScheduler.FLAG_RANDOM_MOTION);
        assert nativeOps.controls.size() == 1;
        assert nativeOps.controls.get(0).gazeX == .5f && nativeOps.controls.get(0).gazeY == -.25f
                && nativeOps.controls.get(0).physicsAmplitude == 1f
                && nativeOps.controls.get(0).flags == Live2DInteractionScheduler.FLAG_RANDOM_MOTION
                : "control must forward gaze, amplitude and flags";
    }

    private static Live2DClientState state(FakeConfig config, Live2DClientState.ModelAccess models,
                                           FakeNative nativeOps, Sequence clock) {
        return new Live2DClientState(config, models, nativeOps, () -> Path.of("shaders"), clock::next);
    }

    private static Live2DClientState.Frame frame(Live2DConfig config) {
        return new Live2DClientState.Frame(config, true, 960, 540, 1920, 1080);
    }

    private static Live2DModelManifest model(String id) {
        Path root = Path.of(id);
        return new Live2DModelManifest(id, id, 3, root, root.resolve(id + ".model3.json"),
                root.resolve(id + ".moc3"), List.of(), null, List.of(), List.of());
    }

    private static final class FakeConfig implements Live2DClientState.ConfigAccess {
        private Live2DConfig loaded;
        private int saves;
        private int failSaves;

        private FakeConfig(Live2DConfig config) { loaded = config; }
        @Override public Live2DConfig load() { return loaded; }
        @Override public void save(Live2DConfig ignored) throws Exception {
            saves++;
            if (failSaves-- > 0) throw new Exception("save failed");
        }
    }

    private record RenderCall(long handle, float delta, int width, int height,
                              float x, float y, float scale, float opacity) {}

    private static final class FakeNative implements Live2DClientState.NativeAccess {
        private final List<String> created = new ArrayList<>();
        private final List<RenderCall> rendered = new ArrayList<>();
        private final List<Long> destroyed = new ArrayList<>();
        private final List<Long> createResults = new ArrayList<>();
        private final List<Boolean> destroyResults = new ArrayList<>();
        private final List<Boolean> initializeResults = new ArrayList<>();
        private final List<long[]> renderResults = new ArrayList<>();
        private final List<Long> textureBudgets = new ArrayList<>();
        private final List<Long> estimateResults = new ArrayList<>();
        private final List<EstimateCall> estimates = new ArrayList<>();
        private final List<ControlCall> controls = new ArrayList<>();

        private record EstimateCall(String root, String modelJson, String shaders) {}
        private record ControlCall(float gazeX, float gazeY, float physicsAmplitude, int flags) {}
        private String error = "destroy failed";
        private Throwable renderFailure;
        private Throwable destroyFailure;
        private Throwable estimateFailure;
        private float[] bounds = {10, 20, 110, 220};
        private boolean relativeBounds;
        private int initializes;
        private int updates;

        @Override public boolean initialize() {
            initializes++;
            return initializeResults.isEmpty() || initializeResults.remove(0);
        }
        @Override public long create(String root, String modelJson, String shaderRoot, long textureBudget) {
            created.add(Path.of(root).getFileName().toString());
            textureBudgets.add(textureBudget);
            return createResults.isEmpty() ? 11 : createResults.remove(0);
        }
        @Override public Live2DNative.StructuredFrame render(long handle, float delta, int width, int height,
                                                             float x, float y, float scale, float opacity, boolean update) {
            if (renderFailure != null) throwUnchecked(renderFailure);
            long[] raw = renderResults.isEmpty() ? new long[]{update ? 2 : 1, 1, width, height, 0}
                    : renderResults.remove(0);
            if (raw != null && raw.length > 0 && (raw[0] == 2 || raw[0] == 3)) updates++;
            if (raw == null || raw.length == 0 || raw[0] == 0 || raw[0] == 3) {
                int status = raw == null || raw.length == 0 ? Live2DNative.STATUS_FAILED_BEFORE_UPDATE : (int) raw[0];
                long texture = raw == null || raw.length < 2 ? 0 : raw[1];
                int textureWidth = raw == null || raw.length < 3 ? 0 : (int) raw[2];
                int textureHeight = raw == null || raw.length < 4 ? 0 : (int) raw[3];
                return new Live2DNative.StructuredFrame(status, texture, textureWidth, textureHeight, error);
            }
            rendered.add(new RenderCall(handle, delta, width, height, x, y, scale, opacity));
            long texture = raw.length < 2 ? 0 : raw[1];
            int textureWidth = raw.length < 3 ? 0 : (int) raw[2];
            int textureHeight = raw.length < 4 ? 0 : (int) raw[3];
            return new Live2DNative.StructuredFrame((int) raw[0], texture, textureWidth, textureHeight, "");
        }
        @Override public float[] bounds(long handle, int width, int height, float x, float y, float scale) {
            return relativeBounds ? new float[]{x - 100, y - 200, x + 100, y + 200} : bounds;
        }
        @Override public boolean destroy(long handle) {
            destroyed.add(handle);
            if (destroyFailure != null) throwUnchecked(destroyFailure);
            return destroyResults.isEmpty() || destroyResults.remove(0);
        }
        @Override public String lastError() { return error; }
        @Override public long estimate(String root, String modelJson, String shaders) {
            if (estimateFailure != null) throwUnchecked(estimateFailure);
            estimates.add(new EstimateCall(root, modelJson, shaders));
            return estimateResults.isEmpty() ? 0 : estimateResults.remove(0);
        }
        @Override public void control(long handle, float gazeX, float gazeY, float physicsAmplitude, int flags) {
            controls.add(new ControlCall(gazeX, gazeY, physicsAmplitude, flags));
        }
    }

    private static final class Sequence {
        private final long[] values;
        private int index;

        private Sequence(long... values) { this.values = values; }
        private long next() { return values[Math.min(index++, values.length - 1)]; }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void throwUnchecked(Throwable failure) throws T { throw (T) failure; }
}
