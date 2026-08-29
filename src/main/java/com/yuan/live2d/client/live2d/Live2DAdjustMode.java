package com.yuan.live2d.client.live2d;

import com.yuan.live2d.client.gui.Live2DConfigScreen;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class Live2DAdjustMode {
    private static final long DOUBLE_CLICK_NANOS = 300_000_000L;
    private static final float SCALE_STEP = .01f;

    private static boolean active;
    private static Live2DAdjustSession session;
    private static String modelId = "";
    private static Live2DAdjustMath.Handle dragHandle = Live2DAdjustMath.Handle.NONE;
    private static Live2DAdjustMath.Bounds lastWindow;
    private static double lastMouseX;
    private static double lastMouseY;
    private static double dragStartDiagonal;
    private static float dragStartScale;
    private static long lastBorderClickNanos;
    private static boolean snappedX;
    private static boolean snappedY;
    private static float snapGuideX = Float.NaN;
    private static float snapGuideY = Float.NaN;
    private static float windowBaseOffsetX;
    private static float windowBaseOffsetY;
    private static boolean entryClamped;
    private static long lastMousePressNanos;
    private static int lastMouseButton;
    private static long lastKeyPressNanos;
    private static int lastKeyCode;

    private Live2DAdjustMode() {}

    public static boolean active() { return active; }
    public static boolean entryClamped() { return entryClamped; }
    public static void markEntryClamped() { entryClamped = true; }
    public static Live2DAdjustSession session() { return session; }
    public static String modelId() { return modelId; }
    public static Live2DAdjustMath.Bounds lastWindow() { return lastWindow; }

    public static void setWindow(Live2DAdjustMath.Bounds window) {
        lastWindow = window;
        if (session != null) {
            windowBaseOffsetX = session.offsetX();
            windowBaseOffsetY = session.offsetY();
        }
    }

    public static void enter(Live2DConfig config, Live2DModelManifest model, Live2DConfigStore store) {
        session = new Live2DAdjustSession(config, model, store::save);
        modelId = model.id();
        active = true;
        dragHandle = Live2DAdjustMath.Handle.NONE;
        lastWindow = null;
        windowBaseOffsetX = 0;
        windowBaseOffsetY = 0;
        entryClamped = false;
        snappedX = false;
        snappedY = false;
        snapGuideX = Float.NaN;
        snapGuideY = Float.NaN;
        lastBorderClickNanos = 0;
        lastMousePressNanos = 0;
        lastKeyPressNanos = 0;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.mouseHandler != null) minecraft.mouseHandler.releaseMouse();
    }

    public static void exitQuietly() {
        if (!active) return;
        active = false;
        session = null;
        modelId = "";
        dragHandle = Live2DAdjustMath.Handle.NONE;
        lastWindow = null;
        windowBaseOffsetX = 0;
        windowBaseOffsetY = 0;
        entryClamped = false;
        snappedX = false;
        snappedY = false;
        snapGuideX = Float.NaN;
        snapGuideY = Float.NaN;
        lastBorderClickNanos = 0;
        lastMousePressNanos = 0;
        lastKeyPressNanos = 0;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.mouseHandler != null && !minecraft.mouseHandler.isMouseGrabbed()) {
            minecraft.mouseHandler.grabMouse();
        }
    }

    public static void exitToConfig() {
        exitQuietly();
        Minecraft.getInstance().setScreen(new Live2DConfigScreen());
    }

    public static void handleMouse(int button, int action, int modifiers) {
        if (!active || session == null || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;
        double x = mouseX();
        double y = mouseY();
        if (action == GLFW.GLFW_PRESS) {
            long now = System.nanoTime();
            if (button == lastMouseButton && now - lastMousePressNanos < 10_000_000L) return;
            lastMouseButton = button;
            lastMousePressNanos = now;
        }
        if (session.confirmPending()) {
            dragHandle = Live2DAdjustMath.Handle.NONE;
            if (action == GLFW.GLFW_PRESS) handleConfirmClick(x, y);
            return;
        }
        if (action == GLFW.GLFW_RELEASE) {
            dragHandle = Live2DAdjustMath.Handle.NONE;
            snappedX = false;
            snappedY = false;
            return;
        }
        Live2DAdjustLayout.Bar bar = Live2DAdjustLayout.bar(screenWidth(), screenHeight());
        if (bar.bar().contains(x, y)) {
            handleBarClick(bar, x, y);
            return;
        }
        if (lastWindow == null) return;
        Live2DAdjustMath.Handle hit = Live2DAdjustMath.hit(x, y, lastWindow);
        if (hit == Live2DAdjustMath.Handle.NONE) return;
        if (hit == Live2DAdjustMath.Handle.MOVE) {
            snappedX = false;
            snappedY = false;
            lastMouseX = x;
            lastMouseY = y;
            long now = System.nanoTime();
            if (now - lastBorderClickNanos < DOUBLE_CLICK_NANOS) {
                session.resetToEntry();
                dragHandle = Live2DAdjustMath.Handle.NONE;
                return;
            }
            lastBorderClickNanos = now;
            dragHandle = Live2DAdjustMath.Handle.MOVE;
            return;
        }
        if (hit == Live2DAdjustMath.Handle.BODY) {
            snappedX = false;
            snappedY = false;
            dragHandle = Live2DAdjustMath.Handle.BODY;
            lastMouseX = x;
            lastMouseY = y;
            return;
        }
        snappedX = false;
        snappedY = false;
        dragHandle = hit;
        lastMouseX = x;
        lastMouseY = y;
        dragStartScale = session.scale();
        dragStartDiagonal = diagonal(x, y, hit, lastWindow);
    }

    public static void pollDrag() {
        if (!active || session == null || dragHandle == Live2DAdjustMath.Handle.NONE) return;
        if (session.confirmPending()) {
            dragHandle = Live2DAdjustMath.Handle.NONE;
            return;
        }
        double x = mouseX();
        double y = mouseY();
        double dx = x - lastMouseX;
        double dy = y - lastMouseY;
        switch (dragHandle) {
            case MOVE, BODY -> {
                session.setOffsetX(session.offsetX() + (float) dx);
                session.setOffsetY(session.offsetY() + (float) dy);
            }
            case LEFT, RIGHT -> {
                float delta = dragHandle == Live2DAdjustMath.Handle.LEFT ? (float) -dx : (float) dx;
                session.setScale(Live2DAdjustMath.freeResize(session.scale(), delta));
            }
            case TOP, BOTTOM -> {
                float delta = dragHandle == Live2DAdjustMath.Handle.TOP ? (float) -dy : (float) dy;
                session.setScale(Live2DAdjustMath.freeResize(session.scale(), delta));
            }
            default -> {
                double current = diagonal(x, y, dragHandle, lastWindow);
                session.setScale(Live2DAdjustMath.aspectResize(dragStartScale, (float) dragStartDiagonal, (float) current));
            }
        }
        lastMouseX = x;
        lastMouseY = y;
        updateSnap();
        clampPosition();
    }

    public static void handleKey(int key, int action, int modifiers) {
        if (!active || session == null || action != GLFW.GLFW_PRESS) return;
        long now = System.nanoTime();
        if (key == lastKeyCode && now - lastKeyPressNanos < 10_000_000L) return;
        lastKeyCode = key;
        lastKeyPressNanos = now;
        if (session.confirmPending()) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                dismissPauseScreen();
                session.cancelConfirm();
            }
            return;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            dismissPauseScreen();
            if (session.requestBack()) exitToConfig();
            return;
        }
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        float step = ctrl ? 10f : 1f;
        switch (key) {
            case GLFW.GLFW_KEY_UP -> session.nudge(0, shift ? 0 : -step, shift ? -SCALE_STEP : 0);
            case GLFW.GLFW_KEY_DOWN -> session.nudge(0, shift ? 0 : step, shift ? SCALE_STEP : 0);
            case GLFW.GLFW_KEY_LEFT -> session.nudge(shift ? 0 : -step, 0, shift ? -SCALE_STEP : 0);
            case GLFW.GLFW_KEY_RIGHT -> session.nudge(shift ? 0 : step, 0, shift ? SCALE_STEP : 0);
            default -> { }
        }
        if (!shift) clampPosition();
    }

    private static void dismissPauseScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.screen instanceof net.minecraft.client.gui.screens.PauseScreen) {
            minecraft.setScreen(null);
            if (active && minecraft.mouseHandler != null) minecraft.mouseHandler.releaseMouse();
        }
    }

    private static void handleBarClick(Live2DAdjustLayout.Bar bar, double x, double y) {
        if (bar.target().contains(x, y)) {
            session.setTarget(session.target() == Live2DAdjustSession.Target.GLOBAL
                    ? Live2DAdjustSession.Target.OVERRIDE : Live2DAdjustSession.Target.GLOBAL);
            return;
        }
        if (bar.back().contains(x, y)) {
            if (session.requestBack()) exitToConfig();
            return;
        }
        if (bar.apply().contains(x, y)) {
            session.apply();
            return;
        }
        if (bar.save().contains(x, y)) {
            if (session.apply()) exitToConfig();
        }
    }

    private static void handleConfirmClick(double x, double y) {
        Live2DAdjustMath.Bounds dialog = Live2DAdjustLayout.confirmDialog(screenWidth(), screenHeight());
        Live2DAdjustMath.Bounds discard = new Live2DAdjustMath.Bounds(dialog.left() + 24, dialog.bottom() - 30,
                dialog.left() + 24 + 100, dialog.bottom() - 10);
        Live2DAdjustMath.Bounds cancel = new Live2DAdjustMath.Bounds(dialog.right() - 24 - 100, dialog.bottom() - 30,
                dialog.right() - 24, dialog.bottom() - 10);
        if (discard.contains(x, y)) {
            session.confirmDiscard();
            exitToConfig();
        } else if (cancel.contains(x, y)) {
            session.cancelConfirm();
        }
    }

    private static void updateSnap() {
        if (lastWindow == null || session == null) return;
        int width = screenWidth();
        int height = adjustAreaHeight();
        if (snappedX) {
            if (guideDistanceX(lastWindow, snapGuideX, width) > Live2DAdjustMath.SNAP_THRESHOLD) {
                snappedX = false;
            }
        } else {
            Live2DAdjustMath.SnapResult snap = Live2DAdjustMath.snap(lastWindow, width, height);
            if (snap.x()) {
                session.setOffsetX(session.offsetX() + snap.dx());
                snappedX = true;
                snapGuideX = snap.guideX();
            }
        }
        if (snappedY) {
            if (guideDistanceY(lastWindow, snapGuideY, height) > Live2DAdjustMath.SNAP_THRESHOLD) {
                snappedY = false;
            }
        } else {
            Live2DAdjustMath.SnapResult snap = Live2DAdjustMath.snap(lastWindow, width, height);
            if (snap.y()) {
                session.setOffsetY(session.offsetY() + snap.dy());
                snappedY = true;
                snapGuideY = snap.guideY();
            }
        }
    }

    private static float guideDistanceX(Live2DAdjustMath.Bounds window, float guide, int width) {
        if (guide == 0f) return window.left();
        if (guide == width) return width - window.right();
        return Math.abs(window.centerX() - guide);
    }

    private static float guideDistanceY(Live2DAdjustMath.Bounds window, float guide, int height) {
        if (guide == 0f) return window.top();
        if (guide == height) return height - window.bottom();
        return Math.abs(window.centerY() - guide);
    }

    private static double diagonal(double x, double y, Live2DAdjustMath.Handle handle, Live2DAdjustMath.Bounds window) {
        double oppositeX = handle == Live2DAdjustMath.Handle.RIGHT || handle == Live2DAdjustMath.Handle.TOP_RIGHT
                || handle == Live2DAdjustMath.Handle.BOTTOM_RIGHT ? window.left() : window.right();
        double oppositeY = handle == Live2DAdjustMath.Handle.BOTTOM || handle == Live2DAdjustMath.Handle.BOTTOM_LEFT
                || handle == Live2DAdjustMath.Handle.BOTTOM_RIGHT ? window.top() : window.bottom();
        return Math.hypot(x - oppositeX, y - oppositeY);
    }

    private static int screenWidth() {
        return Minecraft.getInstance().getWindow().getGuiScaledWidth();
    }

    private static int screenHeight() {
        return Minecraft.getInstance().getWindow().getGuiScaledHeight();
    }

    private static double mouseX() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.mouseHandler.xpos() * screenWidth()
                / Math.max(1, minecraft.getWindow().getScreenWidth());
    }

    private static double mouseY() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.mouseHandler.ypos() * screenHeight()
                / Math.max(1, minecraft.getWindow().getScreenHeight());
    }

    private static int adjustAreaHeight() {
        return Math.max(0, screenHeight() - Live2DAdjustMath.BAR_HEIGHT);
    }

    private static void clampPosition() {
        if (session == null || lastWindow == null) return;
        float currentLeft = lastWindow.left() + (session.offsetX() - windowBaseOffsetX);
        float currentTop = lastWindow.top() + (session.offsetY() - windowBaseOffsetY);
        Live2DAdjustMath.Bounds current = new Live2DAdjustMath.Bounds(currentLeft, currentTop,
                currentLeft + lastWindow.width(), currentTop + lastWindow.height());
        Live2DAdjustMath.Bounds clamped = Live2DAdjustMath.clampToArea(current,
                screenWidth(), adjustAreaHeight());
        if (Float.compare(clamped.left(), currentLeft) != 0) {
            session.setOffsetX(session.offsetX() + (clamped.left() - currentLeft));
        }
        if (Float.compare(clamped.top(), currentTop) != 0) {
            session.setOffsetY(session.offsetY() + (clamped.top() - currentTop));
        }
    }
}
