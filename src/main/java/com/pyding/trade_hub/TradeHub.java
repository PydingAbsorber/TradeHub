package com.pyding.trade_hub;

import net.minecraft.world.item.*;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraftforge.registries.*;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.ModLoadingContext;

@Mod(TradeHub.ID)
public final class TradeHub {
    public static final String ID = "trade_hub";
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, ID);
    public static final RegistryObject<SoundEvent> BUTTON_SOUND = SOUNDS.register("trade_hub_button", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(ID, "trade_hub_button")));
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ID);
    public static final RegistryObject<Item> CHARTER = ITEMS.register("trade_charter", () -> new CharterItem(new Item.Properties().stacksTo(1)));
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB, ID);
    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("trade_hub", () -> CreativeModeTab.builder().title(Component.translatable("itemGroup.trade_hub")).icon(() -> new ItemStack(CHARTER.get())).displayItems((params, output) -> output.accept(CHARTER.get())).build());
    public TradeHub() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(bus);
        SOUNDS.register(bus);
        TABS.register(bus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, TradeConfig.SPEC);
        TradeNetwork.init();
        MinecraftForge.EVENT_BUS.addListener(TradeUtils::firstJoin);
        MinecraftForge.EVENT_BUS.addListener(TradeUtils::serverTick);
        MinecraftForge.EVENT_BUS.addListener(TradeUtils::clonePlayer);
        MinecraftForge.EVENT_BUS.addListener(TradeCommands::register);
        net.minecraftforge.fml.DistExecutor.safeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> com.pyding.trade_hub.client.ClientBridge::init);
    }
}
