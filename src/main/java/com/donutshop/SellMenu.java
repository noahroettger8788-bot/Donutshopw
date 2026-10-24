package com.donutshop;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Verkaufs-Fenster: Items hineinlegen, Fenster schliessen -> alles wird verkauft. */
public class SellMenu extends ChestMenu {
    private final Container sellBox;

    private SellMenu(int id, Inventory inv, Container box) {
        super(MenuType.GENERIC_9x6, id, inv, box, 6);
        this.sellBox = box;
    }

    public static void open(ServerPlayer player) {
        PriceManager.ensureInit(player.server);
        player.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> new SellMenu(id, inv, new SimpleContainer(54)),
                Component.literal("Items verkaufen")));
        player.displayClientMessage(Component.literal("Items hineinlegen, dann Fenster schliessen = verkaufen.")
                .withStyle(ChatFormatting.YELLOW), true);
    }

    @Override
    public void removed(Player player) {
        if (player instanceof ServerPlayer sp) {
            MinecraftServer server = sp.server;
            Map<Item, Integer> sold = new HashMap<>();
            double total = 0;
            int count = 0;
            for (int i = 0; i < sellBox.getContainerSize(); i++) {
                ItemStack st = sellBox.getItem(i);
                if (st.isEmpty() || !PriceManager.isSellable(st)) continue;
                total += PriceManager.stackValue(server, st);
                sold.merge(st.getItem(), st.getCount(), Integer::sum);
                count += st.getCount();
                sellBox.setItem(i, ItemStack.EMPTY);
            }
            total = PriceManager.round(total);
            if (total > 0) {
                EconomyData.get(server).addBalance(sp.getUUID(), total);
                for (Map.Entry<Item, Integer> e : sold.entrySet()) {
                    PriceManager.onSold(server, e.getKey(), e.getValue());
                }
                sp.displayClientMessage(Component.literal("Verkauft: " + count + " Items fuer " + PriceManager.fmt(total))
                        .withStyle(ChatFormatting.GREEN), false);
                sp.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8f, 1.0f);
            }
        }
        // Nicht verkaufbare Items zurueck ins Inventar
        this.clearContainer(player, this.sellBox);
        super.removed(player);
    }
}
