package com.yuan.live2d.client.live2d;

import java.util.function.Consumer;
import java.util.function.Supplier;

final class Live2DLifecycle<T> {
    record Admission<T>(T state) {}
    record Close<T>(T state, boolean cleanupNow) {}

    private boolean worldOpen = true;
    private boolean previewOpen;
    private boolean shutdown;
    private boolean inFrame;
    private T state;

    synchronized T existing() {
        return state;
    }

    synchronized Admission<T> beginFrame(Supplier<T> factory) {
        return begin(factory, worldOpen);
    }

    synchronized Admission<T> beginPreviewFrame(Supplier<T> factory) {
        return begin(factory, previewOpen);
    }

    private Admission<T> begin(Supplier<T> factory, boolean open) {
        if (!open || shutdown || inFrame) return null;
        if (state == null) state = factory.get();
        inFrame = true;
        return new Admission<>(state);
    }

    synchronized T endFrame(Admission<T> admission) {
        if (!inFrame || state != admission.state()) throw new IllegalStateException("Invalid Live2D frame admission");
        inFrame = false;
        return (worldOpen || previewOpen) && !shutdown ? null : state;
    }

    synchronized Close<T> close(boolean terminal, Consumer<T> markPending) {
        if (terminal) shutdown = true;
        worldOpen = false;
        previewOpen = false;
        if (state != null) markPending.accept(state);
        return new Close<>(state, state != null && !inFrame);
    }

    synchronized void cleaned(T cleaned) {
        if (!inFrame && state == cleaned) state = null;
    }

    synchronized void login() {
        if (!shutdown) worldOpen = true;
    }

    synchronized Close<T> logout(Consumer<T> markPending) {
        worldOpen = false;
        return closeIfUnused(markPending);
    }

    synchronized boolean openPreview() {
        if (shutdown) return false;
        previewOpen = true;
        return true;
    }

    synchronized Close<T> closePreview(Consumer<T> markPending) {
        previewOpen = false;
        return closeIfUnused(markPending);
    }

    private Close<T> closeIfUnused(Consumer<T> markPending) {
        if (!worldOpen && !previewOpen && state != null) markPending.accept(state);
        return new Close<>(state, state != null && !worldOpen && !previewOpen && !inFrame);
    }

    synchronized boolean isOpen() {
        return worldOpen && !shutdown;
    }
}
