package com.yuan.live2d.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.yuan.live2d.client.gui.Live2DConfigScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = "yuan_live2d", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class Live2DKeyBindings {
    private static final KeyMapping OPEN = new KeyMapping(
            "key.yuan_live2d.open", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_L, "key.categories.yuan_live2d");

    private Live2DKeyBindings() {
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN);
    }

    @Mod.EventBusSubscriber(modid = "yuan_live2d", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static class KeyHandler {
        @SubscribeEvent
        public static void onKey(InputEvent.Key event) {
            int action = event.getAction();
            int key = event.getKey();
            int scanCode = event.getScanCode();
            int modifiers = event.getModifiers();
            if (action != GLFW.GLFW_PRESS || modifiers != 0 || !OPEN.matches(key, scanCode)) return;
            while (OPEN.consumeClick()) {
                Minecraft.getInstance().setScreen(new Live2DConfigScreen());
            }
        }
    }
}
