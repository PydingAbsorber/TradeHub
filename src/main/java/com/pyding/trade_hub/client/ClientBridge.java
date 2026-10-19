package com.pyding.trade_hub.client;

import java.util.*;
import com.pyding.trade_hub.*;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid=TradeHub.ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class ClientBridge {
    private static final KeyMapping KEY=new KeyMapping("key.trade_hub.open",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_H,"key.categories.trade_hub");
    private static CompoundTag last=new CompoundTag();
    @SubscribeEvent public static void register(RegisterKeyMappingsEvent e){e.register(KEY);}
    public static void init(){MinecraftForge.EVENT_BUS.addListener(ClientBridge::key);}
    private static void key(InputEvent.Key e){if(KEY.consumeClick())TradeNetwork.send(TradeNetwork.request("open",new UUID(0,0),0,0,0,"",new CompoundTag(),false,0));}
    public static void snapshot(CompoundTag data){
        Minecraft mc=Minecraft.getInstance();mc.execute(()->{
            last=data;
            if(mc.screen instanceof TradeScreen screen) {
                screen.applySnapshot(data);
            } else if(data.getBoolean("open")) {
                mc.setScreen(new TradeScreen(data));
            }
        });
    }
    public static void notice(String key) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.screen instanceof TradeScreen screen) {
                screen.showNotice(key);
            }
        });
    }
}
