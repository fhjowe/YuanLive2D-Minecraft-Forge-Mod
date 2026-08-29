package com.yuan.live2d.client.live2d;

import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;

public final class Live2DPaths {
    private Live2DPaths() {}

    public static Path root() {
        Path config = FMLPaths.CONFIGDIR.get();
        return (config == null ? Path.of("config") : config).resolve("yuan_live2d");
    }

    public static Path config() { return root().resolve("config.json"); }
    public static Path models() { return root().resolve("models"); }
    public static Path runtime() { return root().resolve("runtime/windows-x86_64"); }
    public static Path bridgeDll() { return runtime().resolve("yuan_live2d.dll"); }
    public static Path coreDll() { return runtime().resolve("Live2DCubismCore.dll"); }
    public static Path frameworkShaders() { return runtime().resolve("FrameworkShaders"); }
}
