package com.pyding.trade_hub;

import java.util.*;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

public final class TradeNetwork {
    private static final String VERSION="2";
    private static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation(TradeHub.ID,"main"),()->VERSION,VERSION::equals,VERSION::equals);
    private TradeNetwork() {}
    public static void init() {
        CHANNEL.registerMessage(0,Request.class,Request::encode,Request::decode,Request::handle);
        CHANNEL.registerMessage(1,Snapshot.class,Snapshot::encode,Snapshot::decode,Snapshot::handle);
        CHANNEL.registerMessage(2,Notice.class,Notice::encode,Notice::decode,Notice::handle);
    }
    public static void send(Request request) {CHANNEL.sendToServer(request);}
    public static void sendSnapshot(ServerPlayer player,boolean open) {
        TradeSavedData d=TradeSavedData.get(player.server);CompoundTag t=new CompoundTag();
        net.minecraft.nbt.ListTag records=new net.minecraft.nbt.ListTag();for(TradeRecord r:d.listings.values())records.add(r.save());t.put("records",records);
        t.putInt("limit",TradeUtils.limit(player));t.putInt("mine",TradeUtils.count(d,player.getUUID()));
        t.putInt("mail",d.mailbox.getOrDefault(player.getUUID(),List.of()).size());t.putUUID("self",player.getUUID());t.putBoolean("open",open);
        CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new Snapshot(t));
    }
    public record Request(String action,UUID id,int slot,int amount,int wantedCount,String wanted,CompoundTag tag,boolean auction,int minutes) {
        public static void encode(Request r,FriendlyByteBuf b) {b.writeUtf(r.action,24);b.writeUUID(r.id);b.writeInt(r.slot);b.writeInt(r.amount);b.writeInt(r.wantedCount);b.writeUtf(r.wanted,256);b.writeNbt(r.tag);b.writeBoolean(r.auction);b.writeInt(r.minutes);}
        public static Request decode(FriendlyByteBuf b) {return new Request(b.readUtf(24),b.readUUID(),b.readInt(),b.readInt(),b.readInt(),b.readUtf(256),b.readNbt(),b.readBoolean(),b.readInt());}
        public static void handle(Request r,Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context ctx=supplier.get();ctx.enqueueWork(()->{
                ServerPlayer p=ctx.getSender();if(p==null)return;
                switch(r.action){
                    case "open" -> TradeUtils.open(p);
                    case "create" -> TradeUtils.create(p,r.slot,r.amount,r.wanted,r.wantedCount,r.tag,r.auction,r.minutes);
                    case "buy" -> TradeUtils.buy(p,r.id);
                    case "bid" -> TradeUtils.bid(p,r.id,r.amount);
                    case "delete" -> TradeUtils.manage(p,r.id,true);
                    case "toggle" -> TradeUtils.manage(p,r.id,false);
                    case "claim" -> TradeUtils.claim(p);
                }
            });ctx.setPacketHandled(true);
        }
    }
    public static void sendNotice(ServerPlayer player, String key) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Notice(key));
    }
    public record Notice(String translationKey) {
        public static void encode(Notice n, FriendlyByteBuf buf) {
            buf.writeUtf(n.translationKey, 128);
        }
        public static Notice decode(FriendlyByteBuf buf) {
            return new Notice(buf.readUtf(128));
        }
        public static void handle(Notice notice, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> com.pyding.trade_hub.client.ClientBridge.notice(notice.translationKey));
            context.setPacketHandled(true);
        }
    }
    public record Snapshot(CompoundTag data) {
        public static void encode(Snapshot s,FriendlyByteBuf b){b.writeNbt(s.data);}
        public static Snapshot decode(FriendlyByteBuf b){return new Snapshot(b.readNbt());}
        public static void handle(Snapshot s,Supplier<NetworkEvent.Context> supplier){NetworkEvent.Context ctx=supplier.get();ctx.enqueueWork(()->com.pyding.trade_hub.client.ClientBridge.snapshot(s.data));ctx.setPacketHandled(true);}
    }
    public static Request request(String action,UUID id,int slot,int amount,int wantedCount,String wanted,CompoundTag tag,boolean auction,int minutes){return new Request(action,id,slot,amount,wantedCount,wanted,tag,auction,minutes);}
}
