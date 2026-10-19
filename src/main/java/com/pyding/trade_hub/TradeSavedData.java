package com.pyding.trade_hub;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.item.ItemStack;

public final class TradeSavedData extends SavedData {
    public final Map<UUID,TradeRecord> listings=new LinkedHashMap<>();
    public final Map<UUID,List<ItemStack>> mailbox=new HashMap<>();
    public final Set<UUID> charterRecipients=new HashSet<>();
    public static TradeSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TradeSavedData::load,TradeSavedData::new,"trade_hub_world");
    }
    public static TradeSavedData load(CompoundTag t) {
        TradeSavedData d=new TradeSavedData();
        for(Tag x:t.getList("listings",Tag.TAG_COMPOUND)) {
            try { TradeRecord r=TradeRecord.load((CompoundTag)x); if(!r.offered.isEmpty()&&!r.wanted.isEmpty()&&r.offeredCount>0&&r.wantedCount>0)d.listings.put(r.id,r); } catch(RuntimeException ignored) {}
        }
        for(Tag x:t.getList("mailbox",Tag.TAG_COMPOUND)) {
            CompoundTag e=(CompoundTag)x; if(!e.hasUUID("player"))continue;
            UUID id=e.getUUID("player"); List<ItemStack> items=new ArrayList<>();
            for(Tag y:e.getList("items",Tag.TAG_COMPOUND)) { ItemStack s=ItemStack.of((CompoundTag)y); if(!s.isEmpty())items.add(s); }
            d.mailbox.put(id,items);
        }
        for(Tag x:t.getList("charters",Tag.TAG_INT_ARRAY)) { try {d.charterRecipients.add(NbtUtils.loadUUID(x));}catch(RuntimeException ignored){} }
        return d;
    }
    @Override public CompoundTag save(CompoundTag t) {
        ListTag ls=new ListTag(); for(TradeRecord r:listings.values())ls.add(r.save()); t.put("listings",ls);
        ListTag mail=new ListTag(); for(var entry:mailbox.entrySet()) {
            CompoundTag e=new CompoundTag(); e.putUUID("player",entry.getKey()); ListTag items=new ListTag();
            for(ItemStack s:entry.getValue())items.add(s.save(new CompoundTag())); e.put("items",items); mail.add(e);
        } t.put("mailbox",mail);
        ListTag charters=new ListTag(); for(UUID id:charterRecipients)charters.add(NbtUtils.createUUID(id)); t.put("charters",charters);
        return t;
    }
}
