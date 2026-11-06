package io.github.adang101.alpineshred.lift;

import io.github.adang101.alpineshred.park.V3;
import io.github.adang101.alpineshred.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Spawns and despawns lift chairs (design doc section 13). Every {@link #INTERVAL} ticks, for each
 * line: if a player is within {@link #SPAWN_RADIUS} blocks of any <em>node</em> of the line, every
 * missing chair whose position is in an entity-ticking chunk is spawned; if no player is within
 * {@link #DESPAWN_RADIUS} blocks of any node, its empty chairs are removed. Nodes are used rather than
 * chair positions because a line has a few dozen nodes but hundreds of chairs, and a chair is never
 * farther than half a span (at most {@code lift.maxSpan}) from one.
 *
 * <p>Existing chairs are tracked per level, by line and index, in a map of the chair entities
 * themselves: spawned chairs enter it at once, and chairs the game loads (a rider's saved vehicle)
 * enter it through {@link #claim} on their first tick. A removed entity in the map counts as missing.
 */
public final class LiftChairs {
    public static final int INTERVAL = 20;
    public static final double SPAWN_RADIUS = 128;
    public static final double DESPAWN_RADIUS = 192;
    /** How far down {@link #findGround} looks, in blocks. */
    public static final int GROUND_SEARCH = 64;

    private record Key(UUID line, int index) {
    }

    private static final Map<ServerLevel, Map<Key, ChairEntity>> CHAIRS = new WeakHashMap<>();
    private static final Map<ServerLevel, int[]> COUNTERS = new WeakHashMap<>();

    private LiftChairs() {
    }

    private static Map<Key, ChairEntity> chairs(ServerLevel level) {
        return CHAIRS.computeIfAbsent(level, l -> new HashMap<>());
    }

    /** Called from {@link LiftManager#tick}: runs {@link #update} once every {@link #INTERVAL} calls. */
    static void tick(ServerLevel level, Collection<LiftLine> lines) {
        int[] counter = COUNTERS.computeIfAbsent(level, l -> new int[1]);
        if (++counter[0] % INTERVAL != 0) return;
        update(level, lines);
    }

    /** One spawn and despawn pass. Public so tests can run it directly. */
    public static void update(ServerLevel level, Collection<LiftLine> lines) {
        update(level, lines, level.players());
    }

    /** As {@link #update(ServerLevel, Collection)}, but distance is measured only to {@code players}. */
    public static void update(ServerLevel level, Collection<LiftLine> lines, List<? extends Player> players) {
        Map<Key, ChairEntity> map = chairs(level);
        for (Iterator<ChairEntity> it = map.values().iterator(); it.hasNext(); ) {
            if (it.next().isRemoved()) it.remove();
        }
        for (LiftLine line : lines) {
            double near = nearestPlayerSqr(players, line);
            int count = line.chairCount();
            if (near <= SPAWN_RADIUS * SPAWN_RADIUS) {
                for (int i = 0; i < count; i++) {
                    if (map.containsKey(new Key(line.id, i))) continue;
                    V3 p = line.path().chairPos(line.travel, i, count);
                    if (!level.isPositionEntityTicking(BlockPos.containing(p.x(), p.y(), p.z()))) continue;
                    spawn(level, line, i);
                }
            } else if (near > DESPAWN_RADIUS * DESPAWN_RADIUS) {
                for (int i = 0; i < count; i++) {
                    Key key = new Key(line.id, i);
                    ChairEntity chair = map.get(key);
                    if (chair != null && chair.getPassengers().isEmpty()) {
                        chair.discard();
                        map.remove(key);
                    }
                }
            }
        }
    }

    /** Squared distance from the closest player of {@code players} to the closest node of {@code line}. */
    private static double nearestPlayerSqr(List<? extends Player> players, LiftLine line) {
        double best = Double.MAX_VALUE;
        for (Player player : players) {
            for (BlockPos node : line.nodePositions()) {
                best = Math.min(best, player.distanceToSqr(node.getX() + 0.5, node.getY() + 0.5, node.getZ() + 0.5));
            }
        }
        return best;
    }

    /** Spawns chair {@code index} of {@code line} unless one exists; returns the chair, or null. */
    public static ChairEntity spawn(ServerLevel level, LiftLine line, int index) {
        Key key = new Key(line.id, index);
        ChairEntity existing = chairs(level).get(key);
        if (existing != null && !existing.isRemoved()) return null;
        ChairEntity chair = new ChairEntity(ModEntities.LIFT_CHAIR.get(), level);
        chair.setLine(line.id, index);
        chair.place(line);
        if (!level.addFreshEntity(chair)) return null;
        chairs(level).put(key, chair);
        return chair;
    }

    /** The tracked chair for a line and index, or null. */
    public static ChairEntity find(ServerLevel level, UUID line, int index) {
        ChairEntity chair = chairs(level).get(new Key(line, index));
        return chair == null || chair.isRemoved() ? null : chair;
    }

    /**
     * Registers a chair the game loaded. If the slot already has a chair, the one with riders wins and
     * the other is discarded. Returns false when {@code chair} lost and is gone.
     */
    static boolean claim(ServerLevel level, ChairEntity chair) {
        UUID line = chair.lineId();
        if (line == null) return true;
        Key key = new Key(line, chair.chairIndex());
        Map<Key, ChairEntity> map = chairs(level);
        ChairEntity other = map.get(key);
        if (other == null || other.isRemoved() || other == chair) {
            map.put(key, chair);
            return true;
        }
        boolean mine = !chair.getPassengers().isEmpty(), theirs = !other.getPassengers().isEmpty();
        if (mine && !theirs) {
            other.discard();
            map.put(key, chair);
            return true;
        }
        if (mine) return true; // two loaded chairs with riders: keep both rather than drop anyone
        chair.discard();
        return false;
    }

    // ---- safe ground ----

    /**
     * Moves {@code rider} to the nearest safe ground below {@code from} (see {@link #findGround}), else
     * above {@code fallback} (the line's drive) when given, else the level spawn.
     */
    static void putOnGround(ServerLevel level, Entity rider, BlockPos from, BlockPos fallback) {
        BlockPos spot = findGround(level, from);
        if (spot == null) spot = fallback != null ? fallback.above() : level.getSharedSpawnPos();
        double x = spot.getX() + 0.5, y = spot.getY(), z = spot.getZ() + 0.5;
        if (rider instanceof ServerPlayer player) {
            player.teleportTo(level, x, y, z, player.getYRot(), player.getXRot());
        } else {
            rider.setPos(x, y, z);
        }
    }

    /**
     * The block to stand in: searching down from {@code from} at most {@link #GROUND_SEARCH} blocks, the
     * first solid block with two blocks of air above it; the result is the block above that one. Null if none.
     */
    public static BlockPos findGround(ServerLevel level, BlockPos from) {
        int bottom = Math.max(level.getMinBuildHeight(), from.getY() - GROUND_SEARCH);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = Math.min(from.getY(), level.getMaxBuildHeight() - 3); y >= bottom; y--) {
            p.set(from.getX(), y, from.getZ());
            BlockState floor = level.getBlockState(p);
            if (floor.getCollisionShape(level, p).isEmpty()) continue;
            if (!level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()) continue;
            if (!level.getBlockState(p.above(2)).getCollisionShape(level, p.above(2)).isEmpty()) continue;
            return p.above().immutable();
        }
        return null;
    }
}
