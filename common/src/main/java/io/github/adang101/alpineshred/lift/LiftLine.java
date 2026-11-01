package io.github.adang101.alpineshred.lift;

import io.github.adang101.alpineshred.config.AlpineConfig;
import io.github.adang101.alpineshred.park.V3;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One lift line (design doc section 13, "State and persistence"): the nodes and the saved motion
 * state. The drive station is the first node, the heads follow in link order, the return station is
 * last.
 *
 * <p>A {@link BlockPos} maps to a path node at its block centre, {@code (x + 0.5, y + 0.5, z + 0.5)}
 * ({@link #node}); the cable then runs {@link LiftPath#CABLE_HEIGHT} above that. The drive station's
 * platform Y for unloading is the drive block's own Y.
 *
 * <p>Speed is kept as a double (not a float) so the server steps exactly as {@link LiftMotion#advance}
 * does. The path, chair count, spacing and station intervals are cached and rebuilt by
 * {@link #rebuild()}; they are not saved.
 */
public final class LiftLine implements LineView {
    public final UUID id;
    public final BlockPos drive;
    public final BlockPos returnStation;
    public final List<BlockPos> heads;

    public double travel;
    public double speed;
    public double targetSpeed;
    public boolean powered;
    public boolean faulted;
    public boolean emergency;

    // Not saved.
    private LiftPath path;
    private int chairCount;
    private int spacing;
    private LiftStations.Result stations;
    /** Why the line faulted, for logs and commands; lost on reload. */
    public String faultReason = "";
    /** Ticks since the line was last marked dirty, and ticks stepped since it was created in memory. */
    int ticksSinceDirty;
    long age;

    public LiftLine(UUID id, BlockPos drive, List<BlockPos> heads, BlockPos returnStation) {
        this.id = id;
        this.drive = drive.immutable();
        this.returnStation = returnStation.immutable();
        List<BlockPos> copy = new ArrayList<>(heads.size());
        for (BlockPos h : heads) copy.add(h.immutable());
        this.heads = copy;
        rebuild();
    }

    /** The path node for a block: its centre. */
    public static V3 node(BlockPos pos) {
        return new V3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    /** All node positions in loop order: drive, heads, return. */
    public List<BlockPos> nodePositions() {
        List<BlockPos> all = new ArrayList<>(heads.size() + 2);
        all.add(drive);
        all.addAll(heads);
        all.add(returnStation);
        return all;
    }

    /** Builds the cached path, chair count and station intervals; throws if the nodes cannot make a path. */
    public void rebuild() {
        List<V3> nodes = new ArrayList<>();
        for (BlockPos p : nodePositions()) nodes.add(node(p));
        this.path = LiftPath.of(nodes);
        this.spacing = AlpineConfig.LIFT_SPACING.get();
        this.chairCount = LiftPath.chairCount(path.loopLength(), spacing);
        this.stations = LiftStations.compute(path, drive.getY());
    }

    @Override
    public double travel() {
        return travel;
    }

    @Override
    public LiftPath path() {
        return path;
    }

    @Override
    public int chairCount() {
        return chairCount;
    }

    public int spacing() {
        return spacing;
    }

    public LiftStations.Result stations() {
        return stations;
    }

    /** Ticks this line has been stepped since it was loaded or created. */
    public long age() {
        return age;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.put("Drive", NbtUtils.writeBlockPos(drive));
        tag.put("Return", NbtUtils.writeBlockPos(returnStation));
        ListTag list = new ListTag();
        for (BlockPos h : heads) list.add(NbtUtils.writeBlockPos(h));
        tag.put("Heads", list);
        tag.putDouble("Travel", travel);
        tag.putDouble("Speed", speed);
        tag.putDouble("Target", targetSpeed);
        tag.putBoolean("Powered", powered);
        tag.putBoolean("Faulted", faulted);
        tag.putBoolean("Emergency", emergency);
        return tag;
    }

    public static LiftLine load(CompoundTag tag) {
        List<BlockPos> heads = new ArrayList<>();
        for (Tag t : tag.getList("Heads", Tag.TAG_COMPOUND)) heads.add(NbtUtils.readBlockPos((CompoundTag) t));
        LiftLine line = new LiftLine(tag.getUUID("Id"), NbtUtils.readBlockPos(tag.getCompound("Drive")), heads,
                NbtUtils.readBlockPos(tag.getCompound("Return")));
        line.travel = tag.getDouble("Travel");
        line.speed = tag.getDouble("Speed");
        line.targetSpeed = tag.getDouble("Target");
        line.powered = tag.getBoolean("Powered");
        line.faulted = tag.getBoolean("Faulted");
        line.emergency = tag.getBoolean("Emergency");
        return line;
    }
}
