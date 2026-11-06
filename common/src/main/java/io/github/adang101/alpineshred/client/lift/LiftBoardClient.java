package io.github.adang101.alpineshred.client.lift;

import dev.architectury.event.events.client.ClientTickEvent;
import io.github.adang101.alpineshred.client.RideDriver;
import io.github.adang101.alpineshred.config.AlpineConfig;
import io.github.adang101.alpineshred.lift.AutoBoard;
import io.github.adang101.alpineshred.lift.Boarding;
import io.github.adang101.alpineshred.lift.ChairEntity;
import io.github.adang101.alpineshred.lift.LineView;
import io.github.adang101.alpineshred.movement.Riders;
import io.github.adang101.alpineshred.network.ModNetwork;
import io.github.adang101.alpineshred.lift.PassengerRule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Client side of boarding (design doc section 13): auto-board and the "right-click to board" prompt.
 *
 * <p>Auto-board sends {@code c2s/lift_board} for a chair at most once per approach: a chair that was tried is
 * remembered until it leaves the player's reach. The decision is {@link AutoBoard#shouldBoard}.
 */
public final class LiftBoardClient {
    private static final int PROMPT_INTERVAL = 20;
    private static final Set<Integer> TRIED = new HashSet<>();
    private static int promptCountdown;

    private LiftBoardClient() {
    }

    public static void register() {
        ClientTickEvent.CLIENT_POST.register(LiftBoardClient::tick);
    }

    private static void tick(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            TRIED.clear();
            return;
        }
        if (promptCountdown > 0) promptCountdown--;
        if (player.isPassenger() || player.isSpectator()) {
            TRIED.clear();
            return;
        }
        AABB box = player.getBoundingBox().inflate(Math.max(AutoBoard.HORIZONTAL, Boarding.MANUAL_REACH), Math.max(AutoBoard.VERTICAL, Boarding.MANUAL_REACH), Math.max(AutoBoard.HORIZONTAL, Boarding.MANUAL_REACH));
        List<ChairEntity> chairs = minecraft.level.getEntitiesOfClass(ChairEntity.class, box);
        boolean strapped = Riders.isStrapped(player);
        boolean autoBoard = AlpineConfig.CLIENT_AUTO_BOARD.on();
        double maxBoardSpeed = RideDriver.INSTANCE.serverSettings().maxBoardSpeed();
        Set<Integer> inReach = new HashSet<>();
        boolean prompt = false;
        for (ChairEntity chair : chairs) {
            LineView view = chair.lineId() == null ? null : ChairEntity.clientLineLookup.apply(chair.lineId());
            if (view == null || chair.chairIndex() >= view.chairCount()) continue;
            boolean free = PassengerRule.hasFreeSeat(chair.getPassengers().size(), AlpineConfig.LIFT_SEATS.get());
            if (free && Boarding.distance(player, chair) <= Boarding.MANUAL_REACH && view.speed() <= maxBoardSpeed) {
                prompt = true;
            }
            double s = view.path().chairS(view.travel(), chair.chairIndex(), view.chairCount());
            double dx = chair.getX() - player.getX(), dz = chair.getZ() - player.getZ();
            double horizontal = Math.sqrt(dx * dx + dz * dz), vertical = chair.getY() - player.getY();
            if (!AutoBoard.withinReach(horizontal, vertical)) continue;
            inReach.add(chair.getId());
            if (autoBoard && !TRIED.contains(chair.getId())
                    && AutoBoard.shouldBoard(strapped, false, horizontal, vertical, free, view.stations().inBoarding(s))) {
                TRIED.add(chair.getId());
                ModNetwork.sendLiftBoard(chair.getId());
            }
        }
        TRIED.retainAll(inReach);
        if (prompt && promptCountdown == 0 && AlpineConfig.CLIENT_HINTS.on()) {
            promptCountdown = PROMPT_INTERVAL;
            player.displayClientMessage(Component.translatable("message.alpineshred.lift.board_prompt"), true);
        }
    }
}
