package com.donutshop;

import java.util.List;
import java.util.Locale;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Kauf-Shop als Kisten-GUI (rein serverseitig, keine Client-Installation noetig). */
public class ShopMenu extends ChestMenu {
    private static final int PER_PAGE = 45;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_BAL = 49;
    private static final int SLOT_NEXT = 53;

    private final ServerPlayer owner;
    private final List<Item> items;
    private final Container shop;
    private int page;

    private ShopMenu(int id, Inventory inv, Container shop, ServerPlayer owner, List<Item> items) {
        super(MenuType.GENERIC_9x6, id, inv, shop, 6);
        this.shop = shop;
        this.owner = owner;
        this.items = items;
        this.page = 0;
        refresh();
    }

    public static void open(ServerPlayer player, String filter) {
        List<Item> list = PriceManager.search(filter);
        String title = (filter == null || filter.isBlank()) ? "Shop" : "Shop: " + filter;
        player.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> new ShopMenu(id, inv, new SimpleContainer(54), (ServerPlayer) p, list),
                Component.literal(title)));
    }

    // ---------- Anzeige ----------

    private static Component line(String text, ChatFormatting fmt) {
        return Component.literal(text).setStyle(Style.EMPTY.withItalic(false).withColor(fmt));
    }

    private static ItemStack named(ItemStack st, String text, ChatFormatting fmt) {
        return st.setHoverName(line(text, fmt));
    }

    private static void addLore(ListTag lore, String text, ChatFormatting fmt) {
        lore.add(StringTag.valueOf(Component.Serializer.toJson(line(text, fmt))));
    }

    private ItemStack display(MinecraftServer server, Item item) {
        ItemStack st = new ItemStack(item);
        double buy = PriceManager.buyPrice(server, item);
        double sell = PriceManager.sellPrice(server, item);
        double pct = PriceManager.multiplier(server, item) * 100.0;
        ListTag lore = new ListTag();
        addLore(lore, "Kaufpreis: " + PriceManager.fmt(buy), ChatFormatting.GREEN);
        addLore(lore, "Verkaufswert: " + PriceManager.fmt(sell), ChatFormatting.GOLD);
        addLore(lore, String.format(Locale.GERMANY, "Marktlage: %.0f%% vom Normalpreis", pct),
                pct > 105 ? ChatFormatting.RED : (pct < 95 ? ChatFormatting.AQUA : ChatFormatting.GRAY));
        addLore(lore, "Links: 1x | Rechts: 16x | Shift: Stack", ChatFormatting.DARK_GRAY);
        st.getOrCreateTagElement("display").put("Lore", lore);
        return st;
    }

    private int pages() {
        return Math.max(1, (items.size() + PER_PAGE - 1) / PER_PAGE);
    }

    private void refresh() {
        MinecraftServer server = owner.server;
        page = Math.max(0, Math.min(page, pages() - 1));
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = page * PER_PAGE + i;
            shop.setItem(i, idx < items.size() ? display(server, items.get(idx)) : ItemStack.EMPTY);
        }
        ItemStack glass = named(new ItemStack(Items.GRAY_STAINED_GLASS_PANE), " ", ChatFormatting.GRAY);
        for (int i = PER_PAGE; i < 54; i++) shop.setItem(i, glass.copy());

        if (page > 0) {
            shop.setItem(SLOT_PREV, named(new ItemStack(Items.ARROW), "<< Zur\u00fcck", ChatFormatting.WHITE));
        }
        if (page < pages() - 1) {
            shop.setItem(SLOT_NEXT, named(new ItemStack(Items.ARROW), "Weiter >>", ChatFormatting.WHITE));
        }
        ItemStack bal = named(new ItemStack(Items.GOLD_INGOT),
                "Kontostand: " + PriceManager.fmt(EconomyData.get(server).getBalance(owner.getUUID())),
                ChatFormatting.GOLD);
        ListTag lore = new ListTag();
        addLore(lore, "Seite " + (page + 1) + " / " + pages(), ChatFormatting.GRAY);
        addLore(lore, items.size() + " Items", ChatFormatting.GRAY);
        bal.getOrCreateTagElement("display").put("Lore", lore);
        shop.setItem(SLOT_BAL, bal);
    }

    // ---------- Klicks ----------

    @Override
    public void clicked(int slotId, int button, ClickType type, Player player) {
        if (slotId >= 0 && slotId < PER_PAGE && (type == ClickType.PICKUP || type == ClickType.QUICK_MOVE)) {
            int idx = page * PER_PAGE + slotId;
            if (idx < items.size()) {
                Item item = items.get(idx);
                int amount;
                if (type == ClickType.QUICK_MOVE) amount = item.getMaxStackSize();
                else amount = (button == 1) ? Math.min(16, item.getMaxStackSize()) : 1;
                buy(item, amount);
            }
        } else if (type == ClickType.PICKUP) {
            if (slotId == SLOT_PREV && page > 0) {
                page--;
                refresh();
            } else if (slotId == SLOT_NEXT && page < pages() - 1) {
                page++;
                refresh();
            }
        }
        // Alle anderen Klicks werden bewusst ignoriert (Shop-Items koennen nicht entnommen werden).
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    private void buy(Item item, int amount) {
        MinecraftServer server = owner.server;
        EconomyData eco = EconomyData.get(server);
        double total = PriceManager.round(PriceManager.buyPrice(server, item) * amount);
        if (eco.getBalance(owner.getUUID()) + 1e-9 < total) {
            owner.displayClientMessage(Component.literal("Nicht genug Geld! Benoetigt: " + PriceManager.fmt(total))
                    .withStyle(ChatFormatting.RED), true);
            owner.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 1.0f, 1.0f);
            return;
        }
        eco.addBalance(owner.getUUID(), -total);
        PriceManager.onBought(server, item, amount);
        PriceManager.give(owner, item, amount);
        owner.displayClientMessage(Component.literal("Gekauft: " + amount + "x " + item.getDescription().getString()
                + " fuer " + PriceManager.fmt(total)).withStyle(ChatFormatting.GREEN), true);
        owner.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6f, 1.2f);
        refresh();
    }
}
