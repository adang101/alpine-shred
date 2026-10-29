package io.github.adang101.alpineshred.feel;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.data.gson.AnimationSerializing;
import dev.kosmx.playerAnim.core.util.Vec3f;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rider clips, parsed and played by PlayerAnimator's own keyframe core, the code that runs
 * them in game (design doc section 9). Runs in CI, where the library is on the test classpath.
 */
class ClipFilesTest {
    private static KeyframeAnimation load(String name) throws IOException {
        try (InputStream in = ClipFilesTest.class.getResourceAsStream("/assets/alpineshred/player_animation/" + name + ".json")) {
            assertNotNull(in, "missing clip file " + name);
            List<KeyframeAnimation> clips = AnimationSerializing.deserializeAnimation(in);
            assertEquals(1, clips.size(), name);
            return clips.get(0);
        }
    }

    private static Vec3f rotation(KeyframeAnimation clip, String part, int tick) {
        KeyframeAnimationPlayer player = new KeyframeAnimationPlayer(clip, tick);
        return player.get3DTransform(part, TransformType.ROTATION, 0, Vec3f.ZERO);
    }

    @Test
    void everyClipLoadsUnderItsOwnNameAndLeavesTheHeadAlone() throws IOException {
        for (String name : RiderClips.ALL) {
            KeyframeAnimation clip = load(name);
            // The library keeps the name as JSON text and unquotes it into the registry id.
            String stored = String.valueOf(clip.extraData.get("name"));
            assertEquals(name, com.google.gson.JsonParser.parseString(stored).getAsString(), "registry id comes from the name field");
            KeyframeAnimation.StateCollection head = clip.getPart("head");
            assertTrue(head == null || !head.isEnabled(), name + " animates the head, which would freeze the look");
        }
    }

    @Test
    void oneShotsAreAsLongAsTheDirectorPlaysThemAndLoopsLoop() throws IOException {
        assertEquals(RiderClips.POP_LAUNCH_TICKS, load(RiderClips.POP_LAUNCH).endTick);
        assertEquals(RiderClips.LAND_TICKS, load(RiderClips.LAND_CLEAN).endTick);
        assertEquals(RiderClips.POLE_PUSH_TICKS, load(RiderClips.POLE_PUSH_LEFT).endTick);
        assertEquals(30, load(RiderClips.WIPEOUT_FRONT).endTick, "the length of the input lock");
        for (String loop : List.of(RiderClips.GLIDE_BOARD_REGULAR, RiderClips.GLIDE_SKIS, RiderClips.AIR_BOARD,
                RiderClips.TUCK_BOARD, RiderClips.BRAKE_BOARD, RiderClips.WALK_STRAPPED, RiderClips.POLE_CARRY)) {
            assertTrue(load(loop).isInfinite, loop + " should loop");
        }
        for (String once : List.of(RiderClips.POP_LAUNCH, RiderClips.LAND_CLEAN, RiderClips.WIPEOUT_FRONT)) {
            assertFalse(load(once).isInfinite, once + " should play once");
        }
    }

    @Test
    void glidePosesPlayWithTheIntendedSigns() throws IOException {
        KeyframeAnimation regular = load(RiderClips.GLIDE_BOARD_REGULAR);
        // Positive torso pitch leans forward; the lead (left) arm swings out, which is negative roll.
        assertTrue(rotation(regular, "torso", 0).getX() > Math.toRadians(5));
        assertTrue(rotation(regular, "leftArm", 0).getZ() < -Math.toRadians(20));
        assertTrue(rotation(regular, "rightArm", 0).getZ() > Math.toRadians(10));
        // Goofy is the mirror: the right arm leads.
        KeyframeAnimation goofy = load(RiderClips.GLIDE_BOARD_GOOFY);
        assertEquals(-rotation(regular, "leftArm", 0).getZ(), rotation(goofy, "rightArm", 0).getZ(), 1e-5);
    }

    @Test
    void poleClipsMoveOnlyTheArms() throws IOException {
        for (String name : List.of(RiderClips.POLE_CARRY, RiderClips.POLE_PUSH_LEFT, RiderClips.POLE_PUSH_RIGHT)) {
            KeyframeAnimation clip = load(name);
            for (String part : List.of("torso", "rightLeg", "leftLeg", "body")) {
                KeyframeAnimation.StateCollection p = clip.getPart(part);
                assertTrue(p == null || !p.isEnabled(), name + " moves " + part + ", which would override the base clip");
            }
        }
    }
}
