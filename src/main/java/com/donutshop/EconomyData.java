package com.donutshop;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Speichert Kontostand und Preisfaktoren pro Welt. */
public class EconomyData extends SavedData {
    private static final String NAME = "donutshop_economy";
    public static final double START_MONEY = 1000.0;

    private final Map<UUID, Double> balances = new HashMap<>();
    private final Map<String, Double> multipliers = new HashMap<>();

    public static EconomyData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(EconomyData::load, EconomyData::new, NAME);
    }

    public EconomyData() {
    }

    public static EconomyData load(CompoundTag tag) {
        EconomyData d = new EconomyData();
        CompoundTag b = tag.getCompound("balances");
        for (String k : b.getAllKeys()) {
            try {
                d.balances.put(UUID.fromString(k), b.getDouble(k));
            } catch (IllegalArgumentException ignored) {
            }
        }
        CompoundTag m = tag.getCompound("multipliers");
        for (String k : m.getAllKeys()) {
            d.multipliers.put(k, m.getDouble(k));
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag b = new CompoundTag();
        for (Map.Entry<UUID, Double> e : balances.entrySet()) {
            b.putDouble(e.getKey().toString(), e.getValue());
        }
        tag.put("balances", b);
        CompoundTag m = new CompoundTag();
        for (Map.Entry<String, Double> e : multipliers.entrySet()) {
            m.putDouble(e.getKey(), e.getValue());
        }
        tag.put("multipliers", m);
        return tag;
    }

    public double getBalance(UUID id) {
        Double v = balances.get(id);
        if (v == null) {
            balances.put(id, START_MONEY);
            setDirty();
            return START_MONEY;
        }
        return v;
    }

    public void addBalance(UUID id, double amount) {
        double nv = Math.round((getBalance(id) + amount) * 100.0) / 100.0;
        balances.put(id, nv);
        setDirty();
    }

    public double getMultiplier(String key) {
        return multipliers.getOrDefault(key, 1.0);
    }

    public void setMultiplier(String key, double value) {
        multipliers.put(key, value);
        setDirty();
    }
}
