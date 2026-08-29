package com.yuan.live2d.client;

import com.mojang.brigadier.CommandDispatcher;
import com.yuan.live2d.client.gui.Live2DConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "yuan_live2d", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class Live2DCommand {
    private Live2DCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("live2d").executes(ctx -> {
            Minecraft.getInstance().setScreen(new Live2DConfigScreen());
            return 1;
        }));
    }
}
