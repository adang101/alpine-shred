package io.github.adang101.alpineshred.lift;

import io.github.adang101.alpineshred.config.AlpineConfig;
import io.github.adang101.alpineshred.mixin.MobAccessor;
import io.github.adang101.alpineshred.park.V3;
import io.github.adang101.alpineshred.movement.RideServer;
import io.github.adang101.alpineshred.movement.RideSnapshot;
import io.github.adang101.alpineshred.movement.RideState;
import io.github.adang101.alpineshred.movement.RideTransitions;
import io.github.adang101.alpineshred.movement.Riders;
import io.github.adang101.alpineshred.registry.ModStats;
import io.github.adang101.alpineshred.trick.advancement.ModTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * One chair on a lift line (design doc section 13, "Chair entity"). Its place is a function of its line's
 * travel: every tick, on both sides, it sets its position to {@code path.chairPos(travel, index, count)}
 * and its yaw from the cable's tangent, so it never depends on move packets. The server reads the line
 * from {@link LiftManager}; the client asks {@link #clientLineLookup}, which WP5 fills from the client
 * line cache. With no line data a client chair keeps its last position and vanilla move packets move it.
 *
 * <p>An empty chair is never saved (the manager respawns it); a chair with riders is saved with them
 * through vanilla's root-vehicle data. A mob that boards is made persistent and gets its old flag back
 * when it gets off.
 */
public class ChairEntity extends Entity {
    /** Client-side hook: the view of a line by id, or null when the client knows nothing of it. */
    public static volatile Function<UUID, LineView> clientLineLookup = id -> null;

    private static final EntityDataAccessor<Optional<UUID>> DATA_LINE = SynchedEntityData.defineId(ChairEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> DATA_INDEX = SynchedEntityData.defineId(ChairEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_BAR_DOWN = SynchedEntityData.defineId(ChairEntity.class, EntityDataSerializers.BOOLEAN);

    /** Height of a rider's seat above the chair's position (the seat). */
    public static final double SEAT_HEIGHT = 0.1;

    /** Passenger uuid to the persistence flag the mob had before it boarded. */
    private final Map<UUID, Boolean> originalPersistence = new HashMap<>();
    private boolean claimed;

    /** Ticks after the last boarding before the safety bar closes. */
    public static final int BAR_DELAY = 20;
    /** Speed at which an unloaded rider leaves the chair, in blocks per tick, along the cable's direction. */
    public static final double EJECT_SPEED = 0.35;

    /** Loop distance of this chair on the previous server tick, or NaN before the first one. */
    private double lastS = Double.NaN;
    /** Ticks until the bar closes; 0 when no closing is pending. */
    private int barTicks;
    private boolean wasInUnload;
    /** Passenger uuid to the loop distance where the passenger boarded (players only). */
    private final Map<UUID, Double> boardedAt = new HashMap<>();

    public ChairEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_LINE, Optional.empty());
        entityData.define(DATA_INDEX, 0);
        entityData.define(DATA_BAR_DOWN, false);
    }

    public UUID lineId() {
        return entityData.get(DATA_LINE).orElse(null);
    }

    public int chairIndex() {
        return entityData.get(DATA_INDEX);
    }

    public boolean barDown() {
        return entityData.get(DATA_BAR_DOWN);
    }

    public void setBarDown(boolean down) {
        entityData.set(DATA_BAR_DOWN, down);
    }

    public void setLine(UUID lineId, int index) {
        entityData.set(DATA_LINE, Optional.ofNullable(lineId));
        entityData.set(DATA_INDEX, index);
    }

    /** Heading of a cable direction as a Minecraft yaw in degrees; null when it points (almost) straight up or down. */
    public static Float yawOf(V3 tangent) {
        if (tangent.x() * tangent.x() + tangent.z() * tangent.z() < 1.0e-9) return null;
        return (float) Math.toDegrees(Math.atan2(-tangent.x(), tangent.z()));
    }

    /** Puts this chair where {@code view} says chair {@link #chairIndex()} is, facing the way the cable runs. */
    public void place(LineView view) {
        LiftPath path = view.path();
        int index = chairIndex(), count = view.chairCount();
        V3 p = path.chairPos(view.travel(), index, count);
        Float yaw = yawOf(path.tangent(path.chairS(view.travel(), index, count)));
        setPos(p.x(), p.y(), p.z());
        if (yaw != null) setYRot(yaw);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            UUID id = lineId();
            LineView view = id == null ? null : clientLineLookup.apply(id);
            if (view != null && chairIndex() < view.chairCount()) place(view);
            return;
        }
        ServerLevel level = (ServerLevel) level();
        if (!claimed) {
            claimed = true;
            if (!LiftChairs.claim(level, this)) return;
        }
        UUID id = lineId();
        LiftLine line = id == null ? null : LiftManager.get(level, id);
        if (line == null || chairIndex() >= line.chairCount()) {
            retire(level, line);
            return;
        }
        place(line);
        rideTick(level, line);
    }

    /** The server's per-tick riding logic: the bar, the unload stretch and the ejection point. */
    private void rideTick(ServerLevel level, LiftLine line) {
        double s = line.path().chairS(line.travel, chairIndex(), line.chairCount());
        if (getPassengers().isEmpty()) {
            wasInUnload = false;
            barTicks = 0;
        } else {
            LiftStations.Result stations = line.stations();
            boolean inUnload = stations.inUnload(s);
            if (inUnload && !wasInUnload) {
                barTicks = 0;
                setBarDown(false);
            }
            wasInUnload = inUnload;
            if (barTicks > 0 && --barTicks == 0) setBarDown(true);
            if (EjectCrossing.shouldEject(lastS, s, stations.ejectS(), line.path().returnStart(), line.path().loopLength())) {
                unload(level, line, s);
            }
        }
        lastS = s;
    }

    /**
     * Ejects every passenger at the top (design doc section 13, "Unloading"). Players go to the platform's
     * ground point, with {@link #EJECT_SPEED} along the cable's horizontal heading, in {@code GLIDE} when they
     * were strapped, and a completed ride is counted; mobs go to the ground with no speed. The ridden length
     * is the loaded cable from where the player boarded to the ejection point.
     */
    private void unload(ServerLevel level, LiftLine line, double s) {
        V3 tangent = line.path().tangent(s);
        double ejectS = line.stations().ejectS();
        List<Entity> riders = new ArrayList<>(getPassengers());
        Map<UUID, Double> boarded = new HashMap<>(boardedAt);
        ejectPassengers();
        BlockPos from = BlockPos.containing(getX(), Math.max(getY(), line.drive.getY() + 1), getZ());
        Float yaw = yawOf(tangent);
        for (Entity rider : riders) {
            LiftChairs.putOnGround(level, rider, from, line.drive);
            if (rider instanceof ServerPlayer player) {
                player.setDeltaMovement(tangent.x() * EJECT_SPEED, 0, tangent.z() * EJECT_SPEED);
                player.hurtMarked = true;
                RideSnapshot now = Riders.get(player);
                if (now.state().isStrapped()) {
                    double speed = Math.hypot(tangent.x(), tangent.z()) * EJECT_SPEED;
                    Riders.set(player, now.withState(RideState.GLIDE).withMotion(speed, yaw != null ? yaw : player.getYRot(), 0));
                    RideServer.reset(player);
                }
                double boardS = boarded.getOrDefault(player.getUUID(), line.stations().boardStart());
                int ridden = (int) Math.floor(ejectS - boardS);
                if (ridden > 0) {
                    ModStats.award(player, ModStats.LIFT_RIDES, 1);
                    ModTriggers.LIFT_RIDDEN.trigger(player, ridden);
                }
            } else {
                rider.setDeltaMovement(Vec3.ZERO);
                if (rider instanceof Mob mob) mob.getNavigation().stop();
            }
        }
    }

    // ---- right-click ----

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.isSpectator()) return InteractionResult.PASS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.sidedSuccess(level().isClientSide);
        if (hasPassenger(sp)) {
            barTicks = 0;
            setBarDown(!barDown());
            sp.displayClientMessage(Component.translatable(barDown() ? "message.alpineshred.lift.bar_down" : "message.alpineshred.lift.bar_up"), true);
        } else if (sp.getMainHandItem().is(Items.LEAD) || sp.getOffhandItem().is(Items.LEAD)) {
            if (Boarding.distance(sp, this) > Boarding.MANUAL_REACH) {
                sp.displayClientMessage(Component.translatable("message.alpineshred.lift.board_too_far"), true);
            } else {
                Boarding.boardLeashed(sp, this);
            }
        } else {
            Boarding.rightClick(sp, this);
        }
        return InteractionResult.sidedSuccess(false);
    }

    // ---- dismounting ----

    /** Blocks between the seat and the ground below it, or 99 when there is none within 5 blocks. */
    public double heightAboveGround() {
        int x = Mth.floor(getX()), z = Mth.floor(getZ());
        double y = getY();
        int start = Mth.floor(y);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int by = start; by >= start - 5 && by >= level().getMinBuildHeight(); by--) {
            p.set(x, by, z);
            VoxelShape shape = level().getBlockState(p).getCollisionShape(level(), p);
            if (!shape.isEmpty()) return y - (by + shape.max(Direction.Axis.Y));
        }
        return 99;
    }

    /** Whether a rider may leave by sneaking: {@link DismountRule}, the same on client and server. */
    public boolean dismountAllowed() {
        UUID id = lineId();
        boolean faulted;
        if (level().isClientSide) {
            LineView view = id == null ? null : clientLineLookup.apply(id);
            faulted = view != null && view.faulted();
        } else {
            LiftLine line = id == null ? null : LiftManager.get((ServerLevel) level(), id);
            faulted = line == null || line.faulted; // nothing is driving a chair whose line is gone
        }
        return DismountRule.allowed(barDown(), heightAboveGround(), faulted, AlpineConfig.LIFT_DISMOUNT_ANYWHERE.on());
    }

    /** The line is gone or this index no longer exists: put riders on safe ground, then go. */
    private void retire(ServerLevel level, LiftLine line) {
        List<Entity> riders = new ArrayList<>(getPassengers());
        BlockPos from = blockPosition();
        ejectPassengers();
        for (Entity rider : riders) LiftChairs.putOnGround(level, rider, from, line == null ? null : line.drive);
        discard();
    }

    // ---- interaction and physics: none ----

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean canBeHitByProjectile() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source) {
        return true;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    // ---- seats ----

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return PassengerRule.hasFreeSeat(getPassengers().size(), AlpineConfig.LIFT_SEATS.get()) && PassengerRule.allows(passenger);
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction move) {
        if (!hasPassenger(passenger)) return;
        int seats = Math.max(1, AlpineConfig.LIFT_SEATS.get());
        double offset = PassengerRule.seatOffset(Math.max(0, getPassengers().indexOf(passenger)), seats);
        double yaw = Math.toRadians(getYRot());
        // Sideways is perpendicular to the facing (-sin yaw, cos yaw).
        move.accept(passenger, getX() + Math.cos(yaw) * offset, getY() + SEAT_HEIGHT + passenger.getMyRidingOffset(), getZ() + Math.sin(yaw) * offset);
        if (!(passenger instanceof Player)) {
            passenger.setYRot(getYRot());
            passenger.setYHeadRot(getYRot());
            if (passenger instanceof LivingEntity living) living.setYBodyRot(getYRot());
        }
    }

    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        if (!level().isClientSide && passenger instanceof ServerPlayer player) {
            if (!barDown()) barTicks = BAR_DELAY;
            double s = currentS();
            if (!Double.isNaN(s)) boardedAt.putIfAbsent(player.getUUID(), s);
            RideSnapshot now = Riders.get(player);
            if (RideTransitions.serverMay(now.state(), RideState.LIFT, true)) Riders.set(player, now.withState(RideState.LIFT));
            Boarding.petsFollow(player, this);
        }
        if (!level().isClientSide && passenger instanceof Mob mob) {
            originalPersistence.putIfAbsent(mob.getUUID(), mob.isPersistenceRequired());
            ((MobAccessor) mob).alpineshred$setPersistenceRequired(true);
        }
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (!level().isClientSide && passenger instanceof ServerPlayer player) {
            boardedAt.remove(player.getUUID());
            RideSnapshot now = Riders.get(player);
            if (now.state() == RideState.LIFT) Riders.set(player, now.withState(RideState.IDLE));
        }
        if (!level().isClientSide && passenger instanceof Mob mob) {
            Boolean original = originalPersistence.remove(mob.getUUID());
            if (original != null) ((MobAccessor) mob).alpineshred$setPersistenceRequired(original);
        }
    }

    /** This chair's loop distance on the server now, or NaN with no live line. */
    private double currentS() {
        if (level().isClientSide || lineId() == null) return Double.NaN;
        LiftLine line = LiftManager.get((ServerLevel) level(), lineId());
        return line == null ? Double.NaN : line.path().chairS(line.travel, chairIndex(), line.chairCount());
    }

    /** The persistence flag a boarded mob had before boarding, or null when it is not aboard. For tests. */
    public Boolean originalPersistence(UUID mob) {
        return originalPersistence.get(mob);
    }

    // ---- saving ----

    @Override
    public boolean shouldBeSaved() {
        return !getPassengers().isEmpty() && super.shouldBeSaved();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setLine(tag.hasUUID("Line") ? tag.getUUID("Line") : null, tag.getInt("Index"));
        setBarDown(tag.getBoolean("BarDown"));
        barTicks = tag.getInt("BarTicks");
        boardedAt.clear();
        CompoundTag boarded = tag.getCompound("BoardedAt");
        for (String key : boarded.getAllKeys()) {
            try {
                boardedAt.put(UUID.fromString(key), boarded.getDouble(key));
            } catch (IllegalArgumentException ignored) {
                // Not ours.
            }
        }
        originalPersistence.clear();
        CompoundTag saved = tag.getCompound("Persistence");
        for (String key : saved.getAllKeys()) {
            try {
                originalPersistence.put(UUID.fromString(key), saved.getBoolean(key));
            } catch (IllegalArgumentException ignored) {
                // A key that is not a uuid is not ours.
            }
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        UUID id = lineId();
        if (id != null) tag.putUUID("Line", id);
        tag.putInt("Index", chairIndex());
        tag.putBoolean("BarDown", barDown());
        tag.putInt("BarTicks", barTicks);
        CompoundTag boarded = new CompoundTag();
        for (Map.Entry<UUID, Double> e : boardedAt.entrySet()) boarded.putDouble(e.getKey().toString(), e.getValue());
        tag.put("BoardedAt", boarded);
        CompoundTag saved = new CompoundTag();
        for (Map.Entry<UUID, Boolean> e : originalPersistence.entrySet()) saved.putBoolean(e.getKey().toString(), e.getValue());
        tag.put("Persistence", saved);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }
}
