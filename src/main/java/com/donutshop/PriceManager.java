package com.donutshop;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.minecraft.ChatFormatting;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Preise: Basispreis (Tabelle -> Rezept-Ableitung -> Seltenheit) mal Marktfaktor.
 * Der Marktfaktor schwankt alle UPDATE_INTERVAL_TICKS zufaellig und reagiert auf Kaeufe/Verkaeufe.
 */
public final class PriceManager {
    // ===== Einstellungen zum Anpassen =====
    public static final int UPDATE_INTERVAL_TICKS = 20 * 60 * 2; // Preisaenderung alle 2 Minuten Spielzeit
    public static final double SELL_FACTOR = 0.5;   // Verkaufswert = 50 % vom Kaufpreis
    public static final double CRAFT_MARKUP = 1.15; // Aufschlag bei Items, die aus Rezepten berechnet werden
    public static final double MIN_MULT = 0.6;      // niedrigster Marktfaktor
    public static final double MAX_MULT = 1.8;      // hoechster Marktfaktor
    // =======================================

    private static final Map<String, Double> TABLE = new HashMap<>();
    private static final Map<Item, List<Recipe<?>>> RECIPES = new HashMap<>();
    private static final Map<Item, Double> CACHE = new HashMap<>();
    private static final Set<Item> IN_PROGRESS = new HashSet<>();
    private static final Set<Item> LISTED = new HashSet<>();
    private static List<Item> shopItems = List.of();
    private static RegistryAccess access;
    private static boolean ready = false;

    private static final Set<String> BLACKLIST = Set.of(
            "air", "bedrock", "barrier", "light", "command_block", "chain_command_block",
            "repeating_command_block", "command_block_minecart", "structure_block", "structure_void",
            "jigsaw", "debug_stick", "knowledge_book", "spawner", "reinforced_deepslate",
            "end_portal_frame", "petrified_oak_slab", "farmland", "budding_amethyst", "chorus_plant",
            "player_head", "written_book", "filled_map", "enchanted_book", "potion", "splash_potion",
            "lingering_potion", "tipped_arrow", "suspicious_stew");

    private static final String RAW = """
            cobblestone=1
            cobbled_deepslate=1.5
            stone=1.3
            deepslate=2
            dirt=1
            grass_block=2
            podzol=4
            mycelium=8
            sand=1.5
            red_sand=2
            gravel=1.5
            clay_ball=3
            netherrack=0.5
            soul_sand=4
            soul_soil=4
            basalt=2
            blackstone=2
            end_stone=3
            obsidian=40
            crying_obsidian=80
            ice=3
            snowball=0.5
            granite=2
            diorite=2
            andesite=2
            tuff=2
            calcite=4
            dripstone_block=3
            moss_block=4
            mud=1
            coal=9
            charcoal=7
            raw_iron=16
            raw_copper=6
            raw_gold=32
            iron_ingot=22
            copper_ingot=9
            gold_ingot=45
            diamond=300
            emerald=130
            lapis_lazuli=9
            redstone=8
            quartz=14
            amethyst_shard=15
            glowstone_dust=10
            netherite_scrap=900
            ancient_debris=1000
            flint=3
            coal_ore=11
            iron_ore=20
            copper_ore=18
            gold_ore=38
            redstone_ore=30
            lapis_ore=55
            diamond_ore=320
            emerald_ore=140
            nether_gold_ore=22
            nether_quartz_ore=20
            bone=5
            string=4
            gunpowder=12
            spider_eye=8
            rotten_flesh=2
            ender_pearl=60
            blaze_rod=50
            ghast_tear=120
            slime_ball=10
            leather=8
            rabbit_hide=4
            feather=3
            ink_sac=6
            glow_ink_sac=10
            phantom_membrane=40
            shulker_shell=150
            nautilus_shell=150
            prismarine_shard=8
            prismarine_crystals=12
            scute=40
            turtle_egg=60
            echo_shard=200
            disc_fragment_5=50
            beef=6
            porkchop=6
            chicken=5
            mutton=5
            rabbit=6
            cod=4
            salmon=5
            tropical_fish=6
            pufferfish=8
            wheat=3
            wheat_seeds=1
            beetroot_seeds=1
            melon_seeds=1
            pumpkin_seeds=1
            carrot=3
            potato=3
            poisonous_potato=2
            beetroot=3
            melon_slice=1
            pumpkin=6
            sugar_cane=2
            cactus=2
            bamboo=1
            kelp=1
            apple=8
            sweet_berries=2
            glow_berries=3
            cocoa_beans=3
            nether_wart=10
            chorus_fruit=8
            egg=3
            honeycomb=10
            honey_bottle=12
            red_mushroom=5
            brown_mushroom=5
            crimson_fungus=8
            warped_fungus=8
            vine=2
            lily_pad=3
            sea_pickle=4
            sponge=100
            wet_sponge=100
            water_bucket=80
            lava_bucket=100
            milk_bucket=80
            powder_snow_bucket=85
            experience_bottle=60
            name_tag=100
            saddle=150
            totem_of_undying=2500
            elytra=6000
            trident=3000
            heart_of_the_sea=1500
            nether_star=12000
            dragon_egg=50000
            dragon_head=5000
            wither_skeleton_skull=3000
            skeleton_skull=300
            zombie_head=300
            creeper_head=300
            piglin_head=300
            enchanted_golden_apple=8000
            sniffer_egg=500
            torchflower_seeds=15
            pitcher_pod=15
            """;

