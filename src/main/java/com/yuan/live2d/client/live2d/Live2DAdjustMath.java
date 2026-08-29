package com.yuan.live2d.client.live2d;

public final class Live2DAdjustMath {
    public static final float SCALE_MIN = .05f;
    public static final float SCALE_MAX = 2f;
    public static final int SNAP_THRESHOLD = 8;
    public static final int HANDLE_SIZE = 10;
    public static final int WINDOW_MARGIN = 4;
    public static final int BAR_HEIGHT = 30;
    public static final float BORDER = 2f;

    public enum Handle {
        NONE, BODY, MOVE, LEFT, RIGHT, TOP, BOTTOM, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
    }

    public record Bounds(float left, float top, float right, float bottom) {
        public float width() { return right - left; }
        public float height() { return bottom - top; }
        public float centerX() { return (left + right) / 2; }
        public float centerY() { return (top + bottom) / 2; }
        public boolean contains(double x, double y) {
            return x >= left && x < right && y >= top && y < bottom;
        }
        public boolean intersect(Bounds other) {
            return right > other.left && other.right > left && bottom > other.top && other.bottom > top;
        }
        public boolean hasArea() { return right > left && bottom > top; }
    }

    public record SnapResult(float dx, float dy, boolean x, boolean y,
                             float guideX, float guideY) {}

    private Live2DAdjustMath() {}

    public static float clampScale(float scale) {
        return Float.isFinite(scale) ? Math.max(SCALE_MIN, Math.min(SCALE_MAX, scale)) : SCALE_MIN;
    }

    public static Bounds windowBounds(float modelLeft, float modelTop, float modelRight, float modelBottom) {
        return new Bounds(modelLeft - WINDOW_MARGIN, modelTop - WINDOW_MARGIN,
                modelRight + WINDOW_MARGIN, modelBottom + WINDOW_MARGIN);
    }

    public static Bounds clampToArea(Bounds window, int areaWidth, int areaHeight) {
        float minLeft = Math.min(0f, areaWidth - window.width());
        float maxLeft = Math.max(0f, areaWidth - window.width());
        float minTop = Math.min(0f, areaHeight - window.height());
        float maxTop = Math.max(0f, areaHeight - window.height());
        float left = Math.max(minLeft, Math.min(maxLeft, window.left()));
        float top = Math.max(minTop, Math.min(maxTop, window.top()));
        return new Bounds(left, top, left + window.width(), top + window.height());
    }

    public static Handle hit(double x, double y, Bounds window) {
        Bounds probe = new Bounds(window.left() - HANDLE_SIZE / 2f, window.top() - HANDLE_SIZE / 2f,
                window.right() + HANDLE_SIZE / 2f, window.bottom() + HANDLE_SIZE / 2f);
        if (!probe.contains(x, y)) return Handle.NONE;
        boolean nearLeft = Math.abs(x - window.left()) <= HANDLE_SIZE / 2f;
        boolean nearRight = Math.abs(x - window.right()) <= HANDLE_SIZE / 2f;
        boolean nearTop = Math.abs(y - window.top()) <= HANDLE_SIZE / 2f;
        boolean nearBottom = Math.abs(y - window.bottom()) <= HANDLE_SIZE / 2f;
        if (nearLeft && nearTop) return Handle.TOP_LEFT;
        if (nearRight && nearTop) return Handle.TOP_RIGHT;
        if (nearLeft && nearBottom) return Handle.BOTTOM_LEFT;
        if (nearRight && nearBottom) return Handle.BOTTOM_RIGHT;
        boolean onBorder = Math.abs(x - window.left()) <= BORDER
                || Math.abs(x - window.right()) <= BORDER
                || Math.abs(y - window.top()) <= BORDER
                || Math.abs(y - window.bottom()) <= BORDER;
        if (onBorder && window.contains(x, y)) return Handle.MOVE;
        if (nearLeft) return Handle.LEFT;
        if (nearRight) return Handle.RIGHT;
        if (nearTop) return Handle.TOP;
        if (nearBottom) return Handle.BOTTOM;
        return window.contains(x, y) ? Handle.BODY : Handle.NONE;
    }

    public static float freeResize(float scale, float delta) {
        return clampScale(scale + delta * .002f);
    }

    public static float aspectResize(float scale, float startDiagonal, float currentDiagonal) {
        if (startDiagonal <= 0 || currentDiagonal <= 0) return clampScale(scale);
        return clampScale(scale * currentDiagonal / startDiagonal);
    }

    public static float nudgePosition(float value, float step) { return value + step; }

    public static SnapResult snap(Bounds window, int screenWidth, int screenHeight) {
        float dx = 0, dy = 0;
        boolean snapX = false, snapY = false;
        float guideX = Float.NaN, guideY = Float.NaN;
        float leftDelta = -window.left();
        if (Math.abs(leftDelta) <= SNAP_THRESHOLD) { dx = leftDelta; snapX = true; guideX = 0; }
        if (!snapX) {
            float rightDelta = screenWidth - window.right();
            if (Math.abs(rightDelta) <= SNAP_THRESHOLD) { dx = rightDelta; snapX = true; guideX = screenWidth; }
        }
        if (!snapX) {
            float centerDelta = screenWidth / 2f - window.centerX();
            if (Math.abs(centerDelta) <= SNAP_THRESHOLD) { dx = centerDelta; snapX = true; guideX = screenWidth / 2f; }
        }
        float topDelta = -window.top();
        if (Math.abs(topDelta) <= SNAP_THRESHOLD) { dy = topDelta; snapY = true; guideY = 0; }
        if (!snapY) {
            float bottomDelta = screenHeight - window.bottom();
            if (Math.abs(bottomDelta) <= SNAP_THRESHOLD) { dy = bottomDelta; snapY = true; guideY = screenHeight; }
        }
        if (!snapY) {
            float centerDelta = screenHeight / 2f - window.centerY();
            if (Math.abs(centerDelta) <= SNAP_THRESHOLD) { dy = centerDelta; snapY = true; guideY = screenHeight / 2f; }
        }
        return new SnapResult(dx, dy, snapX, snapY, guideX, guideY);
    }
}
