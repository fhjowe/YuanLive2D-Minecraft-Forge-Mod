package com.yuan.live2d.client.live2d;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;

final class Live2DModelIds {
    private Live2DModelIds() {}

    static String id(Path imported, Path modelJson) {
        String relative = imported.relativize(modelJson).toString().replace('\\', '/');
        String key = imported.getFileName() + "\0" + relative;
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