    static {
        for (String line : RAW.split("\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            String[] kv = line.split("=");
            TABLE.put(kv[0].trim(), Double.parseDouble(kv[1].trim()));
        }
    }

    private PriceManager() {
    }

    // ---------- Lebenszyklus ----------

    public static void init(MinecraftServer server) {
        clear();
        access = server.registryAccess();
        for (Recipe<?> r : server.getRecipeManager().getRecipes()) {
            try {
                ItemStack out = r.getResultItem(access);
                if (out.isEmpty()) continue;
                RECIPES.computeIfAbsent(out.getItem(), k -> new ArrayList<>()).add(r);
            } catch (RuntimeException ignored) {
            }
        }
        List<Item> list = new ArrayList<>();
        for (Item item : ForgeRegistries.ITEMS) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
            if (id == null || item == Items.AIR) continue;
            if (item instanceof SpawnEggItem) continue;
            if (id.getNamespace().equals("minecraft") && isBlacklisted(id.getPath())) continue;
            list.add(item);
        }
        list.sort(Comparator.comparing(PriceManager::key));
        shopItems = List.copyOf(list);
        LISTED.addAll(list);
        ready = true;
    }

    public static void ensureInit(MinecraftServer server) {
        if (!ready) init(server);
    }

    public static void clear() {
        RECIPES.clear();
        CACHE.clear();
        IN_PROGRESS.clear();
        LISTED.clear();
        shopItems = List.of();
        access = null;
        ready = false;
    }

    private static boolean isBlacklisted(String p) {
        return BLACKLIST.contains(p) || p.startsWith("infested_");
    }

    // ---------- Preise ----------

    public static String key(Item item) {
        return String.valueOf(ForgeRegistries.ITEMS.getKey(item));
    }

    public static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    public static String fmt(double v) {
        return String.format(Locale.GERMANY, "%,.2f $", v);
    }

    public static boolean isListed(Item item) {
        return LISTED.contains(item);
    }

    public static boolean isSellable(ItemStack st) {
        if (st.isEmpty() || !LISTED.contains(st.getItem())) return false;
        CompoundTag t = st.getTag();
        return t == null || (!t.contains("BlockEntityTag") && !t.contains("Items"));
    }

    public static double multiplier(MinecraftServer s, Item item) {
        return EconomyData.get(s).getMultiplier(key(item));
    }

    public static double buyPrice(MinecraftServer s, Item item) {
        return round(basePrice(item) * multiplier(s, item));
    }

    public static double sellPrice(MinecraftServer s, Item item) {
        return round(basePrice(item) * multiplier(s, item) * SELL_FACTOR);
    }

    public static double stackValue(MinecraftServer s, ItemStack st) {
        double unit = sellPrice(s, st.getItem());
        double factor = 1.0;
        if (st.isDamageableItem() && st.getMaxDamage() > 0) {
            factor = 1.0 - 0.8 * ((double) st.getDamageValue() / st.getMaxDamage());
        }
        return unit * st.getCount() * factor;
    }

    public static List<Item> search(String filter) {
        if (filter == null || filter.isBlank()) return shopItems;
        String f = filter.toLowerCase(Locale.ROOT).trim().replace(' ', '_');
        List<Item> out = new ArrayList<>();
        for (Item i : shopItems) {
            String id = key(i);
            String name = i.getDescription().getString().toLowerCase(Locale.ROOT).replace(' ', '_');
            if (id.contains(f) || name.contains(f)) out.add(i);
        }
        return out;
    }

