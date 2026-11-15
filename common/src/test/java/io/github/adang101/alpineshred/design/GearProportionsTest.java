package io.github.adang101.alpineshred.design;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Player feedback (README 99): the board was too narrow and too short, so a rider's boots hung off
 * it. These tests put the rider's feet, taken from the real animation clips, on the real mesh and
 * check the footprint lies inside the deck, so a later change cannot shrink the board back.
 *
 * <p>The rider model is 32 pixels tall, each leg 4 wide and 4 deep, a leg's pivot 12 above its sole.
 * A board runs along the rider's x axis (the rider faces sideways), skis along z.
 */
class GearProportionsTest {
    private static final float MODEL_HEIGHT = 32f;
    private static final float FOOT = 4f;
    private static final float LEG = 12f;

    /** Extents of a set of quads. */
    private record Box(float x0, float x1, float y0, float y1, float z0, float z1) {
        static Box of(List<GearMesh.Quad> quads) {
            float[] b = {Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE};
            for (GearMesh.Quad q : quads) {
                for (int i = 0; i < 4; i++) {
                    b[0] = Math.min(b[0], q.x(i));
                    b[1] = Math.max(b[1], q.x(i));
                    b[2] = Math.min(b[2], q.y(i));
                    b[3] = Math.max(b[3], q.y(i));
                    b[4] = Math.min(b[4], q.z(i));
                    b[5] = Math.max(b[5], q.z(i));
                }
            }
            return new Box(b[0], b[1], b[2], b[3], b[4], b[5]);
        }

        float width() {
            return x1 - x0;
        }

        float length() {
            return z1 - z0;
        }
    }

    /** The four sole corners of a leg in the rider's frame, relative to the point between the two hips. */
    private record Foot(float[] x, float[] z) {
        float minX() {
            return min(x);
        }

        float maxX() {
            return max(x);
        }

        float minZ() {
            return min(z);
        }

        float maxZ() {
            return max(z);
        }
    }

    private static float min(float[] v) {
        float m = Float.MAX_VALUE;
        for (float f : v) m = Math.min(m, f);
        return m;
    }

    private static float max(float[] v) {
        float m = -Float.MAX_VALUE;
        for (float f : v) m = Math.max(m, f);
        return m;
    }

    private static JsonObject clip(String name) throws IOException {
        try (InputStream in = GearProportionsTest.class.getResourceAsStream("/assets/alpineshred/player_animation/" + name + ".json")) {
            assertNotNull(in, name);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    /**
     * Both soles in every keyframe of a clip. A leg is a 4 x 12 x 4 box hanging from its pivot,
     * turned like Minecraft turns a model part (pitch about x first, then roll about z), y down.
     */
    private static List<Foot[]> soles(String name) throws IOException {
        JsonArray moves = clip(name).getAsJsonObject("emote").getAsJsonArray("moves");
        List<Foot[]> out = new ArrayList<>();
        for (JsonElement m : moves) {
            JsonObject move = m.getAsJsonObject();
            JsonObject right = move.getAsJsonObject("rightLeg"), left = move.getAsJsonObject("leftLeg");
            float centreZ = (right.get("z").getAsFloat() + left.get("z").getAsFloat()) / 2f;
            float centreX = (right.get("x").getAsFloat() + left.get("x").getAsFloat()) / 2f;
            out.add(new Foot[]{sole(right, centreX, centreZ), sole(left, centreX, centreZ)});
        }
        return out;
    }

    private static Foot sole(JsonObject leg, float centreX, float centreZ) {
        double pitch = Math.toRadians(leg.get("pitch").getAsDouble()), roll = Math.toRadians(leg.get("roll").getAsDouble());
        float px = leg.get("x").getAsFloat(), pz = leg.get("z").getAsFloat();
        float[] xs = new float[4], zs = new float[4];
        int i = 0;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                double lx = sx * FOOT / 2, ly = LEG, lz = sz * FOOT / 2;
                double y1 = ly * Math.cos(pitch) - lz * Math.sin(pitch);
                double z1 = ly * Math.sin(pitch) + lz * Math.cos(pitch);
                double x2 = lx * Math.cos(roll) - y1 * Math.sin(roll);
                xs[i] = (float) (px + x2) - centreX;
                zs[i] = (float) (pz + z1) - centreZ;
                i++;
            }
        }
        return new Foot(xs, zs);
    }

