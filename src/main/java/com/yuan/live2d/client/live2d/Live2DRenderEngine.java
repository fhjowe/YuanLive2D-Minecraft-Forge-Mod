package com.yuan.live2d.client.live2d;

import java.nio.file.Path;

public final class Live2DRenderEngine {
    public interface NativeAccess {
        long version();
        long create(String root, String modelJson, String shaders, long textureMemoryBudgetBytes);
        long[] render(long handle, float deltaSeconds, int width, int height,
                      float x, float y, float scale, float opacity, boolean update);
        float[] bounds(long handle, int width, int height, float x, float y, float scale);
        boolean destroy(long handle);
        String lastError();
        default long estimate(String root, String modelJson, String shaders) { return 0; }
    }

    private final Path shaderRoot;
    private final NativeAccess nativeAccess;

    public Live2DRenderEngine(Path shaderRoot) {
        this(shaderRoot, new NativeAccess() {
            @Override public long version() { return Live2DNative.version(); }
            @Override public long create(String root, String modelJson, String shaders, long budget) {
                return Live2DNative.create(root, modelJson, shaders, budget);
            }
            @Override public long[] render(long handle, float delta, int width, int height,
                                           float x, float y, float scale, float opacity, boolean update) {
                return Live2DNative.render(handle, delta, width, height, x, y, scale, opacity, update);
            }
            @Override public float[] bounds(long handle, int width, int height, float x, float y, float scale) {
                return Live2DNative.bounds(handle, width, height, x, y, scale);
            }
            @Override public boolean destroy(long handle) { return Live2DNative.destroy(handle); }
            @Override public long estimate(String root, String modelJson, String shaders) {
                return Live2DNative.estimate(root, modelJson, shaders);
            }
            @Override public String lastError() { return Live2DNative.lastError(); }
        });
    }

    public Live2DRenderEngine(Path shaderRoot, NativeAccess nativeAccess) {
        this.shaderRoot = shaderRoot;
        this.nativeAccess = nativeAccess;
    }

    public long create(Live2DModelManifest manifest, long textureMemoryBudgetBytes) {
        return nativeAccess.create(manifest.root().toString(), manifest.modelJson().toString(),
                shaderRoot.toString(), textureMemoryBudgetBytes);
    }

    public long create(String root, String modelJson, String shaderRoot, long textureMemoryBudgetBytes) {
        return nativeAccess.create(root, modelJson, shaderRoot, textureMemoryBudgetBytes);
    }

    public long estimate(Live2DModelManifest manifest) {
        return nativeAccess.estimate(manifest.root().toString(), manifest.modelJson().toString(), shaderRoot.toString());
    }

    public long estimate(String root, String modelJson, String shaderRoot) {
        return nativeAccess.estimate(root, modelJson, shaderRoot);
    }

    public Live2DNative.StructuredFrame render(long handle, float delta, int width, int height,
                                               float x, float y, float scale, float opacity, boolean update) {
        if (handle == 0) return new Live2DNative.StructuredFrame(Live2DNative.STATUS_SKIPPED, 0, 0, 0, "");
        long[] raw = nativeAccess.render(handle, delta, width, height, x, y, scale, opacity, update);
        if (raw == null || raw.length < 5) {
            return new Live2DNative.StructuredFrame(Live2DNative.STATUS_FAILED_BEFORE_UPDATE, 0, 0, 0,
                    nativeAccess.lastError());
        }
        int status = (int) raw[0];
        String error = status < 0 || status == Live2DNative.STATUS_FAILED_UPDATED ? nativeAccess.lastError() : "";
        return new Live2DNative.StructuredFrame(status, raw[1], (int) raw[2], (int) raw[3], error);
    }

    public float[] bounds(long handle, int width, int height, float x, float y, float scale) {
        return handle == 0 ? null : nativeAccess.bounds(handle, width, height, x, y, scale);
    }

    public boolean destroy(long handle) {
        return handle == 0 || nativeAccess.destroy(handle);
    }

    public String lastError() { return nativeAccess.lastError(); }
}
