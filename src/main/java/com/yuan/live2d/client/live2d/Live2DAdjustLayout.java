package com.yuan.live2d.client.live2d;

public final class Live2DAdjustLayout {
    public record Bar(Live2DAdjustMath.Bounds bar, Live2DAdjustMath.Bounds target,
                      Live2DAdjustMath.Bounds back, Live2DAdjustMath.Bounds apply,
                      Live2DAdjustMath.Bounds save) {}

    private Live2DAdjustLayout() {}

    public static Bar bar(int screenWidth, int screenHeight) {
        int top = screenHeight - Live2DAdjustMath.BAR_HEIGHT;
        Live2DAdjustMath.Bounds bar = new Live2DAdjustMath.Bounds(0, top, screenWidth, screenHeight);
        int margin = 8;
        int gap = 6;
        int targetWidth = 150;
        Live2DAdjustMath.Bounds target = new Live2DAdjustMath.Bounds(margin, top + 6,
                margin + targetWidth, screenHeight - 6);
        int btnWidth = 64;
        int btnHeight = 18;
        int btnTop = top + 6;
        Live2DAdjustMath.Bounds back = new Live2DAdjustMath.Bounds(bar.right() - margin - btnWidth,
                btnTop, bar.right() - margin, btnTop + btnHeight);
        Live2DAdjustMath.Bounds apply = new Live2DAdjustMath.Bounds(back.left() - gap - btnWidth,
                btnTop, back.left() - gap, btnTop + btnHeight);
        Live2DAdjustMath.Bounds save = new Live2DAdjustMath.Bounds(apply.left() - gap - btnWidth,
                btnTop, apply.left() - gap, btnTop + btnHeight);
        return new Bar(bar, target, back, apply, save);
    }

    public static Live2DAdjustMath.Bounds confirmDialog(int screenWidth, int screenHeight) {
        int width = 320;
        int height = 120;
        int left = (screenWidth - width) / 2;
        int top = (screenHeight - height) / 2;
        return new Live2DAdjustMath.Bounds(left, top, left + width, top + height);
    }
}
