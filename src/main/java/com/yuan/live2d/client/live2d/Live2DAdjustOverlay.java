package com.yuan.live2d.client.live2d;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public final class Live2DAdjustOverlay {
    private static final int GOLD = 0xFFE1B65A;
    private static final int TEXT = 0xFFE5E0E8;
    private static final int MUTED = 0xFF948A9E;
    private static final int PANEL = 0xE6121018;
    private static final int PANEL_ALT = 0xE61A1620;
    private static final int RED = 0xFFFF8E78;
    private static final int BORDER = 0xFF3A3042;

    private Live2DAdjustOverlay() {}

    public static void render(GuiGraphics g, Font font, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        Live2DAdjustSession session = Live2DAdjustMode.session();
        Live2DAdjustMath.Bounds window = Live2DAdjustMode.lastWindow();
        Live2DAdjustLayout.Bar bar = Live2DAdjustLayout.bar(screenWidth, screenHeight);
        drawBar(g, font, bar, session, mouseX, mouseY);
        if (window != null) {
            drawWindow(g, font, window, mouseX, mouseY);
            drawGuides(g, window, screenWidth, screenHeight);
        }
        Live2DClientState.ModelStatus status = Live2DHudRenderer.modelStatus();
        if (status != null && !status.error().isEmpty()) {
            int errorY = window == null ? 36 : Math.max(0, (int) window.top() - 26);
            g.drawString(font, status.error(), 8, errorY, RED);
        }
        if (session != null && session.confirmPending()) {
            drawConfirm(g, font, screenWidth, screenHeight, mouseX, mouseY);
        }
    }

    private static void drawBar(GuiGraphics g, Font font, Live2DAdjustLayout.Bar bar,
                                Live2DAdjustSession session, int mouseX, int mouseY) {
        g.fill((int) bar.bar().left(), (int) bar.bar().top(), (int) bar.bar().right(), (int) bar.bar().bottom(), PANEL);
        g.fill((int) bar.bar().left(), (int) bar.bar().top(), (int) bar.bar().right(), (int) bar.bar().top() + 1, BORDER);
        if (session == null) return;
        String targetLabel = session.target() == Live2DAdjustSession.Target.OVERRIDE ? "写入：该模型覆盖" : "写入：全局";
        g.fill((int) bar.target().left(), (int) bar.target().top(), (int) bar.target().right(), (int) bar.target().bottom(),
                bar.target().contains(mouseX, mouseY) ? 0xFF4B3820 : PANEL_ALT);
        g.drawCenteredString(font, targetLabel, (int) ((bar.target().left() + bar.target().right()) / 2),
                (int) bar.target().top() + 5, TEXT);
        drawButton(g, font, bar.back(), "返回", mouseX, mouseY, TEXT);
        drawButton(g, font, bar.apply(), "应用", mouseX, mouseY, TEXT);
        drawButton(g, font, bar.save(), "保存", mouseX, mouseY, GOLD);
        if (!session.error().isEmpty()) {
            g.drawString(font, session.error(), (int) bar.target().right() + 12, (int) bar.bar().top() + 8, RED);
        }
    }

    private static void drawButton(GuiGraphics g, Font font, Live2DAdjustMath.Bounds bounds,
                                   String label, int mouseX, int mouseY, int color) {
        g.fill((int) bounds.left(), (int) bounds.top(), (int) bounds.right(), (int) bounds.bottom(),
                bounds.contains(mouseX, mouseY) ? 0xFF4B3820 : PANEL_ALT);
        g.drawCenteredString(font, label, (int) ((bounds.left() + bounds.right()) / 2),
                (int) bounds.top() + 5, color);
    }

    private static void drawWindow(GuiGraphics g, Font font, Live2DAdjustMath.Bounds window,
                                   int mouseX, int mouseY) {
        int left = (int) window.left();
        int top = (int) window.top();
        int right = (int) window.right();
        int bottom = (int) window.bottom();
        g.fill(left, top, right, top + 1, GOLD);
        g.fill(left, bottom - 1, right, bottom, GOLD);
        g.fill(left, top, left + 1, bottom, GOLD);
        g.fill(right - 1, top, right, bottom, GOLD);
        int half = Live2DAdjustMath.HANDLE_SIZE / 2;
        drawHandle(g, left - half, top - half, half * 2, half * 2, mouseX, mouseY);
        drawHandle(g, right - half, top - half, half * 2, half * 2, mouseX, mouseY);
        drawHandle(g, left - half, bottom - half, half * 2, half * 2, mouseX, mouseY);
        drawHandle(g, right - half, bottom - half, half * 2, half * 2, mouseX, mouseY);
        drawHandle(g, (left + right) / 2 - half, top - half, half * 2, half * 2, mouseX, mouseY);
        drawHandle(g, (left + right) / 2 - half, bottom - half, half * 2, half * 2, mouseX, mouseY);
        drawHandle(g, left - half, (top + bottom) / 2 - half, half * 2, half * 2, mouseX, mouseY);
        drawHandle(g, right - half, (top + bottom) / 2 - half, half * 2, half * 2, mouseX, mouseY);
        String title = Live2DAdjustMode.modelId();
        g.drawString(font, title, left + 4, Math.max(0, top - 12), MUTED);
    }

    private static void drawHandle(GuiGraphics g, int x, int y, int width, int height, int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        g.fill(x, y, x + width, y + height, hover ? GOLD : PANEL_ALT);
        g.fill(x + 1, y + 1, x + width - 1, y + height - 1, BORDER);
    }

    private static void drawGuides(GuiGraphics g, Live2DAdjustMath.Bounds window,
                                   int screenWidth, int screenHeight) {
        int areaHeight = Math.max(0, screenHeight - Live2DAdjustMath.BAR_HEIGHT);
        Live2DAdjustMath.SnapResult snap = Live2DAdjustMath.snap(window, screenWidth, areaHeight);
        if (snap.x() && Float.isFinite(snap.guideX())) {
            int x = (int) Math.max(0, Math.min(screenWidth - 1, snap.guideX()));
            g.fill(x, 0, x + 1, screenHeight, 0x66E1B65A);
        }
        if (snap.y() && Float.isFinite(snap.guideY())) {
            int y = (int) Math.max(0, Math.min(screenHeight - 1, snap.guideY()));
            g.fill(0, y, screenWidth, y + 1, 0x66E1B65A);
        }
    }

    private static void drawConfirm(GuiGraphics g, Font font, int screenWidth, int screenHeight,
                                    int mouseX, int mouseY) {
        Live2DAdjustMath.Bounds dialog = Live2DAdjustLayout.confirmDialog(screenWidth, screenHeight);
        int left = (int) dialog.left();
        int top = (int) dialog.top();
        int right = (int) dialog.right();
        int bottom = (int) dialog.bottom();
        g.fill(left, top, right, bottom, PANEL);
        g.fill(left, top, right, top + 1, GOLD);
        g.fill(left, bottom - 1, right, bottom, GOLD);
        g.fill(left, top, left + 1, bottom, GOLD);
        g.fill(right - 1, top, right, bottom, GOLD);
        g.drawCenteredString(font, "有未保存的修改，返回将丢弃？", (left + right) / 2, top + 18, TEXT);
        Live2DAdjustMath.Bounds discard = new Live2DAdjustMath.Bounds(left + 24, bottom - 30, left + 124, bottom - 10);
        Live2DAdjustMath.Bounds cancel = new Live2DAdjustMath.Bounds(right - 124, bottom - 30, right - 24, bottom - 10);
        drawButton(g, font, discard, "放弃修改", mouseX, mouseY, RED);
        drawButton(g, font, cancel, "取消", mouseX, mouseY, TEXT);
    }
}
