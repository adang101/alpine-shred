package io.github.adang101.alpineshred.movement;

import net.minecraft.world.entity.player.Player;

/**
 * The decisions the movement mixins ask for, in one place. Common code; the client registers a
 * {@link ClientDriver} that owns the local player's {@link SlideController}.
 *
 * <h2>Server replay rule (risk R2)</h2>
 * The vanilla server replays every client move packet with {@code Entity.move} and rubber-bands
 * the player when its result lands more than 0.25 blocks from the client's claim. So the server
 * replay must add nothing the client did not do:
 * <ul>
 *   <li>Edge back-off: never on the server for strapped riders. Back-off only shortens a move, and
 *       the client's claim already includes any back-off the client applied.</li>
 *   <li>Step height: vanilla on the server. A step-up the client made arrives as an upward delta,
 *       which the replay (vertical axis first) follows without stepping; a raised server step would
 *       only climb walls the client stopped at.</li>
 *   <li>Powder: walkable only for the controller that floats. The replay of a floating rider has no
 *       downward component left, so it never enters the powder either way.</li>
 * </ul>
 * The game test {@code vanillaAcceptsARideAtTheSpeedCap} drives the real packet handler to keep
 * these true.
 */
public final class RideHooks {

    /** Runs the local player's slide controller. Registered by the client. */
    public interface ClientDriver {
        /** Moves the player for this tick; false lets vanilla travel run. */
        boolean travel(Player player);

        boolean isLocal(Player player);

        /** Pole push on the local controller; true if it pushed. */
        boolean polePush(Player player);
    }

    private static volatile ClientDriver client;

    private RideHooks() {
    }

    public static void setClientDriver(ClientDriver driver) {
        client = driver;
    }

    /** Player.travel HEAD. Only the local player on its own client is ever driven. */
    public static boolean travel(Player player) {
        ClientDriver driver = client;
        return driver != null && player.level().isClientSide && driver.isLocal(player) && driver.travel(player);
    }

    private static RideStateHolder holder(Player player) {
        return (RideStateHolder) player;
    }

    /**
     * Where a controller moves this player: the flag its runner sets. On the server, also every
     * strapped rider, since the server never runs the controller for real players.
     */
    public static boolean controllerMovement(Player player) {
        if (holder(player).alpineshred$isControlled()) return true;
        return !player.level().isClientSide && Riders.state(player).isControllerDriven();
    }

    /** Edge back-off is off wherever a controller moves the player (see the class comment). */
    public static boolean suppressEdgeBackOff(Player player) {
        return controllerMovement(player);
    }

    /** Raised step height for controller moves, or 0 to keep vanilla's. Never raised on the server's replay. */
    public static float stepHeight(Player player) {
        RideStateHolder h = holder(player);
        return h.alpineshred$isControlled() ? h.alpineshred$rideStep() : 0;
    }

    /** Vanilla's jump is replaced by the pop where a controller runs. */
    public static boolean suppressVanillaJump(Player player) {
        return holder(player).alpineshred$isControlled();
    }

    /** Powder snow is solid only for a controller riding fast enough over it (section 7, "Powder"). */
    public static boolean floatsOnPowder(Player player) {
        return holder(player).alpineshred$floatsOnPowder();
    }

    /**
     * No footsteps while sliding: the local controller, or (as heard by others, from the server's
     * replay) a rider whose synced state is moving rather than idle.
     */
    public static boolean silentFeet(Player player) {
        if (holder(player).alpineshred$isControlled()) return true;
        if (player.level().isClientSide) return false;
        RideState state = Riders.state(player);
        return state.isControllerDriven() && state != RideState.IDLE;
    }

    /** Pole push from {@code SkiPolesItem.use} on the client. */
    public static boolean polePushLocal(Player player) {
        ClientDriver driver = client;
        return driver != null && player.level().isClientSide && driver.isLocal(player) && driver.polePush(player);
    }
}
