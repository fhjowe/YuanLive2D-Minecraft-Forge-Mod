package com.yuan.live2d.client.live2d;

import java.nio.file.Path;
import java.util.List;

public final class Live2DModelRegistry {
    private final Live2DModelRepository repository;
    private List<Live2DModelManifest> cache = List.of();
    private String selectedId = "";
    private String lastError = "";
    private boolean dirty = true;

    public Live2DModelRegistry(Path modelsRoot) {
        this(new Live2DModelRepository(modelsRoot));
    }

    public Live2DModelRegistry(Live2DModelRepository repository) {
        this.repository = repository;
    }

    public List<Live2DModelManifest> list() {
        if (dirty) refresh();
        return cache;
    }

    public Live2DModelManifest selected() {
        return list().stream().filter(m -> m.id().equals(selectedId)).findFirst().orElse(null);
    }

    public boolean selectById(String id) {
        selectedId = id == null ? "" : id;
        return list().stream().anyMatch(m -> m.id().equals(selectedId));
    }

    public void invalidate() {
        dirty = true;
        repository.invalidate();
    }

    public String lastError() { return lastError; }

    private void refresh() {
        try {
            cache = repository.scan();
            lastError = "";
            dirty = false;
        } catch (Exception failure) {
            cache = List.of();
            lastError = String.valueOf(failure);
            repository.invalidate();
        }
    }
}
