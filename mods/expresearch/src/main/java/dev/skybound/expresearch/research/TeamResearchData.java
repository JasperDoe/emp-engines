package dev.skybound.expresearch.research;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Which research each FTB team has unlocked. Stored with the world. */
public final class TeamResearchData extends SavedData {
    private final Map<UUID, Set<ResourceLocation>> unlocked = new HashMap<>();

    public static TeamResearchData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(TeamResearchData::new, TeamResearchData::load, null), "expresearch_teams");
    }

    public Set<ResourceLocation> unlocked(UUID team) {
        return unlocked.getOrDefault(team, Set.of());
    }

    public boolean has(UUID team, ResourceLocation id) {
        return unlocked(team).contains(id);
    }

    public boolean add(UUID team, ResourceLocation id) {
        boolean added = unlocked.computeIfAbsent(team, t -> new HashSet<>()).add(id);
        if (added) setDirty();
        return added;
    }

    public boolean remove(UUID team, ResourceLocation id) {
        Set<ResourceLocation> s = unlocked.get(team);
        boolean removed = s != null && s.remove(id);
        if (removed) setDirty();
        return removed;
    }

    private static TeamResearchData load(CompoundTag tag, HolderLookup.Provider lookup) {
        TeamResearchData data = new TeamResearchData();
        CompoundTag teams = tag.getCompound("teams");
        for (String key : teams.getAllKeys()) {
            Set<ResourceLocation> set = new HashSet<>();
            ListTag list = teams.getList(key, Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                ResourceLocation id = ResourceLocation.tryParse(list.getString(i));
                if (id != null) set.add(id);
            }
            data.unlocked.put(UUID.fromString(key), set);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        CompoundTag teams = new CompoundTag();
        unlocked.forEach((team, set) -> {
            ListTag list = new ListTag();
            set.forEach(id -> list.add(StringTag.valueOf(id.toString())));
            teams.put(team.toString(), list);
        });
        tag.put("teams", teams);
        return tag;
    }
}
