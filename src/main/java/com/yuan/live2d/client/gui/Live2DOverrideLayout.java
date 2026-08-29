package com.yuan.live2d.client.gui;

import java.util.ArrayList;
import java.util.List;

public final class Live2DOverrideLayout {
    public static final int FIELD_COUNT = 9;
    public static final int VISIBILITY_COUNT = Live2DConfigLayout.VISIBILITY_COUNT;
    public static final int ACTION_COUNT = 3;

    private final Live2DConfigLayout.Bounds header;
    private final Live2DConfigLayout.Bounds leftPanel;
    private final Live2DConfigLayout.Bounds preview;
    private final List<Live2DConfigLayout.Bounds> rows = new ArrayList<>();
    private final List<Live2DConfigLayout.Bounds> modeButtons = new ArrayList<>();
    private final List<Live2DConfigLayout.Bounds> controls = new ArrayList<>();
    private final List<Live2DConfigLayout.Bounds> visibilityButtons = new ArrayList<>();
    private Live2DConfigLayout.Bounds copyFromGlobal;
    private Live2DConfigLayout.Bounds clearOverride;
    private Live2DConfigLayout.Bounds back;
    private Live2DConfigLayout.Bounds status;
    private final int scroll;
    private final int maxScroll;

    private Live2DOverrideLayout(int width, int height, int scroll) {
        header = new Live2DConfigLayout.Bounds(0, 0, width, 28);
        boolean stacked = width < 760;
        int margin = 10;
        int leftTop = 38;
        if (stacked) {
            int leftHeight = Math.max(120, height - leftTop - margin - 190);
            leftPanel = new Live2DConfigLayout.Bounds(margin, leftTop, width - margin,
                    Math.min(height - margin, leftTop + leftHeight));
            int previewTop = leftPanel.bottom() + margin;
            preview = new Live2DConfigLayout.Bounds(margin, previewTop, width - margin, height - margin);
        } else {
            int leftWidth = Math.min(360, Math.max(280, width * 4 / 10));
            leftPanel = new Live2DConfigLayout.Bounds(margin, leftTop, margin + leftWidth, height - margin);
            preview = new Live2DConfigLayout.Bounds(leftPanel.right() + margin, leftTop, width - margin, height - margin);
        }

        int innerLeft = leftPanel.left() + 12;
        int innerWidth = leftPanel.width() - 24;
        int rowHeight = 32;
        int gap = 6;
        int top = leftPanel.top() + 30;
        for (int i = 0; i < FIELD_COUNT; i++) {
            int rowTop = top + i * (rowHeight + gap);
            rows.add(new Live2DConfigLayout.Bounds(innerLeft, rowTop, innerLeft + innerWidth, rowTop + rowHeight));
            int modeWidth = 46;
            modeButtons.add(new Live2DConfigLayout.Bounds(innerLeft, rowTop + 3, innerLeft + modeWidth, rowTop + 21));
            int labelWidth = 64;
            int controlLeft = innerLeft + modeWidth + 6 + labelWidth;
            controls.add(new Live2DConfigLayout.Bounds(controlLeft, rowTop + 3, innerLeft + innerWidth, rowTop + 21));
        }
        Live2DConfigLayout.Bounds visibilityControl = controls.get(1);
        int buttonWidth = (visibilityControl.width() - (VISIBILITY_COUNT - 1) * 4) / VISIBILITY_COUNT;
        for (int i = 0; i < VISIBILITY_COUNT; i++) {
            int left = visibilityControl.left() + i * (buttonWidth + 4);
            visibilityButtons.add(new Live2DConfigLayout.Bounds(left, visibilityControl.top(),
                    left + buttonWidth, visibilityControl.bottom()));
        }

        int buttonsTop = rows.get(FIELD_COUNT - 1).bottom() + 10;
        int actionWidth = (innerWidth - (ACTION_COUNT - 1) * 6) / ACTION_COUNT;
        copyFromGlobal = new Live2DConfigLayout.Bounds(innerLeft, buttonsTop, innerLeft + actionWidth, buttonsTop + 22);
        clearOverride = new Live2DConfigLayout.Bounds(innerLeft + actionWidth + 6, buttonsTop,
                innerLeft + 2 * actionWidth + 6, buttonsTop + 22);
        back = new Live2DConfigLayout.Bounds(innerLeft + 2 * actionWidth + 12, buttonsTop,
                innerLeft + innerWidth, buttonsTop + 22);
        status = new Live2DConfigLayout.Bounds(innerLeft, buttonsTop + 26, innerLeft + innerWidth, buttonsTop + 42);

        maxScroll = Math.max(0, status.bottom() + 6 - leftPanel.bottom());
        this.scroll = Math.max(0, Math.min(scroll, maxScroll));
        int offset = this.scroll;
        rows.replaceAll(bounds -> bounds.offset(offset));
        modeButtons.replaceAll(bounds -> bounds.offset(offset));
        controls.replaceAll(bounds -> bounds.offset(offset));
        visibilityButtons.replaceAll(bounds -> bounds.offset(offset));
        copyFromGlobal = copyFromGlobal.offset(offset);
        clearOverride = clearOverride.offset(offset);
        back = back.offset(offset);
        status = status.offset(offset);
    }

    public static Live2DOverrideLayout of(int width, int height, int scroll) {
        return new Live2DOverrideLayout(width, height, scroll);
    }

    public Live2DConfigLayout.Bounds header() { return header; }
    public Live2DConfigLayout.Bounds leftPanel() { return leftPanel; }
    public Live2DConfigLayout.Bounds preview() { return preview; }
    public List<Live2DConfigLayout.Bounds> rows() { return rows; }
    public List<Live2DConfigLayout.Bounds> modeButtons() { return modeButtons; }
    public List<Live2DConfigLayout.Bounds> controls() { return controls; }
    public List<Live2DConfigLayout.Bounds> visibilityButtons() { return visibilityButtons; }
    public Live2DConfigLayout.Bounds copyFromGlobal() { return copyFromGlobal; }
    public Live2DConfigLayout.Bounds clearOverride() { return clearOverride; }
    public Live2DConfigLayout.Bounds back() { return back; }
    public Live2DConfigLayout.Bounds status() { return status; }
    public int scroll() { return scroll; }
    public int maxScroll() { return maxScroll; }
}
