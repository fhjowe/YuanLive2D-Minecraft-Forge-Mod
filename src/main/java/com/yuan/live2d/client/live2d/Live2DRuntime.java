package com.yuan.live2d.client.live2d;

import java.nio.file.Path;
import java.util.function.LongSupplier;

public final class Live2DRuntime implements AutoCloseable {
    private static final int MAX_ERROR_LENGTH = 1024;

    public enum Status { UNCHECKED, READY, UNAVAILABLE, FAILED }

    @FunctionalInterface
    interface LibraryLoader {
        void load(Path path);
    }

    private final Path coreDll;
    private final Path bridgeDll;
    private final LibraryLoader loader;
    private final LongSupplier version;
    private Status status = Status.UNCHECKED;
    private String error = "";
    private long nativeVersion;

    public Live2DRuntime() {
        this(Live2DPaths.coreDll(), Live2DPaths.bridgeDll(),
                path -> System.load(path.toString()), Live2DNative::version);
    }

    Live2DRuntime(Path coreDll, Path bridgeDll, LibraryLoader loader, LongSupplier version) {
        this.coreDll = coreDll;
        this.bridgeDll = bridgeDll;
        this.loader = loader;
        this.version = version;
    }

    static Live2DRuntime forTest(Path runtime, LibraryLoader loader, LongSupplier version) {
        return new Live2DRuntime(runtime.resolve("Live2DCubismCore.dll"),
                runtime.resolve("yuan_live2d.dll"), loader, version);
    }

    public synchronized boolean initialize() {
        if (status != Status.UNCHECKED) return status == Status.READY;
        try {
            loader.load(coreDll.toAbsolutePath());
            loader.load(bridgeDll.toAbsolutePath());
            nativeVersion = version.getAsLong();
            status = Status.READY;
            return true;
        } catch (Throwable failure) {
            rethrowFatal(failure);
            status = failure instanceof SecurityException || failure instanceof LinkageError
                    ? Status.UNAVAILABLE : Status.FAILED;
            error = formatError(failure);
        }
        return false;
    }

    public synchronized boolean available() {
        return status == Status.READY;
    }

    public synchronized Status status() {
        return status;
    }

    public synchronized String error() {
        return error;
    }

    @Override
    public synchronized void close() {
        status = Status.UNAVAILABLE;
    }

    private static String formatError(Throwable failure) {
        try {
            String message = String.valueOf(failure);
            if (message == null) message = failure == null ? "Unknown failure" : failure.getClass().getName();
            return message.substring(0, Math.min(message.length(), MAX_ERROR_LENGTH));
        } catch (Throwable formattingFailure) {
            rethrowFatal(formattingFailure);
            return "Failed to format native runtime error";
        }
    }

    private static void rethrowFatal(Throwable failure) {
        if (failure instanceof ThreadDeath threadDeath) throw threadDeath;
        if (failure instanceof VirtualMachineError virtualMachineError) throw virtualMachineError;
    }
}
