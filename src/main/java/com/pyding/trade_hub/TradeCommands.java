package com.pyding.trade_hub;

import com.mojang.brigadier.CommandDispatcher;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.RegisterCommandsEvent;

public final class TradeCommands {
    private static final UUID BOT_ID = UUID.nameUUIDFromBytes("trade_hub:testing:Bot".getBytes(StandardCharsets.UTF_8));
    private static final Item[] OFFERS = {
        Items.DIAMOND, Items.GOLD_INGOT, Items.EMERALD, Items.IRON_INGOT,
        Items.REDSTONE, Items.LAPIS_LAZULI, Items.ENDER_PEARL, Items.OBSIDIAN,
        Items.BLAZE_ROD, Items.COPPER_INGOT, Items.NETHERITE_SCRAP, Items.AMETHYST_SHARD
    };
    private static final Item[] PRICES = {
        Items.IRON_INGOT, Items.DIAMOND, Items.EMERALD, Items.GOLD_INGOT,
        Items.COAL, Items.COPPER_INGOT, Items.LAPIS_LAZULI, Items.REDSTONE,
        Items.QUARTZ, Items.AMETHYST_SHARD
    };

    private TradeCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("trade_hub")
            .then(Commands.literal("createTestingTrades")
                .requires(source -> source.hasPermission(2))
                .executes(context -> createTestingTrades(context.getSource()))));
    }

    private static int createTestingTrades(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        TradeSavedData saved = TradeSavedData.get(server);
        RandomSource random = server.overworld().getRandom();
        int made = 0;
        for (int i = 0; i < 10; i++) {
            Item offeredItem = OFFERS[random.nextInt(OFFERS.length)];
            Item wantedItem = PRICES[random.nextInt(PRICES.length)];
            if (offeredItem == wantedItem) {
                wantedItem = PRICES[(i + 1) % PRICES.length];
            }
            int offerAmount = 1 + random.nextInt(64);
            int wantedAmount = 1 + random.nextInt(64);
            ItemStack offered = new ItemStack(offeredItem);
            ItemStack wanted = new ItemStack(wantedItem);
            TradeRecord trade = new TradeRecord(UUID.randomUUID(), BOT_ID, "Bot", offered, offerAmount, wanted, wantedAmount, false, 0);
            saved.listings.put(trade.id, trade);
            made++;
        }
        saved.setDirty();
        TradeUtils.broadcast(server);
        int count = made;
        source.sendSuccess(() -> Component.translatable("message.trade_hub.testing_created", count), true);
        return made;
    }
}
