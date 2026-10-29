package io.github.adang101.alpineshred.movement;

import dev.architectury.event.events.common.PlayerEvent;
import io.github.adang101.alpineshred.AlpineShred;
import io.github.adang101.alpineshred.config.AlpineConfig;
import io.github.adang101.alpineshred.gear.GearAccess;
import io.github.adang101.alpineshred.gear.GearKind;
import io.github.adang101.alpineshred.gear.GearNbt;
import io.github.adang101.alpineshred.gear.GearProfile;
import io.github.adang101.alpineshred.gear.GearProfiles;
import io.github.adang101.alpineshred.gear.GearWear;
import io.github.adang101.alpineshred.mixin.ServerGamePacketListenerAccessor;
import io.github.adang101.alpineshred.network.ModNetwork;
import io.github.adang101.alpineshred.park.block.ParkChecks;
import io.github.adang101.alpineshred.network.RideAction;
import io.github.adang101.alpineshred.trick.GrabType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The server half of riding (design doc sections 7 and 15). The client simulates; the server
 * <ul>
 *   <li>accepts {@code c2s/ride_state} claims that pass {@link RideTransitions} and writes them into
 *       the synced data every client animates from,</li>
 *   <li>feeds each rider's accepted positions to a {@link RideValidator} and corrects riders it
 *       flags (strict mode),</li>
 *   <li>recomputes distance from positions and applies wax, edge and durability wear, so a client
 *       cannot skip it, and</li>
 *   <li>deals wipeout damage from the speed it observed, not the speed a client reports.</li>
 * </ul>
 *
 * <h2>Rider clock</h2>
 * The validator runs on client time: one tick per move packet, read from the vanilla packet
 * counter. After a lag spike a second of packets arrives within one server tick, and on a server
 * below 20 TPS each server tick carries more than one client tick; judged per server tick both
 * would look like speeding. The clock may run at most {@link #CLOCK_BURST_TICKS} ahead of server
 * time, so sending extra packets buys no extra distance.
 */
public final class RideServer {
    public static final int CLOCK_BURST_TICKS = 40;
    public static final int SPEED_SAMPLES = 4;
    public static final int DEBUG_INTERVAL_TICKS = 10;

    private static final Map<Player, Rider> RIDERS = new WeakHashMap<>();

    private RideServer() {
    }

    /** Per-rider server state. Package-private for tests in this package. */
    static final class Rider {
        final RideValidator validator = new RideValidator();
        final GearWear.Accumulator wear = new GearWear.Accumulator();
        PlayerBody body;
        long serverClock;
        long clientClock;
        int lastReceived = -1;
        /** The connection's teleport counter when last sampled: any change is a server teleport. */
        int lastTeleport = Integer.MIN_VALUE;
        boolean hasLast;
        double lastX, lastY, lastZ;
        long lastClockAtSample;
        final double[] speeds = new double[SPEED_SAMPLES];
        int speedIndex;
        long holdUntil = Long.MIN_VALUE / 2;
        long lastPoleClock = Long.MIN_VALUE / 2;
        int violations;
        int corrections;
        String lastReason = "";
        boolean debug;
        int debugCountdown;

        double recentSpeed() {
            double max = 0;
            for (double s : speeds) max = Math.max(max, s);
            return max;
        }
    }

    public static void register() {
        PlayerEvent.PLAYER_QUIT.register(RideServer::forget);
        PlayerEvent.PLAYER_RESPAWN.register((player, conqueredEnd) -> reset(player));
    }

    private static synchronized Rider rider(Player player) {
        return RIDERS.computeIfAbsent(player, p -> new Rider());
    }

    public static synchronized void forget(Player player) {
        RIDERS.remove(player);
    }

    /** Forget movement history (strap in or out, teleport, respawn). Keeps the debug flag. */
    public static synchronized void reset(Player player) {
        Rider old = RIDERS.get(player);
        Rider fresh = new Rider();
        if (old != null) {
            fresh.debug = old.debug;
            fresh.lastReceived = old.lastReceived;
            fresh.lastTeleport = old.lastTeleport;
        }
        RIDERS.put(player, fresh);
    }

    /** {@code movement.validation.strict}: {@code auto} is on for dedicated servers only. */
    public static boolean strict(MinecraftServer server) {
        if (AlpineConfig.MOVEMENT_VALIDATION_STRICT.is("true")) return true;
        if (AlpineConfig.MOVEMENT_VALIDATION_STRICT.is("false")) return false;
        return server != null && server.isDedicatedServer();
    }

    public static RideValidator.Limits limits(Player player) {
        GearProfile gear = GearProfiles.get(player);
        return RideValidator.Limits.of(TuningData.tuning(), gear.stats(), AlpineConfig.MOVEMENT_MAX_SPEED_MUL.value());
    }

    // ---- per tick ---------------------------------------------------------------------------------

    /** Once per server tick for every strapped rider, after the auto-unstrap checks. */
    public static void tick(Player player) {
        Rider r = rider(player);
        advanceClock(player, r);
        RideState state = Riders.state(player);
        if (!state.isControllerDriven()) {
            r.hasLast = false;
            r.validator.reset();
            return;
        }
        if (teleported(player, r)) {
            // A server teleport (command, ender pearl, our own correction) is not rider movement,
            // including one the client already answered this tick (an integrated server's is quick).
            r.hasLast = false;
            r.validator.reset();
            return;
        }
        RideValidator.Limits limits = limits(player);
        boolean onPipe = ParkChecks.nearPipe(player);
        if (onPipe) limits = limits.onPipe();
        // Rails, boxes and pipe arcs carry a rider the server's own collision does not see standing
        // (the controller rides their paths): while one is really there, the rider is supported.
        boolean supported = player.onGround() || switch (state) {
            case GRIND -> ParkChecks.grindableNear(player);
            case SLIDE -> ParkChecks.slideableUnder(player);
            case AIR, WIPEOUT -> false;
            default -> onPipe;
        };
        double x = player.getX(), y = player.getY(), z = player.getZ();
        long clockDelta = r.hasLast ? r.clientClock - r.lastClockAtSample : 1;
        double horizontal = r.hasLast ? Math.hypot(x - r.lastX, z - r.lastZ) : 0;

        RideValidator.Verdict verdict = r.validator.observe(r.clientClock, x, y, z, supported, limits);
        handleVerdict(player, r, verdict);
        String airReason = r.validator.checkAirClaim(state == RideState.AIR, player.onGround());
        if (airReason != null && verdict.kind() != RideValidator.Kind.CORRECT) {
            handleVerdict(player, r, r.validator.flag(r.clientClock, airReason, limits));
        }

        if (r.hasLast && verdict.kind() != RideValidator.Kind.CORRECT) {
            double perTick = horizontal / Math.max(1, clockDelta);
            r.speeds[r.speedIndex] = Math.min(perTick, limits.speedCap());
            r.speedIndex = (r.speedIndex + 1) % SPEED_SAMPLES;
            if (horizontal <= limits.speedCap() * Math.max(1, clockDelta) * 1.5) {
                applyWear(player, r, state, horizontal);
            }
        }
        r.lastX = player.getX();
        r.lastY = player.getY();
        r.lastZ = player.getZ();
        r.lastClockAtSample = r.clientClock;
        r.hasLast = true;

        if (r.debug && player instanceof ServerPlayer sp && --r.debugCountdown <= 0) {
            r.debugCountdown = DEBUG_INTERVAL_TICKS;
            ModNetwork.sendDebugStats(sp, r.validator.recentViolations(), r.corrections, r.recentSpeed(), r.lastReason);
        }
    }

    private static void advanceClock(Player player, Rider r) {
        int packets = 1;
        if (player instanceof ServerPlayer sp && sp.connection != null) {
            int received = ((ServerGamePacketListenerAccessor) sp.connection).alpineshred$receivedMovePackets();
            packets = r.lastReceived < 0 ? 1 : Math.max(0, received - r.lastReceived);
            r.lastReceived = received;
        }
        r.serverClock++;
        r.clientClock = Math.min(r.clientClock + packets, r.serverClock + CLOCK_BURST_TICKS);
    }

    private static boolean teleported(Player player, Rider r) {
        if (!(player instanceof ServerPlayer sp) || sp.connection == null) return false;
        var listener = (ServerGamePacketListenerAccessor) sp.connection;
        int id = listener.alpineshred$awaitingTeleport();
        boolean changed = r.lastTeleport != Integer.MIN_VALUE && id != r.lastTeleport;
        r.lastTeleport = id;
        return changed || listener.alpineshred$awaitingPosition() != null;
    }

    private static void handleVerdict(Player player, Rider r, RideValidator.Verdict verdict) {
        switch (verdict.kind()) {
            case OK -> {
            }
            case VIOLATION -> {
                r.violations++;
                r.lastReason = verdict.reason();
                AlpineShred.LOGGER.debug("Ride violation by {}: {}", player.getName().getString(), verdict.reason());
            }
            case CORRECT -> {
                r.violations++;
                r.lastReason = verdict.reason();
                boolean strict = strict(player.getServer());
                AlpineShred.LOGGER.warn("{} rider {} ({}){}", strict ? "Corrected" : "Would correct (validation not strict)",
                        player.getName().getString(), verdict.reason(), strict ? "" : "; set movement.validation.strict to enforce");
                if (strict) correct(player, r, verdict.x(), verdict.y(), verdict.z());
            }
        }
    }

    /** Validator rule 5: back to the last valid position, in WIPEOUT, with the client told why. */
    private static void correct(Player player, Rider r, double x, double y, double z) {
        r.corrections++;
        if (player instanceof ServerPlayer sp && sp.connection != null) {
            sp.connection.teleport(x, y, z, sp.getYRot(), sp.getXRot());
        } else {
            player.setPos(x, y, z);
        }
        player.setDeltaMovement(0, 0, 0);
        RideSnapshot now = Riders.get(player);
        Riders.set(player, now.withState(RideState.WIPEOUT).withMotion(0, now.heading(), 0));
        r.holdUntil = r.clientClock + TuningData.tuning().wipeoutTicks;
        r.hasLast = false;
        if (player instanceof ServerPlayer sp) ModNetwork.sendRideCorrect(sp, x, y, z, RideState.WIPEOUT);
    }

    // ---- client claims ------------------------------------------------------------------------------

    /** {@code c2s/ride_state}. Returns true if the claim was written into the synced data. */
    public static boolean handleState(Player player, int bits) {
        Rider r = rider(player);
        RideSnapshot current = Riders.get(player);
        RideSnapshot wanted = RideSnapshot.unpack(bits);
        if (r.clientClock < r.holdUntil) return false; // a correction's wipeout runs its course
        if (!RideTransitions.clientMayRequest(current.state(), wanted.state())) {
            // Claims that cross an unstrap in flight are normal; anything else is a lie.
            if (current.state().isStrapped() && current.state() != RideState.LIFT) {
                handleVerdict(player, r, r.validator.flag(r.clientClock,
                        "transition " + current.state() + " -> " + wanted.state(), limits(player)));
            }
            return false;
        }
        if (RideTransitions.needsWorldCheck(wanted.state()) && wanted.state() != current.state()
                && !ParkChecks.supports(player, wanted.state())) {
            handleVerdict(player, r, r.validator.flag(r.clientClock,
                    wanted.state() + " claimed with no " + (wanted.state() == RideState.GRIND ? "rail" : "box") + " there", limits(player)));
            return false;
        }
        // The server owns the mode; grabs and trick flags arrive with phase 5.
        RideSnapshot accepted = new RideSnapshot(wanted.state(), current.mode(), wanted.goofy(), false, GrabType.NONE,
                0, wanted.lean(), wanted.speedLevel(), wanted.headingStep());
        if (wanted.state() == RideState.WIPEOUT && current.state() != RideState.WIPEOUT) {
            wipeoutDamage(player, r.recentSpeed());
        }
        Riders.set(player, accepted);
        return true;
    }

    /** {@code c2s/ride_action}. */
    public static void handleAction(Player player, RideAction action, int value) {
        Rider r = rider(player);
        RideState state = Riders.state(player);
        if (!state.isControllerDriven()) return;
        RideValidator.Limits limits = limits(player);
        if (ParkChecks.nearPipe(player)) limits = limits.onPipe(); // a pipe's top launches at up to the speed cap
        MovementTuning t = TuningData.tuning();
        switch (action) {
            case POP -> {
                int charge = Math.max(0, Math.min(value, t.popChargeTicks));
                r.validator.noteLaunch(r.clientClock, SlideModel.popVelocity(t, GearProfiles.get(player).stats(), charge), limits);
            }
            case LAUNCH -> r.validator.noteRampLaunch(r.clientClock, Math.max(0, value) / 1000.0, limits);
            case POLE_PUSH -> {
                GearProfile gear = GearProfiles.get(player);
                if (gear.kind() != GearKind.SKIS || !gear.poles()) {
                    handleVerdict(player, r, r.validator.flag(r.clientClock, "pole push without skis and poles", limits));
                    return;
                }
                // One tick of slack for packet timing.
                if (r.clientClock - r.lastPoleClock < t.poleCooldownTicks - 1) {
                    handleVerdict(player, r, r.validator.flag(r.clientClock, "pole push cooldown", limits));
                    return;
                }
                r.lastPoleClock = r.clientClock;
                ItemStack poles = player.getMainHandItem();
                if (player instanceof ServerPlayer && !player.getAbilities().instabuild) {
                    poles.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(InteractionHand.MAIN_HAND));
                }
            }
        }
    }

    /** A rate-limited packet was dropped (section 15). The limiter calls this at most once per second. */
    public static void flagDroppedPacket(Player player, String packet) {
        Rider r = rider(player);
        if (!Riders.state(player).isControllerDriven()) return;
        handleVerdict(player, r, r.validator.flag(r.clientClock, "rate limit " + packet, limits(player)));
    }

    // ---- damage and wear --------------------------------------------------------------------------

    /** {@code (impact - wallHitSpeed) * 8}, 40% less with a helmet (section 7, "Wipeout"). */
    public static float wipeoutDamage(double impactSpeed, boolean helmet) {
        return (float) SlideModel.wipeoutDamage(TuningData.tuning(), impactSpeed, helmet);
    }

    private static void wipeoutDamage(Player player, double observedSpeed) {
        float damage = wipeoutDamage(observedSpeed, GearProfiles.get(player).helmet());
        if (damage >= 0.5f) player.hurt(player.damageSources().flyIntoWall(), damage);
    }

    private static void applyWear(Player player, Rider r, RideState state, double distance) {
        if (distance <= 0 || !player.onGround() || state == RideState.AIR) return;
        if (player.getAbilities().instabuild) return;
        ItemStack board = GearAccess.current().board(player).orElse(ItemStack.EMPTY);
        if (board.isEmpty()) return;
        if (r.body == null || r.body.player() != player) r.body = new PlayerBody(player);
        SurfaceProfile surface = r.body.surfaceUnderFeet();
        GearWear.Contact contact = state == RideState.GRIND || state == RideState.SLIDE ? GearWear.Contact.GRIND
                : surface == null ? GearWear.Contact.AIR
                : surface.category() == SurfaceProfile.Category.SCRAPE ? GearWear.Contact.SCRAPE
                : surface.category() == SurfaceProfile.Category.BOX ? GearWear.Contact.GRIND : GearWear.Contact.SNOW;
        int wax = GearNbt.wax(board);
        GearWear.Accumulator.Wear wear = r.wear.travel(distance, contact, state == RideState.CARVE, wax > 0,
                AlpineConfig.GEAR_DURABILITY_MUL.value());
        if (wear.isEmpty()) return;
        if (wear.waxUsed() > 0) GearNbt.setWax(board, wax - wear.waxUsed());
        if (wear.edgeLoss() > 0) GearNbt.setEdge(board, GearNbt.edge(board) - wear.edgeLoss());
        if (wear.durabilityDamage() > 0 && board.isDamageableItem()
                && board.hurt(wear.durabilityDamage(), player.getRandom(), player instanceof ServerPlayer sp ? sp : null)) {
            board.shrink(1);
            board.setDamageValue(0);
            GearAccess.current().set(player, GearAccess.Slot.BOARD, ItemStack.EMPTY);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_BREAK,
                    SoundSource.PLAYERS, 0.8f, 0.8f + player.level().random.nextFloat() * 0.4f);
        }
        GearProfiles.invalidate(player);
    }

    // ---- debug and inspection ---------------------------------------------------------------------

    public static void setDebug(Player player, boolean on) {
        Rider r = rider(player);
        r.debug = on;
        r.debugCountdown = 0;
    }

    public static synchronized int corrections(Player player) {
        Rider r = RIDERS.get(player);
        return r == null ? 0 : r.corrections;
    }

    public static synchronized int violations(Player player) {
        Rider r = RIDERS.get(player);
        return r == null ? 0 : r.violations;
    }

    public static synchronized String lastReason(Player player) {
        Rider r = RIDERS.get(player);
        return r == null ? "" : r.lastReason;
    }
}
