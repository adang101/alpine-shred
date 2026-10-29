package io.github.adang101.alpineshred.client.feel;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import io.github.adang101.alpineshred.feel.LeanRig;
import net.minecraft.client.player.AbstractClientPlayer;
import org.jetbrains.annotations.NotNull;

/**
 * The procedural lean layer (design doc section 9): adds {@link LeanRig}'s tilt and crouch on top
 * of the base clip. It also sets the head's height, because clips never touch the head (so it keeps
 * following the look) and the head must sit on the torso wherever the clip and the crouch put it.
 */
final class LeanLayer implements IAnimation {
    private final AbstractClientPlayer player;
    private final IAnimation base;
    private LeanRig.Pose from = LeanRig.Pose.NEUTRAL;
    private LeanRig.Pose to = LeanRig.Pose.NEUTRAL;
    private boolean active;
    private float tickDelta;
    /** Degrees flipped (section 8: flips turn the body root procedurally), last tick and this. */
    private float flipFrom, flipTo;
    /** Snowboarders stand sideways, so their flips roll the body; skiers' pitch it. */
    private boolean flipRolls;
    private float flipSign = 1;

    LeanLayer(AbstractClientPlayer player, IAnimation base) {
        this.player = player;
        this.base = base;
    }

    void update(LeanRig.Pose pose, boolean on) {
        from = to;
        to = pose;
        active = on;
    }

    void flip(double degrees, boolean board, boolean goofy) {
        flipFrom = Math.abs(degrees - flipTo) > 90 ? (float) degrees : flipTo; // a reset snaps, no swing back
        flipTo = (float) degrees;
        flipRolls = board;
        flipSign = board && goofy ? -1 : 1;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public void setupAnim(float tickDelta) {
        this.tickDelta = tickDelta;
    }

    @Override
    public @NotNull Vec3f get3DTransform(@NotNull String part, @NotNull TransformType type, float tickDelta, @NotNull Vec3f v) {
        LeanRig.Pose p = LeanRig.Pose.lerp(from, to, tickDelta);
        float crouch = (float) p.crouch();
        switch (part) {
            case "body" -> {
                if (type == TransformType.POSITION) {
                    // Stand on the board, and undo vanilla's 0.125 block drop for a crouching (braking)
                    // player so the board stays on the snow.
                    float lift = (float) LeanRig.STAND + (player.isCrouching() ? 0.125f : 0f);
                    return new Vec3f(v.getX() + (float) p.rootX(), v.getY() + (float) p.rootY() + lift, v.getZ() + (float) p.rootZ());
                }
                if (type == TransformType.ROTATION) {
                    float flip = (float) Math.toRadians(flipFrom + (flipTo - flipFrom) * tickDelta) * flipSign;
                    return new Vec3f(v.getX() + (float) p.rootPitch() + (flipRolls ? 0 : flip), v.getY(),
                            v.getZ() + (float) p.rootRoll() + (flipRolls ? -flip : 0));
                }
            }
            case "torso" -> {
                if (type == TransformType.POSITION) return new Vec3f(v.getX(), v.getY() + crouch, v.getZ());
                if (type == TransformType.ROTATION) {
                    return new Vec3f(v.getX() + (float) p.torsoPitch(), v.getY(), v.getZ() + (float) p.torsoRoll());
                }
            }
            case "rightArm", "leftArm" -> {
                if (type == TransformType.POSITION) return new Vec3f(v.getX(), v.getY() + crouch, v.getZ());
            }
            case "head" -> {
                if (type == TransformType.POSITION) {
                    float torsoY = base.get3DTransform("torso", TransformType.POSITION, tickDelta, Vec3f.ZERO).getY();
                    return new Vec3f(v.getX(), torsoY + crouch, v.getZ());
                }
            }
            default -> {
            }
        }
        return v;
    }
}
