package com.yuan.live2d.client.live2d;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class Live2DAdjustSessionCheck {
    private Live2DAdjustSessionCheck() {}

    public static void main(String[] args) throws Exception {
        check();
    }

    public static void check() throws Exception {
        Live2DConfig config = Live2DConfig.defaults();
        config.global.selectedModelId = "m";
        config.global.offsetX = 10f;
        config.global.scale = .5f;
        Live2DModelManifest model = model("m");
        List<Live2DConfig> saved = new ArrayList<>();
        int[] failures = {0};

        Live2DAdjustSession session = new Live2DAdjustSession(config, model, value -> {
            if (failures[0] > 0) throw new java.io.IOException("save failed");
            saved.add(value.copy());
        });
        assert session.target() == Live2DAdjustSession.Target.GLOBAL
                : "default target must be global without an override";
        assert session.offsetX() == 10f && session.scale() == .5f;
        assert !session.isDirty();

        session.setOffsetX(20f);
        assert session.isDirty() && session.offsetX() == 20f;
        session.setScale(99f);
        assert session.scale() == Live2DAdjustMath.SCALE_MAX : "draft scale must clamp";
        session.setScale(.3f);
        session.setTarget(Live2DAdjustSession.Target.OVERRIDE);
        assert session.target() == Live2DAdjustSession.Target.OVERRIDE;

        session.apply();
        assert !session.isDirty() && saved.size() == 1;
        assert saved.get(0).modelOverrides.get("m").offsetX == 20f
                && saved.get(0).modelOverrides.get("m").scale == .3f
                : "override apply must write offset and scale";
        assert saved.get(0).modelOverrides.containsKey("m") : "override entry must be created in the saved copy";

        session.setOffsetX(30f);
        assert !session.requestBack() && session.confirmPending()
                : "dirty return must request confirmation";
        session.cancelConfirm();
        assert !session.confirmPending() && session.isDirty();
        session.setOffsetX(20f);
        assert !session.isDirty() && session.requestBack()
                : "clean return must not open confirmation";
        session.confirmDiscard();

        session.setOffsetX(30f);
        assert !session.requestBack() && session.confirmPending();
        session.setOffsetX(20f);
        session.apply();
        assert !session.confirmPending() : "apply success must clear confirmation";

        session.setTarget(Live2DAdjustSession.Target.GLOBAL);
        session.setOffsetX(40f);
        session.apply();
        assert saved.get(2).global.offsetX == 40f : "global apply must write global offset";
        assert !saved.get(2).modelOverrides.containsKey("m")
                : "empty override must be removable after sanitize";

        session.setOffsetX(50f);
        session.resetToEntry();
        assert session.offsetX() == 10f : "double-click reset must restore the entry snapshot";
        boolean dirtyBeforeShift = session.isDirty();

        session.shiftEntry(5f, -3f);
        assert session.offsetX() == 15f && session.offsetY() == -27f && session.isDirty() == dirtyBeforeShift
                : "entry clamp must shift offsets without changing dirty state";
        session.resetToEntry();
        assert session.offsetX() == 15f && session.offsetY() == -27f
                : "reset must restore the shifted entry baseline";

        failures[0] = 1;
        session.setOffsetX(60f);
        session.apply();
        assert session.isDirty() && !session.error().isEmpty()
                : "save failure must keep the draft and report an error";

        Path temp = Files.createTempDirectory("yuan-adjust-session-");
        try {
            Live2DConfigStore store = new Live2DConfigStore(temp);
            store.save(config);
            Live2DAdjustSession stored = new Live2DAdjustSession(config.copy(), model, store::save);
            stored.setTarget(Live2DAdjustSession.Target.OVERRIDE);
            stored.setOffsetX(77f);
            assert stored.save() && stored.error().isEmpty()
                    : "save must write through the store";
            assert new Live2DConfigStore(temp).load().modelOverrides.get("m").offsetX == 77f
                    : "persisted config must contain the saved override";
        } finally {
            deleteTree(temp);
        }
    }

    private static Live2DModelManifest model(String id) {
        Path root = Path.of(id);
        return new Live2DModelManifest(id, id, 3, root, root.resolve(id + ".model3.json"),
                root.resolve(id + ".moc3"), List.of(), null, List.of(), List.of());
    }

    private static void deleteTree(Path root) throws Exception {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }
}
