package com.yuan.live2d.client.live2d;

import com.mojang.logging.LogUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public final class Live2DClientState {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_ERROR_LENGTH = 1024;
    private static final long RETRY_NANOS = 1_000_000_000L;

    interface ConfigAccess {
        Live2DConfig load();
        void save(Live2DConfig config) throws Exception;
    }

    @FunctionalInterface
    interface ModelAccess {
        List<Live2DModelManifest> scan() throws Exception;
        default void invalidate() {}
    }

    interface NativeAccess {
        boolean initialize();
        long create(String root, String modelJson, String shaderRoot, long textureMemoryBudgetBytes);
        Live2DNative.StructuredFrame render(long handle, float deltaSeconds, int width, int height,
                                            float x, float y, float scale, float opacity, boolean update);
        float[] bounds(long handle, int width, int height, float x, float y, float scale);
        boolean destroy(long handle);
        String lastError();
        default long estimate(String root, String modelJson, String shaders) { return 0; }
        default void control(long handle, float gazeX, float gazeY, float physicsAmplitude, int flags) {}
    }

    record Frame(Live2DConfig config, boolean visible, int guiWidth, int guiHeight,
                 int framebufferWidth, int framebufferHeight) {}

    enum DrawStatus { SKIPPED, DRAWN_NO_UPDATE, DRAWN_UPDATED, FAILED_UPDATED }

    record DrawResult(DrawStatus status, long handle,
                      int textureId, int textureWidth, int textureHeight) {
        static final DrawResult SKIPPED = new DrawResult(DrawStatus.SKIPPED, 0, 0, 0, 0);

        boolean hasTexture() {
            return (status == DrawStatus.DRAWN_NO_UPDATE || status == DrawStatus.DRAWN_UPDATED)
                    && textureId > 0 && textureWidth > 0 && textureHeight > 0;
        }
    }

    public record PreviewFrame(Live2DConfig config, int framebufferWidth, int framebufferHeight,
                               float x, float y, float scale, float opacity) {}

    record Placement(float x, float y, float scale) {}

    private final ConfigAccess configs;
    private final ModelAccess models;
    private final NativeAccess nativeAccess;
    private final Supplier<Path> shaderRoot;
    private final LongSupplier nanoTime;
    private final List<Long> cleanupHandles = new ArrayList<>();
    private Live2DConfig config;
    private long handle;
    private long lastFrameNanos;
    private long lastCreateAttemptNanos;
    private boolean createAttempted;
    private Live2DModelManifest selectedModel;
    private String selectionKey;
    private volatile String activeModelId = "";
    private String lastError = "";
    private String previewError = "";
    private volatile String lastAttemptedModelId = "";
    private String modelError = "";
    private Thread renderThread;
    private boolean sessionDisabled;
    private volatile boolean closeRequested;
    private boolean readyLogged;
    private boolean disabledLogged;
    private boolean firstFrameLogged;
    private boolean previewing;
    private boolean suppressAutoReload;
    private boolean interactionsUnavailable;

    public Live2DClientState() {
        try {
            Live2DResourceInstaller.install(Live2DPaths.root());
        } catch (Throwable failure) {
            rethrowFatal(failure);
            LOGGER.error("Live2D resource install failed", failure);
        }
        Live2DConfigStore store = new Live2DConfigStore(Live2DPaths.root());
        this.configs = new ConfigAccess() {
            @Override public Live2DConfig load() { return store.load(); }
            @Override public void save(Live2DConfig value) throws Exception { store.save(value); }
        };
        Live2DModelRepository repository = new Live2DModelRepository(Live2DPaths.models());
        Live2DModelRegistry registry = new Live2DModelRegistry(repository);
        Live2DRenderEngine renderEngine = new Live2DRenderEngine(Live2DPaths.frameworkShaders());
        this.models = new ModelAccess() {
            @Override public List<Live2DModelManifest> scan() { return registry.list(); }
            @Override public void invalidate() { registry.invalidate(); }
        };
        this.nativeAccess = new NativeAccess() {
            private Live2DRuntime runtime = new Live2DRuntime();
            private String error = "";
            @Override public boolean initialize() {
                if (runtime.initialize()) return true;
                error = runtime.error();
                runtime = new Live2DRuntime();
                return false;
            }
            @Override public long create(String root, String modelJson, String shaders, long textureBudget) {
                return renderEngine.create(root, modelJson, shaders, textureBudget);
            }
            @Override public Live2DNative.StructuredFrame render(long value, float delta, int width, int height,
                                                                float x, float y, float scale, float opacity, boolean update) {
                return renderEngine.render(value, delta, width, height, x, y, scale, opacity, update);
            }
            @Override public long estimate(String root, String modelJson, String shaders) {
                return renderEngine.estimate(root, modelJson, shaders);
            }
            @Override public float[] bounds(long value, int width, int height, float x, float y, float scale) {
                return renderEngine.bounds(value, width, height, x, y, scale);
            }
            @Override public void control(long value, float gazeX, float gazeY, float physicsAmplitude, int flags) {
                Live2DNative.control(value, gazeX, gazeY, physicsAmplitude, flags);
            }
            @Override public boolean destroy(long value) { return renderEngine.destroy(value); }
            @Override public String lastError() {
                String nativeError = runtime.available() ? Live2DNative.lastError() : error;
                return nativeError == null ? "" : nativeError;
            }
        };
        this.shaderRoot = Live2DPaths::frameworkShaders;
        this.nanoTime = System::nanoTime;
        this.config = Objects.requireNonNull(configs.load()).copy().sanitize();
    }

    Live2DClientState(ConfigAccess configs, ModelAccess models, NativeAccess nativeAccess,
                      Supplier<Path> shaderRoot, LongSupplier nanoTime) {
        this.configs = Objects.requireNonNull(configs);
        this.models = Objects.requireNonNull(models);
        this.nativeAccess = Objects.requireNonNull(nativeAccess);
        this.shaderRoot = Objects.requireNonNull(shaderRoot);
        this.nanoTime = Objects.requireNonNull(nanoTime);
        this.config = Objects.requireNonNull(configs.load()).copy().sanitize();
    }

    public void render(float partialTick) {
        render(partialTick, true);
    }

    public void render(float partialTick, boolean update) { renderWorld(partialTick, update); }

    DrawResult renderWorld(float partialTick, boolean update) { return renderWorld(partialTick, update, false); }

    DrawResult renderWorld(float partialTick, boolean update, boolean forceVisibleGui) {
        requireRenderThread(true);
        if (sessionDisabled) return DrawResult.SKIPPED;
        try {
            Minecraft minecraft = Minecraft.getInstance();
            Live2DConfig.Global effective = config.effectiveGlobal(config.global.selectedModelId);
            boolean visible = minecraft.level != null && minecraft.player != null
                    && (forceVisibleGui || !minecraft.options.hideGui)
                    && effective.enabled && effective.opacity > 0
                    && Live2DVisibility.matches(effective.visibility, inventory(minecraft.player.getInventory()));
            var window = minecraft.getWindow();
            return renderWorld(new Frame(config, visible, window.getGuiScaledWidth(), window.getGuiScaledHeight(),
                    window.getWidth(), window.getHeight()), update);
        } catch (Throwable failure) {
            rethrowFatal(failure);
            disable(failure);
            return DrawResult.SKIPPED;
        }
    }

    void render(Frame frame) {
        render(frame, true);
    }

    void render(Frame frame, boolean update) { renderWorld(frame, update); }

    DrawResult renderWorld(Frame frame, boolean update) {
        requireRenderThread(true);
        if (sessionDisabled || !frame.visible() || frame.guiWidth() <= 0 || frame.guiHeight() <= 0
                || frame.framebufferWidth() <= 0 || frame.framebufferHeight() <= 0) {
            lastFrameNanos = 0;
            return DrawResult.SKIPPED;
        }
        try {
            return renderFrame(frame, update);
        } catch (Throwable failure) {
            rethrowFatal(failure);
            disable(failure);
            return DrawResult.SKIPPED;
        }
    }

    public DrawResult renderPreview(PreviewFrame frame, boolean update) {
        requireRenderThread(true);
        if (sessionDisabled || frame == null || frame.framebufferWidth() <= 0 || frame.framebufferHeight() <= 0)
            return DrawResult.SKIPPED;
        if (suppressAutoReload) return DrawResult.SKIPPED;
        try {
            Live2DConfig frameConfig = (frame.config() == null ? config : frame.config()).copy().sanitize();
            Live2DConfig.Global effective = frameConfig.effectiveGlobal(frameConfig.global.selectedModelId);
            if (effective.opacity <= 0 || frame.opacity() <= 0) return DrawResult.SKIPPED;
            Prepared prepared = prepare(frameConfig, false);
            if (selectedModel == null) {
                if (handle != 0) closeModel();
                return DrawResult.SKIPPED;
            }
            if (handle == 0) return DrawResult.SKIPPED;
            boolean doUpdate = update || prepared.replaced();
            float delta = delta(doUpdate, prepared.now());
            Live2DNative.StructuredFrame nativeFrame = nativeAccess.render(handle, delta, frame.framebufferWidth(), frame.framebufferHeight(),
                    frame.x(), frame.y(), frame.scale(), frame.opacity(), doUpdate);
            DrawResult result = nativeFrame(nativeFrame, handle, prepared.now(), true);
            return result;
        } catch (Throwable failure) {
            rethrowFatal(failure);
            previewError = formatError(failure);
            return DrawResult.SKIPPED;
        }
    }

    private DrawResult renderFrame(Frame frame) throws Exception { return renderFrame(frame, true); }

    public void control(float gazeX, float gazeY, float physicsAmplitude, int flags) {
        requireRenderThread(true);
        if (handle == 0 || interactionsUnavailable) return;
        try {
            nativeAccess.control(handle, gazeX, gazeY, physicsAmplitude, flags);
        } catch (LinkageError | RuntimeException failure) {
            rethrowFatal(failure);
            if (!interactionsUnavailable) {
                String nativeError = errorFromNative("");
                String detail = nativeError.isEmpty()
                        ? formatError(failure) : formatError(failure) + " native=" + nativeError;
                safeLog(() -> LOGGER.warn("Live2D interactions unavailable: {}", detail));
                interactionsUnavailable = true;
            }
        }
    }

    private DrawResult renderFrame(Frame frame, boolean update) throws Exception {
        if (!frame.visible() || frame.guiWidth() <= 0 || frame.guiHeight() <= 0
                || frame.framebufferWidth() <= 0 || frame.framebufferHeight() <= 0) {
            lastFrameNanos = 0;
            return DrawResult.SKIPPED;
        }
        if (suppressAutoReload) {
            lastFrameNanos = 0;
            return DrawResult.SKIPPED;
        }
        Live2DConfig frameConfig = (frame.config() == null ? config : frame.config()).copy().sanitize();
        Live2DConfig.Global effective = frameConfig.effectiveGlobal(frameConfig.global.selectedModelId);
        if (!effective.enabled || effective.opacity <= 0) {
            lastFrameNanos = 0;
            return DrawResult.SKIPPED;
        }
        Prepared prepared = prepare(frameConfig, !previewing);
        if (selectedModel == null) {
            lastFrameNanos = 0;
            if (handle != 0) closeModel();
            return DrawResult.SKIPPED;
        }
        if (handle == 0) return DrawResult.SKIPPED;
        boolean doUpdate = update || prepared.replaced();
        float deltaSeconds = delta(doUpdate, prepared.now());
        Live2DConfig.Global effectiveAfterPrepare = frameConfig.effectiveGlobal(frameConfig.global.selectedModelId);
        Placement placement = placement(effectiveAfterPrepare.anchorX, effectiveAfterPrepare.anchorY,
                effectiveAfterPrepare.offsetX, effectiveAfterPrepare.offsetY,
                effectiveAfterPrepare.scale, frame.guiWidth(), frame.guiHeight(),
                frame.framebufferWidth(), frame.framebufferHeight());
        placement = clampPlacement(placement, frame.framebufferWidth(), frame.framebufferHeight());
        Live2DNative.StructuredFrame nativeFrame = nativeAccess.render(handle, deltaSeconds, frame.framebufferWidth(), frame.framebufferHeight(),
                placement.x(), placement.y(), placement.scale(), effectiveAfterPrepare.opacity, doUpdate);
        DrawResult result = nativeFrame(nativeFrame, handle, prepared.now(), false);
        return result;
    }


    private Prepared prepare(Live2DConfig frameConfig, boolean allowPersistence) throws Exception {
        String requestedId = frameConfig.global.selectedModelId;
        if (selectedModel == null || selectionKey == null || !selectionKey.equals(requestedId)) {
            selectedModel = select(requestedId, models.scan());
            selectionKey = requestedId;
        }
        if (selectedModel == null) {
            modelError = "";
            return new Prepared(0, false);
        }
        if (!allowPersistence && handle != 0 && requestedId != null && !requestedId.isEmpty()
                && !requestedId.equals(selectedModel.id())) {
            lastAttemptedModelId = requestedId;
            modelError = "Live2D model not found: " + requestedId;
            return new Prepared(nanoTime.getAsLong(), false);
        }
        if (allowPersistence) persistFallback(frameConfig, selectedModel.id());
        long now = nanoTime.getAsLong();
        return new Prepared(now, load(selectedModel, frameConfig, now));
    }

    private float delta(boolean update, long now) {
        if (!update) return 0;
        float delta = lastFrameNanos == 0 ? 0 : (now - lastFrameNanos) / 1_000_000_000f;
        return delta;
    }

    private record Prepared(long now, boolean replaced) {}

    private DrawResult nativeFrame(Live2DNative.StructuredFrame frame, long frameHandle, long now, boolean preview) {
        if (frame == null) {
            setRenderError(preview, errorFromNative("Live2D native returned an invalid frame descriptor"));
            return DrawResult.SKIPPED;
        }
        int status;
        int texture;
        int width;
        int height;
        try {
            status = frame.status();
            texture = Math.toIntExact(frame.textureId());
            width = frame.textureWidth();
            height = frame.textureHeight();
        } catch (ArithmeticException overflow) {
            setRenderError(preview, "Live2D native returned an invalid frame descriptor");
            return DrawResult.SKIPPED;
        }
        if (status == Live2DNative.STATUS_FAILED_BEFORE_UPDATE) {
            setRenderError(preview, errorFromNative("Live2D native render failed before update"));
            return DrawResult.SKIPPED;
        }
        if (status == Live2DNative.STATUS_FAILED_UPDATED) return failedAfterUpdate(preview);
        if (status == Live2DNative.STATUS_SKIPPED) {
            setRenderError(preview, errorFromNative(preview ? "Live2D preview render failed" : "Live2D render failed"));
            return DrawResult.SKIPPED;
        }
        if ((status != Live2DNative.STATUS_DRAWN_NO_UPDATE && status != Live2DNative.STATUS_DRAWN_UPDATED)
                || texture <= 0 || width <= 0 || height <= 0) {
            setRenderError(preview, "Live2D native returned invalid texture metadata");
            return DrawResult.SKIPPED;
        }
        if (status == Live2DNative.STATUS_DRAWN_UPDATED) lastFrameNanos = now;
        if (preview) previewError = "";
        logFirstFrame();
        return new DrawResult(status == Live2DNative.STATUS_DRAWN_UPDATED ? DrawStatus.DRAWN_UPDATED : DrawStatus.DRAWN_NO_UPDATE,
                frameHandle, texture, width, height);
    }

    private void setRenderError(boolean preview, String error) {
        if (preview) previewError = error;
        else lastError = error;
    }

    private DrawResult failedAfterUpdate(boolean preview) {
        long failedHandle = handle;
        String message = preview ? "Live2D preview draw failed after update" : "Live2D draw failed after update";
        String error = errorFromNative(message);
        if (preview) previewError = error;
        else lastError = error;
        safeLog(() -> LOGGER.warn("{}; reloading model", message));
        suppressAutoReload = true;
        return new DrawResult(DrawStatus.FAILED_UPDATED, failedHandle, 0, 0, 0);
    }

    private void logFirstFrame() {
        if (!firstFrameLogged) {
            firstFrameLogged = true;
            safeLog(() -> LOGGER.info("Live2D RENDERING first-frame-success id={}", activeModelId));
        }
    }

    public void closeModel() {
        if (handle == 0 && cleanupHandles.isEmpty()) {
            invalidateSelection();
            return;
        }
        requireRenderThread(false);
        String cleanupError = cleanupHandles();
        if (handle != 0) {
            Cleanup result = destroy(handle, "Live2D model destruction failed");
            if (result.success()) {
                handle = 0;
                activeModelId = "";
                lastFrameNanos = 0;
                firstFrameLogged = false;
            } else {
                cleanupError = joinErrors(cleanupError, result.error());
            }
        }
        invalidateSelection();
        if (!cleanupError.isEmpty()) lastError = cleanupError;
    }

    public void requestClose() {
        closeRequested = true;
    }

    public boolean processPendingClose() {
        if (!closeRequested) return false;
        try {
            closeModel();
        } catch (Throwable failure) {
            rethrowFatal(failure);
            lastError = bounded(joinErrors(lastError, formatError(failure)));
        }
        closeRequested = handle != 0 || !cleanupHandles.isEmpty();
        return true;
    }

    public boolean closePending() {
        return closeRequested;
    }

    public long estimateTextures(Live2DModelManifest manifest) {
        if (manifest == null) return 0;
        if (!nativeAccess.initialize()) return 0;
        long bytes;
        try {
            bytes = nativeAccess.estimate(manifest.root().toString(), manifest.modelJson().toString(),
                    shaderRoot.get().toString());
        } catch (LinkageError | RuntimeException failure) {
            rethrowFatal(failure);
            safeLog(() -> LOGGER.warn("Live2D texture estimate unavailable: {}", formatError(failure)));
            return 0;
        }
        if (bytes > 0) {
            long budget = ((long) config.performance.textureMemoryBudgetMiB) << 20;
            if (bytes > budget) {
                long miB = bytes >> 20;
                safeLog(() -> LOGGER.warn("Live2D texture estimate {} MiB exceeds budget {} MiB id={}",
                        miB, config.performance.textureMemoryBudgetMiB, manifest.id()));
            }
        }
        return bytes;
    }

    public void reloadConfig() {
        requireRenderThread(true);
        config = Objects.requireNonNull(configs.load()).copy().sanitize();
        previewing = false;
        models.invalidate();
        invalidateSelection();
        suppressAutoReload = false;
    }

    public void previewConfig(Live2DConfig value) {
        requireRenderThread(true);
        Live2DConfig preview = Objects.requireNonNull(value).copy().sanitize();
        boolean modelChanged = !Objects.equals(config.global.selectedModelId, preview.global.selectedModelId);
        config = preview;
        previewing = true;
        if (modelChanged) {
            models.invalidate();
            invalidateSelection();
        }
        if (modelChanged) suppressAutoReload = false;
    }

    public Live2DConfig config() { return config.copy(); }

    public String lastError() { return lastError; }

    public String previewError() { return previewError; }

    public record ModelStatus(String activeModelId, String attemptedModelId, String error) {}

    public record ModelBounds(float left, float top, float right, float bottom) {}

    public ModelStatus modelStatus() { return new ModelStatus(activeModelId, lastAttemptedModelId, modelError); }

    public Optional<ModelBounds> modelBounds(int guiWidth, int guiHeight) {
        requireRenderThread(false);
        if (handle == 0) return Optional.empty();
        Minecraft minecraft = Minecraft.getInstance();
        var window = minecraft.getWindow();
        return modelBounds(guiWidth, guiHeight, window.getWidth(), window.getHeight());
    }

    Optional<ModelBounds> modelBounds(int guiWidth, int guiHeight, int framebufferWidth, int framebufferHeight) {
        requireRenderThread(false);
        if (handle == 0 || guiWidth <= 0 || guiHeight <= 0 || framebufferWidth <= 0 || framebufferHeight <= 0)
            return Optional.empty();
        Live2DConfig.Global effective = config.effectiveGlobal(config.global.selectedModelId);
        Placement placement = placement(effective.anchorX, effective.anchorY, effective.offsetX, effective.offsetY, effective.scale,
                guiWidth, guiHeight, framebufferWidth, framebufferHeight);
        float[] raw = nativeAccess.bounds(handle, framebufferWidth, framebufferHeight,
                placement.x(), placement.y(), placement.scale());
        if (raw == null || raw.length != 4
                || !Float.isFinite(raw[0]) || !Float.isFinite(raw[1])
                || !Float.isFinite(raw[2]) || !Float.isFinite(raw[3])
                || raw[0] >= raw[2] || raw[1] >= raw[3]) return Optional.empty();
        float sx = guiWidth / (float) framebufferWidth;
        float sy = guiHeight / (float) framebufferHeight;
        return Optional.of(new ModelBounds(raw[0] * sx, raw[1] * sy, raw[2] * sx, raw[3] * sy));
    }

    Optional<ModelBounds> modelBounds(PreviewFrame frame, int guiWidth, int guiHeight,
                                      int framebufferWidth, int framebufferHeight) {
        requireRenderThread(false);
        if (handle == 0 || frame == null || guiWidth <= 0 || guiHeight <= 0
                || framebufferWidth <= 0 || framebufferHeight <= 0) return Optional.empty();
        float[] raw = nativeAccess.bounds(handle, framebufferWidth, framebufferHeight,
                frame.x(), frame.y(), frame.scale());
        if (raw == null || raw.length != 4
                || !Float.isFinite(raw[0]) || !Float.isFinite(raw[1])
                || !Float.isFinite(raw[2]) || !Float.isFinite(raw[3])
                || raw[0] >= raw[2] || raw[1] >= raw[3]) return Optional.empty();
        float sx = guiWidth / (float) framebufferWidth;
        float sy = guiHeight / (float) framebufferHeight;
        return Optional.of(new ModelBounds(raw[0] * sx, raw[1] * sy, raw[2] * sx, raw[3] * sy));
    }

    public boolean sessionDisabled() { return sessionDisabled; }

    Placement clampPlacement(Placement placement, int framebufferWidth, int framebufferHeight) {
        if (handle == 0 || framebufferWidth <= 0 || framebufferHeight <= 0) return placement;
        float[] raw;
        try {
            raw = nativeAccess.bounds(handle, framebufferWidth, framebufferHeight,
                    placement.x(), placement.y(), placement.scale());
        } catch (RuntimeException failure) {
            return placement;
        }
        if (raw == null || raw.length != 4
                || !Float.isFinite(raw[0]) || !Float.isFinite(raw[1])
                || !Float.isFinite(raw[2]) || !Float.isFinite(raw[3])
                || raw[0] >= raw[2] || raw[1] >= raw[3]) return placement;
        float width = raw[2] - raw[0];
        float height = raw[3] - raw[1];
        float minX = Math.min(0f, framebufferWidth - width);
        float maxX = Math.max(0f, framebufferWidth - width);
        float shiftX = Math.max(minX, Math.min(maxX, raw[0])) - raw[0];
        float minY = Math.min(0f, framebufferHeight - height);
        float maxY = Math.max(0f, framebufferHeight - height);
        float shiftY = Math.max(minY, Math.min(maxY, raw[1])) - raw[1];
        if (Float.compare(shiftX, 0f) == 0 && Float.compare(shiftY, 0f) == 0) return placement;
        return new Placement(placement.x() + shiftX, placement.y() + shiftY, placement.scale());
    }

    public String activeModelId() { return activeModelId; }

    public boolean activeModelChanged(String previousActiveId) {
        return !Objects.equals(previousActiveId, activeModelId);
    }

    boolean firstFrameLoggedForTest() { return firstFrameLogged; }

    long activeHandle() { return handle; }

    static Live2DModelManifest select(String selectedId, List<Live2DModelManifest> models) {
        if (models == null || models.isEmpty()) return null;
        if (selectedId != null && !selectedId.isEmpty())
            for (Live2DModelManifest model : models) if (selectedId.equals(model.id())) return model;
        return models.get(0);
    }

    static Placement placement(float anchorX, float anchorY, float offsetX, float offsetY, float scale,
                               int guiWidth, int guiHeight, int framebufferWidth, int framebufferHeight) {
        return new Placement((anchorX * guiWidth + offsetX) * framebufferWidth / guiWidth,
                (anchorY * guiHeight + offsetY) * framebufferHeight / guiHeight,
                scale * framebufferHeight);
    }

    static Live2DVisibility.InventorySnapshot snapshot(boolean mainHand, boolean offHand,
                                                       boolean hotbar, boolean inventory) {
        return new Live2DVisibility.InventorySnapshot(mainHand, offHand, hotbar, inventory);
    }

    private void persistFallback(Live2DConfig config, String selectedId) {
        if (selectedId.equals(config.global.selectedModelId)) return;
        String oldId = config.global.selectedModelId;
        config.global.selectedModelId = selectedId;
        try {
            configs.save(config);
            this.config.global.selectedModelId = selectedId;
        } catch (Exception failure) {
            config.global.selectedModelId = oldId;
            lastError = formatError(failure);
        }
    }

    private boolean load(Live2DModelManifest selected, Live2DConfig effectiveConfig, long now) {
        if (selected.id().equals(activeModelId)) {
            modelError = "";
            return false;
        }
        if (createAttempted) {
            long elapsed = now - lastCreateAttemptNanos;
            if (elapsed >= 0 && elapsed < RETRY_NANOS) return false;
        }
        createAttempted = true;
        lastCreateAttemptNanos = now;
        lastAttemptedModelId = effectiveConfig.global.selectedModelId;
        modelError = "";
        if (!nativeAccess.initialize()) {
            lastError = errorFromNative("Live2D runtime unavailable");
            modelError = lastError;
            safeLog(() -> LOGGER.warn("Live2D RETRY runtime-unavailable {}", lastError));
            return false;
        }
        long replacement = nativeAccess.create(selected.root().toString(), selected.modelJson().toString(),
                shaderRoot.get().toString(), ((long) effectiveConfig.performance.textureMemoryBudgetMiB) << 20);
        if (replacement == 0) {
            lastError = errorFromNative("Live2D model creation failed");
            modelError = lastError;
            safeLog(() -> LOGGER.warn("Live2D RETRY model-create-failed {}", lastError));
            return false;
        }
        Cleanup oldCleanup = handle == 0 ? Cleanup.SUCCESS : destroy(handle, "Live2D old model destruction failed");
        if (!oldCleanup.success()) {
            String oldError = oldCleanup.error();
            Cleanup replacementCleanup = destroy(replacement, "Live2D replacement cleanup failed");
            if (!replacementCleanup.success()) {
                cleanupHandles.add(replacement);
                sessionDisabled = true;
                lastError = joinErrors(oldError, replacementCleanup.error());
                logDisabled();
            } else {
                lastError = oldError;
            }
            modelError = lastError;
            return false;
        }
        handle = replacement;
        activeModelId = selected.id();
        lastFrameNanos = 0;
        createAttempted = false;
        lastError = "";
        modelError = "";
        firstFrameLogged = false;
        if (!readyLogged) {
            readyLogged = true;
            safeLog(() -> LOGGER.info("Live2D READY model-created id={} manifest={}", selected.id(), selected.modelJson()));
        }
        return true;
    }

    private void disable(Throwable failure) {
        String original = formatError(failure);
        sessionDisabled = true;
        String cleanupError = cleanupHandles();
        if (handle != 0) {
            Cleanup result = destroy(handle, "Live2D model destruction failed");
            if (result.success()) {
                handle = 0;
                activeModelId = "";
                firstFrameLogged = false;
            } else cleanupError = joinErrors(cleanupError, result.error());
        }
        lastError = bounded(joinErrors(original, cleanupError));
        logDisabled();
    }

    private void logDisabled() {
        if (disabledLogged) return;
        disabledLogged = true;
        safeLog(() -> LOGGER.error("Live2D DISABLED {}", lastError));
    }

    private String errorFromNative(String fallback) {
        try {
            String error = nativeAccess.lastError();
            return error == null || error.isBlank() ? fallback : formatError(error);
        } catch (Throwable failure) {
            rethrowFatal(failure);
            return joinErrors(fallback, formatError(failure));
        }
    }

    private String cleanupHandles() {
        String error = "";
        for (int i = cleanupHandles.size() - 1; i >= 0; --i) {
            long pending = cleanupHandles.get(i);
            Cleanup result = destroy(pending, "Live2D pending model destruction failed");
            if (result.success()) cleanupHandles.remove(i);
            else error = joinErrors(error, result.error());
        }
        return error;
    }

    private void invalidateSelection() {
        selectedModel = null;
        selectionKey = null;
        createAttempted = false;
    }

    private Cleanup destroy(long value, String fallback) {
        try {
            return nativeAccess.destroy(value) ? Cleanup.SUCCESS : new Cleanup(false, errorFromNative(fallback));
        } catch (Throwable failure) {
            rethrowFatal(failure);
            return new Cleanup(false, joinErrors(fallback, formatError(failure)));
        }
    }

    private static void safeLog(Runnable log) {
        try { log.run(); }
        catch (Throwable failure) { rethrowFatal(failure); }
    }

    private record Cleanup(boolean success, String error) {
        private static final Cleanup SUCCESS = new Cleanup(true, "");
    }

    private void requireRenderThread(boolean claim) {
        Thread current = Thread.currentThread();
        if (renderThread == null) {
            if (claim) renderThread = current;
            return;
        }
        if (renderThread != current) throw new IllegalStateException("Live2D client state must stay on one render thread");
    }

    private static Live2DVisibility.InventorySnapshot inventory(Inventory inventory) {
        boolean mainHand = !inventory.player.getMainHandItem().isEmpty();
        boolean offHand = !inventory.player.getOffhandItem().isEmpty();
        boolean hotbar = false;
        boolean any = false;
        for (int i = 0; i < inventory.items.size(); i++) {
            boolean nonEmpty = !inventory.items.get(i).isEmpty();
            any |= nonEmpty;
            if (i < 9) hotbar |= nonEmpty;
        }
        return new Live2DVisibility.InventorySnapshot(mainHand, offHand, hotbar, any);
    }

    private static String formatError(Object failure) {
        String message;
        try {
            message = String.valueOf(failure);
        } catch (Throwable formattingFailure) {
            rethrowFatal(formattingFailure);
            message = "Failed to format Live2D error";
        }
        if (message == null || message.isBlank()) message = "Unknown Live2D failure";
        return bounded(message);
    }

    private static String joinErrors(String first, String second) {
        if (first == null || first.isBlank()) return second == null ? "" : second;
        if (second == null || second.isBlank()) return first;
        String cleanup = "; cleanup: " + second;
        if (cleanup.length() >= MAX_ERROR_LENGTH)
            return cleanup.substring(0, MAX_ERROR_LENGTH);
        int firstLimit = MAX_ERROR_LENGTH - cleanup.length();
        return first.substring(0, Math.min(first.length(), firstLimit)) + cleanup;
    }

    private static String bounded(String message) {
        return message.substring(0, Math.min(message.length(), MAX_ERROR_LENGTH));
    }

    private static void rethrowFatal(Throwable failure) {
        if (failure instanceof ThreadDeath threadDeath) throw threadDeath;
        if (failure instanceof VirtualMachineError virtualMachineError) throw virtualMachineError;
    }
}
