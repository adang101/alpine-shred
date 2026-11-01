package io.github.adang101.alpineshred.lift;

import io.github.adang101.alpineshred.config.AlpineConfig;
import io.github.adang101.alpineshred.mixin.MobAccessor;
import io.github.adang101.alpineshred.park.V3;
import net.minecraft.core.BlockPos;
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
        if (!level().isClientSide && passenger instanceof Mob mob) {
            originalPersistence.putIfAbsent(mob.getUUID(), mob.isPersistenceRequired());
            ((MobAccessor) mob).alpineshred$setPersistenceRequired(true);
        }
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (!level().isClientSide && passenger instanceof Mob mob) {
            Boolean original = originalPersistence.remove(mob.getUUID());
            if (original != null) ((MobAccessor) mob).alpineshred$setPersistenceRequired(original);
        }
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
        CompoundTag saved = new CompoundTag();
        for (Map.Entry<UUID, Boolean> e : originalPersistence.entrySet()) saved.putBoolean(e.getKey().toString(), e.getValue());
        tag.put("Persistence", saved);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }
}
