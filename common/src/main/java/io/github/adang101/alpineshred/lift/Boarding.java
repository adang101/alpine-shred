package io.github.adang101.alpineshred.lift;

import io.github.adang101.alpineshred.config.AlpineConfig;
import io.github.adang101.alpineshred.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-side boarding (design doc section 13, "Boarding" and "Boarding mobs"): the checks behind right-click,
 * auto-board packets, leads and pets. The checks themselves are in {@link BoardingRule}; this class reads the
 * world and the config.
 */
public final class Boarding {
    /** Right-click reach to the chair's seat. */
    public static final double MANUAL_REACH = 3.0;
    /** A lead's reach to the mobs it holds. */
    public static final double LEAD_RADIUS = 6.0;
    /** How far from its owner a pet still follows onto the chair. */
    public static final double PET_RADIUS = 8.0;

    private Boarding() {
    }

    /** How the attempt ended. */
    public enum Result {
        BOARDED, TOO_FAR, FULL, TOO_FAST, NO_PASS, NOT_ALLOWED, NO_LINE;

        public boolean ok() {
            return this == BOARDED;
        }

        public String messageKey() {
            return switch (this) {
                case BOARDED -> null;
                case TOO_FAR -> "message.alpineshred.lift.board_too_far";
                case FULL -> "message.alpineshred.lift.board_full";
                case TOO_FAST -> "message.alpineshred.lift.board_too_fast";
                case NO_PASS -> "message.alpineshred.lift.board_no_pass";
                case NOT_ALLOWED, NO_LINE -> "message.alpineshred.lift.board_refused";
            };
        }
    }

    /** Whether {@code player} carries a lift pass anywhere in the inventory (it is not consumed). */
    public static boolean hasPass(Player player) {
        for (ItemStack stack : player.getInventory().items) if (stack.is(ModItems.LIFT_PASS.get())) return true;
        for (ItemStack stack : player.getInventory().offhand) if (stack.is(ModItems.LIFT_PASS.get())) return true;
        return false;
    }

    /** The line speed in blocks per second, or {@code NaN} when the chair has no live line. */
    private static double lineSpeed(ServerLevel level, ChairEntity chair) {
        LiftLine line = chair.lineId() == null ? null : LiftManager.get(level, chair.lineId());
        return line == null ? Double.NaN : Math.abs(line.speed);
    }

    private static Result map(BoardingRule.Refusal refusal) {
        return switch (refusal) {
            case NONE -> Result.BOARDED;
            case TOO_FAR -> Result.TOO_FAR;
            case FULL -> Result.FULL;
            case TOO_FAST -> Result.TOO_FAST;
            case NO_PASS -> Result.NO_PASS;
        };
    }

    /** The distance from {@code entity}'s body to a chair's seat, measured from the centre of its box. */
    public static double distance(Entity entity, ChairEntity chair) {
        return entity.getBoundingBox().getCenter().distanceTo(chair.position());
    }

    /**
     * Checks everything but the passenger's identity, and boards the player when it all passes. With
     * {@code reach} at {@link #MANUAL_REACH} this is right-click boarding. The player's pets follow through
     * {@link ChairEntity}'s own boarding hook.
     */
    public static Result tryBoard(ServerPlayer player, ChairEntity chair, double reach) {
        if (!(player.level() instanceof ServerLevel level)) return Result.NO_LINE;
        double speed = lineSpeed(level, chair);
        if (Double.isNaN(speed)) return Result.NO_LINE;
        int seats = AlpineConfig.LIFT_SEATS.get();
        BoardingRule.Refusal refusal = BoardingRule.check(distance(player, chair), reach,
                PassengerRule.hasFreeSeat(chair.getPassengers().size(), seats), speed,
                AlpineConfig.LIFT_MAX_BOARD_SPEED.get(), AlpineConfig.LIFT_REQUIRE_PASS.on(), hasPass(player));
        if (refusal != BoardingRule.Refusal.NONE) return map(refusal);
        if (!player.startRiding(chair)) return Result.NOT_ALLOWED;
        return Result.BOARDED;
    }

    /** {@link #tryBoard} for a right-click, telling the player why when it fails. */
    public static Result rightClick(ServerPlayer player, ChairEntity chair) {
        Result result = tryBoard(player, chair, MANUAL_REACH);
        if (!result.ok()) player.displayClientMessage(Component.translatable(result.messageKey()), true);
        return result;
    }

