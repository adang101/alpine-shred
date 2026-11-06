package io.github.adang101.alpineshred.lift;

import io.github.adang101.alpineshred.AlpineShred;
import io.github.adang101.alpineshred.config.AlpineConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** All lift lines of one level, by id in insertion order, saved as {@code data/alpineshred_lifts.dat}. */
public final class LiftSavedData extends SavedData {
    public static final String KEY = "alpineshred_lifts";

    private final Map<UUID, LiftLine> lines = new LinkedHashMap<>();

    public static LiftSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(LiftSavedData::load, LiftSavedData::new, KEY);
    }

    public static LiftSavedData load(CompoundTag tag) {
        LiftSavedData data = new LiftSavedData();
        for (Tag t : tag.getList("Lines", Tag.TAG_COMPOUND)) {
            try {
                LiftLine line = LiftLine.load((CompoundTag) t);
                data.lines.put(line.id, line);
            } catch (RuntimeException e) {
                AlpineShred.LOGGER.error("Dropping an unreadable lift line", e);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (LiftLine line : lines.values()) list.add(line.save());
        tag.put("Lines", list);
        return tag;
    }

    public LiftLine get(UUID id) {
        return lines.get(id);
    }

    public Collection<LiftLine> lines() {
        return lines.values();
    }

    public boolean isFull() {
        return lines.size() >= AlpineConfig.LIFT_MAX_LINES_PER_LEVEL.get();
    }

    /** Adds a line; false (and nothing added) if the id exists or the level is at {@code lift.maxLinesPerLevel}. */
    public boolean add(LiftLine line) {
        if (lines.containsKey(line.id) || isFull()) return false;
        lines.put(line.id, line);
        setDirty();
        return true;
    }

    public LiftLine remove(UUID id) {
        LiftLine line = lines.remove(id);
        if (line != null) setDirty();
        return line;
    }
}
