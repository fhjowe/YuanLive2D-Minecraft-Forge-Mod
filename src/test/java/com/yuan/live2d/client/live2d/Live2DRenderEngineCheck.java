package com.yuan.live2d.client.live2d;

import java.nio.file.Path;
import java.util.List;

public final class Live2DRenderEngineCheck {
    private Live2DRenderEngineCheck() {}

    public static void main(String[] args) {
        check();
    }

    public static void check() {
        FakeNative nativeAccess = new FakeNative();
        Live2DRenderEngine engine = new Live2DRenderEngine(Path.of("shaders"), nativeAccess);
        Live2DModelManifest manifest = new Live2DModelManifest("m", "m", 3,
                Path.of("root"), Path.of("root/m.model3.json"), Path.of("root/m.moc3"),
                List.of(Path.of("root/m.png")), null, List.of(), List.of());

        long handle = engine.create(manifest, 96L << 20);
        assert handle == 7;
        assert nativeAccess.createRoot.equals(Path.of("root").toString());
        assert nativeAccess.createModelJson.equals(Path.of("root/m.model3.json").toString());
        assert nativeAccess.createShaders.equals(Path.of("shaders").toString());
        assert nativeAccess.createBudget == 96L << 20;

        Live2DNative.StructuredFrame frame = engine.render(handle, .05f, 1920, 1080, 100, 200, .5f, .8f, true);
        assert frame.status() == Live2DNative.STATUS_DRAWN_UPDATED;
        assert frame.textureId() == 41 && frame.textureWidth() == 1920 && frame.textureHeight() == 1080;
        assert frame.hasTexture();

        float[] bounds = engine.bounds(handle, 1920, 1080, 100, 200, .5f);
        assert bounds != null && bounds.length == 4 && bounds[0] == 1;
        assert engine.destroy(handle) && nativeAccess.destroyed == 7;

        nativeAccess.renderResult = null;
        nativeAccess.error = "boom";
        Live2DNative.StructuredFrame failed = engine.render(handle, 0, 1, 1, 0, 0, 1, 1, false);
        assert failed.status() == Live2DNative.STATUS_FAILED_BEFORE_UPDATE;
        assert failed.error().equals("boom");
        assert !failed.hasTexture();
    }

    private static final class FakeNative implements Live2DRenderEngine.NativeAccess {
        private String createRoot;
        private String createModelJson;
        private String createShaders;
        private long createBudget;
        private long destroyed;
        private String error = "";
        private long[] renderResult = {2, 41, 1920, 1080, 0};

        @Override public long version() { return 6; }
        @Override public long create(String root, String modelJson, String shaders, long textureMemoryBudgetBytes) {
            createRoot = root;
            createModelJson = modelJson;
            createShaders = shaders;
            createBudget = textureMemoryBudgetBytes;
            return 7;
        }
        @Override public long[] render(long handle, float deltaSeconds, int width, int height,
                                       float x, float y, float scale, float opacity, boolean update) {
            return renderResult;
        }
        @Override public float[] bounds(long handle, int width, int height, float x, float y, float scale) {
            return new float[] {1, 2, 3, 4};
        }
        @Override public boolean destroy(long handle) { destroyed = handle; return true; }
        @Override public String lastError() { return error; }
    }
}
