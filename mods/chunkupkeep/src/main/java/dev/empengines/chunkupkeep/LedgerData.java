package dev.empengines.chunkupkeep;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** World data: each team's ledgers, who got their join bonus, daily bonus and rent dates. */
public final class LedgerData extends SavedData {
    final Map<UUID, List<GlobalPos>> ledgers = new HashMap<>();
    final Set<UUID> joined = new HashSet<>();
    final Map<UUID, Long> lastDailyBonus = new HashMap<>();
    final Map<UUID, Long> lastRentDay = new HashMap<>();

    public static LedgerData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(LedgerData::new, LedgerData::load, null), "chunkupkeep");
    }

    public List<GlobalPos> ledgers(UUID team) { return ledgers.getOrDefault(team, List.of()); }

    public void addLedger(UUID team, GlobalPos pos) {
        ledgers.computeIfAbsent(team, t -> new ArrayList<>()).add(pos);
        setDirty();
    }

    public void removeLedger(GlobalPos pos) {
        ledgers.values().forEach(l -> l.remove(pos));
        setDirty();
    }

    public boolean markJoined(UUID player) {
        boolean first = joined.add(player);
        if (first) setDirty();
        return first;
    }

    public boolean claimDailyBonus(UUID player, long day) {
        Long last = lastDailyBonus.get(player);
        if (last != null && last >= day) return false;
        lastDailyBonus.put(player, day);
        setDirty();
        return true;
    }

    public boolean rentDue(UUID team, long day) {
        Long last = lastRentDay.get(team);
        return last == null || last < day;
    }

    public void markRentPaid(UUID team, long day) {
        lastRentDay.put(team, day);
        setDirty();
    }

    private static LedgerData load(CompoundTag tag, HolderLookup.Provider lookup) {
        LedgerData d = new LedgerData();
        CompoundTag l = tag.getCompound("ledgers");
        for (String team : l.getAllKeys()) {
            List<GlobalPos> list = new ArrayList<>();
            ListTag entries = l.getList(team, Tag.TAG_COMPOUND);
            for (int i = 0; i < entries.size(); i++) {
                CompoundTag e = entries.getCompound(i);
                ResourceLocation dim = ResourceLocation.tryParse(e.getString("dim"));
                if (dim != null) list.add(GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dim), BlockPos.of(e.getLong("pos"))));
            }
            d.ledgers.put(UUID.fromString(team), list);
        }
        ListTag joined = tag.getList("joined", Tag.TAG_STRING);
        for (int i = 0; i < joined.size(); i++) d.joined.add(UUID.fromString(joined.getString(i)));
        CompoundTag bonus = tag.getCompound("dailyBonus");
        for (String k : bonus.getAllKeys()) d.lastDailyBonus.put(UUID.fromString(k), bonus.getLong(k));
        CompoundTag rent = tag.getCompound("rent");
        for (String k : rent.getAllKeys()) d.lastRentDay.put(UUID.fromString(k), rent.getLong(k));
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        CompoundTag l = new CompoundTag();
        ledgers.forEach((team, list) -> {
            ListTag entries = new ListTag();
            for (GlobalPos p : list) {
                CompoundTag e = new CompoundTag();
                e.putString("dim", p.dimension().location().toString());
                e.putLong("pos", p.pos().asLong());
                entries.add(e);
            }
            l.put(team.toString(), entries);
        });
        tag.put("ledgers", l);
        ListTag joinedTag = new ListTag();
        joined.forEach(u -> joinedTag.add(StringTag.valueOf(u.toString())));
        tag.put("joined", joinedTag);
        CompoundTag bonus = new CompoundTag();
        lastDailyBonus.forEach((u, day) -> bonus.put(u.toString(), LongTag.valueOf(day)));
        tag.put("dailyBonus", bonus);
        CompoundTag rent = new CompoundTag();
        lastRentDay.forEach((u, day) -> rent.put(u.toString(), LongTag.valueOf(day)));
        tag.put("rent", rent);
        return tag;
    }
}
