package org.vaadin.svgvis.unit;

import in.virit.color.NamedColor;
import org.junit.jupiter.api.Test;
import org.vaadin.svgvis.WindRose;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for WindRose component.
 * Note: Tests that require UI context (like draw()) are covered in integration tests.
 */
public class WindRoseTest {

    @Test
    public void testSectorClickDataRecord() {
        List<Double> values = List.of(10.0, 20.0);
        List<Double> percentages = List.of(25.0, 50.0);

        WindRose.SectorClickData data = new WindRose.SectorClickData(
                0, "N", 0, values, percentages
        );

        assertEquals(0, data.sectorIndex());
        assertEquals("N", data.directionLabel());
        assertEquals(0, data.centerDegrees());
        assertEquals(values, data.seriesValues());
        assertEquals(percentages, data.seriesPercentages());
    }

    @Test
    public void testDataSeriesRecord() {
        double[] values = {1.0, 2.0, 3.0, 4.0};
        WindRose.DataSeries series = new WindRose.DataSeries("Test", NamedColor.GREEN, values);

        assertEquals("Test", series.label());
        assertEquals(NamedColor.GREEN, series.color());
        assertArrayEquals(values, series.values());
    }

    @Test
    public void testSectorClickDataWithMultipleSeries() {
        List<Double> values = List.of(100.0, 200.0, 50.0);
        List<Double> percentages = List.of(10.0, 20.0, 5.0);

        WindRose.SectorClickData data = new WindRose.SectorClickData(
                5, "SW", 225, values, percentages
        );

        assertEquals(5, data.sectorIndex());
        assertEquals("SW", data.directionLabel());
        assertEquals(225, data.centerDegrees());
        assertEquals(3, data.seriesValues().size());
        assertEquals(3, data.seriesPercentages().size());
        assertEquals(200.0, data.seriesValues().get(1));
        assertEquals(20.0, data.seriesPercentages().get(1));
    }

    @Test
    public void testDataSeriesValuesArePreserved() {
        double[] original = {1.5, 2.5, 3.5, 4.5};
        WindRose.DataSeries series = new WindRose.DataSeries("Test", NamedColor.GRAY, original);

        double[] retrieved = series.values();
        for (int i = 0; i < original.length; i++) {
            assertEquals(original[i], retrieved[i], 0.001);
        }
    }
}
