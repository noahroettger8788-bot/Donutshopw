package com.donutshop;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class ModCommands {
    private ModCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("shop")
                .executes(c -> shop(c.getSource(), ""))
                .then(Commands.argument("suche", StringArgumentType.greedyString())
                        .executes(c -> shop(c.getSource(), StringArgumentType.getString(c, "suche")))));

        d.register(Commands.literal("sell").executes(c -> sell(c.getSource())));
        d.register(Commands.literal("sellhand").executes(c -> sellHand(c.getSource())));
        d.register(Commands.literal("price").executes(c -> price(c.getSource())));

        for (String name : new String[] { "balance", "bal", "money" }) {
            d.register(Commands.literal(name).executes(c -> balance(c.getSource())));
        }

        d.register(Commands.literal("eco").requires(s -> s.hasPermission(2))
                .then(Commands.literal("add")
                        .then(Commands.argument("betrag", DoubleArgumentType.doubleArg())
                                .executes(c -> ecoAdd(c.getSource(), DoubleArgumentType.getDouble(c, "betrag"))))));
    }

    private static int shop(CommandSourceStack src, String filter) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        PriceManager.ensureInit(src.getServer());
        if (PriceManager.search(filter).isEmpty()) {
            src.sendFailure(Component.literal("Nichts gefunden."));
            return 0;
        }
        ShopMenu.open(p, filter);
        return 1;
    }

    private static int sell(CommandSourceStack src) throws CommandSyntaxException {
        SellMenu.open(src.getPlayerOrException());
        return 1;
    }

    private static int sellHand(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        MinecraftServer s = src.getServer();
        PriceManager.ensureInit(s);
        ItemStack st = p.getMainHandItem();
        if (!PriceManager.isSellable(st)) {
            src.sendFailure(Component.literal("Dieses Item kann nicht verkauft werden."));
            return 0;
        }
        double value = PriceManager.round(PriceManager.stackValue(s, st));
        int count = st.getCount();
        Item item = st.getItem();
        EconomyData.get(s).addBalance(p.getUUID(), value);
        PriceManager.onSold(s, item, count);
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        src.sendSuccess(() -> Component.literal("Verkauft: " + count + "x " + item.getDescription().getString()
                + " fuer " + PriceManager.fmt(value)).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int price(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        MinecraftServer s = src.getServer();
        PriceManager.ensureInit(s);
        ItemStack st = p.getMainHandItem();
        if (st.isEmpty() || !PriceManager.isListed(st.getItem())) {
            src.sendFailure(Component.literal("Halte ein Item in der Hand, das im Shop gelistet ist."));
            return 0;
        }
        Item item = st.getItem();
        String msg = item.getDescription().getString() + ": Kaufpreis " + PriceManager.fmt(PriceManager.buyPrice(s, item))
                + " | Verkaufswert " + PriceManager.fmt(PriceManager.sellPrice(s, item));
        src.sendSuccess(() -> Component.literal(msg).withStyle(ChatFormatting.GOLD), false);
        return 1;
    }

    private static int balance(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        double b = EconomyData.get(src.getServer()).getBalance(p.getUUID());
        src.sendSuccess(() -> Component.literal("Kontostand: " + PriceManager.fmt(b)).withStyle(ChatFormatting.GOLD), false);
        return 1;
    }

    private static int ecoAdd(CommandSourceStack src, double amount) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        EconomyData eco = EconomyData.get(src.getServer());
        eco.addBalance(p.getUUID(), amount);
        double b = eco.getBalance(p.getUUID());
        src.sendSuccess(() -> Component.literal("Neuer Kontostand: " + PriceManager.fmt(b)).withStyle(ChatFormatting.GOLD), true);
        return 1;
    }
}
