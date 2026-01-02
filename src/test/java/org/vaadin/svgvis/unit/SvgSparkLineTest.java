package org.vaadin.svgvis.unit;

import in.virit.color.NamedColor;
import org.junit.jupiter.api.Test;
import org.vaadin.svgvis.SvgSparkLine;
import org.vaadin.svgvis.SvgSparkLine.DataPoint;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SvgSparkLine component.
 * Note: Tests that require UI context (like draw()) are covered in integration tests.
 */
public class SvgSparkLineTest {

    @Test
    public void testDataPointRecord() {
        DataPoint point = new DataPoint(10.5, 20.5);
        assertEquals(10.5, point.x(), 0.001);
        assertEquals(20.5, point.y(), 0.001);
    }

    @Test
    public void testDataPointFactoryFromInstant() {
        Instant now = Instant.now();
        DataPoint fromInstant = DataPoint.of(now, 42.0);
        assertEquals(now.toEpochMilli(), fromInstant.x(), 0.001);
        assertEquals(42.0, fromInstant.y(), 0.001);
    }

    @Test
    public void testDataPointFactoryFromDoubles() {
        DataPoint fromDoubles = DataPoint.of(1.5, 2.5);
        assertEquals(1.5, fromDoubles.x(), 0.001);
        assertEquals(2.5, fromDoubles.y(), 0.001);
    }

    @Test
    public void testDataSeriesRecord() {
        List<DataPoint> data = Arrays.asList(
                DataPoint.of(0, 10),
                DataPoint.of(1, 20)
        );
        SvgSparkLine.DataSeries series = new SvgSparkLine.DataSeries(data, NamedColor.BLUE);

        assertEquals(data, series.data());
        assertEquals(NamedColor.BLUE, series.color());
    }

    @Test
    public void testSmoothingEnumValues() {
        SvgSparkLine.Smoothing[] values = SvgSparkLine.Smoothing.values();
        assertEquals(3, values.length);
        assertEquals(SvgSparkLine.Smoothing.NONE, SvgSparkLine.Smoothing.valueOf("NONE"));
        assertEquals(SvgSparkLine.Smoothing.RDP, SvgSparkLine.Smoothing.valueOf("RDP"));
        assertEquals(SvgSparkLine.Smoothing.MOVING_AVERAGE, SvgSparkLine.Smoothing.valueOf("MOVING_AVERAGE"));
    }

    @Test
    public void testDataPointEquality() {
        DataPoint p1 = new DataPoint(1.0, 2.0);
        DataPoint p2 = new DataPoint(1.0, 2.0);
        DataPoint p3 = new DataPoint(1.0, 3.0);

        assertEquals(p1, p2);
        assertNotEquals(p1, p3);
    }

    @Test
    public void testDataSeriesWithDifferentColors() {
        List<DataPoint> data = List.of(DataPoint.of(0, 0));

        SvgSparkLine.DataSeries blue = new SvgSparkLine.DataSeries(data, NamedColor.BLUE);
        SvgSparkLine.DataSeries red = new SvgSparkLine.DataSeries(data, NamedColor.RED);

        assertNotEquals(blue.color(), red.color());
        assertEquals(blue.data(), red.data());
    }

    @Test
    public void testDataPointListCreation() {
        List<DataPoint> points = Arrays.asList(
                DataPoint.of(0, 100),
                DataPoint.of(1, 200),
                DataPoint.of(2, 150),
                DataPoint.of(3, 300)
        );

        assertEquals(4, points.size());
        assertEquals(100, points.get(0).y(), 0.001);
        assertEquals(300, points.get(3).y(), 0.001);
    }
}
