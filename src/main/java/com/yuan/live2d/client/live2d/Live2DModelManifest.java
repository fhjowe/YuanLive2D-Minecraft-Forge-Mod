package com.yuan.live2d.client.live2d;

import java.nio.file.Path;
import java.util.List;

public record Live2DModelManifest(
        String id,
        String displayName,
        int version,
        Path root,
        Path modelJson,
        Path moc,
        List<Path> textures,
        Path physics,
        List<Path> expressions,
        List<Path> motions) {
    public Live2DModelManifest {
        textures = List.copyOf(textures);
        expressions = List.copyOf(expressions);
        motions = List.copyOf(motions);
    }
}
