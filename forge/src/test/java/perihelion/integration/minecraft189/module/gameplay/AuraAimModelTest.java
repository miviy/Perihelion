package perihelion.integration.minecraft189.module.gameplay;

import static org.junit.Assert.*;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Random;

public class AuraAimModelTest {
    private AuraAim.Model read(String rows) throws Exception {
        return AuraAim.Model.read(
                new ByteArrayInputStream(rows.getBytes(StandardCharsets.UTF_8)), new Random(1));
    }

    @Test
    public void rejectsHeadersMalformedAndNonFiniteRows() throws Exception {
        assertNull(read("yaw,pitch\nNaN,3\n1,Infinity\nwrong\n361,2\n"));
        AuraAim.Model model = read("yaw,pitch\nNaN,3\n2,-3\nInfinity,0\n2,-3\n");
        for (int i = 0; i < 100; i++)
            assertArrayEquals(new double[] {2, -3}, model.sample(new Random(i)), 0);
    }

    @Test
    public void samplingNeverExposesMutableStoredRows() throws Exception {
        AuraAim.Model model = read("1,2\n1,2\n");
        model.sample(new Random(4))[0] = 999;
        for (int i = 0; i < 20; i++)
            assertArrayEquals(new double[] {1, 2}, model.sample(new Random(i)), 0);
    }

    @Test
    public void reservoirHandlesMoreThanThreeHundredSamplesAndFiniteStatistics() throws Exception {
        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < 2000; i++) rows.append(i % 15).append(',').append(i % 8).append('\n');
        AuraAim.Model model = read(rows.toString());
        Random random = new Random(22);
        for (int i = 0; i < 2000; i++)
            for (double value : model.sample(random)) assertTrue(Double.isFinite(value));
    }

    @Test
    public void shippedPresetsLoadAsRealModels() throws Exception {
        for (String name : new String[] {"legit", "ai"}) {
            java.io.InputStream stream =
                    AuraAim.class.getResourceAsStream(
                            "/assets/perihelion/rotationpresets/" + name + ".csv");
            assertNotNull(stream);
            assertNotNull(AuraAim.Model.read(stream, new Random(9)));
        }
    }
}
