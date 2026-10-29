package io.github.adang101.alpineshred.trick;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Section 15's trick packets survive the wire; garbage does not get through. */
class TrickCodecTest {
    private interface Writer {
        void write(DataOutputStream out) throws IOException;
    }

    private static DataInputStream roundTrip(Writer w) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        w.write(new DataOutputStream(bytes));
        return new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    }

    @Test
    void airReportRoundTrip() throws IOException {
        int[] grabs = new int[GrabType.values().length];
        grabs[GrabType.NOSE.ordinal()] = 11;
        AirReport r = new AirReport(362.5, -12.25, grabs, 3.5, 31, 4.0, 2.5, 12.25, true, false, true);
        assertEquals(r, TrickCodec.readAir(roundTrip(out -> TrickCodec.writeAir(out, r))));
    }

    @Test
    void grindReportRoundTrip() throws IOException {
        GrindReport g = new GrindReport(-12, 64, 900, true, 2, 47.5, true, 8.25, 44, 2);
        assertEquals(g, TrickCodec.readGrind(roundTrip(out -> TrickCodec.writeGrind(out, g))));
    }

    @Test
    void resultRoundTrip() throws IOException {
        TrickResult r = new TrickResult(512, List.of(new TrickResult.Line("spin_360", 300), new TrickResult.Line("grab_indy", 180)),
                LandingQuality.PERFECT, true, 1.5, 1234, 3, false, false);
        assertEquals(r, TrickCodec.readResult(roundTrip(out -> TrickCodec.writeResult(out, r))));
        TrickResult bank = TrickResult.bank(5000, 2.25, 4);
        assertEquals(bank, TrickCodec.readResult(roundTrip(out -> TrickCodec.writeResult(out, bank))));
    }

    @Test
    void nanIsRejected() {
        int[] grabs = new int[GrabType.values().length];
        AirReport nan = new AirReport(Double.NaN, 0, grabs, 0, 10, 0, 0, 0, false, false, false);
        assertThrows(IOException.class, () -> TrickCodec.readAir(roundTrip(out -> TrickCodec.writeAir(out, nan))));
    }
}