    public static void onSold(MinecraftServer s, Item item, int count) {
        EconomyData d = EconomyData.get(s);
        String k = key(item);
        d.setMultiplier(k, Mth.clamp(d.getMultiplier(k) * Math.pow(0.9975, count), MIN_MULT, MAX_MULT));
    }

    public static void onBought(MinecraftServer s, Item item, int count) {
        EconomyData d = EconomyData.get(s);
        String k = key(item);
        d.setMultiplier(k, Mth.clamp(d.getMultiplier(k) * Math.pow(1.0025, count), MIN_MULT, MAX_MULT));
    }

    public static void fluctuate(MinecraftServer server) {
        if (!ready) return;
        EconomyData data = EconomyData.get(server);
        RandomSource rnd = server.overworld().getRandom();
        for (Item item : shopItems) {
            String k = key(item);
            double m = data.getMultiplier(k);
            m += (1.0 - m) * 0.10 + (rnd.nextDouble() - 0.5) * 0.24;
            data.setMultiplier(k, Mth.clamp(m, MIN_MULT, MAX_MULT));
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.displayClientMessage(Component.literal("Die Marktpreise haben sich geaendert!").withStyle(ChatFormatting.YELLOW), true);
        }
    }

    public static void give(ServerPlayer p, Item item, int amount) {
        int left = amount;
        while (left > 0) {
            int n = Math.min(left, item.getMaxStackSize());
            p.getInventory().placeItemBackInInventory(new ItemStack(item, n));
            left -= n;
        }
    }

    // ---------- Basispreis ----------

    public static double basePrice(Item item) {
        Double cached = CACHE.get(item);
        if (cached != null) return cached;
        double price;
        Double t = tablePrice(item);
        if (t != null) {
            price = t;
        } else {
            IN_PROGRESS.add(item);
            Double d;
            try {
                d = derive(item);
            } finally {
                IN_PROGRESS.remove(item);
            }
            price = d != null ? d : fallback(item);
        }
        price = Math.max(0.1, round(price));
        CACHE.put(item, price);
        return price;
    }

    private static Double tablePrice(Item item) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        if (id == null) return null;
        String p = id.getPath();
        Double v = TABLE.get(p);
        if (v != null) return v;
        if (p.startsWith("deepslate_") && p.endsWith("_ore")) {
            v = TABLE.get(p.substring("deepslate_".length()));
            if (v != null) return v;
        }
        if (p.endsWith("_log") || p.endsWith("_stem")) return 4.0;
        if (p.endsWith("_sapling")) return 6.0;
        if (p.endsWith("_leaves")) return 1.0;
        if (p.startsWith("music_disc_")) return 400.0;
        if (p.endsWith("_pottery_sherd")) return 60.0;
        if (p.endsWith("_smithing_template")) return 150.0;
        if (p.endsWith("_banner_pattern")) return 60.0;
        return null;
    }

    private static Double derive(Item item) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        if (id != null && id.getNamespace().equals("minecraft")) {
            String p = id.getPath();
            if (p.startsWith("netherite_") && !p.equals("netherite_ingot") && !p.equals("netherite_scrap")
                    && !p.equals("netherite_block") && !p.contains("template")) {
                Item dia = ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft", "diamond_" + p.substring("netherite_".length())));
                if (dia != null && dia != Items.AIR && !IN_PROGRESS.contains(dia)) {
                    return basePrice(dia) + basePrice(Items.NETHERITE_INGOT);
                }
            }
        }
        List<Recipe<?>> list = RECIPES.get(item);
        if (list == null) return null;
        Double best = null;
        outer:
        for (Recipe<?> r : list) {
            if (r.isSpecial() || r.getIngredients().isEmpty()) continue;
            double sum = 0;
            for (Ingredient ing : r.getIngredients()) {
                if (ing.isEmpty()) continue;
                double cheapest = Double.MAX_VALUE;
                for (ItemStack s : ing.getItems()) {
                    Item it = s.getItem();
                    if (IN_PROGRESS.contains(it)) continue;
                    cheapest = Math.min(cheapest, basePrice(it));
                }
                if (cheapest == Double.MAX_VALUE) continue outer;
                sum += cheapest;
            }
            int outCount = Math.max(1, r.getResultItem(access).getCount());
            double price = sum * CRAFT_MARKUP / outCount;
            if (best == null || price < best) best = price;
        }
        return best;
    }

    private static double fallback(Item item) {
        return switch (new ItemStack(item).getRarity()) {
            case COMMON -> 8.0;
            case UNCOMMON -> 60.0;
            case RARE -> 250.0;
            default -> 1000.0;
        };
    }
}