    /** The boxes of a mesh's bindings, six quads each. */
    private static List<Box> bindingBoxes(GearMesh.Mesh mesh) {
        List<Box> boxes = new ArrayList<>();
        for (int i = 0; i < mesh.bindings().size(); i += 6) boxes.add(Box.of(mesh.bindings().subList(i, i + 6)));
        return boxes;
    }

    private static Box flatMiddle(GearMesh.Mesh mesh, float x0, float x1) {
        List<GearMesh.Quad> flat = new ArrayList<>();
        for (GearMesh.Quad q : mesh.deck()) {
            boolean inside = q.x(0) >= x0 - 1e-3f && q.x(0) <= x1 + 1e-3f && q.x(2) >= x0 - 1e-3f && q.x(2) <= x1 + 1e-3f;
            if (inside && q.ny() > 0.9999f) flat.add(q);
        }
        assertTrue(!flat.isEmpty());
        return Box.of(flat);
    }

    @Test
    void theBoardIsLongAndWide() {
        Box deck = Box.of(GearMesh.of(DesignKind.BOARD, false).deck());
        float length = deck.length(), width = deck.width();
        assertTrue(length >= 0.82f * MODEL_HEIGHT && length <= 0.95f * MODEL_HEIGHT,
                "length " + length + " px is about 85 to 90 percent of the rider's " + MODEL_HEIGHT);
        assertTrue(width >= 1.75f * FOOT, "width " + width + " px covers the " + FOOT + " px foot with margin");
        assertTrue(width <= 11f, "width " + width + " px stays a snowboard");
        // Centred on the item origin, as the rider layer places it.
        assertEquals(GearMesh.CENTRE, (deck.x0() + deck.x1()) / 2f, 1e-3);
        assertEquals(GearMesh.CENTRE, (deck.z0() + deck.z1()) / 2f, 1e-3);
        // The nose and tail kick up, a little and equally.
        float kick = deck.y1() - (GearMesh.BASE_Y + GearMesh.THICK);
        assertTrue(kick >= 1f && kick <= 2f, "kick " + kick);
        assertEquals(GearMesh.BOARD_RISE[0], GearMesh.BOARD_RISE[3], 1e-6);
    }

    @Test
    void bothBootsStandInsideTheBoardInEveryStance() throws IOException {
        GearMesh.Mesh mesh = GearMesh.of(DesignKind.BOARD, false);
        Box deck = Box.of(mesh.deck());
        float halfWidth = deck.width() / 2f;
        // The flat middle, where the boots stand (the kicks are outside it).
        Box flat = flatMiddle(mesh, deck.x0(), deck.x1());
        float halfFlat = flat.length() / 2f;
        for (String name : new String[]{"idle_board", "glide_board_regular", "glide_board_goofy", "tuck_board", "brake_board"}) {
            for (Foot[] feet : soles(name)) {
                for (Foot f : feet) {
                    assertTrue(Math.max(-f.minZ(), f.maxZ()) <= halfWidth - 1.5f,
                            name + ": boot z " + f.minZ() + " to " + f.maxZ() + " leaves 1.5 px of board either side of " + halfWidth);
                    assertTrue(Math.max(-f.minX(), f.maxX()) <= halfFlat, name + ": boot x " + f.minX() + " to " + f.maxX() + " on the flat part");
                }
                // The two boots are on opposite sides of the middle and do not touch.
                assertTrue(feet[0].maxX() < feet[1].minX() || feet[1].maxX() < feet[0].minX(), name + ": boots overlap");
            }
        }
        // Knees bent in the air swing the soles forward a little; they stay over the deck.
        for (Foot[] feet : soles("air_board")) {
            for (Foot f : feet) {
                assertTrue(Math.max(-f.minZ(), f.maxZ()) <= halfWidth + 0.25f, "air_board: boot z " + f.minZ() + " to " + f.maxZ());
            }
        }
    }

