package com.yuan.live2d.client.live2d;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.yuan.live2d.YuanLive2D;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.GameShuttingDownEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import org.joml.Matrix4f;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.Optional;
import java.util.function.Supplier;

@Mod.EventBusSubscriber(modid = YuanLive2D.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class Live2DHudRenderer {
    private static final Live2DLifecycle<Live2DClientState> LIFECYCLE = new Live2DLifecycle<>();
    private static long frameSequence;
    private static final UpdateTracker UPDATES = new UpdateTracker();
    private static boolean previewActive;
    private static boolean showWorldHud;
    private static volatile String activeModelId = "";
    private static int fadeTicksRemaining;
    private static String lastWorldModelId = "";
    private static final Live2DInteractionScheduler SCHEDULER = new Live2DInteractionScheduler(new java.util.Random());
    private static final java.util.Random CLICK_RANDOM = new java.util.Random();
    private static final Live2DOverlayPhysics overlayPhysics = new Live2DOverlayPhysics();
    private static final Live2DViewTracker VIEW_TRACKER = new Live2DViewTracker();
    private static float lastViewYRot = Float.NaN;
    private static float lastViewXRot = Float.NaN;
    private static boolean lastViewFollowEnabled;

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(Live2DHudRenderer::markShutdown, "yuan-live2d-shutdown"));
    }

    /**
     * Rectangle the Java quad is drawn into plus the matching UV rectangle into the
     * offscreen FBO. The quad is sized to the model's visible content so the FBO is
     * never sampled outside the model, which eliminates the dark-background flicker.
     */
    public record DrawBounds(
            int left, int top, int right, int bottom,
            float u0, float v0, float u1, float v1
    ) {}

    /**
     * Build a {@link DrawBounds} from a native {@code Bounds()} result, clamping to the
     * GUI rectangle and converting pixel positions to FBO UVs. Returns {@code null} when
     * the model is entirely off-screen so the caller can skip the draw.
     *
     * <p>The native bounds are already scaled to GUI pixels by the caller. The FBO is
     * framebuffer-sized, so a GUI pixel maps to the same texture UV whether the GUI
     * scale is 1 or 2; normalizing by the GUI dimensions keeps the sampled region
     * aligned at every scale.
     */
    public static DrawBounds buildDrawBounds(
            int guiWidth, int guiHeight,
            float left, float top, float right, float bottom) {
        int l = Math.max(0, Math.min(guiWidth,  (int) left));
        int t = Math.max(0, Math.min(guiHeight, (int) top));
        int r = Math.max(0, Math.min(guiWidth,  (int) right));
        int b = Math.max(0, Math.min(guiHeight, (int) bottom));
        if (r <= l || b <= t) return null;
        return new DrawBounds(
                l, t, r, b,
                l / (float) guiWidth,
                t / (float) guiHeight,
                r / (float) guiWidth,
                b / (float) guiHeight);
    }

    private static DrawBounds fullBounds(int guiWidth, int guiHeight) {
        return new DrawBounds(0, 0, guiWidth, guiHeight, 0f, 0f, 1f, 1f);
    }

    private static DrawBounds scaledFullBounds(int guiWidth, int guiHeight, float scaleX, float scaleY) {
        float width = guiWidth * scaleX;
        float height = guiHeight * scaleY;
        int left = Math.round((guiWidth - width) / 2f);
        int top = Math.round((guiHeight - height) / 2f);
        return new DrawBounds(left, top, Math.round(left + width), Math.round(top + height), 0f, 0f, 1f, 1f);
    }

    private static DrawBounds scaledFullBounds(int guiWidth, int guiHeight, float scaleX, float scaleY,
                                               float dx, float dy) {
        DrawBounds bounds = scaledFullBounds(guiWidth, guiHeight, scaleX, scaleY);
        int shiftX = Math.round(dx);
        int shiftY = Math.round(dy);
        return new DrawBounds(bounds.left() + shiftX, bounds.top() + shiftY,
                bounds.right() + shiftX, bounds.bottom() + shiftY,
                bounds.u0(), bounds.v0(), bounds.u1(), bounds.v1());
    }

    private static float fadeOpacity(int switchFadeTicks) {
        if (fadeTicksRemaining <= 0) return 1f;
        float progress = fadeTicksRemaining / (float) Math.max(1, switchFadeTicks);
        fadeTicksRemaining--;
        return Math.max(.1f, 1f - progress);
    }

    private static float normalizeDegrees(float delta) {
        return ((delta + 180f) % 360f + 360f) % 360f - 180f;
    }

    private static float deltaSeconds() {
        try {
            return Minecraft.getInstance().getDeltaFrameTime();
        } catch (RuntimeException unavailable) {
            return 1f / 60f;
        }
    }

    private static void drawShadow(GuiGraphics g, Live2DClientState.ModelBounds b,
                                   int guiWidth, int guiHeight) {
        if (b == null) return;
        int shadowWidth = Math.max(8, (int) ((b.right() - b.left()) * .7f));
        int shadowLeft = (int) (b.left() + (b.right() - b.left()) / 2f - shadowWidth / 2f);
        int shadowTop = (int) (b.bottom() + 4);
        int shadowBottom = Math.min(guiHeight, shadowTop + 14);
        if (shadowTop >= guiHeight || shadowBottom <= shadowTop) return;
        g.fillGradient(shadowLeft, shadowTop, shadowLeft + shadowWidth, shadowBottom,
                0x33101420, 0x00101420);
    }

    private static void drawQuad(GuiGraphics gui, Live2DClientState.DrawResult result,
                                 int guiWidth, int guiHeight, float widthScale, float heightScale,
                                 float opacity) {
        if (!result.hasTexture()) return;
        DrawBounds bounds = scaledFullBounds(guiWidth, guiHeight, widthScale, heightScale,
                overlayPhysics.dx,
                overlayPhysics.dy);
        float alpha = Math.max(0f, Math.min(1f, opacity));
        try {
            RenderSystem.disableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderTexture(0, result.textureId());
            RenderSystem.setShaderColor(1, 1, 1, alpha);
            Matrix4f matrix = gui.pose().last().pose();
            BufferBuilder buffer = Tesselator.getInstance().getBuilder();
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            buffer.vertex(matrix, bounds.left(),  bounds.bottom(), 0).uv(bounds.u0(), bounds.v0()).endVertex();
            buffer.vertex(matrix, bounds.right(), bounds.bottom(), 0).uv(bounds.u1(), bounds.v0()).endVertex();
            buffer.vertex(matrix, bounds.right(), bounds.top(),    0).uv(bounds.u1(), bounds.v1()).endVertex();
            buffer.vertex(matrix, bounds.left(),  bounds.top(),    0).uv(bounds.u0(), bounds.v1()).endVertex();
            BufferUploader.drawWithShader(buffer.end());
        } finally {
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.enableDepthTest();
        }
    }

    private Live2DHudRenderer() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void render(RenderGuiEvent.Post event) {
        RenderSystem.assertOnRenderThread();
        event.getGuiGraphics().flush();
        if (!previewActive || showWorldHud) {
            frame(LIFECYCLE, false, Live2DClientState::new, state -> {
                Live2DClientState.DrawResult result;
                Minecraft minecraft = Minecraft.getInstance();
                int guiWidth = minecraft.getWindow().getGuiScaledWidth();
                int guiHeight = minecraft.getWindow().getGuiScaledHeight();
                int framebufferWidth = minecraft.getWindow().getWidth();
                int framebufferHeight = minecraft.getWindow().getHeight();
                GuiGraphics gui = event.getGuiGraphics();
                if (Live2DAdjustMode.active()) {
                    Live2DAdjustMode.pollDrag();
                    result = renderAdjustFrame(state, guiWidth, guiHeight, framebufferWidth, framebufferHeight,
                            gui, UPDATES.shouldUpdate(frameSequence, state.activeHandle()));
                } else {
                    result = state.renderWorld(event.getPartialTick(),
                            UPDATES.shouldUpdate(frameSequence, state.activeHandle()), false);
                    Live2DConfig config = state.config();
                    Live2DConfig.Interaction interaction = config.interaction;
                    Live2DConfig.Physics physics = config.physics;
                    long now = System.currentTimeMillis();
                    float partialTick = event.getPartialTick();
                    float deltaSeconds = deltaSeconds();
                    if (config.physics.interactionEnabled) {
                        overlayPhysics.tick(deltaSeconds, config.physics.strength);
                    } else {
                        overlayPhysics.settle(deltaSeconds);
                    }
                    overlayPhysics.decay(deltaSeconds);
                    if (config.physics.interactionEnabled) {
                        state.modelBounds(guiWidth, guiHeight, framebufferWidth, framebufferHeight)
                                .ifPresent(bounds -> {
                                    // While the mouse is grabbed for look-around the raw cursor is
                                    // recentered every frame, so it would shove the model around;
                                    // only dodge when a real pointer can hover the model.
                                    if (Live2DOverlayPhysics.evadeAllowed(minecraft.mouseHandler.isMouseGrabbed())) {
                                        overlayPhysics.evade((float) mouseX(), (float) mouseY(), bounds,
                                                config.physics.strength, guiWidth, guiHeight);
                                    }
                                    overlayPhysics.edgeSquash(bounds, guiWidth, guiHeight,
                                            config.physics.edgeSquash);
                                });
                    }
                    if (result.hasTexture()) {
                        if (!lastWorldModelId.equals(state.activeModelId())) {
                            lastWorldModelId = state.activeModelId();
                            fadeTicksRemaining = config.render.switchFadeTicks;
                        }
                        float opacity = fadeOpacity(config.render.switchFadeTicks);
                        if (config.render.shadowEnabled) {
                            state.modelBounds(guiWidth, guiHeight, framebufferWidth, framebufferHeight)
                                    .ifPresent(b -> drawShadow(gui, b, guiWidth, guiHeight));
                        }
                        float scaleX = overlayPhysics.bounceScale * overlayPhysics.squashX;
                        float scaleY = overlayPhysics.bounceScale * overlayPhysics.squashY;
                        if (opacity < 1f) {
                            drawQuad(gui, result, guiWidth, guiHeight, scaleX, scaleY, opacity);
                        } else {
                            Live2DTextureRenderer.draw(gui, result, scaledFullBounds(
                                    guiWidth, guiHeight, scaleX, scaleY,
                                    overlayPhysics.dx, overlayPhysics.dy));
                        }
                    }
                    int flags = SCHEDULER.poll(interaction.randomMotionIntervalSeconds,
                            interaction.randomExpressionEnabled, now);
                    if (!interaction.randomMotionEnabled) flags &= ~Live2DInteractionScheduler.FLAG_RANDOM_MOTION;
                    if (!interaction.randomExpressionEnabled) flags &= ~Live2DInteractionScheduler.FLAG_RANDOM_EXPRESSION;
                    float lookX = 0f, lookY = 0f;
                    if (interaction.viewFollowEnabled) {
                        net.minecraft.world.entity.player.Player player = minecraft.player;
                        float yawDelta = 0f, pitchDelta = 0f;
                        if (player == null) {
                            lastViewYRot = Float.NaN;
                            lastViewXRot = Float.NaN;
                        } else {
                            float currentYaw = player.getViewYRot(partialTick);
                            float currentPitch = player.getViewXRot(partialTick);
                            if (Float.isFinite(lastViewYRot) && Float.isFinite(lastViewXRot)) {
                                yawDelta = normalizeDegrees(currentYaw - lastViewYRot);
                                pitchDelta = currentPitch - lastViewXRot;
                            }
                            lastViewYRot = currentYaw;
                            lastViewXRot = currentPitch;
                        }
                        if (!lastViewFollowEnabled) {
                            lastViewFollowEnabled = true;
                            lastViewYRot = Float.NaN;
                            lastViewXRot = Float.NaN;
                            VIEW_TRACKER.reset();
                        }
                        VIEW_TRACKER.update(yawDelta, pitchDelta, interaction.viewFollowStrength, deltaSeconds);
                        lookX = VIEW_TRACKER.lookX();
                        lookY = VIEW_TRACKER.lookY();
                    } else {
                        lastViewFollowEnabled = false;
                        VIEW_TRACKER.update(0f, 0f, 0f, deltaSeconds);
                    }
                    state.control(lookX, lookY, physics.amplitude, flags);
                }
                return result;
            });
        }
    }

    @SubscribeEvent
    public static void beginFrame(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) frameSequence++;
    }

    private static Live2DClientState.DrawResult renderAdjustFrame(Live2DClientState state,
            int guiWidth, int guiHeight, int framebufferWidth, int framebufferHeight,
            GuiGraphics gui, boolean update) {
        Live2DAdjustSession session = Live2DAdjustMode.session();
        if (session == null) return Live2DClientState.DrawResult.SKIPPED;
        Live2DConfig draft = session.draftConfig();
        Live2DConfig.Global effective = draft.effectiveGlobal(session.modelId());
        Live2DClientState.Placement placement = Live2DClientState.placement(effective.anchorX, effective.anchorY,
                effective.offsetX, effective.offsetY, effective.scale,
                guiWidth, guiHeight, framebufferWidth, framebufferHeight);
        if (!Live2DAdjustMode.entryClamped()) {
            Live2DClientState.Placement clamped = state.clampPlacement(placement, framebufferWidth, framebufferHeight);
            float shiftGuiX = (clamped.x() - placement.x()) * guiWidth / Math.max(1, framebufferWidth);
            float shiftGuiY = (clamped.y() - placement.y()) * guiHeight / Math.max(1, framebufferHeight);
            if (Float.compare(shiftGuiX, 0f) != 0 || Float.compare(shiftGuiY, 0f) != 0) {
                session.shiftEntry(shiftGuiX, shiftGuiY);
                draft = session.draftConfig();
                effective = draft.effectiveGlobal(session.modelId());
                placement = Live2DClientState.placement(effective.anchorX, effective.anchorY,
                        effective.offsetX, effective.offsetY, effective.scale,
                        guiWidth, guiHeight, framebufferWidth, framebufferHeight);
            }
            Live2DAdjustMode.markEntryClamped();
        }
        Live2DClientState.DrawResult result = state.renderWorld(
                new Live2DClientState.Frame(draft, true, guiWidth, guiHeight, framebufferWidth, framebufferHeight),
                update);
        Optional<Live2DClientState.ModelBounds> modelBounds = state.modelBounds(
                new Live2DClientState.PreviewFrame(draft, framebufferWidth, framebufferHeight,
                        placement.x(), placement.y(), placement.scale(), effective.opacity),
                guiWidth, guiHeight, framebufferWidth, framebufferHeight);
        if (modelBounds.isPresent()) {
            Live2DClientState.ModelBounds b = modelBounds.get();
            Live2DAdjustMode.setWindow(Live2DAdjustMath.windowBounds(
                    b.left(), b.top(), b.right(), b.bottom()));
        }
        Live2DTextureRenderer.draw(gui, result, fullBounds(guiWidth, guiHeight));
        Live2DAdjustOverlay.render(gui, Minecraft.getInstance().font, guiWidth, guiHeight,
                (int) mouseX(), (int) mouseY());
        return result;
    }

    private static double mouseX() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.mouseHandler.xpos() * minecraft.getWindow().getGuiScaledWidth()
                / Math.max(1, minecraft.getWindow().getScreenWidth());
    }

    private static double mouseY() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.mouseHandler.ypos() * minecraft.getWindow().getGuiScaledHeight()
                / Math.max(1, minecraft.getWindow().getScreenHeight());
    }

    public static void beginPreview(boolean showWorldHud) {
        RenderSystem.assertOnRenderThread();
        previewActive = true;
        Live2DHudRenderer.showWorldHud = showWorldHud;
        LIFECYCLE.openPreview();
    }

    public static void setPreviewWorldHud(boolean showWorldHud) {
        RenderSystem.assertOnRenderThread();
        Live2DHudRenderer.showWorldHud = showWorldHud;
    }





    public static void renderPreview(GuiGraphics graphics, Live2DClientState.PreviewFrame preview) {
        RenderSystem.assertOnRenderThread();
        frame(LIFECYCLE, true, Live2DClientState::new, state -> {
            Live2DClientState.DrawResult result = state.renderPreview(
                    preview, UPDATES.shouldUpdate(frameSequence, state.activeHandle()));
            Minecraft minecraft = Minecraft.getInstance();
            int guiWidth = minecraft.getWindow().getGuiScaledWidth();
            int guiHeight = minecraft.getWindow().getGuiScaledHeight();
            int framebufferWidth = minecraft.getWindow().getWidth();
            int framebufferHeight = minecraft.getWindow().getHeight();
            Optional<Live2DClientState.ModelBounds> modelBounds = state.modelBounds(
                    preview, guiWidth, guiHeight, framebufferWidth, framebufferHeight);
            if (modelBounds.isPresent()) {
                Live2DClientState.ModelBounds b = modelBounds.get();
                DrawBounds drawBounds = buildDrawBounds(
                        guiWidth, guiHeight,
                        b.left(), b.top(), b.right(), b.bottom());
                Live2DTextureRenderer.draw(graphics, result, drawBounds);
            }
            return result;
        });
    }

    public static void previewConfig(Live2DConfig config) {
        RenderSystem.assertOnRenderThread();
        frame(LIFECYCLE, true, Live2DClientState::new, state -> {
            state.previewConfig(config);
            return Live2DClientState.DrawResult.SKIPPED;
        });
    }

    public static long estimateTextures(Live2DModelManifest manifest) {
        RenderSystem.assertOnRenderThread();
        long[] result = {0};
        frame(LIFECYCLE, false, Live2DClientState::new, state -> {
            result[0] = state.estimateTextures(manifest);
            return Live2DClientState.DrawResult.SKIPPED;
        });
        return result[0];
    }

    public static void reloadConfig() {
        RenderSystem.assertOnRenderThread();
        frame(LIFECYCLE, true, Live2DClientState::new, state -> {
            state.reloadConfig();
            return Live2DClientState.DrawResult.SKIPPED;
        });
    }

    public static String lastError() {
        Live2DClientState state = LIFECYCLE.existing();
        if (state == null) return "";
        return state.previewError().isEmpty() ? state.lastError() : state.previewError();
    }

    public static Live2DClientState.ModelStatus modelStatus() {
        Live2DClientState state = LIFECYCLE.existing();
        return state == null ? new Live2DClientState.ModelStatus(activeModelId, "", "") : state.modelStatus();
    }

    public static String activeModelId() {
        Live2DClientState state = LIFECYCLE.existing();
        return state == null ? activeModelId : state.activeModelId();
    }

    public static Optional<Live2DClientState.ModelBounds> modelBounds(int guiWidth, int guiHeight) {
        RenderSystem.assertOnRenderThread();
        Live2DClientState state = LIFECYCLE.existing();
        return state == null ? Optional.empty() : state.modelBounds(guiWidth, guiHeight);
    }

    public static void endPreview() {
        RenderSystem.assertOnRenderThread();
        previewActive = false;
        showWorldHud = false;
        Live2DLifecycle.Close<Live2DClientState> close = LIFECYCLE.closePreview(Live2DClientState::requestClose);
        if (close.cleanupNow()) queueClose(close.state());
    }

    static final class UpdateTracker {
        private long sequence = Long.MIN_VALUE;
        private long handle;

        boolean shouldUpdate(long currentSequence, long currentHandle) {
            return sequence != currentSequence || handle != currentHandle || currentHandle == 0;
        }

        void commit(long currentSequence, Live2DClientState.DrawResult result) {
            if (result.status() == Live2DClientState.DrawStatus.DRAWN_UPDATED
                    || result.status() == Live2DClientState.DrawStatus.FAILED_UPDATED) {
                sequence = currentSequence;
                handle = result.handle();
            }
        }
    }

    @SubscribeEvent
    public static void login(ClientPlayerNetworkEvent.LoggingIn event) {
        LIFECYCLE.login();
    }

    @SubscribeEvent
    public static void onMouseButton(net.minecraftforge.client.event.InputEvent.MouseButton.Pre event) {
        if (Live2DAdjustMode.active()) {
            Live2DAdjustMode.handleMouse(event.getButton(), event.getAction(), event.getModifiers());
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onClick(net.minecraftforge.client.event.InputEvent.MouseButton.Pre event) {
        if (event.getAction() != GLFW.GLFW_PRESS || Live2DAdjustMode.active()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || minecraft.player == null) return;
        Live2DClientState state = LIFECYCLE.existing();
        if (state == null) return;
        Live2DConfig config = state.config();
        if (!config.interaction.clickReactionEnabled) return;
        int guiWidth = minecraft.getWindow().getGuiScaledWidth();
        int guiHeight = minecraft.getWindow().getGuiScaledHeight();
        double mx = mouseX();
        double my = mouseY();
        var bounds = state.modelBounds(guiWidth, guiHeight);
        if (bounds.isEmpty() || !Live2DInteractionMath.hitTest(mx, my, bounds.get())) return;
        if (!Live2DInteractionMath.rollChance(config.interaction.clickReactionChance, CLICK_RANDOM)) return;
        state.control(0f, 0f, config.physics.amplitude, Live2DInteractionScheduler.FLAG_CLICK_REACTION);
        overlayPhysics.kickBounce(config.physics.strength);
    }

    @SubscribeEvent
    public static void onKey(net.minecraftforge.client.event.InputEvent.Key event) {
        if (Live2DAdjustMode.active()) Live2DAdjustMode.handleKey(event.getKey(), event.getAction(), event.getModifiers());
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        Live2DAdjustMode.exitQuietly();
        Runnable cleanup = () -> {
            Live2DLifecycle.Close<Live2DClientState> close = LIFECYCLE.logout(Live2DClientState::requestClose);
            if (close.cleanupNow()) queueClose(close.state());
        };
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isSameThread()) cleanup.run(); else minecraft.execute(cleanup);
    }

    @SubscribeEvent
    public static void shutdown(GameShuttingDownEvent event) {
        Live2DAdjustMode.exitQuietly();
        requestClose(true);
    }

    private static void markShutdown() {
        LIFECYCLE.close(true, Live2DClientState::requestClose);
    }

    private static void requestClose(boolean terminal) {
        Live2DLifecycle.Close<Live2DClientState> close =
                LIFECYCLE.close(terminal, Live2DClientState::requestClose);
        if (close.state() == null) return;
        if (close.cleanupNow()) queueClose(close.state());
    }

    private static void queueClose(Live2DClientState state) {
        if (RenderSystem.isOnRenderThread()) {
            RenderSystem.assertOnRenderThread();
            processClose(state);
        } else {
            RenderSystem.recordRenderCall(() -> processClose(state));
        }
    }

    private static void processClose(Live2DClientState state) {
        state.processPendingClose();
        activeModelId = state.activeModelId();
        finishClose(state);
    }

    private static void finishClose(Live2DClientState state) {
        if (!state.closePending()) LIFECYCLE.cleaned(state);
    }

    static void frame(Live2DLifecycle<Live2DClientState> lifecycle, Supplier<Live2DClientState> factory,
                       Consumer<Live2DClientState> render) {
        frame(lifecycle, false, factory, state -> {
            render.accept(state);
            return Live2DClientState.DrawResult.SKIPPED;
        });
    }

    static void frame(Live2DLifecycle<Live2DClientState> lifecycle, boolean preview,
                      Supplier<Live2DClientState> factory,
                      Function<Live2DClientState, Live2DClientState.DrawResult> render) {
        Live2DClientState existing = lifecycle.existing();
        if (existing != null && existing.processPendingClose()) {
            if (!existing.closePending()) lifecycle.cleaned(existing);
            return;
        }
        Live2DLifecycle.Admission<Live2DClientState> admission = preview
                ? lifecycle.beginPreviewFrame(factory) : lifecycle.beginFrame(factory);
        if (admission == null) return;
        try {
            Live2DClientState.DrawResult result = render.apply(admission.state());
            activeModelId = admission.state().activeModelId();
            UPDATES.commit(frameSequence, result);
        } finally {
            Live2DClientState cleanup = lifecycle.endFrame(admission);
            if (cleanup != null) processClose(lifecycle, cleanup);
        }
    }

    private static void processClose(Live2DLifecycle<Live2DClientState> lifecycle, Live2DClientState state) {
        state.processPendingClose();
        activeModelId = state.activeModelId();
        if (!state.closePending()) lifecycle.cleaned(state);
    }


}
