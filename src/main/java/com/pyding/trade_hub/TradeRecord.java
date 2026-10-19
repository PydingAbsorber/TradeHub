package com.pyding.trade_hub;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

public final class TradeRecord {
    public UUID id;
    public UUID owner;
    public String ownerName;
    public ItemStack offered;
    public int offeredCount;
    public ItemStack wanted;
    public int wantedCount;
    public boolean auction;
    public boolean enabled = true;
    public long endsAt;
    public UUID leader;
    public int highestBid;
    public TradeRecord(UUID id, UUID owner, String ownerName, ItemStack offered, int offeredCount, ItemStack wanted, int wantedCount, boolean auction, long endsAt) {
        this.id=id; this.owner=owner; this.ownerName=ownerName; this.offered=offered.copyWithCount(1); this.offeredCount=offeredCount; this.wanted=wanted.copyWithCount(1); this.wantedCount=wantedCount; this.auction=auction; this.endsAt=endsAt;
    }
    public CompoundTag save() {
        CompoundTag t=new CompoundTag(); t.putUUID("id",id); t.putUUID("owner",owner); t.putString("ownerName",ownerName);
        t.put("offered",offered.save(new CompoundTag())); t.putInt("offeredCount",offeredCount);
        t.put("wanted",wanted.save(new CompoundTag())); t.putInt("wantedCount",wantedCount);
        t.putBoolean("auction",auction); t.putBoolean("enabled",enabled); t.putLong("endsAt",endsAt);
        if(leader!=null)t.putUUID("leader",leader); t.putInt("highestBid",highestBid); return t;
    }
    public static TradeRecord load(CompoundTag t) {
        TradeRecord r=new TradeRecord(t.getUUID("id"),t.getUUID("owner"),t.getString("ownerName"),ItemStack.of(t.getCompound("offered")),t.getInt("offeredCount"),ItemStack.of(t.getCompound("wanted")),t.getInt("wantedCount"),t.getBoolean("auction"),t.getLong("endsAt"));
        r.enabled=t.getBoolean("enabled"); if(t.hasUUID("leader"))r.leader=t.getUUID("leader"); r.highestBid=t.getInt("highestBid"); return r;
    }
}
