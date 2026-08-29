package com.yuan.live2d.client.live2d;

public final class Live2DNative {
    public static final int STATUS_SKIPPED = 0;
    public static final int STATUS_DRAWN_NO_UPDATE = 1;
    public static final int STATUS_DRAWN_UPDATED = 2;
    public static final int STATUS_FAILED_UPDATED = 3;
    public static final int STATUS_FAILED_BEFORE_UPDATE = -1;

    public record StructuredFrame(int status, long textureId, int textureWidth, int textureHeight, String error) {
        public boolean hasTexture() {
            return (status == STATUS_DRAWN_NO_UPDATE || status == STATUS_DRAWN_UPDATED)
                    && textureId > 0 && textureWidth > 0 && textureHeight > 0;
        }
    }

    private Live2DNative() {}

    public static native long version();
    public static native long estimate(String root, String modelJson, String shaders);
    public static native long create(String root, String modelJson, String shaders, long textureMemoryBudgetBytes);
    public static native long[] render(long handle, float deltaSeconds, int width, int height,
                                       float x, float y, float scale, float opacity, boolean update);
    public static native float[] bounds(long handle, int width, int height, float x, float y, float scale);
    public static native void control(long handle, float gazeX, float gazeY, float physicsAmplitude, int flags);
    public static native boolean destroy(long handle);
    public static native String lastError();
}
