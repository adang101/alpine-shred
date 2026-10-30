package io.github.adang101.alpineshred.design;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.adang101.alpineshred.design.paint.EditorState;
import io.github.adang101.alpineshred.design.paint.PaintTools;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Section 22's files: default looks in the atlas layout, the mesh, the bench's assets and every text the editor shows. */
class DesignAssetsTest {
    private static final String[] BOARDS = {"snowboard", "park_snowboard", "freeride_snowboard", "pro_snowboard"};
    private static final String[] SKIS = {"skis", "carving_skis", "powder_skis", "park_skis", "pro_skis"};

    private static InputStream open(String path) {
        InputStream in = DesignAssetsTest.class.getResourceAsStream(path);
        assertNotNull(in, path + " missing");
        return in;
    }

    private static JsonObject json(String path) throws IOException {
        try (var r = new InputStreamReader(open(path), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(r).getAsJsonObject();
        }
    }

    @Test
    void defaultLooksAreAtlasesCutToTheOutline() throws IOException {
        for (String[] group : new String[][]{BOARDS, SKIS}) {
            DesignKind kind = group == BOARDS ? DesignKind.BOARD : DesignKind.SKI;
            for (String item : group) {
                BufferedImage img;
                try (InputStream in = open("/assets/alpineshred/textures/gear/" + item + ".png")) {
                    img = ImageIO.read(in);
                }
                assertEquals(kind.width, img.getWidth(), item);
                assertEquals(kind.atlasHeight(), img.getHeight(), item);
                for (int face = 0; face < 2; face++) {
                    for (int y = 0; y < kind.height; y++) {
                        for (int x = 0; x < kind.width; x++) {
                            int alpha = img.getRGB(x, face * kind.height + y) >>> 24;
                            assertEquals(Outline.inside(kind, x, y) ? 255 : 0, alpha, item + " face " + face + " at " + x + "," + y);
                        }
                    }
                }
                for (int y = 2 * kind.height; y < kind.atlasHeight(); y++) {
                    assertEquals(255, img.getRGB(10, y) >>> 24, item + " edge strip");
                }
                // The model sheet the bindings come from is still there.
                open("/assets/alpineshred/textures/item/gear/" + item + ".png").close();
                JsonObject model = json("/assets/alpineshred/models/item/" + item + ".json");
                String parent = model.get("parent").getAsString();
                JsonObject template = json("/assets/alpineshred/models/item/" + parent.substring(parent.indexOf('/') + 1) + ".json");
                assertEquals("builtin/entity", template.get("parent").getAsString(), item + " is drawn by the designed gear renderer");
                assertTrue(template.getAsJsonObject("display").has("gui"), item + " keeps its display transforms");
            }
        }
    }

    @Test
    void theMeshIsClosedAroundTheOutline() {
        for (DesignKind kind : DesignKind.values()) {
            GearMesh.Mesh mesh = GearMesh.of(kind, false);
            int decks = kind == DesignKind.BOARD ? 1 : 2;
            int faces = 0, walls = 0;
            for (GearMesh.Quad q : mesh.deck()) {
                for (int i = 0; i < 4; i++) {
                    assertTrue(q.u(i) >= 0 && q.u(i) <= 1 && q.v(i) >= 0 && q.v(i) <= 1, "uv in the atlas");
                }
                if (Math.abs(q.ny()) > 0.9f) faces++;
                else walls++;
            }
            assertEquals(decks * 6, faces, kind + ": top and base of three sections");
            // Two side walls per outline column, plus the steps and end caps.
            assertTrue(walls >= decks * 2 * kind.width + 2 * decks, kind + " walls " + walls);
            assertEquals(kind == DesignKind.BOARD ? 12 : 12, mesh.bindings().size(), "binding boxes");
            // The flat middle's top faces up, at the deck height.
            GearMesh.Quad top = mesh.deck().get(2);
            assertTrue(top.ny() > 0.99f, "top normal " + top.ny());
            assertEquals(GearMesh.BASE_Y + GearMesh.THICK, top.y(0), 1.0e-4);
        }
        // Mirrored pair: the left ski's rows run the other way.
        GearMesh.Quad plain = GearMesh.of(DesignKind.SKI, false).deck().stream().filter(q -> q.x(0) > 8 && q.ny() > 0.9f).findFirst().orElseThrow();
        GearMesh.Quad mirrored = GearMesh.of(DesignKind.SKI, true).deck().stream().filter(q -> q.x(0) > 8 && q.ny() > 0.9f).findFirst().orElseThrow();
        assertTrue(plain.v(0) != mirrored.v(0), "mirror flips the rows");
    }

    @Test
    void theBenchHasItsAssetsAndTheAdvancementItsTrigger() throws IOException {
        json("/assets/alpineshred/blockstates/paint_bench.json");
        JsonObject model = json("/assets/alpineshred/models/block/paint_bench.json");
        for (var e : model.getAsJsonObject("textures").entrySet()) {
            String t = e.getValue().getAsString();
            if (t.startsWith("alpineshred:")) open("/assets/alpineshred/textures/" + t.substring("alpineshred:".length()) + ".png").close();
        }
        json("/assets/alpineshred/models/item/paint_bench.json");
        JsonObject adv = json("/data/alpineshred/advancements/fresh_coat.json");
        assertEquals("minecraft:inventory_changed", adv.getAsJsonObject("criteria").getAsJsonObject("designed").get("trigger").getAsString());
    }

    /** Every key the editor, bench, commands and tooltips show is translated. */
    @Test
    void everyDesignTextIsTranslated() throws IOException {
        JsonObject lang = json("/assets/alpineshred/lang/en_us.json");
        List<String> keys = new ArrayList<>();
        Path root = Path.of("common/src/main/java/io/github/adang101/alpineshred");
        if (!Files.isDirectory(root)) root = Path.of("src/main/java/io/github/adang101/alpineshred");
        Pattern literal = Pattern.compile("translatable(?:WithFallback)?\\(\"([a-z0-9_.]+)\"");
        for (String dir : new String[]{"design", "client/design"}) {
            try (var files = Files.walk(root.resolve(dir))) {
                for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                    Matcher m = literal.matcher(Files.readString(p));
                    while (m.find()) if (!m.group(1).endsWith(".")) keys.add(m.group(1)); // prefixes are listed below
                }
            }
        }
        for (String k : new String[]{"tooltip.alpineshred.design", "tooltip.alpineshred.design.by", "tooltip.alpineshred.design.untitled",
                "commands.alpineshred.design.none", "commands.alpineshred.design.info", "commands.alpineshred.design.cleared",
                "commands.alpineshred.design.unreadable", "block.alpineshred.paint_bench", "advancements.alpineshred.fresh_coat.title",
                "advancements.alpineshred.fresh_coat.description"}) {
            keys.add(k);
        }
        for (EditorState.Tool t : EditorState.Tool.values()) keys.add("gui.alpineshred.design_editor.tool." + t.id());
        for (PaintTools.GradientType g : PaintTools.GradientType.values()) {
            keys.add("gui.alpineshred.design_editor.gradient." + g.name().toLowerCase(Locale.ROOT));
        }
        for (PaintTools.Pattern p : PaintTools.Pattern.values()) keys.add("gui.alpineshred.design_editor.pattern." + p.id());
        for (Design.Pair p : Design.Pair.values()) keys.add("gui.alpineshred.design_editor.pair." + p.id());
        for (DesignStatus s : DesignStatus.values()) keys.add(s.translationKey());
        assertTrue(keys.size() > 60, "found " + keys.size() + " keys");
        for (String k : keys) assertTrue(lang.has(k), k + " not in en_us.json");
    }
}