    @Test
    void theBoardBindingsSitUnderTheBoots() throws IOException {
        GearMesh.Mesh mesh = GearMesh.of(DesignKind.BOARD, false);
        Box deck = Box.of(mesh.deck());
        List<Box> bindings = bindingBoxes(mesh);
        assertEquals(2, bindings.size());
        float top = GearMesh.BASE_Y + GearMesh.THICK;
        for (Box b : bindings) {
            assertEquals(top, b.y0(), 1e-4, "binding sits on the deck");
            assertTrue(b.x0() >= deck.x0() && b.x1() <= deck.x1(), "binding is within the board's width");
        }
        // A glide stance's boot centres each fall inside one binding along the board.
        for (Foot[] feet : soles("glide_board_regular")) {
            for (Foot f : feet) {
                float centre = GearMesh.CENTRE + (f.minX() + f.maxX()) / 2f;
                long hits = bindings.stream().filter(b -> centre > b.z0() && centre < b.z1()).count();
                assertEquals(1, hits, "boot centre " + centre + " is in exactly one binding");
            }
        }
    }

    @Test
    void eachSkiSitsUnderOneFootAndTheyDoNotTouch() throws IOException {
        GearMesh.Mesh mesh = GearMesh.of(DesignKind.SKI, false);
        List<GearMesh.Quad> right = new ArrayList<>(), left = new ArrayList<>();
        for (GearMesh.Quad q : mesh.deck()) (q.x(0) < GearMesh.CENTRE ? right : left).add(q);
        Box a = Box.of(right), b = Box.of(left);
        for (Box ski : new Box[]{a, b}) {
            assertTrue(ski.length() >= 0.8f * MODEL_HEIGHT && ski.length() <= MODEL_HEIGHT, "ski length " + ski.length());
            assertTrue(ski.width() >= 2.5f && ski.width() <= FOOT, "ski width " + ski.width() + " is about as wide as a foot");
        }
        assertEquals(a.width(), b.width(), 1e-4);
        assertEquals(a.length(), b.length(), 1e-4);
        assertTrue(b.x0() - a.x1() >= 1f, "a gap of " + (b.x0() - a.x1()) + " px between the skis");
        // Symmetric about the middle between the legs.
        assertEquals(GearMesh.CENTRE - a.x1(), b.x0() - GearMesh.CENTRE, 1e-4);
        // Flat tail, upturned tip.
        assertEquals(0f, GearMesh.SKI_RISE[0], 1e-6, "flat tail");
        assertTrue(GearMesh.SKI_RISE[3] >= 1f, "tip rise " + GearMesh.SKI_RISE[3]);
        // Each leg's sole lies over its own ski, whose centre is within a pixel of the sole's.
        List<Box> bindings = bindingBoxes(mesh);
        assertEquals(2, bindings.size());
        for (String name : new String[]{"idle_skis", "glide_skis"}) {
            for (Foot[] feet : soles(name)) {
                for (int leg = 0; leg < 2; leg++) {
                    Foot f = feet[leg];
                    Box ski = leg == 0 ? a : b; // the right leg (x negative) is over the lower-x ski
                    float footCentre = GearMesh.CENTRE + (f.minX() + f.maxX()) / 2f;
                    assertEquals((ski.x0() + ski.x1()) / 2f, footCentre, 1f, name + " leg " + leg + " is over its ski");
                    assertTrue(f.minX() + GearMesh.CENTRE >= ski.x0() - 1f && f.maxX() + GearMesh.CENTRE <= ski.x1() + 1f,
                            name + " leg " + leg + " is no more than a pixel wider than its ski");
                    Box binding = bindings.get(leg);
                    assertTrue(binding.z0() <= GearMesh.CENTRE - FOOT / 2f && binding.z1() >= GearMesh.CENTRE + FOOT / 2f,
                            "binding spans the foot's depth");
                }
            }
        }
    }

    @Test
    void theCanvasStillMapsOntoTheWholeLength() {
        // The painted canvas (64 x 16 and 64 x 8) is unchanged: only its mapping to the geometry moved.
        assertEquals(64, DesignKind.BOARD.width);
        assertEquals(16, DesignKind.BOARD.height);
        assertEquals(64, DesignKind.SKI.width);
        assertEquals(8, DesignKind.SKI.height);
        for (DesignKind kind : DesignKind.values()) {
            float umin = Float.MAX_VALUE, umax = -Float.MAX_VALUE;
            for (GearMesh.Quad q : GearMesh.of(kind, false).deck()) {
                if (Math.abs(q.ny()) < 0.5f) continue;
                for (int i = 0; i < 4; i++) {
                    umin = Math.min(umin, q.u(i));
                    umax = Math.max(umax, q.u(i));
                }
            }
            assertEquals(0f, umin, 1e-4, kind + " tail");
            assertEquals(1f, umax, 1e-4, kind + " nose");
        }
    }
}
