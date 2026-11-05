package io.github.adang101.alpineshred.client.lift;

import dev.architectury.event.events.client.ClientPlayerEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import io.github.adang101.alpineshred.lift.ChairEntity;
import io.github.adang101.alpineshred.lift.LiftDriveStationBlockEntity;
import io.github.adang101.alpineshred.lift.LiftPath;
import io.github.adang101.alpineshred.lift.LiftTowerHeadBlockEntity;
import io.github.adang101.alpineshred.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * {@code LiftSoundManager} (design doc section 17): the sounds of lifts that are near the player. A sound is
 * made only where the client has the block entity loaded ({@link LiftDriveStationBlockEntity} for the motor,
 * {@link LiftTowerHeadBlockEntity} for the creaks), so a line far away or in an unloaded chunk is silent.
 * <ul>
 *   <li>The motor hums at the drive in a loop whose volume is {@code speed / 4}.</li>
 *   <li>Each head creaks at a random interval, only while the cable moves.</li>
 *   <li>The emergency stop sounds once, at the drive, when a line goes into emergency or faults.</li>
 *   <li>The safety bar clicks when a chair's bar closes or opens.</li>
 * </ul>
 * Everything comes from the synced line state, so there are no sound packets.
 */
public final class LiftSoundManager {
    /** Distance within which the drive motor and the emergency stop are made. */
    static final double MOTOR_RANGE = 48;
    /** Distance within which a head creaks. */
    static final double CREAK_RANGE = 32;
    static final double CREAK_MIN_SPEED = 0.5;
    static final int CREAK_MIN_TICKS = 100, CREAK_MAX_TICKS = 300;

    private static final Map<BlockPos, MotorSound> MOTORS = new HashMap<>();
    private static final Map<BlockPos, Integer> CREAKS = new HashMap<>();
    private static final Set<UUID> STOPPED = new HashSet<>();
    private static final RandomSource RANDOM = RandomSource.create();

    /** For the client smoke: motor loops started, and one-shot sounds played (bar, creak, emergency stop). */
    public static int motorsStarted, oneShots;

    private LiftSoundManager() {
    }

    public static void register() {
        ChairEntity.clientBarHook = LiftSoundManager::barChanged;
        ClientTickEvent.CLIENT_POST.register(LiftSoundManager::tick);
        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(player -> clear());
    }

    private static void clear() {
        MOTORS.values().forEach(MotorSound::finish);
        MOTORS.clear();
        CREAKS.clear();
        STOPPED.clear();
    }

    private static void barChanged(ChairEntity chair, boolean down) {
        SoundEvent sound = down ? ModSounds.LIFT_BAR_CLOSE.get() : ModSounds.LIFT_BAR_OPEN.get();
        oneShots++;
        chair.level().playLocalSound(chair.getX(), chair.getY(), chair.getZ(), sound, SoundSource.BLOCKS, 0.8f, 1f, false);
    }

    private static void tick(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null) {
            if (!MOTORS.isEmpty() || !CREAKS.isEmpty()) clear();
            return;
        }
        if (minecraft.isPaused()) return;
        MOTORS.values().removeIf(MotorSound::isStopped);
        Set<UUID> live = new HashSet<>();
        Set<BlockPos> checked = new HashSet<>();
        for (ClientLine line : ClientLiftLines.all()) {
            live.add(line.id);
            drive(level, player, line);
            heads(level, player, line, checked);
        }
        STOPPED.retainAll(live);
        // A head not looked at this tick (far, or its line stopped or gone) forgets its countdown.
        CREAKS.keySet().retainAll(checked);
    }

    private static void drive(ClientLevel level, LocalPlayer player, ClientLine line) {
        BlockPos drive = line.nodes().get(0);
        double dx = player.getX() - (drive.getX() + 0.5), dy = player.getY() - drive.getY(), dz = player.getZ() - (drive.getZ() + 0.5);
        boolean near = dx * dx + dy * dy + dz * dz <= MOTOR_RANGE * MOTOR_RANGE && level.getBlockEntity(drive) instanceof LiftDriveStationBlockEntity;
        if (near && line.speed > 0.05 && !MOTORS.containsKey(drive)) {
            MotorSound motor = new MotorSound(line.id, drive);
            MOTORS.put(drive, motor);
            motorsStarted++;
            Minecraft.getInstance().getSoundManager().play(motor);
        }
        boolean stopped = line.emergency || line.faulted;
        if (!stopped) {
            STOPPED.remove(line.id);
        } else if (STOPPED.add(line.id) && near && line.speed > 0.05) {
            oneShots++;
            level.playLocalSound(drive.getX() + 0.5, drive.getY() + LiftPath.CABLE_HEIGHT, drive.getZ() + 0.5, ModSounds.LIFT_E_STOP.get(),
                    SoundSource.BLOCKS, 1f, 1f, false);
        }
    }

    private static void heads(ClientLevel level, LocalPlayer player, ClientLine line, Set<BlockPos> checked) {
        if (line.speed <= CREAK_MIN_SPEED) return;
        for (int i = 1; i < line.nodes().size() - 1; i++) {
            BlockPos head = line.nodes().get(i);
            double dx = player.getX() - (head.getX() + 0.5), dy = player.getY() - head.getY(), dz = player.getZ() - (head.getZ() + 0.5);
            if (dx * dx + dy * dy + dz * dz > CREAK_RANGE * CREAK_RANGE || !(level.getBlockEntity(head) instanceof LiftTowerHeadBlockEntity)) continue;
            checked.add(head);
            int wait = CREAKS.getOrDefault(head, RANDOM.nextInt(CREAK_MAX_TICKS));
            if (--wait > 0) {
                CREAKS.put(head, wait);
                continue;
            }
            CREAKS.put(head, CREAK_MIN_TICKS + RANDOM.nextInt(CREAK_MAX_TICKS - CREAK_MIN_TICKS));
            oneShots++;
            float volume = (float) Math.min(1.0, line.speed / 4.0) * 0.6f;
            level.playLocalSound(head.getX() + 0.5, head.getY() + LiftPath.CABLE_HEIGHT, head.getZ() + 0.5, ModSounds.LIFT_CABLE_CREAK.get(),
                    SoundSource.BLOCKS, volume, 0.8f + RANDOM.nextFloat() * 0.4f, false);
        }
    }

    /** The drive's motor: a loop at the drive whose volume follows the line's speed, over four. */
    private static final class MotorSound extends AbstractTickableSoundInstance {
        private final UUID line;

        MotorSound(UUID line, BlockPos drive) {
            super(ModSounds.LIFT_MOTOR_LOOP.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
            this.line = line;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.01f;
            this.x = drive.getX() + 0.5;
            this.y = drive.getY() + 0.5;
            this.z = drive.getZ() + 0.5;
        }

        void finish() {
            stop();
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            ClientLine current = ClientLiftLines.get(line);
            if (current == null || current.speed <= 0.01) {
                stop();
                return;
            }
            volume = (float) Math.min(1.0, current.speed / 4.0);
            pitch = (float) (0.85 + Math.min(1.0, current.speed / 4.0) * 0.3);
        }
    }
}