    /** A packet from an auto-boarding client: the same checks with the server's slack, and only for a strapped player. */
    public static Result autoBoard(ServerPlayer player, ChairEntity chair) {
        if (!(player.level() instanceof ServerLevel level) || chair.lineId() == null) return Result.NO_LINE;
        if (player.isPassenger() || !io.github.adang101.alpineshred.movement.Riders.isStrapped(player)) return Result.NOT_ALLOWED;
        LiftLine line = LiftManager.get(level, chair.lineId());
        if (line == null) return Result.NO_LINE;
        double s = line.path().chairS(line.travel, chair.chairIndex(), line.chairCount());
        double dx = player.getX() - chair.getX(), dz = player.getZ() - chair.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz), vertical = chair.getY() - player.getY();
        LiftStations.Result stretch = line.stations();
        // The server accepts a chair a little before or past the stretch, for the client's lag.
        boolean inStretch = s >= stretch.boardStart() - AutoBoard.SERVER_SLACK && s <= stretch.boardEnd() + AutoBoard.SERVER_SLACK;
        if (!AutoBoard.serverAccepts(horizontal, vertical) || !inStretch) return Result.TOO_FAR;
        Result result = tryBoard(player, chair, Double.MAX_VALUE);
        if (result != Result.BOARDED && result != Result.FULL) {
            player.displayClientMessage(Component.translatable(result.messageKey()), true);
        }
        return result;
    }

    // ---- mobs ----

    /**
     * Right-click with a lead: mobs leashed to the player within {@link #LEAD_RADIUS} blocks board up to the
     * free seats and their leads go to the player's inventory. Returns how many boarded.
     */
    public static int boardLeashed(ServerPlayer player, ChairEntity chair) {
        if (!(player.level() instanceof ServerLevel level)) return 0;
        double speed = lineSpeed(level, chair);
        if (Double.isNaN(speed) || speed > AlpineConfig.LIFT_MAX_BOARD_SPEED.get()) {
            player.displayClientMessage(Component.translatable("message.alpineshred.lift.board_too_fast"), true);
            return 0;
        }
        List<Mob> leashed = level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(LEAD_RADIUS),
                m -> m.getLeashHolder() == player && m.distanceTo(player) <= LEAD_RADIUS);
        if (leashed.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.alpineshred.lift.no_leashed"), true);
            return 0;
        }
        int boarded = 0;
        for (Mob mob : leashed) {
            if (!PassengerRule.hasFreeSeat(chair.getPassengers().size(), AlpineConfig.LIFT_SEATS.get())) {
                player.displayClientMessage(Component.translatable("message.alpineshred.lift.board_full"), true);
                break;
            }
            // A mob that cannot board keeps its lead; only a mob that boards hands it back.
            if (!mob.startRiding(chair)) continue;
            mob.dropLeash(true, false);
            ItemStack lead = new ItemStack(Items.LEAD);
            if (!player.getInventory().add(lead)) player.drop(lead, false);
            boarded++;
        }
        if (boarded == 0) player.displayClientMessage(Component.translatable("message.alpineshred.lift.board_refused"), true);
        return boarded;
    }

    /** Tamed pets of {@code owner} within {@link #PET_RADIUS} blocks that are not sitting or already riding. */
    public static List<Mob> petsOf(ServerPlayer owner) {
        List<Mob> pets = new ArrayList<>();
        owner.level().getEntitiesOfClass(Mob.class, owner.getBoundingBox().inflate(PET_RADIUS), m -> {
            if (m.isPassenger() || m.distanceTo(owner) > PET_RADIUS) return false;
            if (!(m instanceof OwnableEntity ownable) || !owner.getUUID().equals(ownable.getOwnerUUID())) return false;
            if (m instanceof TamableAnimal tame) return tame.isTame() && !tame.isOrderedToSit();
            if (m instanceof AbstractHorse horse) return horse.isTamed();
            return true;
        }).forEach(pets::add);
        return pets;
    }

    /** Called when {@code player} has just taken a seat on {@code chair}: their pets follow, up to the free seats. */
    static void petsFollow(ServerPlayer player, ChairEntity chair) {
        if (!AlpineConfig.LIFT_PETS_AUTO_BOARD.on() || !AlpineConfig.LIFT_MOBS_ALLOWED.on()) return;
        for (Mob pet : petsOf(player)) {
            if (!PassengerRule.hasFreeSeat(chair.getPassengers().size(), AlpineConfig.LIFT_SEATS.get())) return;
            pet.startRiding(chair);
        }
    }
}
