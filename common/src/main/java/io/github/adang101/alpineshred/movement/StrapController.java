package io.github.adang101.alpineshred.movement;

import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.EntityEvent;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import io.github.adang101.alpineshred.AlpineShred;
import io.github.adang101.alpineshred.gear.GearProfile;
import io.github.adang101.alpineshred.gear.GearProfiles;
import io.github.adang101.alpineshred.registry.ModSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.Locale;
import java.util.UUID;

/**
 * Server authority for strapping in and out (design doc section 6). Clients request a toggle with
 * {@code c2s/strap_toggle}; the server checks {@link StrapCheck}, writes the synced ride state, and
 * auto-unstraps on fluid, vehicles, death, dimension change, gear removal, spectator and elytra.
 *
 * <p>While strapped, the -35% walk modifier stays on and sprinting is blocked (see
 * {@code LivingEntityMixin}). The modifier only matters when vanilla moves the rider, which is when
 * the slide controller is walking (off snow, or sinking in powder); while sliding, the controller
 * ignores the movement-speed attribute. Riding itself is validated by {@link RideServer}.
 */
public final class StrapController {
    /** Stable id so the modifier can be found and removed, and never stacks. */
    public static final UUID WALK_MODIFIER_ID = UUID.fromString("6f3c2b8e-1d4a-4b7e-9c2f-5a8d7e6b4c31");
    public static final String WALK_MODIFIER_NAME = "alpineshred:strapped_walk";

    public enum UnstrapReason {
        USER, COMMAND, FLUID, VEHICLE, DEATH, DIMENSION, GEAR_REMOVED, SPECTATOR, ELYTRA, DISABLED;

        /** Action-bar message for automatic unstraps; null for ones the player asked for. */
        public String messageKey() {
            return switch (this) {
                case USER, COMMAND, DEATH, DIMENSION -> null;
                default -> "message.alpineshred.unstrap." + name().toLowerCase(Locale.ROOT);
            };
        }
    }

    private StrapController() {
    }

    public static void register() {
        TickEvent.PLAYER_POST.register(player -> {
            if (player instanceof ServerPlayer serverPlayer) serverTick(serverPlayer);
        });
        PlayerEvent.CHANGE_DIMENSION.register((player, oldLevel, newLevel) -> unstrap(player, UnstrapReason.DIMENSION));
        PlayerEvent.PLAYER_QUIT.register(GearProfiles::forget);
        EntityEvent.LIVING_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player) unstrap(player, UnstrapReason.DEATH);
            return EventResult.pass();
        });
    }

    /** Handles {@code c2s/strap_toggle}. {@code wanted} is the state the client asked for. */
    public static void handleToggle(ServerPlayer player, boolean wanted) {
        boolean strapped = Riders.isStrapped(player);
        if (wanted && !strapped) {
            StrapCheck.Result result = tryStrap(player);
            if (!result.ok()) player.displayClientMessage(Component.translatable(result.translationKey()), true);
        } else if (!wanted && strapped) {
            unstrap(player, UnstrapReason.USER);
        }
    }

    /** Straps the player in if every precondition passes. Works for any {@link Player} (game tests use mock players). */
    public static StrapCheck.Result tryStrap(Player player) {
        GearProfiles.invalidate(player);
        GearProfile gear = GearProfiles.get(player);
        StrapCheck.Result result = StrapCheck.check(player, gear);
        if (!result.ok()) return result;

        RideSnapshot snapshot = RideSnapshot.OFF
                .withState(RideState.IDLE)
                .withMode(gear.kind().rideMode())
                .withMotion(0, player.getYRot(), 0);
        Riders.set(player, snapshot);
        RideServer.reset(player);
        applyWalkModifier(player, true);
        player.setSprinting(false);
        playSound(player, true);
        player.displayClientMessage(Component.translatable("message.alpineshred.strap.in"), true);
        return result;
    }

    public static void unstrap(Player player, UnstrapReason reason) {
        if (!Riders.isStrapped(player)) return;
        Riders.set(player, RideSnapshot.OFF);
        RideServer.reset(player);
        applyWalkModifier(player, false);
        if (reason != UnstrapReason.DEATH) {
            playSound(player, false);
            String key = reason.messageKey();
            player.displayClientMessage(Component.translatable(key != null ? key : "message.alpineshred.strap.out"), true);
        }
        AlpineShred.LOGGER.debug("Unstrapped {} ({})", player.getName().getString(), reason);
    }

    /** Runs every server tick for every player. Cheap when not strapped. */
    public static void serverTick(Player player) {
        if (!Riders.isStrapped(player)) {
            // Covers relogs and respawns where the transient modifier could otherwise linger.
            if (player.tickCount % 20 == 0) applyWalkModifier(player, false);
            return;
        }
        UnstrapReason reason = autoUnstrapReason(player);
        if (reason != null) {
            unstrap(player, reason);
            return;
        }
        applyWalkModifier(player, true);
        if (player.isSprinting()) player.setSprinting(false);
        RideServer.tick(player);
    }

    /** The first auto-unstrap condition that holds, or null to stay strapped. */
    public static UnstrapReason autoUnstrapReason(Player player) {
        if (!StrapCheck.ridingEnabled()) return UnstrapReason.DISABLED;
        if (player.isSpectator()) return UnstrapReason.SPECTATOR;
        if (player.isInWater() || player.isInLava()) return UnstrapReason.FLUID;
        if (player.isPassenger()) return UnstrapReason.VEHICLE;
        if (player.isFallFlying()) return UnstrapReason.ELYTRA;
        GearProfile gear = GearProfiles.get(player);
        RideMode mode = Riders.get(player).mode();
        if (!gear.canRide() || gear.kind().rideMode() != mode) return UnstrapReason.GEAR_REMOVED;
        return null;
    }

    /** Adds or removes the transient -35% walk modifier ({@code strappedWalkMod}). Idempotent. */
    public static void applyWalkModifier(Player player, boolean on) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        AttributeModifier existing = speed.getModifier(WALK_MODIFIER_ID);
        double amount = TuningData.tuning().strappedWalkMod;
        if (on) {
            if (existing != null && existing.getAmount() == amount) return;
            if (existing != null) speed.removeModifier(WALK_MODIFIER_ID);
            speed.addTransientModifier(new AttributeModifier(WALK_MODIFIER_ID, WALK_MODIFIER_NAME, amount,
                    AttributeModifier.Operation.MULTIPLY_TOTAL));
        } else if (existing != null) {
            speed.removeModifier(WALK_MODIFIER_ID);
        }
    }

    private static void playSound(Player player, boolean in) {
        if (player.level().isClientSide) return;
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                (in ? ModSounds.STRAP_IN : ModSounds.STRAP_OUT).get(), SoundSource.PLAYERS, 0.9f, 1.0f);
    }
}
