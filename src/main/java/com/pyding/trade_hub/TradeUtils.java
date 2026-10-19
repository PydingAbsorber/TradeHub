package com.pyding.trade_hub;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

public final class TradeUtils {
    private TradeUtils() {}
    private static volatile Set<String> premium=Set.of();
    private static volatile long lastRefresh=0;
    public static void firstJoin(PlayerEvent.PlayerLoggedInEvent e) {
        if(!(e.getEntity() instanceof ServerPlayer p))return;
        TradeSavedData d=TradeSavedData.get(p.server);
        if(d.charterRecipients.add(p.getUUID())) {
            ItemStack s=new ItemStack(TradeHub.CHARTER.get());
            if(!p.getInventory().add(s))p.drop(s,false);
            d.setDirty();
        }
    }
    public static void clonePlayer(PlayerEvent.Clone e) {}
    public static void open(ServerPlayer p) { TradeNetwork.sendSnapshot(p,true); }
    public static void serverTick(TickEvent.ServerTickEvent e) {
        if(e.phase!=TickEvent.Phase.END)return;
        MinecraftServer server=e.getServer();
        if(server.getTickCount()%20!=0)return;
        TradeSavedData d=TradeSavedData.get(server);
        long now=System.currentTimeMillis(); boolean changed=false;
        for(TradeRecord r:new ArrayList<>(d.listings.values()))if(r.auction&&r.endsAt<=now) {
            if(r.leader==null)queue(d,r.owner,r.offered,r.offeredCount);
            else {queue(d,r.leader,r.offered,r.offeredCount);queue(d,r.owner,r.wanted,r.highestBid);}
            d.listings.remove(r.id);changed=true;
        }
        if(changed){d.setDirty();broadcast(server);}
        refreshPremium(now);
    }
    private static void refreshPremium(long now) {
        String url=TradeConfig.PREMIUM_URL.get();
        if(url.isBlank()||now-lastRefresh<TradeConfig.PREMIUM_REFRESH_MINUTES.get()*60000L)return;
        lastRefresh=now;
        try {
            URI uri=URI.create(url);
            if(!"https".equalsIgnoreCase(uri.getScheme())||!"raw.githubusercontent.com".equalsIgnoreCase(uri.getHost()))return;
            HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            client.sendAsync(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(8)).GET().build(),HttpResponse.BodyHandlers.ofString())
                .thenAccept(resp -> {if(resp.statusCode()==200&&resp.body().length()<100000) {
                    Set<String> names=new HashSet<>(); for(String line:resp.body().split("\\R")) {
                        String name=line.strip();if(name.matches("[A-Za-z0-9_]{3,16}"))names.add(name.toLowerCase(Locale.ROOT));
                    } premium=Set.copyOf(names);
                }}).exceptionally(ex -> null);
        }catch(RuntimeException ignored){}
    }
    public static int limit(ServerPlayer p) {int base=TradeConfig.MAX_LISTINGS.get();return premium.contains(p.getGameProfile().getName().toLowerCase(Locale.ROOT))?base*2:base;}
    public static int count(TradeSavedData d,UUID owner) {return (int)d.listings.values().stream().filter(r->r.owner.equals(owner)).count();}
    public static void error(ServerPlayer p,String key) {TradeNetwork.sendNotice(p,key);}
    public static boolean validAmount(int n) {return n>0&&n<=TradeConfig.MAX_AMOUNT.get();}
    public static ItemStack template(String id,CompoundTag nbt) {
        ResourceLocation key=ResourceLocation.tryParse(id);if(key==null||!BuiltInRegistries.ITEM.containsKey(key))return ItemStack.EMPTY;
        ItemStack s=new ItemStack(BuiltInRegistries.ITEM.get(key));
        if(s.isEmpty())return ItemStack.EMPTY;
        if(nbt!=null&&!nbt.isEmpty()) {if(nbt.toString().length()>8192)return ItemStack.EMPTY;s.setTag(nbt.copy());}
        return s;
    }
    public static boolean same(ItemStack a,ItemStack b) {return ItemStack.isSameItemSameTags(a,b);}
    public static int available(ServerPlayer p,ItemStack s) {int count=0;for(int i=0;i<p.getInventory().getContainerSize();i++){ItemStack x=p.getInventory().getItem(i);if(same(x,s))count+=x.getCount();}return count;}
    public static boolean remove(ServerPlayer p,ItemStack s,int count) {
        if(!validAmount(count)||available(p,s)<count)return false;
        for(int i=0;i<p.getInventory().getContainerSize()&&count>0;i++) {
            ItemStack x=p.getInventory().getItem(i);if(!same(x,s))continue;
            int n=Math.min(count,x.getCount());x.shrink(n);count-=n;
        }p.getInventory().setChanged();return count==0;
    }
    public static void queue(TradeSavedData d,UUID player,ItemStack template,int amount) {
        if(amount<=0||template.isEmpty())return;
        List<ItemStack> box=d.mailbox.computeIfAbsent(player,k->new ArrayList<>());
        while(amount>0) {int n=Math.min(amount,template.getMaxStackSize());box.add(template.copyWithCount(n));amount-=n;}
        d.setDirty();
    }
    public static void create(ServerPlayer p,int slot,int amount,String wantedId,int wantedAmount,CompoundTag wantedNbt,boolean auction,int minutes) {
        TradeSavedData d=TradeSavedData.get(p.server);
        if(count(d,p.getUUID())>=limit(p)){error(p,"message.trade_hub.limit");return;}
        if(slot<0||slot>=p.getInventory().getContainerSize()){error(p,"message.trade_hub.select_offer");return;}
        if(!validAmount(amount)||!validAmount(wantedAmount)){error(p,"message.trade_hub.invalid_amount");return;}
        if(auction&&(minutes<1||minutes>10080)){error(p,"message.trade_hub.invalid_duration");return;}
        ItemStack offer=p.getInventory().getItem(slot).copyWithCount(1);
        ItemStack wanted=template(wantedId,wantedNbt);
        if(wanted.isEmpty()){error(p,"message.trade_hub.invalid_item");return;}
        if(offer.isEmpty()){error(p,"message.trade_hub.select_offer");return;}
        if(!remove(p,offer,amount)){error(p,"message.trade_hub.insufficient");return;}
        long end=auction?System.currentTimeMillis()+minutes*60000L:0;
        TradeRecord r=new TradeRecord(UUID.randomUUID(),p.getUUID(),p.getGameProfile().getName(),offer,amount,wanted,wantedAmount,auction,end);
        d.listings.put(r.id,r);d.setDirty();broadcast(p.server);TradeNetwork.sendNotice(p,"message.trade_hub.created");
    }
    public static void buy(ServerPlayer p,UUID id) {
        TradeSavedData d=TradeSavedData.get(p.server);TradeRecord r=d.listings.get(id);
        if(r==null||!r.enabled||r.auction||r.owner.equals(p.getUUID())){error(p,"message.trade_hub.invalid");return;}
        if(!remove(p,r.wanted,r.wantedCount)){error(p,"message.trade_hub.insufficient");return;}
        d.listings.remove(id);queue(d,r.owner,r.wanted,r.wantedCount);queue(d,p.getUUID(),r.offered,r.offeredCount);d.setDirty();broadcast(p.server);
    }
    public static void bid(ServerPlayer p,UUID id,int amount) {
        TradeSavedData d=TradeSavedData.get(p.server);TradeRecord r=d.listings.get(id);
        if(r==null||!r.auction||!r.enabled||r.owner.equals(p.getUUID())||r.endsAt<=System.currentTimeMillis()||!validAmount(amount)||amount<r.wantedCount||amount<=r.highestBid){error(p,"message.trade_hub.invalid");return;}
        if(!remove(p,r.wanted,amount)){error(p,"message.trade_hub.insufficient");return;}
        if(r.leader!=null)queue(d,r.leader,r.wanted,r.highestBid);
        r.leader=p.getUUID();r.highestBid=amount;d.setDirty();broadcast(p.server);
    }
    public static void manage(ServerPlayer p,UUID id,boolean delete) {
        TradeSavedData d=TradeSavedData.get(p.server);TradeRecord r=d.listings.get(id);
        if(r==null||!r.owner.equals(p.getUUID()))return;
        if(delete) {d.listings.remove(id);queue(d,r.owner,r.offered,r.offeredCount);if(r.leader!=null)queue(d,r.leader,r.wanted,r.highestBid);}
        else r.enabled=!r.enabled;
        d.setDirty();broadcast(p.server);
    }
    public static void claim(ServerPlayer p) {
        TradeSavedData d=TradeSavedData.get(p.server);List<ItemStack> box=d.mailbox.get(p.getUUID());if(box==null||box.isEmpty())return;
        net.minecraft.world.entity.player.Inventory inventory=p.getInventory();
        List<ItemStack> simulation=new ArrayList<>();for(int i=0;i<36;i++)simulation.add(inventory.getItem(i).copy());
        for(ItemStack original:box) {
            int remaining=original.getCount();
            for(ItemStack target:simulation)if(!target.isEmpty()&&same(target,original)) {
                int n=Math.min(remaining,target.getMaxStackSize()-target.getCount());if(n>0){target.grow(n);remaining-=n;}if(remaining==0)break;
            }
            for(int i=0;i<simulation.size()&&remaining>0;i++)if(simulation.get(i).isEmpty()) {
                int n=Math.min(remaining,original.getMaxStackSize());simulation.set(i,original.copyWithCount(n));remaining-=n;
            }
            if(remaining>0){error(p,"message.trade_hub.full");return;}
        }
        for(int i=0;i<36;i++)inventory.setItem(i,simulation.get(i));
        d.mailbox.remove(p.getUUID());d.setDirty();TradeNetwork.sendSnapshot(p,false);
    }
    public static void broadcast(MinecraftServer server) {for(ServerPlayer p:server.getPlayerList().getPlayers())TradeNetwork.sendSnapshot(p,false);}
}
