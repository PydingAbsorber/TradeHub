package com.pyding.trade_hub.client;

import java.util.*;
import com.mojang.authlib.GameProfile;
import com.pyding.trade_hub.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class TradeScreen extends Screen {
    private final List<TradeRecord> records = new ArrayList<>();
    private final List<ItemStack> catalog = new ArrayList<>();
    private final List<ItemStack> catalogResults = new ArrayList<>();
    private CompoundTag data;
    private EditBox search;
    private EditBox wantedSearch;
    private EditBox catalogSearch;
    private EditBox offerCount;
    private EditBox wantCount;
    private EditBox duration;
    private EditBox bidCount;
    private boolean mine;
    private boolean create;
    private boolean auction;
    private boolean catalogOpen;
    private int scroll;
    private int catalogScroll;
    private int slot = -1;
    private int left;
    private int top;
    private int pw;
    private int ph;
    private int modalX;
    private int modalY;
    private int modalW;
    private int modalH;
    private int catalogX;
    private int catalogY;
    private int catalogW;
    private int catalogH;
    private long modalStarted;
    private String filter = "all";
    private String lastCatalogQuery = "\u0000";
    private String noticeKey;
    private String wantedId = "minecraft:diamond";
    private long noticeStarted;
    private UUID selected;
    private static final ResourceLocation PAPER = texture("parchment");
    private static final int CARD_W = 138;
    private static final int CARD_H = 40;
    private static final int CARD_STEP_Y = 74;
    private static final int CARD_GAP = 24;
    private static final int CATALOG_COLUMNS = 9;
    private static final int CATALOG_ROWS = 5;
    private static final int CATALOG_CELL = 22;

    public TradeScreen(CompoundTag data) {
        super(Component.translatable("screen.trade_hub.title"));
        applySnapshot(data);
    }

    public void applySnapshot(CompoundTag next) {
        this.data = next;
        records.clear();
        for (Tag entry : next.getList("records", Tag.TAG_COMPOUND)) {
            try {
                records.add(TradeRecord.load((CompoundTag) entry));
            } catch (RuntimeException ignored) {
            }
        }
        if (selected != null && records.stream().noneMatch(record -> record.id.equals(selected))) {
            selected = null;
        }
        if (bidCount != null) {
            updateVisibility();
        }
    }

    public void showNotice(String key) {
        if ("message.trade_hub.created".equals(key)) {
            closeCreate();
        }
        noticeKey = key;
        noticeStarted = System.currentTimeMillis();
    }

    private static ResourceLocation texture(String name) {
        return new ResourceLocation(TradeHub.ID, "textures/gui/" + name + ".png");
    }

    private Component tr(String name, Object... arguments) {
        return Component.translatable(name, arguments);
    }

    private String ts(String name, Object... arguments) {
        return tr(name, arguments).getString();
    }

    @Override
    protected void init() {
        pw = Math.min(640, Math.max(280, width - 12));
        ph = Math.min(420, Math.max(240, height - 12));
        left = (width - pw) / 2;
        top = (height - ph) / 2;
        modalW = Math.min(500, pw - 22);
        modalH = Math.min(352, ph - 12);
        modalX = left + (pw - modalW) / 2;
        modalY = top + (ph - modalH) / 2;
        catalogW = Math.min(330, pw - 25);
        catalogH = Math.min(255, ph - 20);
        catalogX = left + (pw - catalogW) / 2;
        catalogY = top + (ph - catalogH) / 2;
        search = field(left + 115, top + 60, pw - 227, "screen.trade_hub.search", "");
        wantedSearch = field(modalX + modalW / 2 + 18, modalY + 76, modalW / 2 - 66, "screen.trade_hub.wanted_search", "");
        wantedSearch.setEditable(false);
        wantedSearch.setValue(new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation("minecraft:diamond"))).getHoverName().getString());
        offerCount = field(modalX + 38, modalY + 126, 100, "screen.trade_hub.amount", "1");
        wantCount = field(modalX + modalW / 2 + 18, modalY + 126, 100, "screen.trade_hub.amount", "1");
        duration = field(modalX + modalW / 2 + 18, modalY + 165, 100, "screen.trade_hub.duration", "60");
        bidCount = field(left + pw - 122, top + 90, 57, "screen.trade_hub.bid", "1");
        catalogSearch = field(catalogX + 27, catalogY + 46, catalogW - 75, "screen.trade_hub.catalog_search", "");
        catalog.clear();
        BuiltInRegistries.ITEM.forEach(item -> {
            ItemStack stack = new ItemStack(item);
            if (!stack.isEmpty()) {
                catalog.add(stack);
            }
        });
        catalog.sort(Comparator.comparing(stack -> stack.getHoverName().getString().toLowerCase(Locale.ROOT)));
        updateVisibility();
    }

    private EditBox field(int x, int y, int w, String key, String value) {
        EditBox box = new EditBox(font, x, y, Math.max(32, w), 18, tr(key));
        box.setHint(tr(key));
        box.setValue(value);
        addRenderableWidget(box);
        return box;
    }

    private void updateVisibility() {
        search.visible = !create;
        wantedSearch.visible = create && !catalogOpen;
        offerCount.visible = create && !catalogOpen;
        wantCount.visible = create && !catalogOpen;
        duration.visible = create && auction && !catalogOpen;
        bidCount.visible = !create && !mine && selectedAuction() != null;
        catalogSearch.visible = create && catalogOpen;
        if (catalogOpen) {
            setFocused(catalogSearch);
        }
    }

    private TradeRecord selectedAuction() {
        if (selected != null) {
            for (TradeRecord r : records) {
                if (r.id.equals(selected) && r.auction) {
                    return r;
                }
            }
        }
        return null;
    }

    private int number(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private void action(String command, UUID id, int amount) {
        TradeNetwork.send(TradeNetwork.request(command, id, slot, amount, number(wantCount.getValue(), 0), wantedId, new CompoundTag(), auction, number(duration.getValue(), 0)));
    }

    private void playClick() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(TradeHub.BUTTON_SOUND.get(), 1.0f));
        }
    }

    private boolean hit(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private record ClickArea(int x, int y, int width, int height) {
    }

    private boolean hit(double mx, double my, ClickArea area) {
        return hit(mx, my, area.x(), area.y(), area.width(), area.height());
    }

    private ClickArea mineButtonArea() {
        return new ClickArea(left + 30, top + 140, 88, 32);
    }

    private ClickArea filterButtonArea() {
        return new ClickArea(left + 30, top + 220, 88, 32);
    }

    private ClickArea upButtonArea() {
        return new ClickArea(left + pw - 64, top + 100, 26, 26);
    }

    private ClickArea downButtonArea() {
        return new ClickArea(left + pw - 64, top + ph - 107, 26, 26);
    }

    private ClickArea createButtonArea() {
        return new ClickArea(left + pw / 2 - 90, top + ph - 70, 90, 32);
    }

    private ClickArea claimButtonArea() {
        ClickArea createButton = createButtonArea();
        return new ClickArea(createButton.x() + 100, createButton.y() + 1, 88, 32);
    }

    private ClickArea cardArea(int position) {
        return new ClickArea(cardsX() + (position % 2) * (CARD_W + CARD_GAP) - 56,
            cardsY() + (position / 2) * CARD_STEP_Y + 20, CARD_W, CARD_H);
    }

    private ClickArea toggleButtonArea(ClickArea card) {
        return new ClickArea(card.x() + CARD_W - 12, card.y() - 2, 19, 19);
    }

    private ClickArea deleteButtonArea(ClickArea card) {
        return new ClickArea(card.x() + CARD_W - 12, card.y() + 22, 19, 19);
    }

    private ClickArea bidButtonArea() {
        return new ClickArea(cardsX() - 16, top + ph - 100, 121, 26);
    }

    private ClickArea bidSubmitButtonArea() {
        ClickArea bidButton = bidButtonArea();
        return new ClickArea(bidButton.x() + 190, bidButton.y() + 1, 26, 26);
    }

    private ClickArea createCloseButtonArea() {
        return new ClickArea(modalX + modalW - 43, modalY + 18, 25, 25);
    }

    private ClickArea createModeButtonArea() {
        return new ClickArea(modalX + 23, modalY + 155, 150, 30);
    }

    private ClickArea publishButtonArea() {
        return new ClickArea(modalX + (modalW - 164) / 2, modalY + modalH - 38, 164, 30);
    }

    private ClickArea catalogCloseButtonArea() {
        return new ClickArea(catalogX + catalogW - 37, catalogY + 15, 23, 23);
    }

    private ClickArea catalogUpButtonArea() {
        return new ClickArea(catalogX + catalogW - 42, catalogY + 79, 23, 23);
    }

    private ClickArea catalogDownButtonArea() {
        return new ClickArea(catalogX + catalogW - 42, catalogY + 163, 23, 23);
    }

    private void text(GuiGraphics g, Component label, int x, int y, int color) {
        int border = 0xff2b1a10;
        g.drawString(font, label, x - 1, y, border, false);
        g.drawString(font, label, x + 1, y, border, false);
        g.drawString(font, label, x, y - 1, border, false);
        g.drawString(font, label, x, y + 1, border, false);
        g.drawString(font, label, x, y, color, false);
    }

    private void text(GuiGraphics g, String label, int x, int y, int color) {
        text(g, Component.literal(label), x, y, color);
    }

    private void centered(GuiGraphics g, Component label, int centerX, int y, int color) {
        text(g, label, centerX - font.width(label) / 2, y, color);
    }

    private void centered(GuiGraphics g, String label, int centerX, int y, int color) {
        centered(g, Component.literal(label), centerX, y, color);
    }

    private int texWidth(String key) {
        if (key.startsWith("btn_wide_")) return 196;
        if (key.startsWith("trade_card_")) return 276;
        return 42;
    }

    private int texHeight(String key) {
        if (key.startsWith("btn_wide_")) return 42;
        if (key.startsWith("trade_card_")) return 40;
        return 42;
    }

    private void sprite(GuiGraphics g, String key, int x, int y, int w, int h) {
        g.blit(texture(key), x, y, w, h, 0, 0, texWidth(key), texHeight(key), texWidth(key), texHeight(key));
    }

    private void sprite(GuiGraphics g, String key, ClickArea area) {
        sprite(g, key, area.x(), area.y(), area.width(), area.height());
    }

    private void button(GuiGraphics g, String key, Component label, int x, int y, int w, int h) {
        sprite(g, key, x, y, w, h);
        centered(g, label, x + w / 2, y + (h - 8) / 2, 0xff2b4750);
    }

    private void button(GuiGraphics g, String key, Component label, ClickArea area) {
        button(g, key, label, area.x(), area.y(), area.width(), area.height());
    }

    private ResourceLocation face(TradeRecord record) {
        try {
            if (minecraft != null) {
                return minecraft.getSkinManager().getInsecureSkinLocation(new GameProfile(record.owner, record.ownerName));
            }
        } catch (RuntimeException ignored) {
        }
        return DefaultPlayerSkin.getDefaultSkin(record.owner);
    }

    private boolean matchesItemSearch(ItemStack item, String query) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item.getItem());
        if (query.startsWith("@")) {
            String namespace = query.substring(1).trim();
            return !namespace.isEmpty() && id.getNamespace().toLowerCase(Locale.ROOT).contains(namespace);
        }
        return item.getHoverName().getString().toLowerCase(Locale.ROOT).contains(query)
            || id.toString().toLowerCase(Locale.ROOT).contains(query);
    }

    private List<TradeRecord> filtered() {
        List<TradeRecord> result = new ArrayList<>();
        String query = search.getValue().trim().toLowerCase(Locale.ROOT);
        UUID self = data.hasUUID("self") ? data.getUUID("self") : new UUID(0, 0);
        for (TradeRecord record : records) {
            if (mine && !record.owner.equals(self)) continue;
            if (!mine && !record.enabled) continue;
            if (filter.equals("auction") && !record.auction) continue;
            if (filter.equals("trade") && record.auction) continue;
            if (!query.isEmpty() && !matchesItemSearch(record.offered, query)
                && !matchesItemSearch(record.wanted, query)
                && (query.startsWith("@") || !record.ownerName.toLowerCase(Locale.ROOT).contains(query))) continue;
            result.add(record);
        }
        return result;
    }

    private int cardsX() {
        int contentStart = left + 160;
        int contentEnd = left + pw - 42;
        int gridWidth = CARD_W * 2 + CARD_GAP;
        return contentStart + Math.max(0, (contentEnd - contentStart - gridWidth) / 2);
    }

    private int cardsY() {
        return top + 93;
    }

    private int visibleRows() {
        return Math.max(1, (ph - 169) / CARD_STEP_Y);
    }

    private int maxScroll() {
        return Math.max(0, (filtered().size() + 1) / 2 - visibleRows());
    }

    private void drawCard(GuiGraphics g, TradeRecord record, int x, int y, int mx, int my) {
        boolean enough = available(record.wanted) >= (record.auction ? Math.max(record.wantedCount, record.highestBid + 1) : record.wantedCount);
        String textureKey = enough ? "trade_card_green" : "trade_card_orange";
        sprite(g, textureKey, x, y, CARD_W, CARD_H);
        if (record.id.equals(selected)) {
            g.renderOutline(x + 2, y + 2, CARD_W - 6, CARD_H - 6, 0xfffcf2d4);
        }
        int leftItemX = x + 29;
        int rightItemX = x + 109;
        int itemY = y + 10;
        g.renderItem(record.wanted, leftItemX, itemY);
        g.renderItem(record.offered, rightItemX, itemY);
        sprite(g, record.auction ? "icon_auction" : "icon_trade", x + 56, y + 6, 26, 26);
        String wantQuantity = String.valueOf(record.auction ? Math.max(record.highestBid, record.wantedCount) : record.wantedCount);
        String offerQuantity = String.valueOf(record.offeredCount);
        text(g, wantQuantity, leftItemX - 4 - font.width(wantQuantity), y + 14, 0xffffffff);
        text(g, offerQuantity, rightItemX - 4 - font.width(offerQuantity), y + 14, 0xffffffff);
        int headX = x + (CARD_W - 18) / 2;
        int headY = y + CARD_H - 9;
        PlayerFaceRenderer.draw(g, face(record), headX, headY, 18);
        centered(g, record.ownerName, x + CARD_W / 2, headY + 20, 0xfff9ead6);
        if (mine) {
            ClickArea card = new ClickArea(x, y, CARD_W, CARD_H);
            sprite(g, record.enabled ? "icon_toggle_on" : "icon_toggle_off", toggleButtonArea(card));
            sprite(g, "icon_delete", deleteButtonArea(card));
        }
        if (hit(mx, my, leftItemX, itemY, 16, 16)) {
            g.renderTooltip(font, record.wanted, mx, my);
        }
        if (hit(mx, my, rightItemX, itemY, 16, 16)) {
            g.renderTooltip(font, record.offered, mx, my);
        }
    }

    private void renderMain(GuiGraphics g, int mx, int my) {
        button(g, mine ? "btn_wide_green" : "btn_wide_blue", tr("screen.trade_hub.mine"), mineButtonArea());
        text(g, tr("screen.trade_hub.mine_count", data.getInt("mine"), data.getInt("limit")), left + 34, top + 120, 0xffffffff);
        button(g, "btn_wide_blue", tr("screen.trade_hub.filter." + filter), filterButtonArea());
        List<TradeRecord> list = filtered();
        scroll = Math.min(scroll, maxScroll());
        for (int index = scroll * 2; index < Math.min(list.size(), (scroll + visibleRows()) * 2); index++) {
            int position = index - scroll * 2;
            ClickArea card = cardArea(position);
            drawCard(g, list.get(index), card.x(), card.y(), mx, my);
        }
        sprite(g, "icon_up", upButtonArea());
        sprite(g, "icon_down", downButtonArea());
        button(g, "btn_wide_green", tr("screen.trade_hub.create"), createButtonArea());
        button(g, "btn_wide_orange", tr("screen.trade_hub.claim", data.getInt("mail")), claimButtonArea());
        TradeRecord selectedRecord = selectedAuction();
        if (selectedRecord != null && !mine) {
            ClickArea bidButton = bidButtonArea();
            button(g, "btn_wide_orange", tr("screen.trade_hub.place_bid"), bidButton);
            bidCount.setX(bidButton.x() + 127);
            bidCount.setY(bidButton.y() + 4);
            sprite(g, "icon_add", bidSubmitButtonArea());
        }
    }

    private void drawInventory(GuiGraphics g, int mx, int my) {
        int invX = modalX + 25;
        int invY = modalY + 212;
        if (minecraft == null || minecraft.player == null) return;
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 9; col++) {
                int index = row == 3 ? col : 9 + row * 9 + col;
                int x = invX + col * 19;
                int y = invY + row * 19;
                g.fill(x, y, x + 18, y + 18, index == slot ? 0xff80d861 : 0xff51301c);
                g.fill(x + 1, y + 1, x + 17, y + 17, 0xffd7b887);
                ItemStack item = minecraft.player.getInventory().getItem(index);
                if (!item.isEmpty()) {
                    g.renderItem(item, x + 1, y + 1);
                    g.renderItemDecorations(font, item, x + 1, y + 1);
                    if (hit(mx, my, x, y, 18, 18) && !catalogOpen) {
                        g.renderTooltip(font, item, mx, my);
                    }
                }
            }
        }
    }

    private void renderCreate(GuiGraphics g, int mx, int my) {
        g.fill(0, 0, width, height, 0x8c000000);
        float t = Math.min(1f, (System.currentTimeMillis() - modalStarted) / 200f);
        int animateH = Math.round(modalH * (0.88f + t * 0.12f));
        int animateY = modalY + (modalH - animateH) / 2;
        g.blit(PAPER, modalX, animateY, modalW, animateH, 0, 0, 1024, 1024, 1024, 1024);
        centered(g, tr("screen.trade_hub.create_title"), modalX + modalW / 2, modalY + 20, 0xffffffff);
        sprite(g, "icon_close", createCloseButtonArea());
        int leftCenter = modalX + 90;
        int rightCenter = modalX + modalW / 2 + 18 + (modalW / 2 - 66) / 2;
        centered(g, tr("screen.trade_hub.your_item"), leftCenter, modalY + 57, 0xffffffff);
        centered(g, tr("screen.trade_hub.wanted_item"), rightCenter, modalY + 57, 0xffffffff);
        sprite(g, "icon_filter", modalX + modalW - 47, modalY + 75, 22, 22);
        centered(g, tr("screen.trade_hub.amount"), modalX + 38 + 50, modalY + 112, 0xffffffff);
        centered(g, tr("screen.trade_hub.amount"), modalX + modalW / 2 + 68, modalY + 112, 0xffffffff);
        button(g, auction ? "btn_wide_orange" : "btn_wide_green", tr(auction ? "screen.trade_hub.mode.auction" : "screen.trade_hub.mode.trade"), createModeButtonArea());
        if (auction) {
            centered(g, tr("screen.trade_hub.duration"), modalX + modalW / 2 + 68, modalY + 150, 0xffffffff);
        }
        text(g, tr("screen.trade_hub.select_item"), modalX + 25, modalY + 196, 0xffffffff);
        drawInventory(g, mx, my);
        button(g, "btn_wide_green", tr("screen.trade_hub.publish"), publishButtonArea());
        if (slot >= 0 && minecraft != null && minecraft.player != null) {
            ItemStack item = minecraft.player.getInventory().getItem(slot);
            if (!item.isEmpty()) {
                text(g, item.getHoverName(), modalX + 205, modalY + 196, 0xffffffff);
            }
        }
    }

    private void filterCatalog() {
        String q = catalogSearch.getValue().trim().toLowerCase(Locale.ROOT);
        if (q.equals(lastCatalogQuery)) return;
        lastCatalogQuery = q;
        catalogResults.clear();
        for (ItemStack item : catalog) {
            if (q.isEmpty() || matchesItemSearch(item, q)) {
                catalogResults.add(item);
            }
        }
        catalogScroll = Math.min(catalogScroll, maxCatalogScroll());
    }

    private int maxCatalogScroll() {
        return Math.max(0, (catalogResults.size() + CATALOG_COLUMNS - 1) / CATALOG_COLUMNS - CATALOG_ROWS);
    }

    private void renderCatalog(GuiGraphics g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, 0xa8000000);
        g.blit(PAPER, catalogX, catalogY, catalogW, catalogH, 0, 0, 1024, 1024, 1024, 1024);
        centered(g, tr("screen.trade_hub.catalog_title"), catalogX + catalogW / 2, catalogY + 22, 0xffffffff);
        sprite(g, "icon_close", catalogCloseButtonArea());
        catalogSearch.render(g, mx, my, delta);
        filterCatalog();
        int startX = catalogX + (catalogW - CATALOG_COLUMNS * CATALOG_CELL) / 2;
        int startY = catalogY + 77;
        for (int visibleIndex = 0; visibleIndex < CATALOG_ROWS * CATALOG_COLUMNS; visibleIndex++) {
            int actual = (catalogScroll * CATALOG_COLUMNS) + visibleIndex;
            if (actual >= catalogResults.size()) break;
            int col = visibleIndex % CATALOG_COLUMNS;
            int row = visibleIndex / CATALOG_COLUMNS;
            int x = startX + col * CATALOG_CELL;
            int y = startY + row * CATALOG_CELL;
            boolean hovered = hit(mx, my, x, y, 20, 20);
            g.fill(x, y, x + 20, y + 20, hovered ? 0xff72cdec : 0xff66462c);
            g.fill(x + 1, y + 1, x + 19, y + 19, 0xffe0c495);
            ItemStack stack = catalogResults.get(actual);
            g.renderItem(stack, x + 2, y + 2);
            if (hovered) {
                g.renderTooltip(font, stack, mx, my);
            }
        }
        sprite(g, "icon_up", catalogUpButtonArea());
        sprite(g, "icon_down", catalogDownButtonArea());
        centered(g, tr("screen.trade_hub.catalog_count", catalogResults.size()), catalogX + catalogW / 2, catalogY + catalogH - 31, 0xffffffff);
    }

    private void renderNotice(GuiGraphics g) {
        if (noticeKey == null) return;
        long elapsed = System.currentTimeMillis() - noticeStarted;
        if (elapsed > 4500) {
            noticeKey = null;
            return;
        }
        int widthBox = Math.min(340, Math.max(190, width - 24));
        int x = (width - widthBox) / 2;
        int y = Math.max(5, top + 8) + (int) Math.max(0, 12 - elapsed / 12);
        java.util.List<net.minecraft.util.FormattedCharSequence> lines = font.split(tr(noticeKey), widthBox - 26);
        int heightBox = 15 + lines.size() * 12;
        boolean success = "message.trade_hub.created".equals(noticeKey);
        g.pose().pushPose();
        g.pose().translate(0, 0, 450);
        g.fill(x, y, x + widthBox, y + heightBox, 0xff45241b);
        g.fill(x + 2, y + 2, x + widthBox - 2, y + heightBox - 2, success ? 0xff59974d : 0xffbb5340);
        for (int i = 0; i < lines.size(); i++) {
            net.minecraft.util.FormattedCharSequence line = lines.get(i);
            int textX = (width - font.width(line)) / 2;
            int textY = y + 8 + i * 12;
            int border = 0xff2b1a10;
            g.drawString(font, line, textX - 1, textY, border, false);
            g.drawString(font, line, textX + 1, textY, border, false);
            g.drawString(font, line, textX, textY - 1, border, false);
            g.drawString(font, line, textX, textY + 1, border, false);
            g.drawString(font, line, textX, textY, 0xffffffff, false);
        }
        g.pose().popPose();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        renderBackground(g);
        g.blit(PAPER, left, top, pw, ph, 0, 0, 1024, 1024, 1024, 1024);
        centered(g, title, left + pw / 2, top + 16, 0xffffffff);
        if (!create) {
            renderMain(g, mx, my);
        } else {
            renderCreate(g, mx, my);
        }
        super.render(g, mx, my, delta);
        if (catalogOpen) {
            renderCatalog(g, mx, my, delta);
        }
        renderNotice(g);
    }

    private int available(ItemStack template) {
        int found = 0;
        if (minecraft != null && minecraft.player != null) {
            for (int index = 0; index < minecraft.player.getInventory().getContainerSize(); index++) {
                ItemStack candidate = minecraft.player.getInventory().getItem(index);
                if (ItemStack.isSameItemSameTags(candidate, template)) {
                    found += candidate.getCount();
                }
            }
        }
        return found;
    }

    private void openCatalog() {
        catalogOpen = true;
        catalogScroll = 0;
        lastCatalogQuery = "\u0000";
        catalogSearch.setValue("");
        updateVisibility();
    }

    private void closeCatalog() {
        catalogOpen = false;
        setFocused(null);
        updateVisibility();
    }

    private void closeCreate() {
        create = false;
        catalogOpen = false;
        slot = -1;
        setFocused(null);
        updateVisibility();
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (catalogOpen) {
            filterCatalog();
            catalogScroll = Math.max(0, Math.min(maxCatalogScroll(), catalogScroll - (int) Math.signum(amount)));
            return true;
        }
        if (create) return true;
        if (hit(mx, my, left + 156, top + 84, pw - 196, ph - 144)) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(amount)));
            return true;
        }
        return super.mouseScrolled(mx, my, amount);
    }

    private boolean catalogClick(double mx, double my) {
        if (hit(mx, my, catalogCloseButtonArea())) {
            playClick();
            closeCatalog();
            return true;
        }
        int startX = catalogX + (catalogW - CATALOG_COLUMNS * CATALOG_CELL) / 2;
        int startY = catalogY + 77;
        if (hit(mx, my, catalogUpButtonArea())) {
            playClick();
            catalogScroll = Math.max(0, catalogScroll - 1);
            return true;
        }
        if (hit(mx, my, catalogDownButtonArea())) {
            playClick();
            catalogScroll = Math.min(maxCatalogScroll(), catalogScroll + 1);
            return true;
        }
        filterCatalog();
        for (int i = 0; i < CATALOG_COLUMNS * CATALOG_ROWS; i++) {
            int actual = catalogScroll * CATALOG_COLUMNS + i;
            if (actual >= catalogResults.size()) break;
            int x = startX + (i % CATALOG_COLUMNS) * CATALOG_CELL;
            int y = startY + (i / CATALOG_COLUMNS) * CATALOG_CELL;
            if (hit(mx, my, x, y, 20, 20)) {
                playClick();
                ItemStack selectedItem = catalogResults.get(actual);
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(selectedItem.getItem());
                wantedId = id.toString();
                wantedSearch.setValue(selectedItem.getHoverName().getString());
                closeCatalog();
                return true;
            }
        }
        return true;
    }

    private boolean createClick(double mx, double my) {
        if (hit(mx, my, createCloseButtonArea())) {
            playClick();
            closeCreate();
            return true;
        }
        if (hit(mx, my, modalX + modalW / 2 + 10, modalY + 69, modalW / 2 - 34, 32)) {
            playClick();
            openCatalog();
            return true;
        }
        if (hit(mx, my, createModeButtonArea())) {
            playClick();
            auction = !auction;
            updateVisibility();
            return true;
        }
        int invX = modalX + 25;
        int invY = modalY + 212;
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 9; col++) {
                if (hit(mx, my, invX + col * 19, invY + row * 19, 18, 18)) {
                    int next = row == 3 ? col : 9 + row * 9 + col;
                    if (minecraft != null && minecraft.player != null && !minecraft.player.getInventory().getItem(next).isEmpty()) {
                        playClick();
                        slot = next;
                    }
                    return true;
                }
            }
        }
        if (hit(mx, my, publishButtonArea())) {
            playClick();
            if (slot < 0) {
                showNotice("message.trade_hub.select_offer");
            } else if (number(offerCount.getValue(), 0) <= 0 || number(wantCount.getValue(), 0) <= 0) {
                showNotice("message.trade_hub.invalid_amount");
            } else if (wantedId.isBlank()) {
                showNotice("message.trade_hub.invalid_item");
            } else if (auction && number(duration.getValue(), 0) <= 0) {
                showNotice("message.trade_hub.invalid_duration");
            } else {
                action("create", new UUID(0, 0), number(offerCount.getValue(), 0));
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        if (catalogOpen) {
            if (hit(mx, my, catalogSearch.getX(), catalogSearch.getY(), catalogSearch.getWidth(), 18)) {
                return super.mouseClicked(mx, my, button);
            }
            return catalogClick(mx, my);
        }
        if (create) {
            if (createClick(mx, my)) return true;
            return super.mouseClicked(mx, my, button);
        }
        if (hit(mx, my, createButtonArea())) {
            playClick();
            create = true;
            selected = null;
            modalStarted = System.currentTimeMillis();
            setFocused(null);
            updateVisibility();
            return true;
        }
        if (hit(mx, my, claimButtonArea())) {
            playClick();
            action("claim", new UUID(0, 0), 0);
            return true;
        }
        if (hit(mx, my, mineButtonArea())) {
            playClick();
            mine = !mine;
            scroll = 0;
            selected = null;
            updateVisibility();
            return true;
        }
        if (hit(mx, my, filterButtonArea())) {
            playClick();
            filter = filter.equals("all") ? "trade" : filter.equals("trade") ? "auction" : "all";
            scroll = 0;
            selected = null;
            updateVisibility();
            return true;
        }
        if (hit(mx, my, upButtonArea())) {
            playClick();
            scroll = Math.max(0, scroll - 1);
            return true;
        }
        if (hit(mx, my, downButtonArea())) {
            playClick();
            scroll = Math.min(maxScroll(), scroll + 1);
            return true;
        }
        TradeRecord chosen = selectedAuction();
        if (chosen != null && !mine) {
            if (hit(mx, my, bidButtonArea()) || hit(mx, my, bidSubmitButtonArea())) {
                playClick();
                action("bid", chosen.id, number(bidCount.getValue(), 0));
                return true;
            }
        }
        List<TradeRecord> list = filtered();
        for (int index = scroll * 2; index < Math.min(list.size(), (scroll + visibleRows()) * 2); index++) {
            int position = index - scroll * 2;
            ClickArea card = cardArea(position);
            TradeRecord record = list.get(index);
            if (mine && hit(mx, my, toggleButtonArea(card))) {
                playClick();
                action("toggle", record.id, 0);
                return true;
            }
            if (mine && hit(mx, my, deleteButtonArea(card))) {
                playClick();
                action("delete", record.id, 0);
                return true;
            }
            if (hit(mx, my, card.x(), card.y(), card.width(), card.height() + 16)) {
                playClick();
                if (!mine && !record.auction) {
                    action("buy", record.id, 0);
                } else if (!mine) {
                    selected = record.id;
                    bidCount.setValue(String.valueOf(Math.max(record.wantedCount, record.highestBid + 1)));
                    updateVisibility();
                }
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (minecraft != null && minecraft.options.keyInventory.matches(key, scan)) {
            if (getFocused() instanceof EditBox activeField && activeField.isFocused() && activeField.visible) {
                return super.keyPressed(key, scan, modifiers);
            }
            onClose();
            return true;
        }
        if (key == 256 && catalogOpen) {
            playClick();
            closeCatalog();
            return true;
        }
        if (key == 256 && create) {
            playClick();
            closeCreate();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
