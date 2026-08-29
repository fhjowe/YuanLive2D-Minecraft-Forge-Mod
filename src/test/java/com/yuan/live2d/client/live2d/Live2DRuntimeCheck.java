package com.yuan.live2d.client.live2d;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class Live2DRuntimeCheck {
    private Live2DRuntimeCheck() {}

    public static void check() throws Exception {
        Path temp = Files.createTempDirectory("yuan-live2d-runtime-");
        List<Path> loaded = new ArrayList<>();
        Live2DRuntime runtime = Live2DRuntime.forTest(temp, loaded::add, () -> 0x06000001L);
        assert runtime.status() == Live2DRuntime.Status.UNCHECKED;
        assert runtime.initialize();
        assert loaded.equals(List.of(
                temp.resolve("Live2DCubismCore.dll").toAbsolutePath(),
                temp.resolve("yuan_live2d.dll").toAbsolutePath()));
        assert runtime.status() == Live2DRuntime.Status.READY;
        assert runtime.available();
        assert runtime.error().isEmpty();
        assert runtime.initialize() && loaded.size() == 2;
        runtime.close();
        runtime.close();
        assert runtime.status() == Live2DRuntime.Status.UNAVAILABLE;
        assert !runtime.available();

        Live2DRuntime closed = Live2DRuntime.forTest(temp, path -> {
            throw new AssertionError("closed runtime loaded a DLL");
        }, () -> 0);
        closed.close();
        closed.close();
        assert !closed.initialize();
        assert closed.status() == Live2DRuntime.Status.UNAVAILABLE;

        assertUnavailable(temp, new UnsatisfiedLinkError("missing"));
        assertUnavailable(temp, new SecurityException("denied"));
        assertUnavailable(temp, new NoClassDefFoundError("linkage"));
        int[] bridgeLoads = {0};
        Live2DRuntime bridgeFailure = Live2DRuntime.forTest(temp, path -> {
            if (++bridgeLoads[0] == 2) throw new UnsatisfiedLinkError("bridge");
        }, () -> 0);
        assert !bridgeFailure.initialize();
        assert bridgeFailure.status() == Live2DRuntime.Status.UNAVAILABLE;
        assert bridgeLoads[0] == 2;
        assertFailed(temp, new IllegalStateException("x".repeat(2048)), 1024);
        assertFailed(temp, new Error("nonfatal"), -1);

        int[] versionCalls = {0};
        Live2DRuntime versionFailure = Live2DRuntime.forTest(temp, path -> {}, () -> {
            versionCalls[0]++;
            throw new AssertionError("bad version");
        });
        assert !versionFailure.initialize();
        assert versionFailure.status() == Live2DRuntime.Status.FAILED;
        assert !versionFailure.initialize();
        assert versionCalls[0] == 1;

        Live2DRuntime brokenMessage = Live2DRuntime.forTest(temp, path -> {
            throw new Error() {
                @Override public String toString() { throw new IllegalStateException("format failed"); }
            };
        }, () -> 0);
        assert !brokenMessage.initialize();
        assert brokenMessage.status() == Live2DRuntime.Status.FAILED;
        assert !brokenMessage.error().isEmpty();

        Live2DRuntime nullMessage = Live2DRuntime.forTest(temp, path -> {
            throw new Error() {
                @Override public String toString() { return null; }
            };
        }, () -> 0);
        assert !nullMessage.initialize();
        assert !nullMessage.error().isEmpty();
        assert nullMessage.error().length() <= 1024;
    }

    private static void assertUnavailable(Path temp, Throwable failure) {
        int[] attempts = {0};
        Live2DRuntime runtime = Live2DRuntime.forTest(temp, path -> {
            attempts[0]++;
            throwUnchecked(failure);
        }, () -> 0);
        assert !runtime.initialize();
        assert runtime.status() == Live2DRuntime.Status.UNAVAILABLE;
        assert !runtime.initialize();
        assert attempts[0] == 1;
    }

    private static void assertFailed(Path temp, Throwable failure, int errorLength) {
        Live2DRuntime runtime = Live2DRuntime.forTest(temp, path -> throwUnchecked(failure), () -> 0);
        assert !runtime.initialize();
        assert runtime.status() == Live2DRuntime.Status.FAILED;
        if (errorLength >= 0) assert runtime.error().length() == errorLength;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void throwUnchecked(Throwable failure) throws T {
        throw (T) failure;
    }
}
